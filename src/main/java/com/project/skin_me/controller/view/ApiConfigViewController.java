package com.project.skin_me.controller.view;

import com.project.skin_me.model.config.ApiConfig;
import com.project.skin_me.service.api.ApiConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
@RequestMapping("/views/api-configs")
public class ApiConfigViewController {

    private final ApiConfigService apiConfigService;

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public String getAllApiConfigs(Model model) {
        try {
            List<ApiConfig> apiConfigs = apiConfigService.getAllApiConfigs();
            model.addAttribute("apiConfigs", apiConfigs);
            model.addAttribute("pageTitle", "API Configurations");
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load API configurations: " + e.getMessage());
        }
        return "api-configs";
    }

    @GetMapping("/{id}/edit")
    @PreAuthorize("isAuthenticated()")
    public String editApiConfig(@PathVariable Long id, Model model) {
        try {
            ApiConfig apiConfig = apiConfigService.getApiConfigById(id);
            model.addAttribute("apiConfig", apiConfig);
            model.addAttribute("apiConfigs", apiConfigService.getAllApiConfigs()); // for the table
            model.addAttribute("pageTitle", "Edit API Configuration");
            model.addAttribute("editMode", true);
        } catch (Exception e) {
            model.addAttribute("error", "Failed to load API configuration: " + e.getMessage());
            return "api-configs";
        }
        return "api-configs";
    }
}