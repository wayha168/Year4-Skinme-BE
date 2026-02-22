package com.project.skin_me.config;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;

import java.util.Locale;

/**
 * Locale resolver that uses Accept-Language header or lang query param for API paths (/api/**),
 * and cookie for web/dashboard paths.
 */
public class ApiAwareLocaleResolver extends CookieLocaleResolver {

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        if (request.getRequestURI().startsWith("/api/")) {
            // API: use ?lang= param first, then Accept-Language header
            String langParam = request.getParameter("lang");
            if (langParam != null && !langParam.isBlank()) {
                return Locale.forLanguageTag(langParam);
            }
            String acceptLanguage = request.getHeader("Accept-Language");
            if (acceptLanguage != null && !acceptLanguage.isBlank()) {
                Locale requestLocale = request.getLocale();
                if (requestLocale != null && !requestLocale.getLanguage().isBlank()) {
                    return requestLocale;
                }
            }
            return getDefaultLocale() != null ? getDefaultLocale() : Locale.ENGLISH;
        }
        return super.resolveLocale(request);
    }
}
