package com.project.skin_me.service.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
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

    public void sendAlert(String message) {
        if (!isConfigured()) {
            logger.warn("Telegram not configured (bot-token or chat-id missing), skipping alert");
            return;
        }
        try {
            String url = String.format(TELEGRAM_API, botToken);
            Map<String, Object> body = Map.of(
                    "chat_id", chatId,
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
                logger.info("Telegram alert sent successfully");
            } else {
                logger.warn("Telegram API returned {}: {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            logger.error("Failed to send Telegram alert - check bot token, chat-id, and network. Error: {}", e.getMessage(), e);
        }
    }

    public void notifyNewOrder(Long orderId, String userInfo, String totalAmount) {
        String msg = String.format(
                "<b>🛒 New Order</b>\nOrder #%d\nUser: %s\nTotal: %s",
                orderId, userInfo, totalAmount
        );
        sendAlert(msg);
    }

    public void notifyPaymentCompleted(Long orderId, String userInfo, String totalAmount) {
        String msg = String.format(
                "<b>💰 Payment Completed</b>\nOrder #%d\nUser: %s\nAmount: %s",
                orderId, userInfo, totalAmount
        );
        sendAlert(msg);
    }

    public void notifyDeliveryDone(Long orderId, String userInfo, String trackingNumber) {
        String tracking = (trackingNumber != null && !trackingNumber.isBlank()) ? "\nTracking: " + trackingNumber : "";
        String msg = String.format(
                "<b>✅ Delivery Done</b>\nOrder #%d\nUser: %s%s",
                orderId, userInfo, tracking
        );
        sendAlert(msg);
    }

    private boolean isConfigured() {
        return botToken != null && !botToken.isBlank() && chatId != null && !chatId.isBlank();
    }
}
