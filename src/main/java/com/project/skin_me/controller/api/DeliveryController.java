package com.project.skin_me.controller.api;

import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.model.Order;
import com.project.skin_me.request.MarkDeliveredRequest;
import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.service.delivery.IDeliveryService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequestMapping("${api.prefix}/delivery")
@RequiredArgsConstructor
public class DeliveryController {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryController.class);
    private final IDeliveryService deliveryService;

    // 📦 Create shipment after payment success (order must be PAID)
    @PostMapping("/ship/{orderId}")
    public ResponseEntity<ApiResponse> createShipment(@PathVariable Long orderId) {
        try {
            Order order = deliveryService.createShipment(orderId);
            return ResponseEntity.ok(ApiResponse.ofKey("api.delivery.shipment.created", order));
        } catch (IllegalStateException e) {
            logger.warn("Shipment rejected for order ID {}: {}", orderId, e.getMessage());
            return ResponseEntity.status(BAD_REQUEST).body(new ApiResponse(e.getMessage(), null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        } catch (Exception e) {
            logger.error("Error creating shipment for order ID: {}", orderId, e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(new ApiResponse(e.getMessage(), null));
        }
    }

    // ✅ Mark order as delivered (order must be SHIPPED). Optional body: { "logisticCompany": "VET" | "JNT" | "DHL" }
    @PostMapping("/delivered/{orderId}")
    public ResponseEntity<ApiResponse> markAsDelivered(
            @PathVariable Long orderId,
            @RequestBody(required = false) MarkDeliveredRequest body) {
        try {
            Order order = deliveryService.markAsDelivered(orderId, body != null ? body.getLogisticCompany() : null);
            return ResponseEntity.ok(ApiResponse.ofKey("api.delivery.order.delivered", order));
        } catch (IllegalStateException e) {
            logger.warn("Mark delivered rejected for order ID {}: {}", orderId, e.getMessage());
            return ResponseEntity.status(BAD_REQUEST).body(new ApiResponse(e.getMessage(), null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(NOT_FOUND).body(new ApiResponse(e.getMessage(), null));
        } catch (Exception e) {
            logger.error("Error marking order as delivered: {}", orderId, e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR).body(new ApiResponse(e.getMessage(), null));
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

            Map<String, Object> addressInfo = new HashMap<>();
            addressInfo.put("orderId", order.getId());
            addressInfo.put("deliveryStreet", order.getDeliveryStreet() != null ? order.getDeliveryStreet() : "");
            addressInfo.put("deliveryCity", order.getDeliveryCity() != null ? order.getDeliveryCity() : "");
            addressInfo.put("deliveryProvince", order.getDeliveryProvince() != null ? order.getDeliveryProvince() : "");
            addressInfo.put("deliveryPostalCode", order.getDeliveryPostalCode() != null ? order.getDeliveryPostalCode() : "");
            addressInfo.put("deliveryAddressFull", order.getDeliveryAddressFull() != null ? order.getDeliveryAddressFull() : "");
            addressInfo.put("deliveryLatitude", order.getDeliveryLatitude() != null ? order.getDeliveryLatitude() : 0.0);
            addressInfo.put("deliveryLongitude", order.getDeliveryLongitude() != null ? order.getDeliveryLongitude() : 0.0);
            addressInfo.put("logisticCompany", order.getLogisticCompany() != null ? order.getLogisticCompany().name() : "");
            
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
