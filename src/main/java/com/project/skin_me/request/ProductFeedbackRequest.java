package com.project.skin_me.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ProductFeedbackRequest {

    @NotNull
    private Long productId;

    @NotNull
    @DecimalMin("0.0")
    @DecimalMax("5.0")
    private BigDecimal rating;

    private String comment;

    /** Optional: tie feedback to a specific delivered order for verification. */
    private Long orderId;
}
