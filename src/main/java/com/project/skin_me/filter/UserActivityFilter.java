package com.project.skin_me.filter;

import com.project.skin_me.repository.UserRepository;
import com.project.skin_me.security.user.ShopUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
@RequiredArgsConstructor
public class UserActivityFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(UserActivityFilter.class);
    private final UserRepository userRepository;

    private static final List<String> SKIP_PATHS = List.of(
            "/v3/api-docs",
            "/swagger",
            "/webjars",
            "/api/v1/auth/login",
            "/api/v1/auth/logout",
            "/api/v1/auth/signup",
            "/login-page",
            "/logout",
            "/signup",
            "/reset-password",
            "/css",
            "/js",
            "/images"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        // Skip tracking for certain paths
        if (SKIP_PATHS.stream().anyMatch(path::startsWith)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            // Check if user is authenticated
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() 
                    && authentication.getPrincipal() instanceof ShopUserDetails) {
                
                ShopUserDetails userDetails = (ShopUserDetails) authentication.getPrincipal();
                Long userId = userDetails.getId();
                
                // Real-time online: if user is logged in and viewing the site, mark online and refresh last activity
                userRepository.findById(userId).ifPresent(user -> {
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime previousActivity = user.getLastActivity();
                    String ipAddress = getClientIpAddress(request);
                    user.setIsOnline(true);
                    user.setLastActivity(now);
                    if (ipAddress != null && !ipAddress.isEmpty() && !"unknown".equals(ipAddress)) {
                        user.setLastIpAddress(ipAddress);
                    }
                    // Persist at most once per minute to avoid excessive DB writes
                    boolean shouldSave = previousActivity == null || ChronoUnit.MINUTES.between(previousActivity, now) >= 1;
                    if (shouldSave) {
                        userRepository.save(user);
                        logger.debug("Updated activity for user ID: {} from IP: {} (online)", userId, ipAddress);
                    }
                });
            }
        } catch (Exception e) {
            logger.error("Error updating user activity: {}", e.getMessage(), e);
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Get client IP address from HttpServletRequest
     */
    private String getClientIpAddress(HttpServletRequest request) {
        if (request == null) {
            return "unknown";
        }
        String ipAddress = request.getHeader("X-Forwarded-For");
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("X-Real-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
            ipAddress = request.getRemoteAddr();
        }
        // Handle multiple IPs (X-Forwarded-For can contain multiple IPs)
        if (ipAddress != null && ipAddress.contains(",")) {
            ipAddress = ipAddress.split(",")[0].trim();
        }
        return ipAddress != null ? ipAddress : "unknown";
    }
}
