package com.project.skin_me.controller.api;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.project.skin_me.dto.OrderDto;
import com.project.skin_me.dto.RealTimeUpdateDto;
import com.project.skin_me.enums.OrderStatus;
import com.project.skin_me.enums.PaymentMethod;
import com.project.skin_me.exception.ResourceNotFoundException;
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

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @PostMapping("/create-checkout-session/{userId}")
    public ResponseEntity<ApiResponse> createCheckoutSession(
            @PathVariable Long userId,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            Order order = orderService.placeOrderItem(userId);
            if (body != null) {
                applyDeliveryToOrder(order, body);
                orderService.updateOrder(order);
            }
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

            try {
                notificationService.notifyUser(
                        order.getUser().getId().toString(),
                        "Payment Pending",
                        "Your payment for order #" + order.getId() + " is pending. Please complete the payment.",
                        "PAYMENT");

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
                                "checkoutUrl", session.getUrl()))
                        .build();
                messagingTemplate.convertAndSend("/topic/orders", update);
            } catch (Exception e) {
                System.err.println("Failed to send payment pending notification: " + e.getMessage());
            }

            Map<String, Object> data = Map.of(
                    "checkoutUrl", session.getUrl(),
                    "orderId", order.getId(),
                    "sessionId", session.getId());
            return ResponseEntity.ok(new ApiResponse("Stripe checkout session created", data));
        } catch (StripeException e) {
            return ResponseEntity.status(500)
                    .body(new ApiResponse("Stripe error: " + e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(new ApiResponse("Error: " + e.getMessage(), null));
        }
    }

    private void applyDeliveryToOrder(Order order, Map<String, Object> body) {
        if (body.get("deliveryStreet") != null) order.setDeliveryStreet(String.valueOf(body.get("deliveryStreet")));
        if (body.get("deliveryCity") != null) order.setDeliveryCity(String.valueOf(body.get("deliveryCity")));
        if (body.get("deliveryProvince") != null) order.setDeliveryProvince(String.valueOf(body.get("deliveryProvince")));
        if (body.get("deliveryPostalCode") != null) order.setDeliveryPostalCode(String.valueOf(body.get("deliveryPostalCode")));
        if (body.get("deliveryAddressFull") != null) order.setDeliveryAddressFull(String.valueOf(body.get("deliveryAddressFull")));
        if (body.get("deliveryLatitude") != null) {
            Object v = body.get("deliveryLatitude");
            if (v instanceof Number) order.setDeliveryLatitude(((Number) v).doubleValue());
        }
        if (body.get("deliveryLongitude") != null) {
            Object v = body.get("deliveryLongitude");
            if (v instanceof Number) order.setDeliveryLongitude(((Number) v).doubleValue());
        }
    }

    /**
     * Call this when the user lands on /payment-success?orderId=... after Stripe
     * redirect.
     * Verifies payment with Stripe and, if paid, confirms the order (inventory,
     * Telegram alert, etc.).
     * Safe to call multiple times; if order is already PAID, returns success
     * without changing anything.
     */
    @GetMapping("/verify-success")
    public ResponseEntity<ApiResponse> verifyPaymentSuccess(@RequestParam Long orderId) {
        try {
            Order order = orderRepository.findById(orderId)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + orderId));

            OrderDto orderDto = orderService.convertToDto(order);

            if (order.getOrderStatus() == OrderStatus.PAID) {
                Map<String, Object> data = new HashMap<>();
                data.put("order", orderDto);
                data.put("alreadyConfirmed", true);
                return ResponseEntity.ok(new ApiResponse("Payment already confirmed", data));
            }

            String sessionId = order.getStripeSessionId();
            if (sessionId == null || sessionId.isBlank()) {
                Map<String, Object> data = new HashMap<>();
                data.put("order", orderDto);
                data.put("alreadyConfirmed", false);
                return ResponseEntity.ok(new ApiResponse("No Stripe session for this order", data));
            }

            Session session = Session.retrieve(sessionId);
            String paymentStatus = session.getPaymentStatus();
            if (!"paid".equalsIgnoreCase(paymentStatus)) {
                Map<String, Object> data = new HashMap<>();
                data.put("order", orderDto);
                data.put("paymentStatus", paymentStatus);
                return ResponseEntity.ok(new ApiResponse("Payment not completed yet", data));
            }

            orderService.confirmOrderPayment(order);

            orderDto = orderService.convertToDto(order);
            Map<String, Object> data = new HashMap<>();
            data.put("order", orderDto);
            data.put("confirmed", true);
            return ResponseEntity.ok(new ApiResponse("Payment verified and order confirmed", data));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(new ApiResponse(e.getMessage(), null));
        } catch (StripeException e) {
            return ResponseEntity.status(502)
                    .body(new ApiResponse("Stripe error: " + e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(new ApiResponse("Error verifying payment: " + e.getMessage(), null));
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
                            "PAYMENT");

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
                                    "message", "Payment confirmed successfully"))
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

    /**
     * Record or update a payment via API (e.g. from external gateway webhook or
     * manual input).
     * Accepts: orderId (or transactionRef), amount, status; optional: method,
     * transactionRef, cardHolderName, cardLast4, cardBrand, message.
     */
    @PostMapping("/record")
    public ResponseEntity<ApiResponse> recordPayment(@RequestBody Map<String, Object> body) {
        try {
            Long orderId = body.get("orderId") != null ? Long.parseLong(body.get("orderId").toString()) : null;
            String transactionRef = body.get("transactionRef") != null ? body.get("transactionRef").toString().trim()
                    : null;
            if (orderId == null && (transactionRef == null || transactionRef.isEmpty())) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse("Either orderId or transactionRef is required", null));
            }

            BigDecimal amount = body.get("amount") != null ? new BigDecimal(body.get("amount").toString()) : null;
            String statusStr = body.get("status") != null ? body.get("status").toString().toUpperCase() : "PENDING";
            OrderStatus status = OrderStatus.valueOf(statusStr.replace(" ", "_"));

            Payment payment = null;
            if (transactionRef != null && !transactionRef.isEmpty()) {
                payment = paymentRepository.findByTransactionRef(transactionRef).orElse(null);
            }
            if (payment == null && orderId != null) {
                Order order = orderRepository.findById(orderId)
                        .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
                payment = paymentRepository.findByOrder(order).orElse(null);
                if (payment == null) {
                    String methodStr = body.get("method") != null ? body.get("method").toString().toUpperCase()
                            : "CREDIT_CARD";
                    PaymentMethod method = PaymentMethod.valueOf(methodStr.replace("-", "_").replace(" ", "_"));
                    payment = Payment.builder()
                            .order(order)
                            .amount(amount != null ? amount : order.getOrderTotalAmount())
                            .method(method)
                            .status(status)
                            .transactionRef(transactionRef)
                            .transactionTime(LocalDateTime.now())
                            .build();
                }
            }
            if (payment == null) {
                return ResponseEntity.badRequest()
                        .body(new ApiResponse("Payment not found and cannot create without orderId", null));
            }

            if (amount != null)
                payment.setAmount(amount);
            payment.setStatus(status);
            if (transactionRef != null && !transactionRef.isEmpty())
                payment.setTransactionRef(transactionRef);
            if (body.get("cardHolderName") != null)
                payment.setCardHolderName(body.get("cardHolderName").toString().trim());
            if (body.get("cardLast4") != null) {
                String digits = body.get("cardLast4").toString().replaceAll("\\D", "");
                if (!digits.isEmpty())
                    payment.setCardLast4(digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits);
            }
            if (body.get("cardBrand") != null)
                payment.setCardBrand(body.get("cardBrand").toString().trim());
            if (body.get("message") != null)
                payment.setMessage(body.get("message").toString());
            if (status == OrderStatus.SUCCESS)
                payment.setTransactionTime(LocalDateTime.now());

            paymentRepository.save(payment);

            if (status == OrderStatus.SUCCESS && payment.getOrder() != null) {
                orderService.confirmOrderPayment(payment.getOrder());
            }

            Map<String, Object> data = new HashMap<>();
            data.put("paymentId", payment.getId());
            data.put("orderId", payment.getOrder() != null ? payment.getOrder().getOrderId() : null);
            data.put("status", payment.getStatus().toString());
            return ResponseEntity.ok(new ApiResponse("Payment recorded", data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(e.getMessage(), null));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new ApiResponse("Error recording payment: " + e.getMessage(), null));
        }
    }

    @PostMapping("/webhook")
    public ResponseEntity<ApiResponse> handleWebhook(@RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {
        Event event;
        try {
            if (sigHeader == null || sigHeader.isEmpty()) {
                log.warn("Stripe webhook received without Stripe-Signature header");
                return ResponseEntity.status(400).body(new ApiResponse("Missing Stripe-Signature header", null));
            }
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (Exception e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.status(400).body(new ApiResponse("Webhook signature verification failed", null));
        }

        Map<String, Object> processedEvents = new HashMap<>();
        processedEvents.put("event_type", event.getType());
        processedEvents.put("event_id", event.getId());

        try {
            // Parse payload from raw JSON so we don't depend on Stripe SDK deserialization
            // (avoids 500 with API version 2025+)
            JsonNode root = objectMapper.readTree(payload);
            JsonNode data = root.path("data").path("object");

            if ("checkout.session.completed".equals(event.getType())) {
                String sessionId = data.path("id").asText("");
                if (sessionId.isEmpty()) {
                    log.warn("checkout.session.completed: missing session id in event {}", event.getId());
                    return ResponseEntity.ok(new ApiResponse("Event received, no session id", processedEvents));
                }
                String paymentIntentId = null;
                if (data.has("payment_intent")) {
                    JsonNode pi = data.get("payment_intent");
                    paymentIntentId = pi.isTextual() ? pi.asText() : pi.path("id").asText("");
                    if (paymentIntentId.isEmpty())
                        paymentIntentId = null;
                }
                processCheckoutSessionCompleted(sessionId, paymentIntentId, processedEvents);
            } else if ("payment_intent.succeeded".equals(event.getType())) {
                String paymentIntentId = data.path("id").asText("");
                if (paymentIntentId.isEmpty()) {
                    log.warn("payment_intent.succeeded: missing id in event {}", event.getId());
                    return ResponseEntity.ok(new ApiResponse("Event received, no payment_intent id", processedEvents));
                }
                processPaymentIntentSucceeded(paymentIntentId, processedEvents);
            }

            processedEvents.put("processed", true);
            return ResponseEntity.ok(new ApiResponse("Webhook processed successfully", processedEvents));
        } catch (Exception e) {
            log.error("Stripe webhook processing failed for event {}: {}", event.getId(), e.getMessage(), e);
            processedEvents.put("processed", false);
            processedEvents.put("error", e.getMessage());
            return ResponseEntity
                    .ok(new ApiResponse("Webhook received but processing failed: " + e.getMessage(), processedEvents));
        }
    }

    private void processCheckoutSessionCompleted(String sessionId, String paymentIntentId,
            Map<String, Object> processedEvents) {
        orderService.getOrderByStripeSessionId(sessionId).ifPresentOrElse(
                order -> {
                    paymentRepository.findByTransactionRef(sessionId).ifPresentOrElse(
                            payment -> {
                                if (paymentIntentId != null && !paymentIntentId.isEmpty()) {
                                    payment.setPaymentIntentId(paymentIntentId);
                                }
                                payment.setStatus(OrderStatus.SUCCESS);
                                payment.setTransactionTime(LocalDateTime.now());
                                paymentRepository.save(payment);
                                orderService.confirmOrderPayment(order);
                                processedEvents.put("checkout_session", sessionId);
                                processedEvents.put("order_id", order.getId());
                                log.info("Webhook: checkout.session.completed processed for session {} order {}",
                                        sessionId, order.getId());
                            },
                            () -> log.warn("Webhook: payment not found for sessionId {}", sessionId));
                },
                () -> log.warn("Webhook: order not found for stripe sessionId {}", sessionId));
    }

    private void processPaymentIntentSucceeded(String paymentIntentId, Map<String, Object> processedEvents) {
        paymentRepository.findByPaymentIntentId(paymentIntentId)
                .or(() -> paymentRepository.findByTransactionRef(paymentIntentId))
                .ifPresentOrElse(
                        payment -> {
                            Order order = payment.getOrder();
                            if (order.getOrderStatus() != OrderStatus.PAID) {
                                payment.setPaymentIntentId(paymentIntentId);
                                payment.setStatus(OrderStatus.SUCCESS);
                                payment.setTransactionTime(LocalDateTime.now());
                                paymentRepository.save(payment);
                                orderService.confirmOrderPayment(order);
                                log.info("Webhook: payment_intent.succeeded processed for pi {} order {}",
                                        paymentIntentId, order.getId());
                            }
                            processedEvents.put("payment_intent", paymentIntentId);
                            processedEvents.put("order_id", order.getId());
                        },
                        () -> log.warn("Webhook: payment not found for paymentIntentId {}", paymentIntentId));
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

            // In a real implementation, you would verify the payment with the bank/merchant
            // API
            // For now, we'll mark it as pending and require manual verification
            // payment.setStatus(OrderStatus.SUCCESS);
            // payment.setTransactionTime(LocalDateTime.now());
            // paymentRepository.save(payment);
            // orderService.confirmOrderPayment(order);

            Map<String, Object> data = new HashMap<>();
            data.put("orderId", orderId);
            data.put("status", "pending_verification");
            data.put("message",
                    "Payment verification pending. Please contact support with your transaction reference.");

            return ResponseEntity.ok(new ApiResponse("KHQR payment verification initiated", data));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(new ApiResponse("Error verifying KHQR payment: " + e.getMessage(), null));
        }
    }
}
