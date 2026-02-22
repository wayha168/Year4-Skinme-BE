package com.project.skin_me.service.notification;

import com.project.skin_me.controller.api.WebSocketController;
import com.project.skin_me.dto.NotificationDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final WebSocketController webSocketController;

    /**
     * Send notification to specific user via WebSocket
     */
    public void notifyUser(String userId, String title, String message, String type) {
        NotificationDto notification = NotificationDto.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .type(type)
                .build();

        webSocketController.sendUserNotification(userId, notification);
    }

    /**
     * Send notification to specific user with action URL
     */
    public void notifyUserWithAction(String userId, String title, String message,
            String type, String actionUrl) {
        NotificationDto notification = NotificationDto.builder()
                .userId(userId)
                .title(title)
                .message(message)
                .type(type)
                .actionUrl(actionUrl)
                .build();

        webSocketController.sendUserNotification(userId, notification);
    }

    /**
     * Broadcast notification to all users
     */
    public void broadcastNotification(String title, String message, String type) {
        NotificationDto notification = NotificationDto.builder()
                .title(title)
                .message(message)
                .type(type)
                .build();

        webSocketController.sendBroadcastNotification(notification);
    }

    /**
     * Notify about order status change
     */
    public void notifyOrderStatusChange(String userId, String orderId, String status) {
        String message = "Your order #" + orderId + " status has been updated to: " + status;
        notifyUserWithAction(userId, "Order Update", message, "ORDER", "/orders/" + orderId);
    }

    /**
     * Notify about delivery status change
     */
    public void notifyDeliveryUpdate(String userId, String orderId, String deliveryStatus) {
        String message = "Your delivery status: " + deliveryStatus;
        notifyUserWithAction(userId, "Delivery Update", message, "DELIVERY", "/orders/" + orderId);
    }

    /**
     * Notify about product availability
     */
    public void notifyProductAvailability(String userId, String productId, String productName) {
        String message = productName + " is now back in stock!";
        notifyUserWithAction(userId, "Product Available", message, "PRODUCT", "/products/" + productId);
    }

    /**
     * Notify about promotional offers
     */
    public void notifyPromotion(String userId, String title, String message, String promoUrl) {
        notifyUserWithAction(userId, title, message, "PROMOTION", promoUrl);
    }

}
