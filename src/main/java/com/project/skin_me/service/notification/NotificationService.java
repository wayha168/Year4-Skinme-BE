package com.project.skin_me.service.notification;

import com.project.skin_me.dto.NotificationDto;
import com.project.skin_me.model.Notification;
import com.project.skin_me.model.Order;
import com.project.skin_me.model.User;
import com.project.skin_me.repository.NotificationRepository;
import com.project.skin_me.repository.OrderRepository;
import com.project.skin_me.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final Pattern API_ORDER_PATH = Pattern.compile("/api/v\\d+/orders/(\\d+)(?:/.*)?");
    private static final Pattern API_PRODUCT_DETAIL = Pattern.compile("/api/v\\d+/products/product/(\\d+)/product.*");
    /** Public JSON product at {@code /products/{id}} (ProductPublicController) — map to Thymeleaf details. */
    private static final Pattern PUBLIC_PRODUCT_JSON = Pattern.compile("/products/(\\d+)(?:/.*)?");

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Persist notification and send to user via WebSocket (using principal name =
     * email).
     */
    @Transactional
    public void createAndNotifyUser(Long userId, String title, String message, String type, String actionUrl) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null)
            return;

        Notification n = new Notification();
        n.setUser(user);
        n.setTitle(title);
        n.setMessage(message);
        n.setType(type != null ? type : "INFO");
        n.setStatus("UNREAD");
        n.setActionUrl(normalizeActionUrlForWebApp(actionUrl));
        n.setCreatedAt(LocalDateTime.now());
        n = notificationRepository.save(n);

        NotificationDto dto = toDto(n);
        sendToPrincipal(user.getEmail(), dto);
    }

    private void sendToPrincipal(String principalName, NotificationDto dto) {
        if (dto.getId() == null)
            dto.setId(UUID.randomUUID().toString());
        if (dto.getCreatedAt() == null)
            dto.setCreatedAt(LocalDateTime.now());
        if (dto.getStatus() == null)
            dto.setStatus("UNREAD");
        messagingTemplate.convertAndSendToUser(principalName, "/topic/notifications", dto);
    }

    /**
     * Send notification to specific user via WebSocket (persisted when user
     * exists).
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
     * Send notification to specific user with action URL (persisted when user
     * exists).
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
                    .actionUrl(normalizeActionUrlForWebApp(actionUrl))
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
        notifyUserWithAction(userId, "Product Available", message, "PRODUCT", "/view/products/" + productId);
    }

    public void notifyPromotion(String userId, String title, String message, String promoUrl) {
        notifyUserWithAction(userId, title, message, "PROMOTION", promoUrl);
    }

    /**
     * Persist a notification per admin and push each over {@code /user/topic/notifications} so the bell panel,
     * badge, and toast show product name, rating, comment, and reviewer.
     */
    @Transactional
    public void notifyAdminsNewProductFeedback(String productName, BigDecimal rating,
            String comment, String reviewerEmail) {
        List<User> admins = userRepository.findAllByRoleName("ROLE_ADMIN");
        if (admins.isEmpty()) {
            return;
        }
        String r = rating != null ? rating.stripTrailingZeros().toPlainString() : "?";
        String commentPart = (comment != null && !comment.isBlank()) ? comment.trim() : "(no comment)";
        if (commentPart.length() > 400) {
            commentPart = commentPart.substring(0, 397) + "...";
        }
        String reviewer = (reviewerEmail != null && !reviewerEmail.isBlank()) ? reviewerEmail : "Customer";
        String pname = (productName != null && !productName.isBlank()) ? productName : "Product";
        String message = pname + " · " + r + "★ — " + commentPart + " · " + reviewer;
        String title = "New product review";
        String actionUrl = "/views/user-feedback";
        for (User admin : admins) {
            try {
                createAndNotifyUser(admin.getId(), title, message, "FEEDBACK", actionUrl);
            } catch (Exception e) {
                log.warn("Could not notify admin {} of new product feedback: {}", admin.getId(), e.getMessage());
            }
        }
    }

    /**
     * Ensures the user has a notification for each of their existing orders
     * (backfill for orders
     * created before notifications were implemented, or missed events).
     */
    @Transactional
    public void ensureOrderNotificationsForUser(User user) {
        List<Order> orders = orderRepository.findByUserId(user.getId());
        for (Order order : orders) {
            String actionUrl = "/view/orders/" + order.getOrderId();
            if (notificationRepository.existsByUserAndActionUrl(user, actionUrl))
                continue;
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
                .actionUrl(normalizeActionUrlForWebApp(n.getActionUrl()))
                .build();
    }

    /**
     * Ensures notification links open server-rendered pages, not REST JSON.
     * Handles legacy/ mistaken URLs like {@code /api/v1/orders/1} or {@code /chat}.
     */
    public static String normalizeActionUrlForWebApp(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String u = url.trim();
        try {
            if (u.startsWith("http://") || u.startsWith("https://")) {
                int schemeEnd = u.indexOf("://") + 3;
                int pathStart = u.indexOf('/', schemeEnd);
                if (pathStart >= 0) {
                    u = u.substring(pathStart);
                } else {
                    u = "/";
                }
            }
        } catch (Exception ignored) {
            // keep u as-is
        }
        if (!u.startsWith("/")) {
            u = "/" + u;
        }
        Matcher mOrder = API_ORDER_PATH.matcher(u);
        if (mOrder.matches()) {
            return "/view/orders/" + mOrder.group(1);
        }
        Matcher mProduct = API_PRODUCT_DETAIL.matcher(u);
        if (mProduct.matches()) {
            return "/view/products/" + mProduct.group(1);
        }
        Matcher mPublicProduct = PUBLIC_PRODUCT_JSON.matcher(u);
        if (mPublicProduct.matches()) {
            return "/view/products/" + mPublicProduct.group(1);
        }
        if ("/chat".equals(u) || u.startsWith("/chat?")) {
            return u.startsWith("/chat?") ? "/views/chat" + u.substring("/chat".length()) : "/views/chat";
        }
        return u;
    }
}
