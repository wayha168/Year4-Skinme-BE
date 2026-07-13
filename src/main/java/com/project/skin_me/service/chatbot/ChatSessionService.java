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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    /**
     * Stable session id for a user. Prefers an existing DB value; otherwise uses
     * {@code skinme_{userId}} without requiring a chatbot round-trip first.
     */
    @Transactional
    public String getOrCreateSessionId(User user) {
        if (user == null) {
            return null;
        }
        if (StringUtils.hasText(user.getChatSessionId())) {
            return user.getChatSessionId();
        }
        String sessionId = provisionalSessionId(user.getId());
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

    public static String provisionalSessionId(Long userId) {
        return userId == null ? null : "skinme_" + userId;
    }

    /**
     * Admin Message Center: every non-admin user as a chat thread, merged with
     * chatbot.skinme.store session metadata. Read-only — does not mass-write session ids.
     */
    @Transactional(readOnly = true)
    public List<ChatbotSessionSummary> buildAdminUserSessions(List<ChatbotSessionSummary> chatbotSessions) {
        Map<String, ChatbotSessionSummary> bySessionId = new HashMap<>();
        Map<String, ChatbotSessionSummary> byUserId = new HashMap<>();
        Map<String, ChatbotSessionSummary> byEmail = new HashMap<>();
        if (chatbotSessions != null) {
            for (ChatbotSessionSummary s : chatbotSessions) {
                if (s == null) {
                    continue;
                }
                if (StringUtils.hasText(s.getSessionId())) {
                    bySessionId.put(s.getSessionId(), s);
                }
                if (StringUtils.hasText(s.getUserId())) {
                    byUserId.put(s.getUserId().trim(), s);
                }
                if (StringUtils.hasText(s.getUserEmail())) {
                    byEmail.put(s.getUserEmail().trim().toLowerCase(Locale.ROOT), s);
                }
            }
        }

        List<ChatbotSessionSummary> result = new ArrayList<>();
        Set<String> includedSessionIds = new HashSet<>();
        Set<Long> includedUserIds = new HashSet<>();

        for (User user : userRepository.findAll()) {
            if (isAdminUser(user)) {
                continue;
            }
            ChatbotSessionSummary fromBot = null;
            if (StringUtils.hasText(user.getChatSessionId())) {
                fromBot = bySessionId.get(user.getChatSessionId());
            }
            if (fromBot == null) {
                fromBot = byUserId.get(String.valueOf(user.getId()));
            }
            if (fromBot == null && StringUtils.hasText(user.getEmail())) {
                fromBot = byEmail.get(user.getEmail().trim().toLowerCase(Locale.ROOT));
            }

            // Prefer chatbot session id so history/WebSocket match chatbot.skinme.store
            String sessionId;
            if (fromBot != null && StringUtils.hasText(fromBot.getSessionId())) {
                sessionId = fromBot.getSessionId();
            } else if (StringUtils.hasText(user.getChatSessionId())) {
                sessionId = user.getChatSessionId();
            } else {
                sessionId = provisionalSessionId(user.getId());
            }

            ChatbotSessionSummary summary = fromBot != null ? copySummary(fromBot) : new ChatbotSessionSummary();
            summary.setSessionId(sessionId);
            summary.setUserId(String.valueOf(user.getId()));
            summary.setUserEmail(user.getEmail());
            summary.setUserName(ChatbotService.displayName(user));
            summary.setOnline(user.isOnline());
            if (!StringUtils.hasText(summary.getLastMessage())) {
                summary.setLastMessage("No messages yet — tap to chat");
            }
            result.add(summary);
            includedSessionIds.add(sessionId);
            includedUserIds.add(user.getId());
            if (fromBot != null && StringUtils.hasText(fromBot.getSessionId())) {
                includedSessionIds.add(fromBot.getSessionId());
            }
        }

        if (chatbotSessions != null) {
            for (ChatbotSessionSummary s : chatbotSessions) {
                if (s == null || !StringUtils.hasText(s.getSessionId())) {
                    continue;
                }
                if (includedSessionIds.contains(s.getSessionId())) {
                    continue;
                }
                Long parsedUserId = parseUserId(s.getUserId());
                if (parsedUserId != null && includedUserIds.contains(parsedUserId)) {
                    continue;
                }
                ChatbotSessionSummary orphan = copySummary(s);
                if (!StringUtils.hasText(orphan.getUserName()) && StringUtils.hasText(orphan.getUserEmail())) {
                    orphan.setUserName(orphan.getUserEmail());
                }
                if (!StringUtils.hasText(orphan.getLastMessage())) {
                    orphan.setLastMessage("No messages yet — tap to chat");
                }
                if (orphan.getOnline() == null) {
                    orphan.setOnline(false);
                }
                result.add(orphan);
                includedSessionIds.add(s.getSessionId());
            }
        }

        result.sort(Comparator
                .comparing((ChatbotSessionSummary s) -> hasRealLastMessage(s) ? 0 : 1)
                .thenComparing(ChatbotSessionSummary::getLastMessageAt, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(s -> s.getUserName() != null ? s.getUserName() : "", String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /**
     * Resolve profile for an admin-selected session (local user or chatbot orphan).
     */
    @Transactional(readOnly = true)
    public Optional<ChatbotSessionSummary> resolveSessionUser(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return Optional.empty();
        }
        String sid = sessionId.trim();

        Optional<User> bySession = userRepository.findByChatSessionId(sid);
        if (bySession.isPresent()) {
            return Optional.of(summaryFromUser(bySession.get(), sid));
        }

        Long userId = parseSkinmeUserId(sid);
        if (userId != null) {
            Optional<User> byId = userRepository.findById(userId);
            if (byId.isPresent() && !isAdminUser(byId.get())) {
                return Optional.of(summaryFromUser(byId.get(), sid));
            }
        }

        return Optional.empty();
    }

    /** Persist session id when admin opens a thread for a known user. */
    @Transactional
    public void ensureSessionBound(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return;
        }
        String sid = sessionId.trim();
        if (userRepository.findByChatSessionId(sid).isPresent()) {
            return;
        }
        Long userId = parseSkinmeUserId(sid);
        if (userId == null) {
            return;
        }
        userRepository.findById(userId).ifPresent(user -> {
            if (!isAdminUser(user) && !sid.equals(user.getChatSessionId())) {
                user.setChatSessionId(sid);
                userRepository.save(user);
            }
        });
    }

    private static ChatbotSessionSummary summaryFromUser(User user, String sessionId) {
        ChatbotSessionSummary summary = new ChatbotSessionSummary();
        summary.setSessionId(sessionId);
        summary.setUserId(String.valueOf(user.getId()));
        summary.setUserEmail(user.getEmail());
        summary.setUserName(ChatbotService.displayName(user));
        summary.setOnline(user.isOnline());
        summary.setLastMessage("No messages yet — tap to chat");
        return summary;
    }

    private static ChatbotSessionSummary copySummary(ChatbotSessionSummary source) {
        ChatbotSessionSummary copy = new ChatbotSessionSummary();
        copy.setSessionId(source.getSessionId());
        copy.setUserId(source.getUserId());
        copy.setUserEmail(source.getUserEmail());
        copy.setUserName(source.getUserName());
        copy.setSessionCreatedAt(source.getSessionCreatedAt());
        copy.setLastMessage(source.getLastMessage());
        copy.setLastMessageAt(source.getLastMessageAt());
        copy.setLastMessageRole(source.getLastMessageRole());
        copy.setLastMessageSender(source.getLastMessageSender());
        copy.setOnline(source.getOnline());
        return copy;
    }

    private static boolean hasRealLastMessage(ChatbotSessionSummary s) {
        String msg = s.getLastMessage();
        return StringUtils.hasText(msg)
                && !msg.toLowerCase(Locale.ROOT).startsWith("no messages yet");
    }

    private static Long parseUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Parse {@code skinme_123} or {@code skinme_123_abcd1234}. */
    private static Long parseSkinmeUserId(String sessionId) {
        if (!StringUtils.hasText(sessionId) || !sessionId.startsWith("skinme_")) {
            return null;
        }
        String rest = sessionId.substring("skinme_".length());
        int underscore = rest.indexOf('_');
        String idPart = underscore > 0 ? rest.substring(0, underscore) : rest;
        return parseUserId(idPart);
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
