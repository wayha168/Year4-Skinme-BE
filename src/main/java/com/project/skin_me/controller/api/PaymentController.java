package com.project.skin_me.controller.api;

import com.project.skin_me.dto.RealTimeUpdateDto;
import com.project.skin_me.enums.OrderStatus;
import com.project.skin_me.enums.PaymentMethod;
import com.project.skin_me.model.Order;
import com.project.skin_me.model.Payment;
import com.project.skin_me.repository.OrderRepository;
import com.project.skin_me.repository.PaymentRepository;
import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.service.checkout.ICheckoutService;
import com.project.skin_me.service.notification.NotificationService;
import com.project.skin_me.service.order.IOrderService;
import com.project.skin_me.service.payment.KhqrService;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("${api.prefix}/payment")
public class PaymentController {

    private final ICheckoutService checkoutService;
    private final IOrderService orderService;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final KhqrService khqrService;
    private final NotificationService notificationService;
    private final SimpMessagingTemplate messagingTemplate;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    @PostMapping("/create-checkout-session/{userId}")
    public ResponseEntity<ApiResponse> createCheckoutSession(@PathVariable Long userId) {
        try {
            Order order = orderService.placeOrderItem(userId);
            long amountInCents = order.getOrderTotalAmount()
                    .multiply(BigDecimal.valueOf(100))
                    .longValue();

            Session session = checkoutService.createCheckoutSession(order.getId(), amountInCents);

            order.setStripeSessionId(session.getId());
            order.setOrderStatus(OrderStatus.PAYMENT_PENDING);
            orderService.updateOrder(order);

            // Save initial payment record
            Payment payment = Payment.builder()
                    .order(order)
                    .amount(order.getOrderTotalAmount())
                    .method(PaymentMethod.CREDIT_CARD)
                    .status(OrderStatus.PENDING)
                    .transactionRef(session.getId())
                    .transactionTime(LocalDateTime.now())
                    .build();
            paymentRepository.save(payment);

            // Send WebSocket notification for payment pending
            try {
                notificationService.notifyUser(
                    order.getUser().getId().toString(),
                    "Payment Pending",
                    "Your payment for order #" + order.getId() + " is pending. Please complete the payment.",
                    "PAYMENT"
                );
                
                RealTimeUpdateDto update = RealTimeUpdateDto.builder()
                    .entityType("PAYMENT")
                    .entityId(payment.getId().toString())
                    .action("CREATE")
                    .timestamp(LocalDateTime.now())
                    .affectedUsers(order.getUser().getId().toString())
                    .data(Map.of(
                        "orderId", order.getId(),
                        "paymentId", payment.getId(),
                        "status", "PENDING",
                        "checkoutUrl", session.getUrl()
                    ))
                    .build();
                messagingTemplate.convertAndSend("/topic/orders", update);
            } catch (Exception e) {
                System.err.println("Failed to send payment pending notification: " + e.getMessage());
            }

            Map<String, Object> data = Map.of(
                    "checkoutUrl", session.getUrl(),
                    "orderId", order.getId(),
                    "sessionId", session.getId()
            );
            return ResponseEntity.ok(new ApiResponse("Stripe checkout session created", data));
        } catch (StripeException e) {
            return ResponseEntity.status(500)
                    .body(new ApiResponse("Stripe error: " + e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(new ApiResponse("Error: " + e.getMessage(), null));
        }
    }

    @PostMapping("/create-payment-intent")
    public ResponseEntity<ApiResponse> createPaymentIntent(@RequestBody Map<String, Object> request) {
        try {
            Long orderId = Long.parseLong(request.get("orderId").toString());
            Long amountCents = Long.parseLong(request.get("amountCents").toString());

            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Order not found"));

            // Create PaymentIntent
            PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                    .setAmount(amountCents)
                    .setCurrency("usd")
                    .setDescription("Skin.me Order #" + order.getOrderId())
                    .putMetadata("orderId", orderId.toString())
                    .putMetadata("orderNumber", order.getOrderId().toString())
                    .build();

            PaymentIntent paymentIntent = PaymentIntent.create(params);

            // Save payment record
            Payment payment = Payment.builder()
                    .order(order)
                    .amount(order.getOrderTotalAmount())
                    .method(PaymentMethod.CREDIT_CARD)
                    .status(OrderStatus.PENDING)
                    .transactionRef(paymentIntent.getId())
                    .transactionTime(LocalDateTime.now())
                    .build();
            paymentRepository.save(payment);

            Map<String, Object> data = new HashMap<>();
            data.put("clientSecret", paymentIntent.getClientSecret());
            data.put("paymentIntentId", paymentIntent.getId());
            data.put("orderId", orderId);

            return ResponseEntity.ok(new ApiResponse("Payment intent created", data));
        } catch (StripeException e) {
            return ResponseEntity.status(500)
                    .body(new ApiResponse("Stripe error: " + e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(new ApiResponse("Error: " + e.getMessage(), null));
        }
    }

    @PostMapping("/confirm-payment/{paymentIntentId}")
    public ResponseEntity<ApiResponse> confirmPayment(@PathVariable String paymentIntentId) {
        try {
            PaymentIntent paymentIntent = PaymentIntent.retrieve(paymentIntentId);

            if ("succeeded".equals(paymentIntent.getStatus())) {
                // Find order by payment intent ID
                Payment payment = paymentRepository.findByTransactionRef(paymentIntentId)
                        .orElseThrow(() -> new IllegalArgumentException("Payment not found"));

                Order order = payment.getOrder();
                payment.setStatus(OrderStatus.SUCCESS);
                payment.setTransactionTime(LocalDateTime.now());
                paymentRepository.save(payment);

                orderService.confirmOrderPayment(order);

                // Send WebSocket notification for payment success
                try {
                    notificationService.notifyUser(
                        order.getUser().getId().toString(),
                        "Payment Successful",
                        "Your payment for order #" + order.getId() + " has been confirmed successfully!",
                        "PAYMENT"
                    );
                    
                    RealTimeUpdateDto update = RealTimeUpdateDto.builder()
                        .entityType("PAYMENT")
                        .entityId(payment.getId().toString())
                        .action("UPDATE")
                        .timestamp(LocalDateTime.now())
                        .affectedUsers(order.getUser().getId().toString())
                        .data(Map.of(
                            "orderId", order.getId(),
                            "paymentId", payment.getId(),
                            "status", "SUCCESS",
                            "message", "Payment confirmed successfully"
                        ))
                        .build();
                    messagingTemplate.convertAndSend("/topic/orders", update);
                } catch (Exception e) {
                    System.err.println("Failed to send payment success notification: " + e.getMessage());
                }

                Map<String, Object> data = new HashMap<>();
                data.put("orderId", order.getId());
                data.put("paymentIntentId", paymentIntentId);
                data.put("status", "succeeded");

                return ResponseEntity.ok(new ApiResponse("Payment confirmed", data));
            } else {
                return ResponseEntity.status(400)
                        .body(new ApiResponse("Payment not succeeded. Status: " + paymentIntent.getStatus(), null));
            }
        } catch (StripeException e) {
            return ResponseEntity.status(500)
                    .body(new ApiResponse("Stripe error: " + e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(new ApiResponse("Error: " + e.getMessage(), null));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse> handleWebhook(@RequestBody String payload, @RequestHeader("Stripe-Signature") String sigHeader) {
        try {
            Event event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            Map<String, Object> processedEvents = new HashMap<>();
            
            // Handle checkout session completed
            if ("checkout.session.completed".equals(event.getType())) {
                Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
                if (session != null) {
                    orderService.getOrderByStripeSessionId(session.getId())
                            .ifPresent(order -> {
                                Payment payment = paymentRepository.findByTransactionRef(session.getId())
                                        .orElseThrow(() -> new IllegalArgumentException("Payment not found for sessionId: " + session.getId()));
                                payment.setStatus(OrderStatus.SUCCESS);
                                payment.setTransactionTime(LocalDateTime.now());
                                paymentRepository.save(payment);
                                orderService.confirmOrderPayment(order);
                                
                                // Send WebSocket notification via orderService.confirmOrderPayment
                                // which already handles notifications
                                
                                processedEvents.put("checkout_session", session.getId());
                            });
                }
            }
            
            // Handle payment intent succeeded
            if ("payment_intent.succeeded".equals(event.getType())) {
                PaymentIntent paymentIntent = (PaymentIntent) event.getDataObjectDeserializer().getObject().orElse(null);
                if (paymentIntent != null) {
                    Payment payment = paymentRepository.findByTransactionRef(paymentIntent.getId())
                            .orElseThrow(() -> new IllegalArgumentException("Payment not found for paymentIntentId: " + paymentIntent.getId()));
                    Order order = payment.getOrder();
                    payment.setStatus(OrderStatus.SUCCESS);
                    payment.setTransactionTime(LocalDateTime.now());
                    paymentRepository.save(payment);
                    orderService.confirmOrderPayment(order);
                    processedEvents.put("payment_intent", paymentIntent.getId());
                }
            }
            
            processedEvents.put("event_type", event.getType());
            processedEvents.put("processed", true);
            
            return ResponseEntity.ok(new ApiResponse("Webhook processed successfully", processedEvents));
        } catch (Exception e) {
            return ResponseEntity.status(400)
                    .body(new ApiResponse("Webhook error: " + e.getMessage(), null));
        }
    }

    @GetMapping("/generate-khqr")
    public ResponseEntity<ApiResponse> generateKhqr(
            @RequestParam Long orderId,
            @RequestParam Double amount,
            @RequestParam(defaultValue = "USD") String currency) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Order not found"));

            BigDecimal amountDecimal = BigDecimal.valueOf(amount);
            Map<String, String> khqrData = khqrService.generateKhqrForOrder(amountDecimal, currency);

            // Create or update payment record for KHQR
            Payment payment = paymentRepository.findByOrder(order).orElse(null);
            if (payment == null) {
                payment = Payment.builder()
                        .order(order)
                        .amount(amountDecimal)
                        .method(PaymentMethod.KHQR)
                        .status(OrderStatus.PENDING)
                        .transactionRef("KHQR-" + orderId + "-" + System.currentTimeMillis())
                        .transactionTime(LocalDateTime.now())
                        .build();
            } else {
                payment.setMethod(PaymentMethod.KHQR);
                payment.setAmount(amountDecimal);
                payment.setStatus(OrderStatus.PENDING);
                payment.setTransactionTime(LocalDateTime.now());
            }
            paymentRepository.save(payment);

            Map<String, Object> responseData = new HashMap<>();
            responseData.put("qrData", khqrData.get("qrData"));
            responseData.put("qrImage", khqrData.get("qrImage"));
            responseData.put("amount", khqrData.get("amount"));
            responseData.put("currency", khqrData.get("currency"));
            responseData.put("orderId", orderId);
            responseData.put("paymentId", payment.getId());

            return ResponseEntity.ok(new ApiResponse("KHQR QR code generated successfully", responseData));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(new ApiResponse("Error generating KHQR: " + e.getMessage(), null));
        }
    }

    @PostMapping("/verify-khqr/{orderId}")
    public ResponseEntity<ApiResponse> verifyKhqrPayment(@PathVariable Long orderId) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new IllegalArgumentException("Order not found"));

            Payment payment = paymentRepository.findByOrder(order)
                    .orElseThrow(() -> new IllegalArgumentException("Payment not found"));

            if (payment.getMethod() != PaymentMethod.KHQR) {
                return ResponseEntity.status(400)
                        .body(new ApiResponse("Payment method is not KHQR", null));
            }

            // In a real implementation, you would verify the payment with the bank/merchant API
            // For now, we'll mark it as pending and require manual verification
            // payment.setStatus(OrderStatus.SUCCESS);
            // payment.setTransactionTime(LocalDateTime.now());
            // paymentRepository.save(payment);
            // orderService.confirmOrderPayment(order);

            Map<String, Object> data = new HashMap<>();
            data.put("orderId", orderId);
            data.put("status", "pending_verification");
            data.put("message", "Payment verification pending. Please contact support with your transaction reference.");

            return ResponseEntity.ok(new ApiResponse("KHQR payment verification initiated", data));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(new ApiResponse("Error verifying KHQR payment: " + e.getMessage(), null));
        }
    }
}
