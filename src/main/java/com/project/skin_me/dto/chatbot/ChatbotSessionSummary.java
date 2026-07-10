package com.project.skin_me.dto.chatbot;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ChatbotSessionSummary {
    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("user_email")
    private String userEmail;

    @JsonProperty("user_name")
    private String userName;

    @JsonProperty("session_created_at")
    private String sessionCreatedAt;

    @JsonProperty("last_message")
    private String lastMessage;

    @JsonProperty("last_message_at")
    private String lastMessageAt;

    @JsonProperty("last_message_role")
    private String lastMessageRole;

    @JsonProperty("last_message_sender")
    private String lastMessageSender;

    @JsonProperty("online")
    private Boolean online;
}
