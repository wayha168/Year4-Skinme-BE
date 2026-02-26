package com.project.skin_me.filter;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ensures redirect responses use HTTPS when the request was forwarded over HTTPS
 * (e.g. behind a reverse proxy). Fixes "Mixed Content" when the server sends
 * Location: http://... and the page was loaded over HTTPS.
 * Set app.force-https-redirects=true on the server if the proxy does not send X-Forwarded-Proto.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class HttpsRedirectRewriteFilter extends OncePerRequestFilter {

    @Value("${app.force-https-redirects:false}")
    private boolean forceHttpsRedirects;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        boolean useHttps = forceHttpsRedirects
                || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"))
                || "on".equalsIgnoreCase(request.getHeader("X-Forwarded-Ssl"));
        if (!useHttps) {
            filterChain.doFilter(request, response);
            return;
        }

        HttpServletResponseWrapper wrapper = new HttpServletResponseWrapper(response) {
            @Override
            public void sendRedirect(String location) throws IOException {
                if (location != null && location.startsWith("http://")) {
                    location = "https" + location.substring(4);
                }
                super.sendRedirect(location);
            }
        };
        filterChain.doFilter(request, wrapper);
    }
}
