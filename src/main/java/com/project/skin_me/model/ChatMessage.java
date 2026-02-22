package com.project.skin_me.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "user_id", nullable = true)
    private User user;
    
    @Column(nullable = false, length = 100)
    private String sender; // username or "assistant" or "admin"
    
    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;
    
    @Column(nullable = false, length = 20)
    private String type; // "user", "assistant", "admin", "notification"
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Column(name = "conversation_id", length = 100)
    private String conversationId;
    
    @Column(name = "is_ai_response")
    private boolean isAiResponse = false;
    
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
