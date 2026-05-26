package com.project.skin_me.service.chatbot;

import com.project.skin_me.dto.chatbot.*;
import com.project.skin_me.model.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Collections;

@Slf4j
@Service
public class ChatbotService {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String adminKey;

    public ChatbotService(
            @Qualifier("chatbotRestTemplate") RestTemplate restTemplate,
            @Value("${app.chat.url:https://chatbot.skinme.store}") String baseUrl,
            @Value("${app.chat.admin-key:}") String adminKey) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.adminKey = adminKey != null ? adminKey.trim() : "";
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getWebSocketBaseUrl() {
        if (baseUrl.startsWith("https://")) {
            return "wss://" + baseUrl.substring("https://".length());
        }
        if (baseUrl.startsWith("http://")) {
            return "ws://" + baseUrl.substring("http://".length());
        }
        return "wss://" + baseUrl;
    }

    public boolean hasAdminKey() {
        return StringUtils.hasText(adminKey);
    }

    public String buildWebSocketUrl(String sessionId, String role) {
        UriComponentsBuilder builder = UriComponentsBuilder
                .fromUriString(getWebSocketBaseUrl() + "/v1/ws/chat/" + sessionId)
                .queryParam("role", role);
        if ("admin".equalsIgnoreCase(role) && StringUtils.hasText(adminKey)) {
            builder.queryParam("admin_key", adminKey);
        }
        return builder.build().toUriString();
    }

    public ChatbotChatResponse postChat(ChatbotChatRequest request) {
        return exchange(HttpMethod.POST, "/v1/chat", request, ChatbotChatResponse.class, false);
    }

    public ChatbotImageResponse postChatWithImage(
            String message,
            String sessionId,
            User user,
            MultipartFile image,
            boolean useDatabase) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        if (StringUtils.hasText(adminKey)) {
            headers.set("X-Admin-Key", adminKey);
        }

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("message", message != null ? message : "");
        if (StringUtils.hasText(sessionId)) {
            body.add("session_id", sessionId);
        }
        if (user != null) {
            body.add("user_id", String.valueOf(user.getId()));
            body.add("user_email", user.getEmail());
            body.add("user_name", displayName(user));
        }
        body.add("use_database", String.valueOf(useDatabase));

        byte[] bytes = image.getBytes();
        ByteArrayResource resource = new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                String name = image.getOriginalFilename();
                return name != null ? name : "image.jpg";
            }
        };
        body.add("image", resource);

        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);
        ResponseEntity<ChatbotImageResponse> response = restTemplate.exchange(
                baseUrl + "/v1/chat/with-image",
                HttpMethod.POST,
                entity,
                ChatbotImageResponse.class);
        return response.getBody();
    }

    public ChatbotSessionsResponse listSessions(int limit) {
        URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/v1/chat/sessions")
                .queryParam("limit", limit)
                .queryParam("admin_key", StringUtils.hasText(adminKey) ? adminKey : null)
                .build()
                .toUri();
        HttpHeaders headers = adminHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<ChatbotSessionsResponse> response = restTemplate.exchange(
                uri, HttpMethod.GET, entity, ChatbotSessionsResponse.class);
        ChatbotSessionsResponse body = response.getBody();
        if (body == null || body.getSessions() == null) {
            body = new ChatbotSessionsResponse();
            body.setCount(0);
            body.setSessions(Collections.emptyList());
        }
        return body;
    }

    public ChatbotHistoryResponse getSessionHistory(String sessionId, int limit) {
        URI uri = UriComponentsBuilder
                .fromUriString(baseUrl + "/v1/chat/sessions/{sessionId}/history")
                .queryParam("limit", limit)
                .queryParam("admin_key", StringUtils.hasText(adminKey) ? adminKey : null)
                .buildAndExpand(sessionId)
                .toUri();
        HttpHeaders headers = adminHeaders();
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<ChatbotHistoryResponse> response = restTemplate.exchange(
                uri, HttpMethod.GET, entity, ChatbotHistoryResponse.class);
        ChatbotHistoryResponse body = response.getBody();
        if (body == null) {
            body = new ChatbotHistoryResponse();
            body.setSessionId(sessionId);
            body.setMessages(Collections.emptyList());
        }
        return body;
    }

    public ChatbotAdminReplyResponse postAdminReply(ChatbotAdminReplyRequest request) {
        if (!StringUtils.hasText(request.getAdminKey()) && StringUtils.hasText(adminKey)) {
            request.setAdminKey(adminKey);
        }
        HttpHeaders headers = adminHeaders();
        HttpEntity<ChatbotAdminReplyRequest> entity = new HttpEntity<>(request, headers);
        ResponseEntity<ChatbotAdminReplyResponse> response = restTemplate.exchange(
                baseUrl + "/v1/chat/admin-reply",
                HttpMethod.POST,
                entity,
                ChatbotAdminReplyResponse.class);
        return response.getBody();
    }

    public Object postLog(ChatbotLogRequest request) {
        return exchange(HttpMethod.POST, "/v1/chat/log", request, Object.class, false);
    }

    public void populateUserContext(ChatbotChatRequest request, User user) {
        if (user == null) {
            return;
        }
        request.setUserId(String.valueOf(user.getId()));
        request.setUserEmail(user.getEmail());
        request.setUserName(displayName(user));
    }

    public static String displayName(User user) {
        if (user == null) {
            return "";
        }
        String first = user.getFirstName() != null ? user.getFirstName().trim() : "";
        String last = user.getLastName() != null ? user.getLastName().trim() : "";
        String full = (first + " " + last).trim();
        return full.isEmpty() ? user.getEmail() : full;
    }

    private HttpHeaders adminHeaders() {
        HttpHeaders headers = new HttpHeaders();
        if (StringUtils.hasText(adminKey)) {
            headers.set("X-Admin-Key", adminKey);
        }
        return headers;
    }

    private <T> T exchange(HttpMethod method, String path, Object body, Class<T> type, boolean admin) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (admin) {
            adminHeaders().forEach(headers::addAll);
        }
        HttpEntity<Object> entity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<T> response = restTemplate.exchange(baseUrl + path, method, entity, type);
            return response.getBody();
        } catch (RestClientException e) {
            log.error("Chatbot API {} {} failed: {}", method, path, e.getMessage());
            throw e;
        }
    }
}
