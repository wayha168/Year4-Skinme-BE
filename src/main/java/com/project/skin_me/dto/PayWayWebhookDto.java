package com.project.skin_me.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Reference shape for PayWay (ABA / KHQR) callbacks.
 * The live endpoint parses JSON as {@link com.fasterxml.jackson.databind.JsonNode} and
 * normalizes field aliases — see {@code PayWayWebhookNormalizer}.
 */
@Data
public class PayWayWebhookDto {

    /** Unique transaction ID from PayWay. */
    private String transaction_id;

    /** From QR subtag 62.01 – use to match our order/payment (e.g. order ID or transactionRef). */
    private String merchant_ref;

    /** Date and time of the transaction. */
    private String datetime;

    /** Core banking booking entry. */
    private String bank_ref;

    /** 0 = success. */
    private Integer status;

    /** Payment status description in words. */
    private String description;

    /** Approval code (6 digits). */
    private String apv;

    /** Amount merchant received. */
    private BigDecimal original_amount;

    /** Merchant currency. */
    private String original_currency;

    /** Payer payment amount. */
    private BigDecimal payment_amount;

    /** Payer currency (KHR or USD). */
    private String payment_currency;

    /** "ABA PAY" or "KHQR". */
    private String payment_type;

    /** Masked payer account. */
    private String payer_account;

    /** Payer name. */
    private String payer_name;

    /** Issuer bank name. */
    private String bank_name;
}
