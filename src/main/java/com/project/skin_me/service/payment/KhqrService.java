package com.project.skin_me.service.payment;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class KhqrService {
    private static final Logger logger = LoggerFactory.getLogger(KhqrService.class);

    public static final String GATEWAY_ABA = "aba";
    public static final String GATEWAY_KHQR = "khqr";
    private static final String FALLBACK_TEST_ACCOUNT = "0000000000";

    // Option 1: ABA (ABA Mobile)
    @Value("${aba.khqr.merchant.account:}")
    private String abaMerchantAccount;
    @Value("${aba.khqr.merchant.test-account-usd:111111111}")
    private String abaTestAccountUsd;
    @Value("${aba.khqr.merchant.test-account-khr:222222222}")
    private String abaTestAccountKhr;
    @Value("${aba.khqr.merchant.name:SkinMe Store}")
    private String abaMerchantName;
    @Value("${aba.khqr.merchant.city:Phnom Penh}")
    private String abaMerchantCity;
    @Value("${aba.khqr.merchant.category.code:5999}")
    private String abaMerchantCategoryCode;
    @Value("${aba.khqr.merchant.use-test-when-empty:true}")
    private boolean abaUseTestWhenEmpty;

    // Option 2: KHQR (any KHQR bank)
    @Value("${khqr.merchant.account:}")
    private String khqrMerchantAccount;
    @Value("${khqr.merchant.test-account-usd:111111111}")
    private String khqrTestAccountUsd;
    @Value("${khqr.merchant.test-account-khr:222222222}")
    private String khqrTestAccountKhr;
    @Value("${khqr.merchant.name:SkinMe Store}")
    private String khqrMerchantName;
    @Value("${khqr.merchant.city:Phnom Penh}")
    private String khqrMerchantCity;
    @Value("${khqr.merchant.category.code:5999}")
    private String khqrMerchantCategoryCode;
    @Value("${khqr.merchant.use-test-when-empty:true}")
    private boolean khqrUseTestWhenEmpty;

    /**
     * Generate KHQR QR code data for the given gateway (aba or khqr).
     */
    public String generateKhqrData(BigDecimal amount, String currency, String gateway) {
        boolean useAba = GATEWAY_ABA.equalsIgnoreCase(gateway);
        String account = useAba ? abaMerchantAccount : khqrMerchantAccount;
        String name = useAba ? abaMerchantName : khqrMerchantName;
        String city = useAba ? abaMerchantCity : khqrMerchantCity;
        String categoryCode = useAba ? abaMerchantCategoryCode : khqrMerchantCategoryCode;
        boolean useTestWhenEmpty = useAba ? abaUseTestWhenEmpty : khqrUseTestWhenEmpty;
        String gatewayLabel = useAba ? "ABA" : "KHQR";

        if (account == null) account = "";
        account = account.trim();
        if (account.isEmpty()) {
            if (useTestWhenEmpty) {
                // Use currency-specific testing accounts: 111 111 111 (USD), 222 222 222 (KHR)
                boolean isUsd = "USD".equalsIgnoreCase(currency);
                account = useAba
                        ? (isUsd ? normalizeAccount(abaTestAccountUsd) : normalizeAccount(abaTestAccountKhr))
                        : (isUsd ? normalizeAccount(khqrTestAccountUsd) : normalizeAccount(khqrTestAccountKhr));
                if (account.isEmpty()) account = FALLBACK_TEST_ACCOUNT;
                logger.debug("Using test merchant account for {} {} ({}); set merchant.account for production", gatewayLabel, currency, account);
            } else {
                throw new IllegalStateException(gatewayLabel + " merchant account is not configured. Set " + (useAba ? "aba.khqr" : "khqr") + ".merchant.account in application.properties or enable use-test-when-empty for testing.");
            }
        }

        try {
            StringBuilder qrData = new StringBuilder();
            qrData.append("000201");
            qrData.append("010212");

            String merchantInfo = String.format("00%02d%s", account.length(), account);
            qrData.append("30").append(String.format("%02d", merchantInfo.length())).append(merchantInfo);
            qrData.append("52").append(String.format("%02d", categoryCode.length())).append(categoryCode);

            String currencyCode = "USD".equalsIgnoreCase(currency) ? "840" : "116";
            qrData.append("53").append(String.format("%02d", currencyCode.length())).append(currencyCode);

            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                String amountStr = "USD".equalsIgnoreCase(currency)
                        ? amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString()
                        : amount.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString();
                qrData.append("54").append(String.format("%02d", amountStr.length())).append(amountStr);
            }

            qrData.append("58").append("02KH");
            String nameTrim = name.length() > 25 ? name.substring(0, 25) : name;
            qrData.append("59").append(String.format("%02d", nameTrim.length())).append(nameTrim);
            String cityTrim = city.length() > 15 ? city.substring(0, 15) : city;
            qrData.append("60").append(String.format("%02d", cityTrim.length())).append(cityTrim);

            String dataWithoutCrc = qrData.toString();
            qrData.append("63").append("04").append(calculateCRC(dataWithoutCrc));

            logger.debug("Generated {} KHQR data", gatewayLabel);
            return qrData.toString();
        } catch (Exception e) {
            logger.error("Failed to generate KHQR data: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate KHQR data: " + e.getMessage(), e);
        }
    }

    /**
     * Generate QR code image as Base64 string
     */
    public String generateQrCodeImage(String qrData, int width, int height) {
        try {
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.CHARACTER_SET, StandardCharsets.UTF_8.name());
            hints.put(EncodeHintType.MARGIN, 1);

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(qrData, BarcodeFormat.QR_CODE, width, height, hints);

            BufferedImage qrImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            qrImage.createGraphics();

            Graphics2D graphics = (Graphics2D) qrImage.getGraphics();
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, width, height);
            graphics.setColor(Color.BLACK);

            for (int i = 0; i < width; i++) {
                for (int j = 0; j < height; j++) {
                    if (bitMatrix.get(i, j)) {
                        graphics.fillRect(i, j, 1, 1);
                    }
                }
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(qrImage, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();

            return "data:image/png;base64," + Base64.getEncoder().encodeToString(imageBytes);
        } catch (WriterException | IOException e) {
            logger.error("Failed to generate QR code image: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate QR code image: " + e.getMessage(), e);
        }
    }

    /** Strip spaces from account number (e.g. "111 111 111" -> "111111111"). */
    private static String normalizeAccount(String account) {
        if (account == null) return "";
        return account.replaceAll("\\s+", "").trim();
    }

    /**
     * Calculate CRC-16/AUG-CCITT checksum for KHQR
     */
    private String calculateCRC(String data) {
        int crc = 0xFFFF;
        int polynomial = 0x1021;

        byte[] bytes = data.getBytes(StandardCharsets.UTF_8);
        for (byte b : bytes) {
            for (int i = 0; i < 8; i++) {
                boolean bit = ((b >> (7 - i) & 1) == 1);
                boolean c15 = ((crc >> 15 & 1) == 1);
                crc <<= 1;
                if (c15 ^ bit) {
                    crc ^= polynomial;
                }
            }
        }
        crc &= 0xFFFF;
        return String.format("%04X", crc);
    }

    /**
     * Generate complete KHQR QR code with image for an order.
     * @param gateway "aba" for ABA Mobile or "khqr" for generic KHQR
     */
    public Map<String, String> generateKhqrForOrder(BigDecimal amount, String currency, String gateway) {
        if (gateway == null || gateway.isBlank()) gateway = GATEWAY_ABA;
        String qrData = generateKhqrData(amount, currency, gateway);
        String qrImageBase64 = generateQrCodeImage(qrData, 300, 300);
        String merchantName = GATEWAY_ABA.equalsIgnoreCase(gateway) ? abaMerchantName : khqrMerchantName;

        Map<String, String> result = new HashMap<>();
        result.put("qrData", qrData);
        result.put("qrImage", qrImageBase64);
        result.put("amount", amount.toString());
        result.put("currency", currency);
        result.put("merchantName", merchantName);
        result.put("gateway", gateway);

        return result;
    }
}
