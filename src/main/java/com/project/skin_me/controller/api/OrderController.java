package com.project.skin_me.controller.api;

import com.project.skin_me.dto.OrderDto;
import com.project.skin_me.exception.AlreadyExistsException;
import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.model.Order;
import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.service.delivery.IDeliveryService;
import com.project.skin_me.service.order.IOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

import static org.springframework.http.HttpStatus.*;

@RequiredArgsConstructor
@RestController
@RequestMapping("${api.prefix}/orders")
public class OrderController {

    private final IOrderService orderService;
    private final IDeliveryService deliveryService;

    @PostMapping("/order")
    public ResponseEntity<ApiResponse> createOrder(@RequestParam Long userId) {
        try {
            Order order = orderService.placeOrderItem(userId);
            OrderDto orderDto = orderService.convertToDto(order);
            return ResponseEntity.ok(new ApiResponse("Order successfully placed", orderDto));
        } catch (AlreadyExistsException e) {
            return ResponseEntity.status(CONFLICT)
                    .body(new ApiResponse(e.getMessage(), null));
        }
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<ApiResponse> getOrderById(@PathVariable Long orderId) {
        try {
            OrderDto order = orderService.getOrder(orderId);
            return ResponseEntity.ok(new ApiResponse("Order fetched!", order));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse("Order not found", e.getMessage()));
        }
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResponse> getUserOrders(@PathVariable Long userId) {
        try {
            List<OrderDto> orders = orderService.getUserOrders(userId);
            return ResponseEntity.ok(new ApiResponse("User orders fetched!", orders));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse("User orders not found", e.getMessage()));
        }
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse> getAllOrders() {
        try {
            List<OrderDto> orders = orderService.getAllUserOrders();
            return ResponseEntity.ok(new ApiResponse("All orders fetched!", orders));
        } catch (Exception e) {
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error fetching orders: " + e.getMessage(), null));
        }
    }

    @PutMapping("/{orderId}/ship")
    public ResponseEntity<ApiResponse> markAsShipped(@PathVariable Long orderId,
                                                     @RequestParam(required = false) String trackingNumber) {
        try {
            Order order = orderService.markAsShipped(orderId, trackingNumber);
            OrderDto orderDto = orderService.convertToDto(order);
            return ResponseEntity.ok(new ApiResponse("Order marked as shipped", orderDto));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse("Order not found", null));
        } catch (Exception e) {
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error marking order as shipped: " + e.getMessage(), null));
        }
    }

    @PutMapping("/{orderId}/deliver")
    public ResponseEntity<ApiResponse> markAsDelivered(@PathVariable Long orderId) {
        try {
            Order order = orderService.markAsDelivered(orderId);
            OrderDto orderDto = orderService.convertToDto(order);
            return ResponseEntity.ok(new ApiResponse("Order marked as delivered", orderDto));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse("Order not found", null));
        } catch (Exception e) {
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error marking order as delivered: " + e.getMessage(), null));
        }
    }

    @PutMapping("/{orderId}/delivery-address")
    @Deprecated
    public ResponseEntity<ApiResponse> updateDeliveryAddress(
            @PathVariable Long orderId,
            @RequestBody Map<String, Object> addressData) {
        // This endpoint is deprecated. Please use /api/v1/delivery/address/{orderId} instead
        // Keeping for backward compatibility, but delegates to delivery service
        try {
            Order order = deliveryService.updateDeliveryAddress(orderId, addressData);
            OrderDto orderDto = orderService.convertToDto(order);
            return ResponseEntity.ok(new ApiResponse("Delivery address updated successfully (via delivery service)", orderDto));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse("Order not found", null));
        } catch (Exception e) {
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error updating delivery address: " + e.getMessage(), null));
        }
    }
}
