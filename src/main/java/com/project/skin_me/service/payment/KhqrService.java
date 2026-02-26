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

    @Value("${khqr.merchant.account:}")
    private String merchantAccount;

    @Value("${khqr.merchant.name:SkinMe Store}")
    private String merchantName;

    @Value("${khqr.merchant.city:Phnom Penh}")
    private String merchantCity;

    @Value("${khqr.merchant.category.code:5999}")
    private String merchantCategoryCode;

    /**
     * Generate KHQR QR code data string
     */
    public String generateKhqrData(BigDecimal amount, String currency) {
        try {
            // KHQR follows EMV QR Code Payment Specification (EMV QRCPS)
            StringBuilder qrData = new StringBuilder();

            // Payload Format Indicator (00) - Mandatory
            qrData.append("000201");

            // Point of Initiation Method (01) - 12 for dynamic QR (with amount)
            qrData.append("010212");

            // Merchant Account Information (30) - Mandatory
            if (merchantAccount == null || merchantAccount.isEmpty()) {
                throw new IllegalStateException("KHQR merchant account is not configured");
            }
            String merchantInfo = String.format("00%02d%s", merchantAccount.length(), merchantAccount);
            qrData.append("30").append(String.format("%02d", merchantInfo.length())).append(merchantInfo);

            // Merchant Category Code (52) - Mandatory
            qrData.append("52").append(String.format("%02d", merchantCategoryCode.length())).append(merchantCategoryCode);

            // Transaction Currency (53) - Mandatory
            // 116 = KHR (Cambodian Riel), 840 = USD
            String currencyCode = "116"; // Default to KHR
            if ("USD".equalsIgnoreCase(currency)) {
                currencyCode = "840";
            }
            qrData.append("53").append(String.format("%02d", currencyCode.length())).append(currencyCode);

            // Transaction Amount (54) - Conditional (required for dynamic QR)
            if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
                // For KHR, no decimals; for USD, 2 decimals
                String amountStr;
                if ("USD".equalsIgnoreCase(currency)) {
                    amountStr = amount.setScale(2, BigDecimal.ROUND_HALF_UP).toPlainString();
                } else {
                    amountStr = amount.setScale(0, BigDecimal.ROUND_HALF_UP).toPlainString();
                }
                qrData.append("54").append(String.format("%02d", amountStr.length())).append(amountStr);
            }

            // Country Code (58) - Mandatory
            qrData.append("58").append("02KH");

            // Merchant Name (59) - Mandatory (max 25 characters)
            String name = merchantName.length() > 25 ? merchantName.substring(0, 25) : merchantName;
            qrData.append("59").append(String.format("%02d", name.length())).append(name);

            // Merchant City (60) - Mandatory
            String city = merchantCity.length() > 15 ? merchantCity.substring(0, 15) : merchantCity;
            qrData.append("60").append(String.format("%02d", city.length())).append(city);

            // CRC (63) - Cyclic Redundancy Check - 4 characters
            String dataWithoutCrc = qrData.toString();
            String crc = calculateCRC(dataWithoutCrc);
            qrData.append("63").append("04").append(crc);

            logger.debug("Generated KHQR data: {}", qrData.toString());
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
     * Generate complete KHQR QR code with image for an order
     */
    public Map<String, String> generateKhqrForOrder(BigDecimal amount, String currency) {
        String qrData = generateKhqrData(amount, currency);
        String qrImageBase64 = generateQrCodeImage(qrData, 300, 300);

        Map<String, String> result = new HashMap<>();
        result.put("qrData", qrData);
        result.put("qrImage", qrImageBase64);
        result.put("amount", amount.toString());
        result.put("currency", currency);

        return result;
    }
}
