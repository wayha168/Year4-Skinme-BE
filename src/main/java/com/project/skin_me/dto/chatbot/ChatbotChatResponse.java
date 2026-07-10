package com.project.skin_me.dto.chatbot;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ChatbotChatResponse {
    private String reply;
    private List<String> options;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("admin_connected")
    private Boolean adminConnected;
}
