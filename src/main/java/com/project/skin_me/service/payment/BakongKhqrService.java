package com.project.skin_me.service.payment;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.project.skin_me.exception.ResourceNotFoundException;
import com.project.skin_me.model.KhqrBankAccount;
import com.project.skin_me.repository.KhqrBankAccountRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Single service for Bakong KHQR integration: manage bank accounts (any bank token/credentials)
 * and generate QR codes. Input token from any bank in Admin > KHQR Bank Accounts; this service
 * uses the active account to produce Bakong KHQR QR data and images for checkout.
 */
@Service
@RequiredArgsConstructor
public class BakongKhqrService implements IBakongKhqrService {

    private static final Logger logger = LoggerFactory.getLogger(BakongKhqrService.class);

    private final KhqrBankAccountRepository repository;

    private static final String FALLBACK_TEST_ACCOUNT = "0000000000";

    // ---------- Bank account (CRUD) ----------

    @Override
    public List<KhqrBankAccount> findAll() {
        return repository.findAllByOrderByDisplayOrderAscGatewayAsc();
    }

    @Override
    public Optional<KhqrBankAccount> findById(Long id) {
        return repository.findById(id);
    }

    @Override
    public Optional<KhqrBankAccount> findActiveByGateway(String gateway) {
        List<KhqrBankAccount> list = repository.findByGatewayAndActiveTrueOrderByDisplayOrderAsc(gateway);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Override
    public Optional<String> getFirstActiveTelegramChatId() {
        return repository.findAllByActiveTrueOrderByDisplayOrderAsc().stream()
                .map(KhqrBankAccount::getTelegramChatId)
                .filter(id -> id != null && !id.isBlank())
                .findFirst();
    }

    @Override
    @Transactional
    public KhqrBankAccount create(KhqrBankAccount account) {
        if (account.getGateway() == null || account.getGateway().isBlank()) {
            account.setGateway(IBakongKhqrService.GATEWAY_KHQR);
        }
        account.setGateway(account.getGateway().toLowerCase().trim());
        return repository.save(account);
    }

    @Override
    @Transactional
    public KhqrBankAccount update(Long id, KhqrBankAccount account) {
        KhqrBankAccount existing = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("KHQR bank account not found: " + id));
        if (account.getGateway() != null && !account.getGateway().isBlank()) {
            existing.setGateway(account.getGateway().toLowerCase().trim());
        }
        existing.setAccount(account.getAccount());
        if (account.getMerchantName() != null) existing.setMerchantName(account.getMerchantName());
        if (account.getCity() != null) existing.setCity(account.getCity());
        if (account.getCategoryCode() != null) existing.setCategoryCode(account.getCategoryCode());
        existing.setTestAccountUsd(account.getTestAccountUsd());
        existing.setTestAccountKhr(account.getTestAccountKhr());
        existing.setUseTestWhenEmpty(account.isUseTestWhenEmpty());
        existing.setActive(account.isActive());
        existing.setDisplayOrder(account.getDisplayOrder());
        existing.setBakongToken(account.getBakongToken());
        existing.setTelegramChatId(account.getTelegramChatId());
        existing.setPaywayMerchantId(account.getPaywayMerchantId());
        existing.setPaywayPublicKey(account.getPaywayPublicKey());
        existing.setPaywayApiUrl(account.getPaywayApiUrl());
        return repository.save(existing);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        repository.findById(id)
                .ifPresentOrElse(repository::delete,
                        () -> { throw new ResourceNotFoundException("KHQR bank account not found: " + id); });
    }

    // ---------- Bakong KHQR QR generation ----------

    @Override
    public String generateKhqrData(BigDecimal amount, String currency, String gateway, Long orderId) {
        String gatewayNorm = gateway == null ? "" : gateway.trim().toLowerCase();
        if (gatewayNorm.isEmpty()) gatewayNorm = GATEWAY_ABA;
        final String gatewayKey = gatewayNorm;

        KhqrBankAccount b = findActiveByGateway(gatewayKey)
                .orElseThrow(() -> {
                    String label = GATEWAY_ABA.equals(gatewayKey) ? "ABA" : "KHQR";
                    return new IllegalStateException(label + " has no active bank account. Add one in Admin > KHQR Bank Accounts.");
                });

        String account = b.getAccount() != null ? b.getAccount().trim() : "";
        String name = b.getMerchantName() != null ? b.getMerchantName() : "SkinMe Store";
        String city = b.getCity() != null ? b.getCity() : "Phnom Penh";
        String categoryCode = b.getCategoryCode() != null ? b.getCategoryCode() : "5999";
        boolean useTestWhenEmpty = b.isUseTestWhenEmpty();
        String gatewayLabel = GATEWAY_ABA.equals(gatewayKey) ? "ABA" : "KHQR";

        if (account.isEmpty()) {
            if (useTestWhenEmpty) {
                boolean isUsd = "USD".equalsIgnoreCase(currency);
                account = isUsd ? normalizeAccount(b.getTestAccountUsd()) : normalizeAccount(b.getTestAccountKhr());
                if (account.isEmpty()) account = FALLBACK_TEST_ACCOUNT;
            } else {
                throw new IllegalStateException(gatewayLabel + " merchant account is not set. Edit this bank account in Admin > KHQR Bank Accounts and set Account or enable Use test when empty.");
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

            if (orderId != null) {
                String ref = orderId.toString();
                String sub62 = "01" + String.format("%02d", ref.length()) + ref;
                qrData.append("62").append(String.format("%02d", sub62.length())).append(sub62);
            }

            String dataWithoutCrc = qrData.toString();
            qrData.append("63").append("04").append(calculateCRC(dataWithoutCrc));

            logger.debug("Generated {} KHQR data", gatewayLabel);
            return qrData.toString();
        } catch (Exception e) {
            logger.error("Failed to generate KHQR data: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to generate KHQR data: " + e.getMessage(), e);
        }
    }

    @Override
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

    @Override
    public Map<String, String> generateKhqrForOrder(BigDecimal amount, String currency, String gateway, Long orderId) {
        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
        if (gateway == null || gateway.isBlank()) gateway = GATEWAY_ABA;
        final String gatewayKey = gateway.trim().toLowerCase();
        KhqrBankAccount bankAccount = findActiveByGateway(gatewayKey)
                .orElseThrow(() -> {
                    String label = GATEWAY_ABA.equals(gatewayKey) ? "ABA" : "KHQR";
                    return new IllegalStateException(label + " has no active bank account. Add one in Admin > KHQR Bank Accounts.");
                });
        String qrData = generateKhqrData(safeAmount, currency, gateway, orderId);
        String qrImageBase64 = generateQrCodeImage(qrData, 300, 300);
        String merchantName = bankAccount.getMerchantName() != null ? bankAccount.getMerchantName() : "SkinMe Store";

        Map<String, String> result = new HashMap<>();
        result.put("qrData", qrData);
        result.put("qrImage", qrImageBase64);
        result.put("amount", safeAmount.toString());
        result.put("currency", currency);
        result.put("merchantName", merchantName);
        result.put("gateway", gateway);

        return result;
    }

    private static String normalizeAccount(String account) {
        if (account == null) return "";
        return account.replaceAll("\\s+", "").trim();
    }

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
}
