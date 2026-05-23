package com.project.skin_me.dto.chatbot;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ChatbotImageResponse {
    private String reply;

    @JsonProperty("image_analysis")
    private String imageAnalysis;

    @JsonProperty("session_id")
    private String sessionId;
}
