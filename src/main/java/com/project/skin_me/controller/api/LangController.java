package com.project.skin_me.controller.api;

import com.project.skin_me.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.util.Locale;
import java.util.Map;

/**
 * Language/locale switching for dashboard (redirect) and API (JSON).
 * Uses the same "lang" param as LocaleChangeInterceptor (cookie skinme_lang).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("${api.prefix}/lang")
public class LangController {

    /**
     * Get current locale or set it via ?lang= and return new locale.
     * For API clients: GET /api/v1/lang returns current; GET /api/v1/lang?lang=km sets cookie and returns { "locale": "km" }.
     */
    @GetMapping
    public ResponseEntity<ApiResponse> getOrSetLang(
            @RequestParam(required = false) String lang,
            HttpServletRequest request) {
        Locale locale = RequestContextUtils.getLocale(request);
        if (lang != null && !lang.isBlank()) {
            locale = Locale.forLanguageTag(lang.trim());
        }
        String tag = locale.toLanguageTag();
        if (tag.length() > 2) tag = tag.substring(0, 2);
        return ResponseEntity.ok(new ApiResponse("OK", Map.of("locale", tag)));
    }
}
