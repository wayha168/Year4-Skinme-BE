package com.project.skin_me.request;

import com.project.skin_me.enums.PromotionType;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CreatePromotionRequest {

    @NotNull(message = "Promotion type is required")
    private PromotionType promotionType;

    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    private String description;

    @Size(max = 500, message = "Link must not exceed 500 characters")
    private String link;

    /** Required for PRODUCT_DISCOUNT and MIN_ORDER_SPEND; use 0 for FREE_DELIVERY. */
    private BigDecimal discountPercentage;

    /** For FREE_DELIVERY and MIN_ORDER_SPEND: optional threshold (null = no minimum). */
    private BigDecimal minimumOrderAmount;

    private Boolean freeDelivery;

    @NotNull(message = "Deadline is required")
    @Future(message = "Deadline must be in the future")
    private LocalDateTime deadline;

    @NotNull(message = "Start date is required")
    private LocalDateTime startDate;

    /** Required when promotionType is PRODUCT_DISCOUNT */
    private Long productId;

    private Boolean active = true;
}
