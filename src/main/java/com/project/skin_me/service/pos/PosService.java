package com.project.skin_me.service.pos;

import com.project.skin_me.dto.PosCalculateResultDto;
import com.project.skin_me.dto.PosLineDetailDto;
import com.project.skin_me.dto.PosLineItemDto;
import com.project.skin_me.enums.OrderStatus;
import com.project.skin_me.enums.PaymentMethod;
import com.project.skin_me.enums.ProductStatus;
import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.model.Order;
import com.project.skin_me.model.OrderItem;
import com.project.skin_me.model.Payment;
import com.project.skin_me.model.Product;
import com.project.skin_me.model.User;
import com.project.skin_me.repository.OrderRepository;
import com.project.skin_me.repository.PaymentRepository;
import com.project.skin_me.repository.ProductRepository;
import com.project.skin_me.service.order.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PosService implements IPosService {

    private static final String PICKUP_LABEL = "PICKUP - In-store";
    private static final String DELIVERY_LABEL = "DELIVERY";

    private final ProductRepository productRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final IOrderService orderService;

    @Value("${app.pos.shop-name:SkinMe Store}")
    private String shopName;

    @Value("${app.pos.shop-address:Phnom Penh, Cambodia}")
    private String shopAddress;

    @Value("${app.pos.shop-phone:}")
    private String shopPhone;

    @Override
    public PosCalculateResultDto calculate(List<PosLineItemDto> items) {
        if (items == null || items.isEmpty()) {
            return PosCalculateResultDto.builder()
                    .subtotal(BigDecimal.ZERO)
                    .deliveryFee(BigDecimal.ZERO)
                    .total(BigDecimal.ZERO)
                    .itemCount(0)
                    .lines(List.of())
                    .build();
        }
        List<PosLineDetailDto> lines = buildLineDetails(items);
        BigDecimal subtotal = lines.stream()
                .map(PosLineDetailDto::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int itemCount = lines.stream().mapToInt(PosLineDetailDto::getQuantity).sum();
        return PosCalculateResultDto.builder()
                .subtotal(subtotal)
                .deliveryFee(BigDecimal.ZERO)
                .total(subtotal)
                .itemCount(itemCount)
                .lines(lines)
                .build();
    }

    @Override
    @Transactional
    public Order createPosOrder(User cashier, List<PosLineItemDto> items, String fulfillmentType) {

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }
        PosCalculateResultDto calc = calculate(items);
        if (calc.getTotal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Order total must be greater than zero");
        }

        Order order = new Order();
        order.setUser(cashier);
        order.setOrderDate(LocalDate.now());
        order.setOrderStatus(OrderStatus.PAYMENT_PENDING);
        order.setDeliveryAddressFull(resolveFulfillmentLabel(fulfillmentType));

        order.setItemsSubtotalAmount(calc.getSubtotal());
        order.setDeliveryFeeAmount(BigDecimal.ZERO);
        order.setOrderTotalAmount(calc.getTotal());
        order.setPosOrder(true);

        List<OrderItem> orderItems = new ArrayList<>();
        for (PosLineDetailDto line : calc.getLines()) {
            Product product = productRepository.findById(line.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + line.getProductId()));
            if (product.getInventory() < line.getQuantity()) {
                throw new IllegalArgumentException("Insufficient stock for " + product.getName());
            }
            orderItems.add(new OrderItem(order, product, line.getQuantity(), line.getUnitPrice()));
        }
        order.setOrderItems(new HashSet<>(orderItems));
        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public Map<String, Object> completePayment(Order order, User cashier, PaymentMethod method, String cardLast4) {
        Long orderId = order != null ? order.getOrderId() : null;
        if (orderId == null) {
            throw new IllegalArgumentException("Invalid order");
        }

        Order managed = orderRepository.findByIdWithOrderItemsAndProducts(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        // POS UI can click the payment button multiple times; be idempotent.
        if (managed.getOrderStatus() == OrderStatus.PAID || managed.getOrderStatus() == OrderStatus.DELIVERED) {
            if (isPickupOrder(managed) && managed.getOrderStatus() == OrderStatus.PAID) {
                markPickupDelivered(managed);
                managed = orderRepository.findByIdWithOrderItemsAndProducts(orderId).orElse(managed);
            }
            Payment existingPayment = paymentRepository.findByOrderId(orderId).orElse(null);

            String receipt = buildReceiptMarkdown(managed, displayName(cashier));
            Map<String, Object> result = new HashMap<>();
            result.put("orderId", managed.getOrderId());
            result.put("paymentId", existingPayment != null ? existingPayment.getId() : null);
            result.put("status", managed.getOrderStatus() != null ? managed.getOrderStatus().name() : "PAID");
            result.put("receiptMarkdown", receipt);
            return result;
        }

        if (managed.getOrderStatus() != OrderStatus.PAYMENT_PENDING
                && managed.getOrderStatus() != OrderStatus.PENDING) {
            throw new IllegalStateException(
                    "Order cannot be paid in status: " + managed.getOrderStatus());
        }

        String txRef = "POS-" + method.name() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String cashierName = displayName(cashier);

        Payment payment = paymentRepository.findByOrderId(orderId).orElse(null);
        if (payment == null) {
            payment = Payment.builder()
                    .order(managed)
                    .amount(managed.getOrderTotalAmount())
                    .method(method)
                    .status(OrderStatus.SUCCESS)
                    .transactionRef(txRef)
                    .transactionTime(LocalDateTime.now())
                    .message("POS " + method.name() + " - cashier: " + cashierName)
                    .build();
        } else {
            payment.setOrder(managed);
            payment.setMethod(method);
            payment.setAmount(managed.getOrderTotalAmount());
            payment.setStatus(OrderStatus.SUCCESS);
            payment.setTransactionRef(txRef);
            payment.setTransactionTime(LocalDateTime.now());
            payment.setMessage("POS " + method.name() + " - cashier: " + cashierName);
        }

        if (method == PaymentMethod.CREDIT_CARD && cardLast4 != null && !cardLast4.isBlank()) {
            String digits = cardLast4.replaceAll("\\D", "");
            if (digits.length() >= 4) {
                payment.setCardLast4(digits.substring(digits.length() - 4));
            }
            payment.setCardBrand("POS_CARD");
        }

        paymentRepository.save(payment);

        orderService.confirmOrderPayment(managed);
        if (isPickupOrder(managed)) {
            markPickupDelivered(managed);
        }

        Order fresh = orderRepository.findByIdWithOrderItemsAndProducts(orderId).orElse(managed);
        String receipt = buildReceiptMarkdown(fresh, cashierName);

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", fresh.getOrderId());
        result.put("paymentId", payment.getId());
        result.put("status", fresh.getOrderStatus() != null ? fresh.getOrderStatus().name() : OrderStatus.PAID.name());
        result.put("receiptMarkdown", receipt);
        return result;
    }

    @Override
    public String buildOrderSummaryMarkdown(Order order, String cashierDisplayName) {
        Order loaded = orderRepository.findByIdWithOrderItemsAndProducts(order.getOrderId())
                .orElse(order);
        return buildInvoiceMarkdown(loaded, cashierDisplayName, true, null);
    }

    @Override
    public String buildReceiptMarkdown(Order order, String cashierDisplayName) {
        Order loaded = orderRepository.findByIdWithOrderItemsAndProducts(order.getOrderId())
                .orElse(order);
        Payment pay = paymentRepository.findByOrderId(loaded.getOrderId()).orElse(null);
        return buildInvoiceMarkdown(loaded, cashierDisplayName, false, pay);
    }

    private String buildInvoiceMarkdown(Order loaded, String cashierDisplayName, boolean awaitingPayment,
            Payment pay) {
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("dd.MM.yyyy");
        String invoiceNo = loaded.getOrderId() != null ? "INV-" + String.format("%04d", loaded.getOrderId()) : "INV-0000";
        String issueDate = LocalDateTime.now().format(dateFmt);
        String cashier = cashierDisplayName != null ? cashierDisplayName : "—";
        String fulfillment = isPickupOrder(loaded) ? "Pickup (in-store)" : "Delivery";

        StringBuilder md = new StringBuilder();
        md.append("<div class=\"pos-invoice\">\n\n");
        md.append("<div class=\"pos-invoice-header\">\n\n");
        md.append("## ").append(escapeMdInline(shopName.toUpperCase())).append("\n\n");
        md.append(escapeMdInline(shopAddress)).append("  \n");
        if (shopPhone != null && !shopPhone.isBlank()) {
            md.append("Tel: ").append(escapeMdInline(shopPhone)).append("  \n");
        }
        md.append("\n</div>\n\n");

        md.append("<div class=\"pos-invoice-meta\">\n\n");
        md.append("| **BILL TO** | **INVOICE** |\n");
        md.append("|:---|---:|\n");
        md.append("| Walk-in customer | **Invoice No:** ").append(invoiceNo).append(" |\n");
        md.append("| ").append(fulfillment).append(" | **Issue Date:** ").append(issueDate).append(" |\n");
        md.append("| Cashier: ").append(escapeMdInline(cashier)).append(" | ");
        if (awaitingPayment) {
            md.append("**Status:** Awaiting payment |\n");
        } else if (pay != null) {
            md.append("**Payment:** ").append(formatPaymentMethod(pay.getMethod())).append(" |\n");
        } else {
            md.append("**Status:** Paid |\n");
        }
        md.append("\n</div>\n\n");

        md.append("| Description | Quantity | Unit Price | Amount |\n");
        md.append("|:---|---:|---:|---:|\n");
        if (loaded.getOrderItems() != null && !loaded.getOrderItems().isEmpty()) {
            for (OrderItem item : loaded.getOrderItems()) {
                String name = item.getProduct() != null ? item.getProduct().getName() : "Item";
                BigDecimal unit = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
                BigDecimal line = unit.multiply(BigDecimal.valueOf(item.getQuantity()));
                md.append("| ").append(escapeMdCell(name)).append(" | ")
                        .append(item.getQuantity()).append(" | ")
                        .append(formatMoney(unit)).append(" | ")
                        .append(formatMoney(line)).append(" |\n");
            }
        } else {
            md.append("| — | — | — | — |\n");
        }

        BigDecimal subtotal = loaded.getItemsSubtotalAmount() != null
                ? loaded.getItemsSubtotalAmount()
                : loaded.getOrderTotalAmount();
        md.append("\n<div class=\"pos-invoice-totals\">\n\n");
        md.append("| | |\n");
        md.append("|:---|---:|\n");
        md.append("| **Subtotal** | ").append(formatMoney(subtotal)).append(" |\n");
        md.append("| **Delivery** | $0.00 |\n");
        md.append("| **Total** | **").append(formatMoney(loaded.getOrderTotalAmount())).append("** |\n");
        md.append("\n</div>\n\n");

        if (awaitingPayment) {
            md.append("*Tap **Process payment** to complete this sale.*\n\n");
        } else {
            md.append("*Thank you for shopping at ").append(escapeMdInline(shopName)).append("!*\n\n");
        }
        md.append("</div>\n");
        return md.toString();
    }

    private static String escapeMdInline(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("|", "\\|");
    }

    private static String formatMoney(BigDecimal amount) {
        if (amount == null) {
            return "$0.00";
        }
        return "$" + amount.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static String formatPaymentMethod(PaymentMethod method) {
        if (method == null) {
            return "—";
        }
        return method.name().replace('_', ' ');
    }

    private boolean isPickupOrder(Order order) {
        if (order == null || order.getDeliveryAddressFull() == null) {
            return true;
        }
        return order.getDeliveryAddressFull().contains("PICKUP");
    }

    private String resolveFulfillmentLabel(String fulfillmentType) {
        if (fulfillmentType == null) {
            return PICKUP_LABEL;
        }
        String v = fulfillmentType.trim().toUpperCase();
        return ("DELIVERY".equals(v) || "SHIPMENT".equals(v)) ? DELIVERY_LABEL : PICKUP_LABEL;
    }

    private void markPickupDelivered(Order order) {

        Order managed = orderRepository.findById(order.getOrderId()).orElse(order);
        managed.setOrderStatus(OrderStatus.DELIVERED);
        managed.setDeliveredAt(LocalDateTime.now());
        managed.setTrackingNumber("PICKUP");
        orderRepository.save(managed);
    }

    @Override
    @Transactional
    public void cancelPosOrder(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("orderId is required");
        }

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        // Only cancel if it’s still pending; never override already-paid/completed states.
        if (order.getOrderStatus() == OrderStatus.PAID
                || order.getOrderStatus() == OrderStatus.SUCCESS
                || order.getOrderStatus() == OrderStatus.DELIVERED
                || order.getOrderStatus() == OrderStatus.COMPLETED  
                || order.getOrderStatus() == OrderStatus.CANCELLED) {

            return;
        }

        order.setOrderStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
    }


    private List<PosLineDetailDto> buildLineDetails(List<PosLineItemDto> items) {
        List<PosLineDetailDto> lines = new ArrayList<>();
        for (PosLineItemDto item : items) {
            if (item.getProductId() == null || item.getQuantity() <= 0) {
                continue;
            }
            Product product = productRepository.findById(item.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + item.getProductId()));
            if (product.getStatus() != null && product.getStatus() != ProductStatus.ACTIVE) {
                throw new IllegalArgumentException("Product is not active: " + product.getName());
            }
            BigDecimal unitPrice = product.getPrice() != null ? product.getPrice() : BigDecimal.ZERO;
            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(item.getQuantity()));
            lines.add(PosLineDetailDto.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .barcode(product.getBarcode())
                    .quantity(item.getQuantity())
                    .unitPrice(unitPrice)
                    .lineTotal(lineTotal)
                    .inventory(product.getInventory())
                    .build());
        }
        return lines;
    }

    private static String displayName(User user) {
        if (user == null) {
            return "Admin";
        }
        String first = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String last = user.getLastName() != null ? user.getLastName().trim() : "";
        String full = (first + " " + last).trim();
        if (!full.isEmpty()) {
            return full;
        }
        return user.getEmail() != null ? user.getEmail() : "Admin";
    }

    private static String escapeMdCell(String text) {
        return escapeMdInline(text);
    }
}
