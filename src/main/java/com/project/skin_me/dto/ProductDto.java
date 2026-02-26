package com.project.skin_me.dto;

import java.math.BigDecimal;
import java.util.List;

import com.project.skin_me.model.Brand;
import com.project.skin_me.model.Category;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ProductDto {

    private Long id;
    private String name;
    private BigDecimal price;
    private String productType;
    private int inventory;
    private String description;
    private String howToUse;
    /** Brand (category is brand.getCategory()). Omit in API responses to avoid Hibernate proxy serialization. */
    private Brand brand;
    /** Convenience: category from brand, for backward compatibility. Omit in API responses to avoid proxy issues. */
    private Category category;
    /** Brand name for API responses (use instead of brand entity to avoid proxy serialization). */
    private String brandName;
    /** Category name for API responses (use instead of category entity to avoid proxy serialization). */
    private String categoryName;
    private List<ImageDto> images;
}
