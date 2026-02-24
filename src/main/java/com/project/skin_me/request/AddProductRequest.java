package com.project.skin_me.request;

import java.math.BigDecimal;

import lombok.Data;

@Data
public class AddProductRequest {
    private Long id;
    private String name;
    private BigDecimal price;
    private String productType;
    private int inventory;
    private String description;
    private String howToUse;
    /** Brand ID (required). */
    private Long brandId;
}
