package com.project.skin_me.dto.chatbot;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ChatbotHistoryMessage {
    private String role;
    private String content;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("is_ai_response")
    private Boolean isAiResponse;

    private String sender;

    @JsonProperty("image_analysis")
    private String imageAnalysis;
}
