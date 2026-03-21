package com.project.skin_me.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductFeedbackDto {
    private Long id;
    private Long productId;
    private String productName;
    private Long userId;
    private String userDisplayName;
    private BigDecimal rating;
    private String comment;
    private boolean visibleOnFrontend;
    private LocalDateTime createdAt;
}
