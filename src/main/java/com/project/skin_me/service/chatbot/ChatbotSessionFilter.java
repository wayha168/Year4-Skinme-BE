package com.project.skin_me.service.chatbot;

import com.project.skin_me.dto.chatbot.ChatbotSessionSummary;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public final class ChatbotSessionFilter {

    public static final String ALL = "all";
    public static final String ADMIN_REPLIED = "admin-replied";
    public static final String AWAITING_ADMIN = "awaiting-admin";

    private ChatbotSessionFilter() {
    }

    public static List<ChatbotSessionSummary> apply(List<ChatbotSessionSummary> sessions, String filter) {
        if (sessions == null || sessions.isEmpty()) {
            return List.of();
        }
        Stream<ChatbotSessionSummary> stream = sessions.stream();
        if (ADMIN_REPLIED.equals(filter)) {
            stream = stream.filter(ChatbotSessionFilter::hasAdminReply);
        } else if (AWAITING_ADMIN.equals(filter)) {
            stream = stream.filter(s -> !hasAdminReply(s) && isLastFromUser(s));
        }
        return stream.toList();
    }

    public static boolean hasAdminReply(ChatbotSessionSummary session) {
        if (session == null) {
            return false;
        }
        return equalsIgnoreCase(session.getLastMessageSender(), "admin")
                || equalsIgnoreCase(session.getLastMessageRole(), "admin");
    }

    public static boolean isLastFromUser(ChatbotSessionSummary session) {
        if (session == null) {
            return false;
        }
        return equalsIgnoreCase(session.getLastMessageRole(), "user")
                || equalsIgnoreCase(session.getLastMessageSender(), "user");
    }

    public static String displayUser(ChatbotSessionSummary session) {
        if (session == null) {
            return "—";
        }
        if (StringUtils.hasText(session.getUserName())) {
            return session.getUserName();
        }
        if (StringUtils.hasText(session.getUserEmail())) {
            return session.getUserEmail();
        }
        if (StringUtils.hasText(session.getUserId())) {
            String id = session.getUserId();
            return id.length() > 12 ? id.substring(0, 8) + "…" : id;
        }
        return shortenSessionId(session.getSessionId());
    }

    public static String shortenSessionId(String sessionId) {
        if (!StringUtils.hasText(sessionId)) {
            return "—";
        }
        return sessionId.length() > 14 ? sessionId.substring(0, 8) + "…" : sessionId;
    }

    private static boolean equalsIgnoreCase(String a, String expected) {
        return a != null && a.toLowerCase(Locale.ROOT).equals(expected);
    }
}
