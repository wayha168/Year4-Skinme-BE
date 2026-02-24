package com.project.skin_me.model;

import com.project.skin_me.enums.OrderStatus;
import com.project.skin_me.enums.PaymentMethod;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "payments")

public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;

    @Column(name = "transaction_ref", unique = true)
    private String transactionRef;

    @Column(name = "payer_account")
    private String payerAccount;

    @Column(name = "merchant_account")
    private String merchantAccount;

    @Column(name = "transaction_time")
    private LocalDateTime transactionTime;

    private String message;

    /** Card holder name (e.g. from Stripe or manual input). */
    @Column(name = "card_holder_name", length = 255)
    private String cardHolderName;

    /** Last 4 digits of card (e.g. 4242). */
    @Column(name = "card_last4", length = 4)
    private String cardLast4;

    /** Card brand (e.g. visa, mastercard). */
    @Column(name = "card_brand", length = 32)
    private String cardBrand;
}
