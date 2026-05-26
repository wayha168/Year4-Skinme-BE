package com.project.skin_me.controller.api;

import com.project.skin_me.dto.chatbot.*;
import com.project.skin_me.model.ChatAi;
import com.project.skin_me.model.ChatMessage;
import com.project.skin_me.model.User;
import com.project.skin_me.repository.ChatAiRepository;
import com.project.skin_me.repository.ChatMessageRepository;
import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.service.chatbot.ChatSessionService;
import com.project.skin_me.service.chatbot.ChatbotService;
import com.project.skin_me.service.user.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/v1/chat")
public class ChatController {

    private final ChatbotService chatbotService;
    private final ChatSessionService chatSessionService;
    private final IUserService userService;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatAiRepository chatAiRepository;

    /** Live chat config for dashboard (session id, WebSocket URL, role). */
    @GetMapping("/config")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getChatConfig() {
        try {
            User currentUser = userService.getAuthenticatedUser();
            boolean isAdmin = isAdmin(currentUser);
            String sessionId = isAdmin ? null : chatSessionService.getOrCreateSessionId(currentUser);
            String role = isAdmin ? "admin" : "user";

            Map<String, Object> config = new LinkedHashMap<>();
            config.put("chatUrl", chatbotService.getBaseUrl());
            config.put("sessionId", sessionId);
            config.put("role", role);
            config.put("isAdmin", isAdmin);
            config.put("userId", currentUser.getId());
            config.put("userEmail", currentUser.getEmail());
            config.put("userName", ChatbotService.displayName(currentUser));
            if (StringUtils.hasText(sessionId)) {
                config.put("webSocketUrl", chatbotService.buildWebSocketUrl(sessionId, role));
            }
            return ResponseEntity.ok(ApiResponse.ofKey("api.chat.config.success", config));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    /** POST /v1/chat — AI assistant + optional admin handoff. */
    @PostMapping("")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> sendMessage(@RequestBody ChatbotChatRequest request) {
        try {
            User currentUser = userService.getAuthenticatedUser();
            if (!StringUtils.hasText(request.getSessionId())) {
                request.setSessionId(chatSessionService.getOrCreateSessionId(currentUser));
            }
            chatbotService.populateUserContext(request, currentUser);

            ChatbotChatResponse response = chatbotService.postChat(request);
            if (response != null && StringUtils.hasText(response.getSessionId())) {
                chatSessionService.saveSessionId(currentUser, response.getSessionId());
            }
            return ResponseEntity.ok(ApiResponse.ofKey("api.chat.send.success", response));
        } catch (Exception e) {
            return ResponseEntity.status(502)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    @PostMapping("/with-image")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> sendMessageWithImage(
            @RequestParam(required = false) String message,
            @RequestParam(required = false) String session_id,
            @RequestParam(value = "use_database", defaultValue = "true") boolean useDatabase,
            @RequestParam("image") MultipartFile image) {
        try {
            User currentUser = userService.getAuthenticatedUser();
            String sessionId = StringUtils.hasText(session_id)
                    ? session_id
                    : chatSessionService.getOrCreateSessionId(currentUser);

            ChatbotImageResponse response = chatbotService.postChatWithImage(
                    message, sessionId, currentUser, image, useDatabase);
            if (response != null && StringUtils.hasText(response.getSessionId())) {
                chatSessionService.saveSessionId(currentUser, response.getSessionId());
            } else if (StringUtils.hasText(sessionId)) {
                chatSessionService.saveSessionId(currentUser, sessionId);
            }
            return ResponseEntity.ok(ApiResponse.ofKey("api.chat.send.success", response));
        } catch (Exception e) {
            return ResponseEntity.status(502)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    /** Admin: list chatbot sessions. */
    @GetMapping("/sessions")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse> listSessions(
            @RequestParam(defaultValue = "50") int limit) {
        try {
            ChatbotSessionsResponse sessions = chatbotService.listSessions(Math.min(limit, 200));
            return ResponseEntity.ok(ApiResponse.ofKey("api.chat.sessions.success", sessions));
        } catch (Exception e) {
            return ResponseEntity.status(502)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    /** Session history from chatbot (admin: any session; user: own session only). */
    @GetMapping("/sessions/{sessionId}/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getSessionHistory(
            @PathVariable String sessionId,
            @RequestParam(defaultValue = "100") int limit) {
        try {
            User currentUser = userService.getAuthenticatedUser();
            if (!isAdmin(currentUser)) {
                String own = chatSessionService.getSessionId(currentUser);
                if (!sessionId.equals(own)) {
                    return ResponseEntity.status(403)
                            .body(ApiResponse.ofKey("api.error.generic", new Object[]{"Access denied"}, null));
                }
            }
            ChatbotHistoryResponse history = chatbotService.getSessionHistory(sessionId, Math.min(limit, 500));
            return ResponseEntity.ok(ApiResponse.ofKey("api.chat.history.success", history));
        } catch (Exception e) {
            return ResponseEntity.status(502)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    /** Admin reply via chatbot (persists + WebSocket push to user). */
    @PostMapping("/admin-reply")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse> adminReply(@RequestBody ChatbotAdminReplyRequest request) {
        try {
            User admin = userService.getAuthenticatedUser();
            if (!StringUtils.hasText(request.getUserEmail())) {
                request.setUserEmail(admin.getEmail());
            }
            ChatbotAdminReplyResponse response = chatbotService.postAdminReply(request);
            persistAdminReply(request, admin);
            return ResponseEntity.ok(ApiResponse.ofKey("api.chat.adminReply.success", response));
        } catch (Exception e) {
            return ResponseEntity.status(502)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    /**
     * Receive chat turn logs from chatbot service (when SPRING_BACKEND_URL is configured there).
     * Also usable from authenticated clients.
     */
    @PostMapping("/log")
    public ResponseEntity<ApiResponse> chatLog(@RequestBody ChatbotLogRequest request) {
        try {
            persistChatLog(request);
            Map<String, Object> result = Map.of("saved", true);
            return ResponseEntity.ok(ApiResponse.ofKey("api.chat.log.success", result));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    /** Legacy: local DB chat_messages history. */
    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getLocalChatHistory() {
        try {
            User currentUser = userService.getAuthenticatedUser();
            List<ChatMessage> chatHistory;
            if (isAdmin(currentUser)) {
                chatHistory = chatMessageRepository.findAllByOrderByTimestampDesc();
            } else {
                chatHistory = chatMessageRepository.findByUserIdOrderByTimestampAsc(currentUser.getId());
            }
            return ResponseEntity.ok(ApiResponse.ofKey("api.chat.history.success", chatHistory));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    @GetMapping("/ai-responses")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getAiResponses() {
        try {
            List<ChatMessage> aiResponses = chatMessageRepository.findAllAiResponses();
            return ResponseEntity.ok(ApiResponse.ofKey("api.chat.aiResponses.success", aiResponses));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    @GetMapping("/user-admin")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse> getUserAdminChats() {
        try {
            List<ChatMessage> chats = chatMessageRepository.findAllUserAdminChats();
            return ResponseEntity.ok(ApiResponse.ofKey("api.chat.userAdmin.success", chats));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    private void persistChatLog(ChatbotLogRequest request) {
        if (!StringUtils.hasText(request.getSessionId())) {
            return;
        }
        LocalDateTime ts = LocalDateTime.now();
        if (StringUtils.hasText(request.getTimestamp())) {
            try {
                ts = LocalDateTime.parse(request.getTimestamp());
            } catch (DateTimeParseException ignored) {
                // keep now
            }
        }

        ChatAi ai = new ChatAi();
        ai.setSession(request.getSessionId());
        ai.setContent("Q: " + nullToEmpty(request.getMessage()) + "\nA: " + nullToEmpty(request.getReply()));
        ai.setTimestamp(ts);
        chatAiRepository.save(ai);

        User user = resolveUser(request.getUserId());
        if (StringUtils.hasText(request.getMessage())) {
            saveMessage(user, request.getSessionId(), "user", request.getMessage(), false, ts);
        }
        if (StringUtils.hasText(request.getReply())) {
            saveMessage(user, request.getSessionId(), "assistant", request.getReply(), true, ts);
        }
    }

    private void persistAdminReply(ChatbotAdminReplyRequest request, User admin) {
        User target = resolveUser(request.getUserId());
        saveMessage(
                target != null ? target : admin,
                request.getSessionId(),
                "admin",
                request.getContent(),
                false,
                LocalDateTime.now());
    }

    private void saveMessage(User user, String sessionId, String type, String content, boolean ai, LocalDateTime ts) {
        ChatMessage msg = new ChatMessage();
        msg.setUser(user);
        msg.setSender(type.equals("admin") ? "admin" : (user != null ? user.getEmail() : "assistant"));
        msg.setContent(content);
        msg.setType(type);
        msg.setTimestamp(ts);
        msg.setConversationId(sessionId);
        msg.setAiResponse(ai);
        chatMessageRepository.save(msg);
    }

    private User resolveUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        try {
            return userService.getUserById(Long.parseLong(userId.trim()));
        } catch (Exception e) {
            return null;
        }
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }

    private static boolean isAdmin(User user) {
        return user.getRoles().stream().anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()));
    }
}
