package com.project.skin_me.config;

import com.project.skin_me.model.User;
import com.project.skin_me.service.user.IUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
@RequiredArgsConstructor
public class GlobalModelAttribute {

    private final IUserService userService;

    @Value("${api.prefix:/api/v1}")
    private String apiPrefix;

    @ModelAttribute
    public void addGlobalAttributes(Model model) {
        // Add API prefix to all templates
        model.addAttribute("api", new ApiConfig(apiPrefix));
        
        // Add current user if authenticated
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && 
                !authentication.getName().equals("anonymousUser")) {
                User currentUser = userService.getAuthenticatedUser();
                model.addAttribute("currentUser", currentUser);
            }
        } catch (Exception e) {
            // User not authenticated or error getting user - ignore
        }
    }

    // Simple inner class to hold API config
    public static class ApiConfig {
        private final String prefix;

        public ApiConfig(String prefix) {
            this.prefix = prefix;
        }

        public String getPrefix() {
            return prefix;
        }
    }
}
