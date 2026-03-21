package com.project.skin_me.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Pushed to {@code /topic/feedback} when a customer submits product feedback.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFeedbackEventDto {
    private Long feedbackId;
    private Long productId;
    private String productName;
    private BigDecimal rating;
    private String userEmail;
    private String commentPreview;
    private long createdAtEpochMs;
}
