package com.project.skin_me.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.util.Locale;
import java.util.Map;

/**
 * Translates common English pageTitle values set by controllers into the active locale.
 */
@Component
public class PageTitleI18nInterceptor implements HandlerInterceptor {

    private static final Map<String, String> TITLE_KEYS = Map.ofEntries(
            Map.entry("Dashboard", "page.dashboard"),
            Map.entry("Point of Sale", "page.pos"),
            Map.entry("Categories Management", "page.categories"),
            Map.entry("Brands Management", "page.brands"),
            Map.entry("Products", "page.products"),
            Map.entry("Promotion Management", "page.promotions"),
            Map.entry("Orders Record", "page.orders"),
            Map.entry("Payments Record", "page.payments"),
            Map.entry("Payment Record", "page.payments"),
            Map.entry("Delivery Records", "page.delivery"),
            Map.entry("Delivery Record", "page.delivery"),
            Map.entry("Chat", "page.chat"),
            Map.entry("Chat History", "page.chat.history"),
            Map.entry("Chat history", "page.chat.history"),
            Map.entry("Chat Activity", "page.chat.activity"),
            Map.entry("User Management", "page.users"),
            Map.entry("Audit Logs", "page.audit"),
            Map.entry("Audit Log", "page.audit"),
            Map.entry("User Feedback", "page.feedback"),
            Map.entry("User Orders", "page.my.orders"),
            Map.entry("KHQR Bank Accounts", "page.khqr"),
            Map.entry("API Configurations", "page.api"),
            Map.entry("Checkout", "page.checkout")
    );

    private final MessageSource messageSource;

    public PageTitleI18nInterceptor(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           ModelAndView modelAndView) {
        if (modelAndView == null || modelAndView.getModel() == null) {
            return;
        }
        Object title = modelAndView.getModel().get("pageTitle");
        if (!(title instanceof String english) || english.isBlank()) {
            return;
        }
        String key = TITLE_KEYS.get(english.trim());
        if (key == null) {
            return;
        }
        Locale locale = LocaleContextHolder.getLocale();
        String translated = messageSource.getMessage(key, null, english, locale);
        modelAndView.getModel().put("pageTitle", translated);
    }
}
