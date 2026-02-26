package com.project.skin_me.dto;

import com.project.skin_me.model.CartItem;
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
            this.product = new ProductDto(
                    p.getId(),
                    p.getName(),
                    p.getPrice(),
                    p.getProductType(),
                    p.getInventory(),
                    p.getDescription(),
                    p.getHowToUse(),
                    p.getBrand(),
                    p.getCategory(),
                    images
            );
        }
    }
}
