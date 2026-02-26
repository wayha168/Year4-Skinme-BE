package com.project.skin_me.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Minimal product data for dropdowns and JSON in views (avoids serializing full Product entity). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProductOptionDto {
    private Long id;
    private String name;
    private BigDecimal price;
}
