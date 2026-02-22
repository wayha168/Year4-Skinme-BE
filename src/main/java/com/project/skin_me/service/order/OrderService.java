package com.project.skin_me.service.order;

import com.project.skin_me.dto.OrderDto;
import com.project.skin_me.dto.RealTimeUpdateDto;
import com.project.skin_me.enums.OrderStatus;
import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.model.*;
import com.project.skin_me.repository.OrderRepository;
import com.project.skin_me.repository.PaymentRepository;
import com.project.skin_me.repository.ProductRepository;
import com.project.skin_me.enums.ActivityType;
import com.project.skin_me.model.Activity;
import com.project.skin_me.repository.ActivityRepository;
import com.project.skin_me.service.cart.ICartService;
import com.project.skin_me.service.notification.NotificationService;
import com.project.skin_me.service.popularProduct.IPopularProductService;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderService implements IOrderService {

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final PaymentRepository paymentRepository;
    private final ModelMapper modelMapper;
    private final ICartService cartService;
    private final IPopularProductService popularProductService;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;
    private final ActivityRepository activityRepository;

    @Override
    @Transactional
    public Order placeOrderItem(Long userId) {
        Cart cart = cartService.getCartByUserId(userId);
        Order order = createOrder(cart);

        List<OrderItem> orderItemList = createOrderItems(order, cart);
        order.setOrderItems(new HashSet<>(orderItemList));
        order.setOrderTotalAmount(calculateTotalAmount(orderItemList));
        Order savedOrder = orderRepository.save(order);
        
        // Send WebSocket notification for order creation
        try {
            notificationService.notifyUser(
                userId.toString(),
                "Order Created",
                "Your order #" + savedOrder.getId() + " has been created successfully. Please proceed to payment.",
                "ORDER"
            );
            
            // Send real-time update
            RealTimeUpdateDto update = RealTimeUpdateDto.builder()
                .entityType("ORDER")
                .entityId(savedOrder.getId().toString())
                .action("CREATE")
                .timestamp(LocalDateTime.now())
                .affectedUsers(userId.toString())
                .data(Map.of(
                    "orderId", savedOrder.getId(),
                    "status", savedOrder.getOrderStatus().toString(),
                    "totalAmount", savedOrder.getOrderTotalAmount()
                ))
                .build();
            messagingTemplate.convertAndSend("/topic/orders", update);
        } catch (Exception e) {
            // Log but don't fail the order creation
            System.err.println("Failed to send order notification: " + e.getMessage());
        }

        // Record order placed in audit log
        try {
            Activity orderPlacedActivity = new Activity();
            orderPlacedActivity.setUser(savedOrder.getUser());
            orderPlacedActivity.setActivityType(ActivityType.ORDER_PLACED);
            orderPlacedActivity.setTimestamp(LocalDateTime.now());
            orderPlacedActivity.setDetails("Order placed - Order #" + savedOrder.getId() + " - Total: $" + savedOrder.getOrderTotalAmount());
            activityRepository.save(orderPlacedActivity);
        } catch (Exception e) {
            System.err.println("Failed to record order placed activity: " + e.getMessage());
        }

//        cartService.removeCart(cart.getId());
        return  savedOrder;
    }

    private Order createOrder(Cart cart) {

        for (CartItem item : cart.getItems()) {
            Product product = item.getProduct();
            if (product.getInventory() < item.getQuantity()) {
                throw new IllegalArgumentException("Insufficient stock for " + product.getName());            }
        }

        Order order = new Order();
        order.setUser(cart.getUser());
        order.setOrderStatus(OrderStatus.PENDING);
        order.setOrderDate(LocalDate.now());
        return order;
    }

    private List<OrderItem> createOrderItems(Order order, Cart cart) {
        // Do not deduct inventory here; move to payment confirmation
        return cart.getItems().stream().map(cartItem -> {
            return new OrderItem(
                    order,
                    cartItem.getProduct(),
                    cartItem.getQuantity(),
                    cartItem.getUnitPrice());
        }).toList();
    }

    private BigDecimal calculateTotalAmount(List<OrderItem> orderItemList) {

        return orderItemList.stream()
                .map(item -> item.getPrice().multiply(new BigDecimal(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    public OrderDto getOrder(Long orderId) {
        return orderRepository.findById(orderId).map(this::convertToDto)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    @Override
    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    public List<OrderDto> getUserOrders(Long userId){
        List<Order> orders = orderRepository.findByUserId(userId);
        return orders.stream().map(this::convertToDto).toList();
    }

    @Override
    public List<OrderDto> getAllUserOrders() {
        List<Order> orders = orderRepository.findAll();
        return orders.stream().map(this::convertToDto).toList();
    }

    @Override
    public OrderDto convertToDto(Order order) {
        return modelMapper.map(order, OrderDto.class);
    }

//    public void updatePopularProducts(List<OrderItem> items) {
//        for (OrderItem item : items) {
//            Product product = item.getProduct();
//            product.setTotalOrders(product.getTotalOrders() + item.getQuantity());
//
//            // Example rule: if ordered more than 50 times -> mark as popular
//            if (product.getTotalOrders() >= 50 && product.getPopularProduct() == null) {
//                PopularProduct popular = new PopularProduct();
//                popular.setSellRecord(product.getTotalOrders());
//                popularProductRepository.save(popular);
//                product.setPopularProduct(popular);
//            }
//
//            productRepository.save(product);
//        }
//    }

    @Transactional
    public void updateOrder(Order order) {
        orderRepository.save(order);
    }

    @Override
    public Optional<Order> getOrderByStripeSessionId(String sessionId) {
        return orderRepository.findByStripeSessionId(sessionId);
    }

    @Override
    @Transactional
    public void confirmOrderPayment(Order order) {
        if (order.getOrderStatus() != OrderStatus.PENDING && order.getOrderStatus() != OrderStatus.PAYMENT_PENDING) {
            return;
        }

        // Deduct inventory
        for (OrderItem item : order.getOrderItems()) {
            Product product = item.getProduct();
            if (product.getInventory() < item.getQuantity()) {
                throw new IllegalArgumentException("Insufficient stock for " + product.getName() + " during confirmation");
            }
            product.setInventory(product.getInventory() - item.getQuantity());
            productRepository.save(product);
        }
        // Update popular products
        popularProductService.saveFromOrder(order);
        // Remove cart
        Cart cart = cartService.getCartByUserId(order.getUser().getId());
        if (cart != null) {
            cartService.removeCart(cart.getId());
        }
        // Update payment record
        Payment payment = paymentRepository.findByTransactionRef(order.getStripeSessionId())
                .orElseThrow(() -> new IllegalArgumentException("Payment not found for sessionId: " + order.getStripeSessionId()));
        payment.setStatus(OrderStatus.SUCCESS);
        payment.setTransactionTime(LocalDateTime.now());
        paymentRepository.save(payment);

        // Update order status
        order.setOrderStatus(OrderStatus.PAID);
        updateOrder(order);
        
        // Record payment success and purchase in audit log
        try {
            Activity paymentSuccessActivity = new Activity();
            paymentSuccessActivity.setUser(order.getUser());
            paymentSuccessActivity.setActivityType(ActivityType.PAYMENT_SUCCESS);
            paymentSuccessActivity.setTimestamp(LocalDateTime.now());
            paymentSuccessActivity.setDetails("Payment success - Order #" + order.getId() + " - Total: $" + order.getOrderTotalAmount());
            activityRepository.save(paymentSuccessActivity);

            Activity purchaseActivity = new Activity();
            purchaseActivity.setUser(order.getUser());
            purchaseActivity.setActivityType(ActivityType.PURCHASE);
            purchaseActivity.setTimestamp(LocalDateTime.now());
            purchaseActivity.setDetails("Purchase completed - Order #" + order.getId() + " - Total: $" + order.getOrderTotalAmount());
            activityRepository.save(purchaseActivity);
        } catch (Exception e) {
            System.err.println("Failed to record payment/purchase activity: " + e.getMessage());
        }
        
        // Send WebSocket notification for payment confirmation
        try {
            notificationService.notifyOrderStatusChange(
                order.getUser().getId().toString(),
                order.getId().toString(),
                "PAID"
            );
            
            // Send real-time update
            RealTimeUpdateDto update = RealTimeUpdateDto.builder()
                .entityType("ORDER")
                .entityId(order.getId().toString())
                .action("UPDATE")
                .timestamp(LocalDateTime.now())
                .affectedUsers(order.getUser().getId().toString())
                .data(Map.of(
                    "orderId", order.getId(),
                    "status", "PAID",
                    "message", "Payment confirmed successfully"
                ))
                .build();
            messagingTemplate.convertAndSend("/topic/orders", update);
        } catch (Exception e) {
            System.err.println("Failed to send payment confirmation notification: " + e.getMessage());
        }
    }
    @Override
    @Transactional
    public Order markAsShipped(Long orderId, String trackingNumber) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

        if (trackingNumber == null || trackingNumber.isBlank()) {
            trackingNumber = "TRK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }

        order.setTrackingNumber(trackingNumber);
        order.setOrderStatus(OrderStatus.SHIPPED);
        order.setShippedAt(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);
        
        // Send WebSocket notification for shipping
        try {
            notificationService.notifyDeliveryUpdate(
                order.getUser().getId().toString(),
                order.getId().toString(),
                "SHIPPED - Tracking: " + trackingNumber
            );
            
            // Send real-time update
            RealTimeUpdateDto update = RealTimeUpdateDto.builder()
                .entityType("ORDER")
                .entityId(order.getId().toString())
                .action("UPDATE")
                .timestamp(LocalDateTime.now())
                .affectedUsers(order.getUser().getId().toString())
                .data(Map.of(
                    "orderId", order.getId(),
                    "status", "SHIPPED",
                    "trackingNumber", trackingNumber
                ))
                .build();
            messagingTemplate.convertAndSend("/topic/orders", update);
        } catch (Exception e) {
            System.err.println("Failed to send shipping notification: " + e.getMessage());
        }
        
        return savedOrder;
    }

    @Override
    @Transactional
    public Order markAsDelivered(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

        order.setOrderStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);
        
        // Send WebSocket notification for delivery
        try {
            notificationService.notifyDeliveryUpdate(
                order.getUser().getId().toString(),
                order.getId().toString(),
                "DELIVERED"
            );
            
            // Send real-time update
            RealTimeUpdateDto update = RealTimeUpdateDto.builder()
                .entityType("ORDER")
                .entityId(order.getId().toString())
                .action("UPDATE")
                .timestamp(LocalDateTime.now())
                .affectedUsers(order.getUser().getId().toString())
                .data(Map.of(
                    "orderId", order.getId(),
                    "status", "DELIVERED",
                    "message", "Your order has been delivered"
                ))
                .build();
            messagingTemplate.convertAndSend("/topic/orders", update);
        } catch (Exception e) {
            System.err.println("Failed to send delivery notification: " + e.getMessage());
        }
        
        return savedOrder;
    }
}
