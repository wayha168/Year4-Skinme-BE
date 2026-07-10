package com.project.skin_me.dto.chatbot;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ChatbotAdminReplyResponse {
    private boolean saved;

    @JsonProperty("delivered_via_websocket")
    private boolean deliveredViaWebsocket;

    @JsonProperty("message_id")
    private String messageId;
}
