package com.project.skin_me.service.delivery;

import com.project.skin_me.enums.OrderStatus;
import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.model.Order;
import com.project.skin_me.repository.OrderRepository;
import com.project.skin_me.service.telegram.TelegramNotificationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryService implements IDeliveryService {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryService.class);
    private final OrderRepository orderRepository;
    private final TelegramNotificationService telegramNotificationService;

    @Override
    @Transactional
    public Order createShipment(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        order.setOrderStatus(OrderStatus.SHIPPED);
        order.setTrackingNumber("TRK-" + UUID.randomUUID());
        order.setShippedAt(LocalDateTime.now());
        logger.info("Shipment created for order ID: {}", orderId);
        return orderRepository.save(order);
    }

    @Override
    @Transactional
    public Order markAsDelivered(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
        order.setOrderStatus(OrderStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());
        Order savedOrder = orderRepository.save(order);
        logger.info("Order marked as delivered: {}", orderId);
        try {
            String userInfo = order.getUser() != null ? order.getUser().getEmail() : "N/A";
            telegramNotificationService.notifyDeliveryDone(order.getId(), userInfo, order.getTrackingNumber());
        } catch (Exception e) {
            logger.warn("Failed to send Telegram delivery alert: {}", e.getMessage());
        }
        return savedOrder;
    }

    @Override
    public Order trackOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));
    }

    @Override
    @Transactional
    public Order updateDeliveryAddress(Long orderId, Map<String, Object> addressData) {
        logger.debug("Updating delivery address for order ID: {}", orderId);
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

        // Update delivery address fields
        if (addressData.containsKey("deliveryStreet")) {
            order.setDeliveryStreet(addressData.get("deliveryStreet") != null ? 
                addressData.get("deliveryStreet").toString() : null);
        }
        
        if (addressData.containsKey("deliveryCity")) {
            order.setDeliveryCity(addressData.get("deliveryCity") != null ? 
                addressData.get("deliveryCity").toString() : null);
        }
        
        if (addressData.containsKey("deliveryProvince")) {
            order.setDeliveryProvince(addressData.get("deliveryProvince") != null ? 
                addressData.get("deliveryProvince").toString() : null);
        }
        
        if (addressData.containsKey("deliveryPostalCode")) {
            order.setDeliveryPostalCode(addressData.get("deliveryPostalCode") != null ? 
                addressData.get("deliveryPostalCode").toString() : null);
        }
        
        if (addressData.containsKey("deliveryLatitude")) {
            Object latObj = addressData.get("deliveryLatitude");
            if (latObj != null) {
                try {
                    order.setDeliveryLatitude(Double.parseDouble(latObj.toString()));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid latitude value: {}", latObj);
                }
            }
        }
        
        if (addressData.containsKey("deliveryLongitude")) {
            Object lngObj = addressData.get("deliveryLongitude");
            if (lngObj != null) {
                try {
                    order.setDeliveryLongitude(Double.parseDouble(lngObj.toString()));
                } catch (NumberFormatException e) {
                    logger.warn("Invalid longitude value: {}", lngObj);
                }
            }
        }
        
        if (addressData.containsKey("deliveryAddressFull")) {
            order.setDeliveryAddressFull(addressData.get("deliveryAddressFull") != null ? 
                addressData.get("deliveryAddressFull").toString() : null);
        }

        Order savedOrder = orderRepository.save(order);
            logger.info("Delivery address updated successfully for order ID: {}", orderId);
        return savedOrder;
    }

    @Override
    @Transactional
    public Order clearDeliveryAddress(Long orderId) {
        logger.debug("Clearing delivery address for order ID: {}", orderId);
        
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with ID: " + orderId));

        // Clear all delivery address fields
        order.setDeliveryStreet(null);
        order.setDeliveryCity(null);
        order.setDeliveryProvince(null);
        order.setDeliveryPostalCode(null);
        order.setDeliveryLatitude(null);
        order.setDeliveryLongitude(null);
        order.setDeliveryAddressFull(null);

        Order savedOrder = orderRepository.save(order);
        logger.info("Delivery address cleared successfully for order ID: {}", orderId);
        return savedOrder;
    }
}
