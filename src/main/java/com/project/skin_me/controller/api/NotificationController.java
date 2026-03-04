package com.project.skin_me.controller.api;

import com.project.skin_me.dto.NotificationDto;
import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.model.Notification;
import com.project.skin_me.model.User;
import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.service.notification.NotificationService;
import com.project.skin_me.service.user.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("${api.prefix}/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final IUserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse> getMyNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        try {
            User user = userService.getAuthenticatedUser();
            notificationService.ensureOrderNotificationsForUser(user);
            Page<Notification> notifications = notificationService.findByUserOrderByCreatedAtDesc(
                    user, PageRequest.of(page, Math.min(size, 50)));
            long unreadCount = notificationService.countUnreadByUser(user);
            List<NotificationDto> dtos = notifications.getContent().stream()
                    .map(NotificationService::toDto)
                    .collect(Collectors.toList());
            Map<String, Object> data = new HashMap<>();
            data.put("notifications", dtos);
            data.put("unreadCount", unreadCount);
            data.put("totalPages", notifications.getTotalPages());
            data.put("totalElements", notifications.getTotalElements());
            return ResponseEntity.ok(new ApiResponse("Success", data));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(new ApiResponse(e.getMessage(), null));
        }
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse> getUnreadCount() {
        try {
            User user = userService.getAuthenticatedUser();
            long count = notificationService.countUnreadByUser(user);
            return ResponseEntity.ok(new ApiResponse("Success", Map.of("unreadCount", count)));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(new ApiResponse(e.getMessage(), null));
        }
    }

    @PatchMapping("/{id}/read")
    public ResponseEntity<ApiResponse> markAsRead(@PathVariable Long id) {
        try {
            User user = userService.getAuthenticatedUser();
            notificationService.markAsRead(id, user);
            return ResponseEntity.ok(new ApiResponse("Marked as read", null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(new ApiResponse(e.getMessage(), null));
        }
    }

    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse> markAllAsRead() {
        try {
            User user = userService.getAuthenticatedUser();
            notificationService.markAllAsRead(user);
            return ResponseEntity.ok(new ApiResponse("All marked as read", null));
        } catch (ResourceNotFoundException e) {
            return ResponseEntity.status(404).body(new ApiResponse(e.getMessage(), null));
        }
    }
}
