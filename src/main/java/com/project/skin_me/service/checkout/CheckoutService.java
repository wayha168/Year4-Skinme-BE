package com.project.skin_me.service.checkout;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CheckoutService implements ICheckoutService {

    @Value("${app.frontend.url:https://skinme.store}")
    private String frontendUrl;

    @Override
    public Session createCheckoutSession(Long orderId, Long amountCents) throws StripeException {
        String orderKey = String.valueOf(orderId);
        SessionCreateParams params = SessionCreateParams.builder()
                // Enable card payment method - Stripe Checkout Session automatically provides card input form
                // When CARD payment method type is added, Stripe displays a secure card input form
                .addPaymentMethodType(SessionCreateParams.PaymentMethodType.CARD)
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(frontendUrl + "/payment-success?orderId=" + orderId)
                .setCancelUrl(frontendUrl + "/payment-cancel?orderId=" + orderId)
                // Session + PaymentIntent metadata so webhooks (especially payment_intent.succeeded) can resolve the order
                .putMetadata("order_id", orderKey)
                .putMetadata("orderId", orderKey)
                .setPaymentIntentData(
                        SessionCreateParams.PaymentIntentData.builder()
                                .putMetadata("order_id", orderKey)
                                .putMetadata("orderId", orderKey)
                                .build())
                // Enable billing address collection for card payments
                .setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.AUTO)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("usd")
                                                .setUnitAmount(amountCents)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("Skin.me Order #" + orderId)
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();

        return Session.create(params);
    }
}