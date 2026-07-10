package com.project.skin_me.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "chat_ai")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChatAi {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Chat session identifier (displayed as sender in admin view). */
    @Column(name = "session", length = 255)
    private String session;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    /** Map to DB column (MySQL often uses created_at; change name if your table differs). */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime timestamp;

    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
