package com.project.skin_me.repository;

import com.project.skin_me.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    
    List<ChatMessage> findByUserIdOrderByTimestampAsc(Long userId);
    
    List<ChatMessage> findByConversationIdOrderByTimestampAsc(String conversationId);
    
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.type = 'assistant' OR cm.isAiResponse = true ORDER BY cm.timestamp DESC")
    List<ChatMessage> findAllAiResponses();
    
    @Query("SELECT cm FROM ChatMessage cm WHERE cm.type IN ('user', 'admin') ORDER BY cm.timestamp DESC")
    List<ChatMessage> findAllUserAdminChats();
    
    List<ChatMessage> findAllByOrderByTimestampDesc();
}
