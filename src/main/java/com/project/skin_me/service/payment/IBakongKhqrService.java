package com.project.skin_me.service.payment;

import com.project.skin_me.model.KhqrBankAccount;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Bakong KHQR integration: manage bank accounts (any bank token/credentials) and generate QR codes.
 * Input token from any bank in Admin > KHQR Bank Accounts; implementation uses the active account
 * to produce Bakong KHQR QR data and images for checkout.
 */
public interface IBakongKhqrService {

    /** Gateway: ABA Mobile (abapay). */
    String GATEWAY_ABA = "aba";
    /** Gateway: generic KHQR (any bank). */
    String GATEWAY_KHQR = "khqr";

    List<KhqrBankAccount> findAll();

    Optional<KhqrBankAccount> findById(Long id);

    /** First active account for the gateway (by display order), used for QR generation. */
    Optional<KhqrBankAccount> findActiveByGateway(String gateway);

    /** First active account's Telegram Chat ID (owner/group), if set. For routing notifications. */
    Optional<String> getFirstActiveTelegramChatId();

    KhqrBankAccount create(KhqrBankAccount account);

    KhqrBankAccount update(Long id, KhqrBankAccount account);

    void deleteById(Long id);

    /**
     * Generate KHQR QR code data for the given gateway (aba or khqr).
     * When orderId is not null, tag 62.01 (merchant_ref) is set so PayWay webhook can return it.
     */
    String generateKhqrData(BigDecimal amount, String currency, String gateway, Long orderId);

    /** Generate QR code image as Base64 string. */
    String generateQrCodeImage(String qrData, int width, int height);

    /**
     * Generate complete KHQR QR code with image for an order.
     * @param gateway "aba" for ABA Mobile or "khqr" for generic KHQR
     * @param orderId optional; when set, embedded in QR tag 62.01 so PayWay webhook can return as merchant_ref
     */
    Map<String, String> generateKhqrForOrder(BigDecimal amount, String currency, String gateway, Long orderId);
}
