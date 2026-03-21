package com.project.skin_me.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Bank account configuration for KHQR payment (ABA or generic KHQR).
 * Admin can add/edit/delete these; BakongKhqrService uses the active account for QR generation.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "khqr_bank_accounts")
public class KhqrBankAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Gateway identifier: "aba" (ABA Mobile) or "khqr" (generic KHQR). */
    @Column(nullable = false, length = 20)
    private String gateway;

    /** Merchant bank account number (production). Leave empty to use test accounts when useTestWhenEmpty is true. */
    @Column(length = 50)
    private String account;

    @Column(name = "merchant_name", nullable = false, length = 100)
    private String merchantName;

    @Column(nullable = false, length = 50)
    private String city;

    @Column(name = "category_code", nullable = false, length = 10)
    private String categoryCode;

    /** Test account for USD (e.g. 111111111). */
    @Column(name = "test_account_usd", length = 50)
    private String testAccountUsd;

    /** Test account for KHR (e.g. 222222222). */
    @Column(name = "test_account_khr", length = 50)
    private String testAccountKhr;

    /** When true and account is empty, use test account for the currency. */
    @Column(name = "use_test_when_empty", nullable = false)
    private boolean useTestWhenEmpty = true;

    /** When false, this account is not used for QR generation. */
    @Column(nullable = false)
    private boolean active = true;

    /** Display order in admin list; lower = first. */
    @Column(name = "display_order")
    private int displayOrder;

    /** Bakong API token for auto payment gateway (payment confirmation via Bakong). Optional. */
    @Column(name = "bakong_token", length = 512)
    private String bakongToken;

    /** Telegram Chat ID for this account owner/group (e.g. -1002415540940). Notifications sent here so owner gets alerts. */
    @Column(name = "telegram_chat_id", length = 64)
    private String telegramChatId;

    /** PayWay Merchant ID (e.g. ec464035). Used with PayWay Purchase/QR API. */
    @Column(name = "payway_merchant_id", length = 64)
    private String paywayMerchantId;

    /** PayWay Public Key (API key for HMAC hash, e.g. 40-char hex). Valid until date is per your PayWay email. */
    @Column(name = "payway_public_key", length = 128)
    private String paywayPublicKey;

    /** PayWay API base URL (e.g. https://checkout-sandbox.payway.com.kh). Purchase: .../api/payment-gateway/v1/payments/purchase */
    @Column(name = "payway_api_url", length = 255)
    private String paywayApiUrl;

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
}
