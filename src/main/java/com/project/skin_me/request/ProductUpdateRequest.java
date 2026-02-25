package com.project.skin_me.request;

import java.math.BigDecimal;

import com.project.skin_me.enums.ProductStatus;

import lombok.Data;

@Data
public class ProductUpdateRequest {
    private Long id;
    private String name;
    private BigDecimal price;
    private String productType;
    private int inventory;
    private String description;
    private String howToUse;
    private String skinType;
    private String benefit;
    private Long brandId;
    private ProductStatus status;
}
