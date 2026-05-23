package com.project.skin_me.dto.chatbot;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatbotChatRequest {
    private String message;
    private List<ChatbotMessage> history;

    @JsonProperty("use_llm")
    @Builder.Default
    private boolean useLlm = true;

    @JsonProperty("use_database")
    @Builder.Default
    private boolean useDatabase = true;

    @JsonProperty("session_id")
    private String sessionId;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("user_email")
    private String userEmail;

    @JsonProperty("user_name")
    private String userName;
}
