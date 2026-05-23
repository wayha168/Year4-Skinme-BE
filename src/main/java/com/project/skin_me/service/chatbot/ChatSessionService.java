package com.project.skin_me.service.chatbot;

import com.project.skin_me.model.User;
import com.project.skin_me.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatSessionService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public String getSessionId(User user) {
        if (user == null) {
            return null;
        }
        return user.getChatSessionId();
    }

    @Transactional
    public String getOrCreateSessionId(User user) {
        if (user == null) {
            return null;
        }
        if (StringUtils.hasText(user.getChatSessionId())) {
            return user.getChatSessionId();
        }
        String sessionId = "skinme_" + user.getId() + "_" + UUID.randomUUID().toString().substring(0, 8);
        user.setChatSessionId(sessionId);
        userRepository.save(user);
        return sessionId;
    }

    @Transactional
    public void saveSessionId(User user, String sessionId) {
        if (user == null || !StringUtils.hasText(sessionId)) {
            return;
        }
        if (sessionId.equals(user.getChatSessionId())) {
            return;
        }
        user.setChatSessionId(sessionId);
        userRepository.save(user);
    }
}
