package com.project.skin_me.dto;

import com.project.skin_me.model.CartItem;
import com.project.skin_me.model.Product;
import lombok.Data;

import java.util.List;
import java.util.stream.Collectors;

@Data
public class CartItemDto {

    private Long itemId;
    private Integer quantity;
    private java.math.BigDecimal unitPrice;
    private ProductDto product;

    public CartItemDto(CartItem item) {
        this.itemId = item.getId();
        this.quantity = item.getQuantity();
        this.unitPrice = item.getUnitPrice();

        var p = item.getProduct();
        if (p != null) {
            var images = p.getImages() != null
                    ? p.getImages().stream()
                    .map(img -> {
                        String url = (img.getFileName() != null && !img.getFileName().isBlank())
                                ? "/uploads/" + img.getFileName()
                                : (img.getDownloadUrl() != null ? img.getDownloadUrl() : "");
                        return new ImageDto(img.getId(), img.getFileName(), url);
                    })
                    .collect(Collectors.toList())
                    : List.<ImageDto>of();
            // Use brandName/categoryName only (brand/category = null) to avoid Hibernate proxy serialization in API
            String brandName = safeBrandName(p);
            String categoryName = safeCategoryName(p);
            this.product = new ProductDto(
                    p.getId(),
                    p.getName(),
                    p.getPrice(),
                    p.getProductType(),
                    p.getInventory(),
                    p.getDescription(),
                    p.getHowToUse(),
                    null,
                    null,
                    brandName,
                    categoryName,
                    images
            );
        }
    }

    private static String safeBrandName(Product p) {
        if (p == null || p.getBrand() == null) return null;
        try {
            return p.getBrand().getName();
        } catch (Exception e) {
            return null;
        }
    }

    private static String safeCategoryName(Product p) {
        if (p == null) return null;
        try {
            if (p.getCategory() != null) return p.getCategory().getName();
            if (p.getBrand() != null && p.getBrand().getCategory() != null) return p.getBrand().getCategory().getName();
        } catch (Exception e) { /* ignore */ }
        return null;
    }
}
