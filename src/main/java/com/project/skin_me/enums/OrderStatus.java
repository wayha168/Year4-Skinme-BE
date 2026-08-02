package com.project.skin_me.enums;

public enum OrderStatus {
    PENDING,
    PAYMENT,
    PROCESSING,
    PAYMENT_PENDING,
    SHIPPED,
    /** Courier is out for delivery to the customer. */
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED,
    COMPLETED,
    FAILED,
    PAID,
    SUCCESS
}
