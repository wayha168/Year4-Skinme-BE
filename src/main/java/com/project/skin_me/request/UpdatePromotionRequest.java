package com.project.skin_me.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UpdatePromotionRequest {
    
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;
    
    private String description;
    
    @Size(max = 500, message = "Link must not exceed 500 characters")
    private String link;
    
    @DecimalMin(value = "0.01", message = "Discount percentage must be greater than 0")
    @DecimalMax(value = "100.00", message = "Discount percentage must not exceed 100")
    private BigDecimal discountPercentage;
    
    @Future(message = "Deadline must be in the future")
    private LocalDateTime deadline;
    
    private LocalDateTime startDate;
    
    private Long productId;
    
    private Boolean active;
}
