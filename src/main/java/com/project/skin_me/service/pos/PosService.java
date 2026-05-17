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
        // POS UI can click the payment button multiple times; be idempotent.
        if (order.getOrderStatus() == OrderStatus.PAID || order.getOrderStatus() == OrderStatus.DELIVERED) {
            Order loaded = orderRepository.findByIdWithOrderItemsAndProducts(order.getOrderId()).orElse(order);
            Payment existingPayment = paymentRepository.findByOrder(loaded).orElse(null);

            String receipt = buildReceiptMarkdown(loaded, displayName(cashier));
            Map<String, Object> result = new HashMap<>();
            result.put("orderId", loaded.getOrderId());
            result.put("paymentId", existingPayment != null ? existingPayment.getId() : null);
            result.put("status", loaded.getOrderStatus() != null ? loaded.getOrderStatus().name() : "PAID");
            result.put("receiptMarkdown", receipt);
            return result;
        }

        String txRef = "POS-" + method.name() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Payment payment = paymentRepository.findByOrder(order).orElse(null);
        if (payment == null) {
            payment = Payment.builder()
                    .order(order)
                    .amount(order.getOrderTotalAmount())
                    .method(method)
                    .status(OrderStatus.SUCCESS)
                    .transactionRef(txRef)
                    .transactionTime(LocalDateTime.now())
                    .message("POS " + method.name() + " — cashier: " + displayName(cashier))
                    .build();
        } else {
            payment.setMethod(method);
            payment.setAmount(order.getOrderTotalAmount());
            payment.setStatus(OrderStatus.SUCCESS);
            payment.setTransactionRef(txRef);
            payment.setTransactionTime(LocalDateTime.now());
            payment.setMessage("POS " + method.name() + " — cashier: " + displayName(cashier));
        }

        if (method == PaymentMethod.CREDIT_CARD && cardLast4 != null && !cardLast4.isBlank()) {
            String digits = cardLast4.replaceAll("\\D", "");
            if (digits.length() >= 4) {
                payment.setCardLast4(digits.substring(digits.length() - 4));
            }
            payment.setCardBrand("POS_CARD");
        }

        paymentRepository.save(payment);

        orderService.confirmOrderPayment(order);
        if (isPickupOrder(order)) {
            markPickupDelivered(order);
        }

        String receipt = buildReceiptMarkdown(order, displayName(cashier));

        Map<String, Object> result = new HashMap<>();
        result.put("orderId", order.getOrderId());
        result.put("paymentId", payment.getId());
        result.put("status", order.getOrderStatus().name());
        result.put("receiptMarkdown", receipt);
        return result;
    }

    @Override
    public String buildReceiptMarkdown(Order order, String cashierDisplayName) {
        Order loaded = orderRepository.findByIdWithOrderItemsAndProducts(order.getOrderId())
                .orElse(order);

        DateTimeFormatter dt = DateTimeFormatter.ofPattern("MMM dd, yyyy HH:mm");
        StringBuilder md = new StringBuilder();
        md.append("# ").append(shopName).append("\n\n");
        md.append("**").append(shopAddress).append("**\n");
        if (shopPhone != null && !shopPhone.isBlank()) {
            md.append("Tel: ").append(shopPhone).append("\n");
        }
        md.append("\n---\n\n");
        md.append("| | |\n|---|---|\n");
        md.append("| **Order** | #").append(loaded.getOrderId()).append(" |\n");
        md.append("| **Date** | ").append(LocalDateTime.now().format(dt)).append(" |\n");
        md.append("| **Cashier** | ").append(cashierDisplayName != null ? cashierDisplayName : "—").append(" |\n");
        md.append("| **Fulfillment** | Pickup (in-store) |\n\n");
        md.append("### Items\n\n");
        md.append("| Product | Qty | Price | Total |\n");
        md.append("|---------|-----|-------|-------|\n");

        if (loaded.getOrderItems() != null) {
            for (OrderItem item : loaded.getOrderItems()) {
                String name = item.getProduct() != null ? item.getProduct().getName() : "Item";
                BigDecimal unit = item.getPrice() != null ? item.getPrice() : BigDecimal.ZERO;
                BigDecimal line = unit.multiply(BigDecimal.valueOf(item.getQuantity()));
                md.append("| ").append(escapeMdCell(name)).append(" | ")
                        .append(item.getQuantity()).append(" | $")
                        .append(unit).append(" | $").append(line).append(" |\n");
            }
        }

        md.append("\n---\n\n");
        md.append("**Subtotal:** $").append(loaded.getItemsSubtotalAmount() != null ? loaded.getItemsSubtotalAmount()
                : loaded.getOrderTotalAmount()).append("\n\n");
        md.append("**Delivery:** $0.00 *(pickup)*\n\n");
        md.append("## **Total: $").append(loaded.getOrderTotalAmount()).append("**\n\n");

        Payment pay = paymentRepository.findByOrder(loaded).orElse(null);
        if (pay != null) {
            md.append("**Payment:** ")
                    .append(pay.getMethod() != null ? pay.getMethod().name().replace('_', ' ') : "—")
                    .append("\n\n");
        }

        md.append("---\n\n");
        md.append("*Thank you for shopping at ").append(shopName).append("!*\n");
        return md.toString();
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
        if (text == null) {
            return "";
        }
        return text.replace("|", "\\|");
    }
}
