package com.project.skin_me.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromotionDto {
    private Long id;
    private String title;
    private String description;
    private String link;
    private BigDecimal discountPercentage;
    private LocalDateTime deadline;
    private LocalDateTime startDate;
    private boolean active;
    private Long productId;
    private String productName;
    private BigDecimal originalPrice;
    private BigDecimal discountedPrice;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private boolean currentlyActive;
    private boolean expired;
    private List<ImageDto> images;
}
