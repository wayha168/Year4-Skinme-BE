package com.project.skin_me.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "product_feedback", uniqueConstraints = {
        @UniqueConstraint(name = "uk_feedback_user_product", columnNames = { "user_id", "product_id" })
})
public class ProductFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id")
    private Product product;

    /** 0–5 stars, half-star supported (e.g. 4.5). */
    @Column(nullable = false, precision = 3, scale = 2)
    private BigDecimal rating;

    @Column(columnDefinition = "TEXT")
    private String comment;

    /** Optional review photo URL (e.g. /uploads/filename). */
    @Column(length = 2048)
    private String imageUrl;

    @Column(nullable = false)
    private boolean visibleOnFrontend = false;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
}
