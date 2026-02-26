package com.project.skin_me.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
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

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(length = 500)
    private String link;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercentage;

    @Column(nullable = false)
    private LocalDateTime deadline;

    @Column(nullable = false)
    private LocalDateTime startDate;

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    @JsonIgnoreProperties({"promotions", "images", "category"})
    private Product product;

    @OneToMany(mappedBy = "promotion", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnoreProperties({"promotion", "product"})
    private List<Image> images;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    /**
     * Calculate discounted price for the product
     */
    public BigDecimal calculateDiscountedPrice() {
        if (product == null || product.getPrice() == null || discountPercentage == null) {
            return null;
        }
        BigDecimal discountAmount = product.getPrice()
                .multiply(discountPercentage)
                .divide(BigDecimal.valueOf(100), 2, java.math.RoundingMode.HALF_UP);
        return product.getPrice().subtract(discountAmount);
    }

    /**
     * Check if promotion is currently active (within date range and active flag)
     */
    public boolean isCurrentlyActive() {
        LocalDateTime now = LocalDateTime.now();
        return active && 
               now.isAfter(startDate) && 
               now.isBefore(deadline);
    }
}
