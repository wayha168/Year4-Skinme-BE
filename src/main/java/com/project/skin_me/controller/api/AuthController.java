package com.project.skin_me.controller.api;

import com.project.skin_me.request.LoginRequest;
import com.project.skin_me.request.SignupRequest;
import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.service.auth.IAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("${api.prefix}/auth")
public class AuthController {

    private final IAuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse> login(
            @Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request,
            HttpServletResponse response) {
        return authService.login(loginRequest, request, response);
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse> signup(@Valid @RequestBody SignupRequest signupRequest) {
        return authService.signup(signupRequest);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout() {
        return authService.logout();
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse> resetPassword(
            @RequestParam String email,
            @RequestParam String token,
            @RequestParam String password,
            @RequestParam String confirmPassword) {
        return authService.resetPassword(email, token, password, confirmPassword);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse> forgotPassword(
            @RequestParam String email) {
        return authService.forgotPassword(email);
    }

    @PostMapping("/google")
    public ResponseEntity<ApiResponse> googleLogin(
            @RequestBody Map<String, String> requestBody,
            HttpServletRequest request,
            HttpServletResponse response) {
        String code = requestBody != null ? requestBody.get("code") : null;
        if (code == null || code.isBlank()) {
            return ResponseEntity.status(org.springframework.http.HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.ofKey("api.auth.google.code.required", null));
        }
        return authService.googleLogin(code, request, response);
    }
}
