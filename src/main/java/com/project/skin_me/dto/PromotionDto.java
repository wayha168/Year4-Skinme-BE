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
    /** Enum name, e.g. PRODUCT_DISCOUNT */
    private String promotionType;
    /** Short label for admin and UI */
    private String promotionTypeLabel;
    /** One-line description of what the rule does */
    private String summaryLine;
    private String title;
    private String description;
    private String link;
    private BigDecimal discountPercentage;
    /** Minimum cart subtotal for FREE_DELIVERY or MIN_ORDER_SPEND; null means no minimum. */
    private BigDecimal minimumOrderAmount;
    private boolean freeDelivery;
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
