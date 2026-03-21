package com.project.skin_me.service.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Service
public class TelegramNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(TelegramNotificationService.class);
    private static final String TELEGRAM_API = "https://api.telegram.org/bot%s/sendMessage";

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.telegram.bot-token:}")
    private String botToken;

    @Value("${app.telegram.chat-id:}")
    private String chatId;

    @PostConstruct
    public void logTelegramStatus() {
        if (isConfigured()) {
            logger.info("Telegram alerts ENABLED (chat_id configured). Payment/order/delivery alerts will be sent.");
        } else {
            logger.warn("Telegram alerts DISABLED: set app.telegram.bot-token and app.telegram.chat-id to enable.");
        }
    }

    /** Send to default chat (app.telegram.chat-id). */
    public void sendAlert(String message) {
        if (!isConfigured()) {
            logger.warn("Telegram not configured (bot-token or chat-id missing), skipping alert");
            return;
        }
        sendToChat(message, chatId);
    }

    /** Send to a specific chat (e.g. KHQR account owner group). Uses same bot token. */
    public void sendAlertToChat(String message, String targetChatId) {
        if (botToken == null || botToken.isBlank() || targetChatId == null || targetChatId.isBlank()) {
            return;
        }
        sendToChat(message, targetChatId);
    }

    private void sendToChat(String message, String targetChatId) {
        try {
            String url = String.format(TELEGRAM_API, botToken);
            Map<String, Object> body = Map.of(
                    "chat_id", targetChatId,
                    "text", message,
                    "parse_mode", "HTML"
            );
            String jsonBody = objectMapper.writeValueAsString(body);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            ResponseEntity<String> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    new HttpEntity<>(jsonBody, headers),
                    String.class
            );
            if (response.getStatusCode().is2xxSuccessful()) {
                logger.debug("Telegram alert sent to chat {}", targetChatId);
            } else {
                logger.warn("Telegram API returned {}: {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            logger.error("Failed to send Telegram alert to {}: {}", targetChatId, e.getMessage(), e);
        }
    }

    /** Build clean notification: alert emoji + bold title + lines. Escapes HTML entities in values. */
    private static String alertBlock(String emoji, String title, String... lines) {
        StringBuilder sb = new StringBuilder();
        sb.append(emoji).append(" <b>").append(escapeHtml(title)).append("</b>\n");
        for (String line : lines) {
            if (line != null && !line.isEmpty()) {
                sb.append(escapeHtml(line)).append("\n");
            }
        }
        return sb.toString().trim();
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    /** Fired when order is placed (before payment). Sends to default chat and optionally to account owner chat. */
    @Async
    public void notifyNewOrder(Long orderId, String userInfo, String totalAmount) {
        String msg = alertBlock("🔔", "New order (pending payment)",
                "Order #" + orderId,
                "User: " + userInfo,
                "Total: " + totalAmount);
        sendAlert(msg);
    }

    @Async
    public void notifyNewOrder(Long orderId, String userInfo, String totalAmount, String ownerChatId) {
        String msg = alertBlock("🔔", "New order (pending payment)",
                "Order #" + orderId,
                "User: " + userInfo,
                "Total: " + totalAmount);
        sendAlert(msg);
        if (ownerChatId != null && !ownerChatId.isBlank()) {
            sendAlertToChat(msg, ownerChatId.trim());
        }
    }

    /** Fired when payment is confirmed. Sends to default chat and optionally to account owner chat. */
    @Async
    public void notifyPaymentCompleted(Long orderId, String userInfo, String totalAmount) {
        String msg = alertBlock("💰", "Payment completed",
                "Order #" + orderId,
                "User: " + userInfo,
                "Amount: " + totalAmount);
        sendAlert(msg);
    }

    @Async
    public void notifyPaymentCompleted(Long orderId, String userInfo, String totalAmount, String ownerChatId) {
        String msg = alertBlock("💰", "Payment completed",
                "Order #" + orderId,
                "User: " + userInfo,
                "Amount: " + totalAmount);
        sendAlert(msg);
        if (ownerChatId != null && !ownerChatId.isBlank()) {
            sendAlertToChat(msg, ownerChatId.trim());
        }
    }

    @Async
    public void notifyDeliveryDone(Long orderId, String userInfo, String trackingNumber) {
        String tracking = (trackingNumber != null && !trackingNumber.isBlank()) ? "Tracking: " + trackingNumber : null;
        String msg = alertBlock("✅", "Delivery done",
                "Order #" + orderId,
                "User: " + userInfo,
                tracking);
        sendAlert(msg);
    }

    @Async
    public void notifyDeliveryDone(Long orderId, String userInfo, String trackingNumber, String ownerChatId) {
        String tracking = (trackingNumber != null && !trackingNumber.isBlank()) ? "Tracking: " + trackingNumber : null;
        String msg = alertBlock("✅", "Delivery done",
                "Order #" + orderId,
                "User: " + userInfo,
                tracking);
        sendAlert(msg);
        if (ownerChatId != null && !ownerChatId.isBlank()) {
            sendAlertToChat(msg, ownerChatId.trim());
        }
    }

    private boolean isConfigured() {
        return botToken != null && !botToken.isBlank() && chatId != null && !chatId.isBlank();
    }
}
