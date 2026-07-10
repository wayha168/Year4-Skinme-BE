package com.project.skin_me.service.chatbot;

import com.project.skin_me.dto.chatbot.ChatbotSessionSummary;
import com.project.skin_me.model.User;
import com.project.skin_me.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    /**
     * Admin Message Center: every non-admin user as a chat thread, merged with
     * chatbot.skinme.store session metadata (last message, online, etc.).
     */
    @Transactional
    public List<ChatbotSessionSummary> buildAdminUserSessions(List<ChatbotSessionSummary> chatbotSessions) {
        Map<String, ChatbotSessionSummary> bySessionId = new HashMap<>();
        Map<String, ChatbotSessionSummary> byUserId = new HashMap<>();
        if (chatbotSessions != null) {
            for (ChatbotSessionSummary s : chatbotSessions) {
                if (s == null) {
                    continue;
                }
                if (StringUtils.hasText(s.getSessionId())) {
                    bySessionId.put(s.getSessionId(), s);
                }
                if (StringUtils.hasText(s.getUserId())) {
                    byUserId.put(s.getUserId(), s);
                }
            }
        }

        List<ChatbotSessionSummary> result = new ArrayList<>();
        Set<String> includedSessionIds = new HashSet<>();

        for (User user : userRepository.findAll()) {
            if (isAdminUser(user)) {
                continue;
            }
            ChatbotSessionSummary fromBot = null;
            if (StringUtils.hasText(user.getChatSessionId())) {
                fromBot = bySessionId.get(user.getChatSessionId());
            }
            if (fromBot == null && user.getId() != null) {
                fromBot = byUserId.get(String.valueOf(user.getId()));
            }

            String sessionId = getOrCreateSessionId(user);
            ChatbotSessionSummary summary = fromBot != null ? fromBot : new ChatbotSessionSummary();
            summary.setSessionId(sessionId);
            summary.setUserId(String.valueOf(user.getId()));
            summary.setUserEmail(user.getEmail());
            summary.setUserName(ChatbotService.displayName(user));
            if (summary.getOnline() == null) {
                summary.setOnline(user.isOnline());
            }
            if (!StringUtils.hasText(summary.getLastMessage())) {
                summary.setLastMessage("No messages yet");
            }
            result.add(summary);
            includedSessionIds.add(sessionId);
            if (fromBot != null && StringUtils.hasText(fromBot.getSessionId())) {
                includedSessionIds.add(fromBot.getSessionId());
            }
        }

        // Keep orphan chatbot sessions that are not tied to a local user account
        if (chatbotSessions != null) {
            for (ChatbotSessionSummary s : chatbotSessions) {
                if (s == null || !StringUtils.hasText(s.getSessionId())) {
                    continue;
                }
                if (!includedSessionIds.contains(s.getSessionId())) {
                    result.add(s);
                }
            }
        }

        result.sort(Comparator
                .comparing(ChatbotSessionSummary::getLastMessageAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(s -> s.getUserName() != null ? s.getUserName() : "", String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    private static boolean isAdminUser(User user) {
        if (user == null || user.getRoles() == null) {
            return false;
        }
        return user.getRoles().stream()
                .map(role -> role.getName())
                .filter(StringUtils::hasText)
                .anyMatch(name -> "ADMIN".equalsIgnoreCase(name) || "ROLE_ADMIN".equalsIgnoreCase(name));
    }
}
