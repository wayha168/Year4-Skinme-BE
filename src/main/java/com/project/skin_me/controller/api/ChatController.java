package com.project.skin_me.controller.api;

import com.project.skin_me.model.ChatMessage;
import com.project.skin_me.model.User;
import com.project.skin_me.repository.ChatMessageRepository;
import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.service.user.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("${api.prefix}/chat")
public class ChatController {

    private final ChatMessageRepository chatMessageRepository;
    private final IUserService userService;

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse> getChatHistory() {
        try {
            User currentUser = userService.getAuthenticatedUser();
            boolean isAdmin = currentUser.getRoles().stream()
                    .anyMatch(role -> role.getName().equalsIgnoreCase("ADMIN"));
            
            List<ChatMessage> chatHistory;
            if (isAdmin) {
                // Admin sees all chats
                chatHistory = chatMessageRepository.findAllByOrderByTimestampDesc();
            } else {
                // User sees their own chats
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
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse> getUserAdminChats() {
        try {
            List<ChatMessage> chats = chatMessageRepository.findAllUserAdminChats();
            return ResponseEntity.ok(ApiResponse.ofKey("api.chat.userAdmin.success", chats));
        } catch (Exception e) {
            return ResponseEntity.status(500)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }
}
