package com.project.skin_me.enums;

/**
 * Courier for shipment (VET, J&T, DHL). Set at checkout before payment; stored on the order for delivery.
 */
public enum LogisticCompany {
    VET,
    JNT,
    DHL;

    /** Accepts VET, JNT, DHL, or J&T / JT for JNT. */
    public static LogisticCompany fromString(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.trim();
        if (s.isEmpty()) {
            return null;
        }
        if ("J&T".equalsIgnoreCase(s) || "JT".equalsIgnoreCase(s)) {
            return JNT;
        }
        try {
            return LogisticCompany.valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
