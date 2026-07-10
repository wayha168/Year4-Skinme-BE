package com.project.skin_me.dto.chatbot;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotAdminReplyRequest {
    @JsonProperty("session_id")
    private String sessionId;
    private String content;

    @JsonProperty("admin_key")
    private String adminKey;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("user_email")
    private String userEmail;

    @JsonProperty("user_name")
    private String userName;
}
