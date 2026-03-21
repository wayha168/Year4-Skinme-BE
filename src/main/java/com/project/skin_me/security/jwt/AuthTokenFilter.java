package com.project.skin_me.security.jwt;

import com.project.skin_me.security.user.ShopUserDetailsService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class AuthTokenFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final ShopUserDetailsService userDetailsService;

    /** Paths that never require JWT (public assets, login, etc.). /uploads = product images, public for scraping/embedding. */
    private static final List<String> SKIP_PATHS = List.of(
            "/v3/api-docs",
            "/swagger",
            "/webjars",
            "/api/v1/auth",
            "/login-page",
            "/login",
            "/signup",
            "/reset-password",
            "/logout",
            "/css",
            "/js",
            "/images",
            "/uploads",
            "/.well-known"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (SKIP_PATHS.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Skip JWT processing if user is already authenticated via session (form login)
        org.springframework.security.core.Authentication existingAuth = 
                SecurityContextHolder.getContext().getAuthentication();
        if (existingAuth != null && existingAuth.isAuthenticated() 
                && !(existingAuth instanceof org.springframework.security.authentication.AnonymousAuthenticationToken)) {
            // User already authenticated via form login or other means, skip JWT processing
            filterChain.doFilter(request, response);
            return;
        }

        try {
            String jwt = parseToken(request);
            if (jwt != null) {
                if (jwtUtils.validateToken(jwt)) {
                    String username = jwtUtils.getUsernameFromToken(jwt);
                    var userDetails = userDetailsService.loadUserByUsername(username);
                    var auth = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            // Token expired - clear token cookie and redirect to login for web requests
            if (!path.startsWith("/api/")) {
                clearTokenCookie(response);
                response.sendRedirect("/login-page?expired=true");
                return;
            } else {
                // For API requests, return JSON error
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Token expired\",\"message\":\"Please login again\"}");
                return;
            }
        } catch (JwtException e) {
            // Invalid token - clear token cookie and redirect to login for web requests
            if (!path.startsWith("/api/")) {
                clearTokenCookie(response);
                response.sendRedirect("/login-page?error=Invalid token");
                return;
            } else {
                // For API requests, return JSON error
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"error\":\"Invalid token\",\"message\":\"" + e.getMessage() + "\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    private String parseToken(HttpServletRequest request) {
        // Try Authorization header first
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        
        // Try cookie as fallback
        jakarta.servlet.http.Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (jakarta.servlet.http.Cookie cookie : cookies) {
                if ("token".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        
        return null;
    }

    private void clearTokenCookie(HttpServletResponse response) {
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie("token", null);
        cookie.setPath("/");
        cookie.setMaxAge(0);
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        response.addCookie(cookie);
    }
}