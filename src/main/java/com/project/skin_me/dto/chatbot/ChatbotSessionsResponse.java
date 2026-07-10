package com.project.skin_me.dto.chatbot;

import lombok.Data;

import java.util.List;

@Data
public class ChatbotSessionsResponse {
    private int count;
    private List<ChatbotSessionSummary> sessions;
}
