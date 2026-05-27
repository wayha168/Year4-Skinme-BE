package com.project.skin_me.controller.api;

import com.project.skin_me.model.config.ApiConfig;
import com.project.skin_me.service.api.ApiConfigService;
import com.project.skin_me.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static org.springframework.http.HttpStatus.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("${api.prefix}/config/api")
public class ApiConfigController {

    private final ApiConfigService apiConfigService;

    @GetMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse> getAllApiConfigs() {
        try {
            return ResponseEntity.ok(new ApiResponse("Success!", apiConfigService.getAllApiConfigs()));
        } catch (Exception e) {
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(new ApiResponse("Error: " + e.getMessage(), null));
        }
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse> getApiConfigById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(new ApiResponse("Found:", apiConfigService.getApiConfigById(id)));
        } catch (Exception e) {
            return ResponseEntity.status(NOT_FOUND)
                    .body(new ApiResponse(e.getMessage(), null));
        }
    }

    @PostMapping
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse> createApiConfig(@RequestBody ApiConfig apiConfig) {
        try {
            ApiConfig created = apiConfigService.createApiConfig(apiConfig);
            return ResponseEntity.ok(new ApiResponse("Created:", created));
        } catch (Exception e) {
            return ResponseEntity.status(BAD_REQUEST)
                    .body(new ApiResponse("Error: " + e.getMessage(), null));
        }
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse> updateApiConfig(@PathVariable Long id, @RequestBody ApiConfig apiConfig) {
        try {
            ApiConfig updated = apiConfigService.updateApiConfig(id, apiConfig);
            return ResponseEntity.ok(new ApiResponse("Updated:", updated));
        } catch (Exception e) {
            return ResponseEntity.status(BAD_REQUEST)
                    .body(new ApiResponse("Error: " + e.getMessage(), null));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse> deleteApiConfig(@PathVariable Long id) {
        try {
            apiConfigService.deleteApiConfig(id);
            return ResponseEntity.ok(new ApiResponse("Deleted:", null));
        } catch (Exception e) {
            return ResponseEntity.status(BAD_REQUEST)
                    .body(new ApiResponse("Error: " + e.getMessage(), null));
        }
    }
}