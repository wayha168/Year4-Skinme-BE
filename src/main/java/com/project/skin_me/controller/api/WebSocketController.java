package com.project.skin_me.controller.api;

import com.project.skin_me.dto.ChatMessageDto;
import com.project.skin_me.dto.NotificationDto;
import com.project.skin_me.dto.RealTimeUpdateDto;
import com.project.skin_me.model.ChatMessage;
import com.project.skin_me.model.User;
import com.project.skin_me.repository.ChatMessageRepository;
import com.project.skin_me.service.chatAI.GeminiService;
import com.project.skin_me.service.user.IUserService;
import com.project.skin_me.util.MarkdownCatalogLoader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class WebSocketController {

    private final SimpMessagingTemplate messagingTemplate;
    private final GeminiService geminiService;
    private final ChatMessageRepository chatMessageRepository;
    private final IUserService userService;

    /**
     * Handle chat messages between users and admins
     * Client sends to: /app/chat/message
     */
    @MessageMapping("/chat/message")
    @SendTo("/topic/chat")
    public ChatMessageDto handleChatMessage(ChatMessageDto message) {
        try {
            // Get current user
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = null;
            if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
                try {
                    currentUser = userService.getAuthenticatedUser();
                } catch (Exception e) {
                    // User not found, continue without user
                }
            }
            
            // Determine sender type
            String senderType = "user";
            if (currentUser != null) {
                boolean isAdmin = currentUser.getRoles().stream()
                    .anyMatch(role -> role.getName().equalsIgnoreCase("ADMIN"));
                senderType = isAdmin ? "admin" : "user";
                message.setSender(currentUser.getEmail());
            }
            
            // Add metadata
            message.setId(UUID.randomUUID().toString());
            message.setTimestamp(LocalDateTime.now());
            message.setType(senderType);
            
            // Save to database
            ChatMessage chatMessage = new ChatMessage();
            chatMessage.setUser(currentUser);
            chatMessage.setSender(message.getSender());
            chatMessage.setContent(message.getContent());
            chatMessage.setType(senderType);
            chatMessage.setTimestamp(message.getTimestamp());
            chatMessage.setConversationId(message.getConversationId());
            chatMessage.setAiResponse(false);
            chatMessageRepository.save(chatMessage);
            
            // Broadcast to all subscribers
            return message;
        } catch (Exception e) {
            // Return message even if save fails
            message.setId(UUID.randomUUID().toString());
            message.setTimestamp(LocalDateTime.now());
            return message;
        }
    }

    /**
     * Handle chat queries with AI assistant
     * Client sends to: /app/chat/query
     * Response sent to specific user
     */
    @MessageMapping("/chat/query")
    @SendToUser("/topic/chat")
    public ChatMessageDto handleChatQuery(ChatMessageDto message) {
        try {
            // Get current user
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User currentUser = null;
            if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
                try {
                    currentUser = userService.getAuthenticatedUser();
                } catch (Exception e) {
                    // User not found
                }
            }
            
            // Save user message first
            if (currentUser != null) {
                ChatMessage userMessage = new ChatMessage();
                userMessage.setUser(currentUser);
                userMessage.setSender(currentUser.getEmail());
                userMessage.setContent(message.getContent());
                userMessage.setType("user");
                userMessage.setTimestamp(LocalDateTime.now());
                userMessage.setConversationId(message.getConversationId());
                userMessage.setAiResponse(false);
                chatMessageRepository.save(userMessage);
            }
            
            // Get AI response using the assistant endpoint with product catalog
            String markdownTable = "";
            try {
                markdownTable = MarkdownCatalogLoader.load();
            } catch (Exception e) {
                log.warn("Failed to load product catalog: " + e.getMessage());
            }
            
            String prompt;
            if (!markdownTable.isEmpty()) {
                prompt = """
                    You are a helpful skincare assistant for SkinMe.
                    Use ONLY the product data below (Markdown table) to answer.
                    Do not invent products.
                    
                    When recommending a product, respond in HTML format with:
                    - Product name
                    - Product image (as <img src="..."/>)
                    - Link to the product page (as <a href="...">Link</a>)
                    
                    PRODUCT CATALOG:
                    %s
                    
                    USER QUESTION: %s
                    
                    If no match, say: "I couldn't find a matching product."
                    """.formatted(markdownTable, message.getContent());
            } else {
                // Fallback if catalog not available
                prompt = "You are a helpful skincare assistant for SkinMe. " + message.getContent();
            }
            
            String aiResponse = geminiService.askGemini(prompt);
            
            ChatMessageDto aiMessageDto = ChatMessageDto.builder()
                    .id(UUID.randomUUID().toString())
                    .sender("assistant")
                    .content(aiResponse)
                    .type("assistant")
                    .timestamp(LocalDateTime.now())
                    .conversationId(message.getConversationId())
                    .build();
            
            // Save AI response to database
            ChatMessage aiMessage = new ChatMessage();
            aiMessage.setUser(currentUser);
            aiMessage.setSender("assistant");
            aiMessage.setContent(aiResponse);
            aiMessage.setType("assistant");
            aiMessage.setTimestamp(aiMessageDto.getTimestamp());
            aiMessage.setConversationId(message.getConversationId());
            aiMessage.setAiResponse(true);
            chatMessageRepository.save(aiMessage);
            
            return aiMessageDto;
        } catch (Exception e) {
            ChatMessageDto errorResponse = ChatMessageDto.builder()
                    .id(UUID.randomUUID().toString())
                    .sender("assistant")
                    .content("Sorry, I encountered an error. Please try again.")
                    .type("assistant")
                    .timestamp(LocalDateTime.now())
                    .conversationId(message.getConversationId())
                    .build();
            
            // Save error response
            try {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                User currentUser = null;
                if (auth != null && auth.isAuthenticated() && !auth.getName().equals("anonymousUser")) {
                    try {
                        currentUser = userService.getAuthenticatedUser();
                    } catch (Exception ex) {
                        // User not found
                    }
                }
                
                ChatMessage errorMessage = new ChatMessage();
                errorMessage.setUser(currentUser);
                errorMessage.setSender("assistant");
                errorMessage.setContent(errorResponse.getContent());
                errorMessage.setType("assistant");
                errorMessage.setTimestamp(errorResponse.getTimestamp());
                errorMessage.setConversationId(message.getConversationId());
                errorMessage.setAiResponse(true);
                chatMessageRepository.save(errorMessage);
            } catch (Exception ex) {
                // Ignore save errors
            }
            
            return errorResponse;
        }
    }

    /**
     * Send notification to specific user
     * Internal service method - call from other services
     */
    public void sendUserNotification(String userId, NotificationDto notification) {
        notification.setId(UUID.randomUUID().toString());
        notification.setCreatedAt(LocalDateTime.now());
        notification.setStatus("UNREAD");

        messagingTemplate.convertAndSendToUser(
                userId,
                "/topic/notifications",
                notification);
    }

    /**
     * Send broadcast notification to all users
     * Internal service method - call from other services
     */
    public void sendBroadcastNotification(NotificationDto notification) {
        notification.setId(UUID.randomUUID().toString());
        notification.setCreatedAt(LocalDateTime.now());

        messagingTemplate.convertAndSend(
                "/topic/notifications",
                notification);
    }

    /**
     * Send real-time order update
     * Client sends to: /app/orders/update
     */
    @MessageMapping("/orders/update")
    @SendTo("/topic/orders")
    public RealTimeUpdateDto handleOrderUpdate(RealTimeUpdateDto update) {
        update.setUpdateId(UUID.randomUUID().toString());
        update.setTimestamp(LocalDateTime.now());
        update.setEntityType("ORDER");
        return update;
    }

    /**
     * Send real-time product update
     * Client sends to: /app/products/update
     */
    @MessageMapping("/products/update")
    @SendTo("/topic/products")
    public RealTimeUpdateDto handleProductUpdate(RealTimeUpdateDto update) {
        update.setUpdateId(UUID.randomUUID().toString());
        update.setTimestamp(LocalDateTime.now());
        update.setEntityType("PRODUCT");
        return update;
    }

    /**
     * Send real-time inventory update
     * Client sends to: /app/inventory/update
     */
    @MessageMapping("/inventory/update")
    @SendTo("/topic/inventory")
    public RealTimeUpdateDto handleInventoryUpdate(RealTimeUpdateDto update) {
        update.setUpdateId(UUID.randomUUID().toString());
        update.setTimestamp(LocalDateTime.now());
        update.setEntityType("INVENTORY");
        return update;
    }

}
