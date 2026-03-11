package com.project.skin_me.controller.api;

import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.service.telegram.TelegramNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Telegram notification test endpoint.
 * Use POST /api/v1/telegram/test to verify bot-token and chat-id are working.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("${api.prefix}/telegram")
public class TelegramController {

    private final TelegramNotificationService telegramNotificationService;

    @PostMapping("/test")
    public ResponseEntity<ApiResponse> sendTestAlert() {
        String testMessage = "<b>🧪 Skin.me Telegram test</b>\nIf you see this, alerts are working. You will receive:\n• New order (when customer places order)\n• Payment completed (when payment succeeds)\n• Delivery done (when order is delivered)";
        telegramNotificationService.sendAlert(testMessage);
        return ResponseEntity.ok(new ApiResponse(
                "Test message sent to Telegram (if configured). Check your chat.",
                Map.of("sent", true)
        ));
    }
}
