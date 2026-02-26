package com.project.skin_me.config;

import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.service.i18n.I18nService;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Central translation for API responses. When ApiResponse.message is a message key (e.g. "api.auth.login.success"),
 * translates it to the request locale before serialization. No need to inject I18nService in each controller.
 */
@RestControllerAdvice
public class ApiResponseTranslateAdvice implements ResponseBodyAdvice<Object> {

    private final I18nService i18n;

    public ApiResponseTranslateAdvice(I18nService i18n) {
        this.i18n = i18n;
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (body instanceof ApiResponse apiResponse) {
            String msg = apiResponse.getMessage();
            if (msg != null && msg.startsWith("api.")) {
                Object[] args = apiResponse.getMessageArgs();
                String translated = args != null && args.length > 0
                        ? i18n.getMessage(msg, args)
                        : i18n.getMessage(msg);
                apiResponse.setMessage(translated);
                apiResponse.setMessageArgs(null);
            }
        }
        return body;
    }
}
