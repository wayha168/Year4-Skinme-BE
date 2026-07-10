package com.project.skin_me.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.project.skin_me.enums.PromotionType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "promotions")
public class Promotion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "promotion_type", nullable = false, length = 32)
    private PromotionType promotionType = PromotionType.PRODUCT_DISCOUNT;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 500)
    private String link;

    /** Used for PRODUCT_DISCOUNT and MIN_ORDER_SPEND (order-wide %). Zero for FREE_DELIVERY. */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercentage = BigDecimal.ZERO;

    /** Minimum cart/order subtotal for FREE_DELIVERY or MIN_ORDER_SPEND; null or zero means no minimum (all qualifying orders). */
    @Column(name = "minimum_order_amount", precision = 12, scale = 2)
    private BigDecimal minimumOrderAmount;

    @Column(name = "free_delivery", nullable = false)
    private boolean freeDelivery = false;

    @Column(nullable = false)
    private LocalDateTime deadline;

    @Column(nullable = false)
    private LocalDateTime startDate;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id")
    @JsonIgnoreProperties({"promotions", "images", "category"})
    private Product product;

    @OneToMany(mappedBy = "promotion", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"promotion", "product"})
    private List<Image> images;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PostLoad
    protected void fillLegacyDefaults() {
        if (promotionType == null) {
            promotionType = PromotionType.PRODUCT_DISCOUNT;
        }
        if (discountPercentage == null) {
            discountPercentage = BigDecimal.ZERO;
        }
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public boolean isProductPromotion() {
        return promotionType == PromotionType.PRODUCT_DISCOUNT && product != null;
    }

    /**
     * Calculate discounted price for the linked product (PRODUCT_DISCOUNT only).
     */
    public BigDecimal calculateDiscountedPrice() {
        if (!isProductPromotion() || product.getPrice() == null || discountPercentage == null) {
            return null;
        }
        BigDecimal discountAmount = product.getPrice()
                .multiply(discountPercentage)
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        return product.getPrice().subtract(discountAmount);
    }

    public boolean isCurrentlyActive() {
        LocalDateTime now = LocalDateTime.now();
        return active &&
                now.isAfter(startDate) &&
                now.isBefore(deadline);
    }
}
