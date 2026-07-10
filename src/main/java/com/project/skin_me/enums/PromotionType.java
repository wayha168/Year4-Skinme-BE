package com.project.skin_me.enums;

/**
 * How the promotion applies: product line discount, free delivery rule, or cart minimum spend benefit.
 */
public enum PromotionType {
    /** Percentage off a single linked product */
    PRODUCT_DISCOUNT,
    /** Free delivery; optional minimum cart total via {@code minimumOrderAmount} */
    FREE_DELIVERY,
    /** Percentage off the order when subtotal is at or above {@code minimumOrderAmount} */
    MIN_ORDER_SPEND
}
