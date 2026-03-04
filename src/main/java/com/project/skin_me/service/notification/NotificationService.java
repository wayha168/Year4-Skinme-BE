package com.project.skin_me.service.notification;

import com.project.skin_me.dto.NotificationDto;
import com.project.skin_me.model.Notification;
import com.project.skin_me.model.Order;
import com.project.skin_me.model.User;
import com.project.skin_me.repository.NotificationRepository;
import com.project.skin_me.repository.OrderRepository;
import com.project.skin_me.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Persist notification and send to user via WebSocket (using principal name = email).
     */
    @Transactional
    public void createAndNotifyUser(Long userId, String title, String message, String type, String actionUrl) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        Notification n = new Notification();
        n.setUser(user);
        n.setTitle(title);
        n.setMessage(message);
        n.setType(type != null ? type : "INFO");
        n.setStatus("UNREAD");
        n.setActionUrl(actionUrl);
        n.setCreatedAt(LocalDateTime.now());
        n = notificationRepository.save(n);

        NotificationDto dto = toDto(n);
        sendToPrincipal(user.getEmail(), dto);
    }

    private void sendToPrincipal(String principalName, NotificationDto dto) {
        if (dto.getId() == null) dto.setId(UUID.randomUUID().toString());
        if (dto.getCreatedAt() == null) dto.setCreatedAt(LocalDateTime.now());
        if (dto.getStatus() == null) dto.setStatus("UNREAD");
        messagingTemplate.convertAndSendToUser(principalName, "/topic/notifications", dto);
    }

    /**
     * Send notification to specific user via WebSocket (persisted when user exists).
     */
    public void notifyUser(String userId, String title, String message, String type) {
        User user = userRepository.findById(Long.parseLong(userId)).orElse(null);
        if (user == null) {
            NotificationDto notification = NotificationDto.builder()
                    .userId(userId)
                    .title(title)
                    .message(message)
                    .type(type)
                    .build();
            messagingTemplate.convertAndSendToUser(userId, "/topic/notifications", notification);
            return;
        }
        createAndNotifyUser(user.getId(), title, message, type, null);
    }

    /**
     * Send notification to specific user with action URL (persisted when user exists).
     */
    public void notifyUserWithAction(String userId, String title, String message,
            String type, String actionUrl) {
        User user = userRepository.findById(Long.parseLong(userId)).orElse(null);
        if (user == null) {
            NotificationDto notification = NotificationDto.builder()
                    .userId(userId)
                    .title(title)
                    .message(message)
                    .type(type)
                    .actionUrl(actionUrl)
                    .build();
            messagingTemplate.convertAndSendToUser(userId, "/topic/notifications", notification);
            return;
        }
        createAndNotifyUser(user.getId(), title, message, type, actionUrl);
    }

    /**
     * Broadcast notification to all users (no persistence).
     */
    public void broadcastNotification(String title, String message, String type) {
        NotificationDto notification = NotificationDto.builder()
                .title(title)
                .message(message)
                .type(type)
                .build();
        notification.setId(UUID.randomUUID().toString());
        notification.setCreatedAt(LocalDateTime.now());
        messagingTemplate.convertAndSend("/topic/notifications", notification);
    }

    public void notifyOrderStatusChange(String userId, String orderId, String status) {
        String message = "Your order #" + orderId + " status has been updated to: " + status;
        notifyUserWithAction(userId, "Order Update", message, "ORDER", "/view/orders/" + orderId);
    }

    public void notifyDeliveryUpdate(String userId, String orderId, String deliveryStatus) {
        String message = "Your delivery status: " + deliveryStatus;
        notifyUserWithAction(userId, "Delivery Update", message, "DELIVERY", "/view/orders/" + orderId);
    }

    public void notifyProductAvailability(String userId, String productId, String productName) {
        String message = productName + " is now back in stock!";
        notifyUserWithAction(userId, "Product Available", message, "PRODUCT", "/products/" + productId);
    }

    public void notifyPromotion(String userId, String title, String message, String promoUrl) {
        notifyUserWithAction(userId, title, message, "PROMOTION", promoUrl);
    }

    /**
     * Ensures the user has a notification for each of their existing orders (backfill for orders
     * created before notifications were implemented, or missed events).
     */
    @Transactional
    public void ensureOrderNotificationsForUser(User user) {
        List<Order> orders = orderRepository.findByUserId(user.getId());
        for (Order order : orders) {
            String actionUrl = "/view/orders/" + order.getOrderId();
            if (notificationRepository.existsByUserAndActionUrl(user, actionUrl)) continue;
            String statusStr = order.getOrderStatus() != null ? order.getOrderStatus().name() : "CREATED";
            String title = "Order #" + order.getOrderId();
            String message = "Order #" + order.getOrderId() + " – " + statusStr
                    + (order.getOrderTotalAmount() != null ? " · $" + order.getOrderTotalAmount() : "");
            Notification n = new Notification();
            n.setUser(user);
            n.setTitle(title);
            n.setMessage(message);
            n.setType("ORDER");
            n.setStatus("UNREAD");
            n.setActionUrl(actionUrl);
            n.setCreatedAt(LocalDateTime.now());
            notificationRepository.save(n);
        }
    }

    @Transactional(readOnly = true)
    public Page<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable) {
        return notificationRepository.findByUserOrderByCreatedAtDesc(user, pageable);
    }

    public long countUnreadByUser(User user) {
        return notificationRepository.countByUserAndStatus(user, "UNREAD");
    }

    @Transactional
    public void markAsRead(Long notificationId, User user) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getUser().getId().equals(user.getId())) {
                n.setStatus("READ");
                notificationRepository.save(n);
            }
        });
    }

    @Transactional
    public void markAllAsRead(User user) {
        notificationRepository.markAllByUserAndStatus(user, "UNREAD", "READ");
    }

    public static NotificationDto toDto(Notification n) {
        return NotificationDto.builder()
                .id(n.getId() != null ? n.getId().toString() : null)
                .userId(n.getUser() != null ? n.getUser().getId().toString() : null)
                .title(n.getTitle())
                .message(n.getMessage())
                .type(n.getType())
                .status(n.getStatus())
                .createdAt(n.getCreatedAt())
                .actionUrl(n.getActionUrl())
                .build();
    }
}
