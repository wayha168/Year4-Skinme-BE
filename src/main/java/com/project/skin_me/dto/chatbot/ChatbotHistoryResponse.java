package com.project.skin_me.dto.chatbot;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class ChatbotHistoryResponse {
    @JsonProperty("session_id")
    private String sessionId;
    private List<ChatbotHistoryMessage> messages;
}
