package com.project.skin_me.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class NotificationDto {
    private String id;
    private String userId;
    private String title;
    private String message;
    /** ORDER, DELIVERY, PRODUCT, PROMOTION, PAYMENT, FEEDBACK, USER, AUTH, INFO, … */
    private String type;
    private String status; // "UNREAD", "READ"
    private LocalDateTime createdAt;
    private String actionUrl;
}
