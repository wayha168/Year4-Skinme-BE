package com.project.skin_me.util;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Optional;

/**
 * PayWay / ABA KHQR callbacks vary by channel (JSON field names and status coding).
 * Normalizes payloads so payment matching works reliably.
 */
public final class PayWayWebhookNormalizer {

    private PayWayWebhookNormalizer() {}

    public record NormalizedPayWayWebhook(
            String merchantRef,
            String transactionId,
            boolean success,
            String payerAccount
    ) {}

    public static Optional<NormalizedPayWayWebhook> parse(JsonNode root) {
        if (root == null || root.isNull() || !root.isObject()) {
            return Optional.empty();
        }
        String merchantRef = firstNonBlank(
                text(root, "merchant_ref"),
                text(root, "merchant_ref_no"),
                text(root, "merchantRef"));
        if (merchantRef == null || merchantRef.isBlank()) {
            return Optional.empty();
        }
        String transactionId = firstNonBlank(
                text(root, "transaction_id"),
                text(root, "tran_id"),
                text(root, "transactionId"));
        boolean success = isPayWaySuccess(root.get("status"));
        String payer = firstNonBlank(
                text(root, "payer_account"),
                text(root, "payerAccount"));
        return Optional.of(new NormalizedPayWayWebhook(
                merchantRef.trim(),
                transactionId != null ? transactionId : "",
                success,
                payer != null ? payer : ""));
    }

    private static String text(JsonNode root, String field) {
        JsonNode n = root.get(field);
        if (n == null || n.isNull()) {
            return null;
        }
        if (n.isTextual()) {
            String s = n.asText();
            return s != null && !s.isBlank() ? s : null;
        }
        if (n.isNumber()) {
            return n.asText();
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }

    /**
     * PayWay success: integer 0, or strings "0", "00", "SUCCESS" (case-insensitive).
     */
    static boolean isPayWaySuccess(JsonNode statusNode) {
        if (statusNode == null || statusNode.isNull()) {
            return false;
        }
        if (statusNode.isInt() || statusNode.isLong()) {
            return statusNode.asInt() == 0;
        }
        if (statusNode.isTextual()) {
            String s = statusNode.asText().trim();
            if (s.isEmpty()) {
                return false;
            }
            if ("0".equals(s) || "00".equals(s)) {
                return true;
            }
            if ("success".equalsIgnoreCase(s)) {
                return true;
            }
            try {
                return Integer.parseInt(s) == 0;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        return false;
    }
}
