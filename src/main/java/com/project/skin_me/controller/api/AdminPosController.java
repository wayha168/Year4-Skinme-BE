package com.project.skin_me.controller.api;

import com.project.skin_me.dto.PosCalculateResultDto;
import com.project.skin_me.dto.PosLineItemDto;
import com.project.skin_me.enums.OrderStatus;
import com.project.skin_me.enums.PaymentMethod;
import com.project.skin_me.model.Order;
import com.project.skin_me.model.Product;
import com.project.skin_me.model.User;
import com.project.skin_me.repository.OrderRepository;
import com.project.skin_me.repository.UserRepository;
import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.service.pos.IPosService;
import com.project.skin_me.service.product.IProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("${api.prefix}/admin/pos")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminPosController {

    private final IPosService posService;
    private final IProductService productService;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;

    @GetMapping("/products")
    public ResponseEntity<ApiResponse> listProducts() {
        List<Product> products = productService.getAllProducts();
        productService.loadImagesForProducts(products);
        return ResponseEntity.ok(new ApiResponse("OK", products));
    }

    @PostMapping("/calculate")
    public ResponseEntity<ApiResponse> calculate(@RequestBody Map<String, Object> body) {
        List<PosLineItemDto> items = parseLineItems(body);
        PosCalculateResultDto result = posService.calculate(items);
        return ResponseEntity.ok(new ApiResponse("OK", result));
    }

    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse> checkout(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        User cashier = resolveCashier(authentication);
        List<PosLineItemDto> items = parseLineItems(body);
        String fulfillmentType = body.get("fulfillmentType") != null ? body.get("fulfillmentType").toString() : "PICKUP";
        Order order = posService.createPosOrder(cashier, items, fulfillmentType);

        PosCalculateResultDto calc = posService.calculate(items);
        return ResponseEntity.ok(new ApiResponse("Order created", Map.of(
                "orderId", order.getOrderId(),
                "total", order.getOrderTotalAmount(),
                "calculation", calc)));
    }

    @PostMapping("/pay/cash")
    public ResponseEntity<ApiResponse> payCash(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        return completePay(body, authentication, PaymentMethod.CASH, null);
    }

    @PostMapping("/pay/card")
    public ResponseEntity<ApiResponse> payCard(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        String cardLast4 = body.get("cardLast4") != null ? body.get("cardLast4").toString() : null;
        return completePay(body, authentication, PaymentMethod.CREDIT_CARD, cardLast4);
    }

    @PostMapping("/complete-pickup/{orderId}")
    public ResponseEntity<ApiResponse> completePickup(@PathVariable Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        if (order.getDeliveryAddressFull() == null || !order.getDeliveryAddressFull().contains("PICKUP")) {
            return ResponseEntity.badRequest().body(new ApiResponse("Not a POS pickup order", null));
        }
        if (order.getOrderStatus() != OrderStatus.DELIVERED) {
            order.setOrderStatus(OrderStatus.DELIVERED);
            order.setDeliveredAt(java.time.LocalDateTime.now());
            order.setTrackingNumber("PICKUP");
            orderRepository.save(order);
        }
        return ResponseEntity.ok(new ApiResponse("Pickup completed", Map.of("orderId", orderId)));
    }

    @GetMapping("/receipt/{orderId}")
    public ResponseEntity<ApiResponse> receipt(
            @PathVariable Long orderId,
            Authentication authentication) {
        User cashier = resolveCashier(authentication);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        String markdown = posService.buildReceiptMarkdown(order, cashierDisplayName(cashier));
        return ResponseEntity.ok(new ApiResponse("OK", Map.of(
                "orderId", orderId,
                "receiptMarkdown", markdown)));
    }

    private ResponseEntity<ApiResponse> completePay(
            Map<String, Object> body,
            Authentication authentication,
            PaymentMethod method,
            String cardLast4) {
        Long orderId = body.get("orderId") != null ? Long.parseLong(body.get("orderId").toString()) : null;
        if (orderId == null) {
            return ResponseEntity.badRequest().body(new ApiResponse("orderId is required", null));
        }
        User cashier = resolveCashier(authentication);
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));
        try {
            Map<String, Object> result = posService.completePayment(order, cashier, method, cardLast4);
            return ResponseEntity.ok(new ApiResponse("Payment completed", result));
        } catch (IllegalStateException | IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ApiResponse(e.getMessage(), null));
        }
    }

    @SuppressWarnings("unchecked")
    private List<PosLineItemDto> parseLineItems(Map<String, Object> body) {
        Object raw = body.get("items");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("items array is required");
        }
        return list.stream().map(entry -> {
            if (entry instanceof Map<?, ?> map) {
                Long productId = map.get("productId") != null
                        ? Long.parseLong(map.get("productId").toString())
                        : null;
                int qty = map.get("quantity") != null
                        ? Integer.parseInt(map.get("quantity").toString())
                        : 1;
                return new PosLineItemDto(productId, qty);
            }
            throw new IllegalArgumentException("Invalid item in cart");
        }).toList();
    }

    private User resolveCashier(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new IllegalStateException("Not authenticated");
        }
        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }

    private static String cashierDisplayName(User user) {
        if (user == null) {
            return "Admin";
        }
        String first = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String last = user.getLastName() != null ? user.getLastName().trim() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? (user.getEmail() != null ? user.getEmail() : "Admin") : full;
    }
}
