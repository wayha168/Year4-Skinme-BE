package com.project.skin_me.controller.api;

import com.project.skin_me.model.Order;
import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.service.delivery.IDeliveryService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("${api.prefix}/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryController.class);
    private final IDeliveryService deliveryService;

    // 📦 Create shipment after payment success (admin or webhook action)
    @PostMapping("/ship/{orderId}")
    public ResponseEntity<ApiResponse> createShipment(@PathVariable Long orderId) {
        try {
            Order order = deliveryService.createShipment(orderId);
            return ResponseEntity.ok(ApiResponse.ofKey("api.delivery.shipment.created", order));
        } catch (Exception e) {
            logger.error("Error creating shipment for order ID: {}", orderId, e);
            return ResponseEntity.status(NOT_FOUND)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    // ✅ Mark order as delivered
    @PostMapping("/delivered/{orderId}")
    public ResponseEntity<ApiResponse> markAsDelivered(@PathVariable Long orderId) {
        try {
            Order order = deliveryService.markAsDelivered(orderId);
            return ResponseEntity.ok(ApiResponse.ofKey("api.delivery.order.delivered", order));
        } catch (Exception e) {
            logger.error("Error marking order as delivered: {}", orderId, e);
            return ResponseEntity.status(NOT_FOUND)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    // 🔍 Track order
    @GetMapping("/track/{orderId}")
    public ResponseEntity<ApiResponse> trackOrder(@PathVariable Long orderId) {
        try {
            Order order = deliveryService.trackOrder(orderId);
            return ResponseEntity.ok(ApiResponse.ofKey("api.delivery.tracking.retrieved", order));
        } catch (Exception e) {
            logger.error("Error tracking order: {}", orderId, e);
            return ResponseEntity.status(NOT_FOUND)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    // 📍 Update delivery address for an order
    @PutMapping("/address/{orderId}")
    public ResponseEntity<ApiResponse> updateDeliveryAddress(
            @PathVariable Long orderId,
            @RequestBody Map<String, Object> addressData) {
        try {
            logger.debug("Received delivery address update request for order ID: {}", orderId);
            
            // Validate required fields
            if (addressData == null || addressData.isEmpty()) {
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.address.required", null));
            }

            Order order = deliveryService.updateDeliveryAddress(orderId, addressData);
            
            logger.info("Delivery address updated successfully for order ID: {}", orderId);
            return ResponseEntity.ok(ApiResponse.ofKey("api.delivery.address.updated", order));
        } catch (com.project.skin_me.exception.ResourceNotFoundException e) {
            logger.warn("Order not found: {}", orderId);
            return ResponseEntity.status(NOT_FOUND)
                    .body(ApiResponse.ofKey("api.order.notFound", null));
        } catch (Exception e) {
            logger.error("Error updating delivery address for order ID: {}", orderId, e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    // 📍 Get delivery address for an order
    @GetMapping("/address/{orderId}")
    public ResponseEntity<ApiResponse> getDeliveryAddress(@PathVariable Long orderId) {
        try {
            Order order = deliveryService.trackOrder(orderId);
            
            Map<String, Object> addressInfo = Map.of(
                    "orderId", order.getId(),
                    "deliveryStreet", order.getDeliveryStreet() != null ? order.getDeliveryStreet() : "",
                    "deliveryCity", order.getDeliveryCity() != null ? order.getDeliveryCity() : "",
                    "deliveryProvince", order.getDeliveryProvince() != null ? order.getDeliveryProvince() : "",
                    "deliveryPostalCode", order.getDeliveryPostalCode() != null ? order.getDeliveryPostalCode() : "",
                    "deliveryAddressFull", order.getDeliveryAddressFull() != null ? order.getDeliveryAddressFull() : "",
                    "deliveryLatitude", order.getDeliveryLatitude() != null ? order.getDeliveryLatitude() : 0.0,
                    "deliveryLongitude", order.getDeliveryLongitude() != null ? order.getDeliveryLongitude() : 0.0
            );
            
            return ResponseEntity.ok(ApiResponse.ofKey("api.delivery.address.retrieved", addressInfo));
        } catch (Exception e) {
            logger.error("Error retrieving delivery address for order ID: {}", orderId, e);
            return ResponseEntity.status(NOT_FOUND)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    // 🗑️ Clear delivery address for an order
    @DeleteMapping("/address/{orderId}")
    public ResponseEntity<ApiResponse> clearDeliveryAddress(@PathVariable Long orderId) {
        try {
            logger.debug("Received request to clear delivery address for order ID: {}", orderId);
            
            Order order = deliveryService.clearDeliveryAddress(orderId);
            
            logger.info("Delivery address cleared successfully for order ID: {}", orderId);
            return ResponseEntity.ok(ApiResponse.ofKey("api.delivery.address.cleared", order));
        } catch (com.project.skin_me.exception.ResourceNotFoundException e) {
            logger.warn("Order not found: {}", orderId);
            return ResponseEntity.status(NOT_FOUND)
                    .body(ApiResponse.ofKey("api.order.notFound", null));
        } catch (Exception e) {
            logger.error("Error clearing delivery address for order ID: {}", orderId, e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }
}
