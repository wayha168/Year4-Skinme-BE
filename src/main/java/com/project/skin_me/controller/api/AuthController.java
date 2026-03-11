package com.project.skin_me.controller.api;

import com.project.skin_me.request.LoginRequest;
import com.project.skin_me.request.LoginWithPhoneRequest;
import com.project.skin_me.request.RegisterWithPhoneRequest;
import com.project.skin_me.request.SendOtpRequest;
import com.project.skin_me.request.SignupRequest;
import com.project.skin_me.request.VerifyPhoneRequest;
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

    @PostMapping("/send-otp")
    public ResponseEntity<ApiResponse> sendOtp(@Valid @RequestBody SendOtpRequest request) {
        return authService.sendOtp(request);
    }

    @PostMapping("/login-with-phone")
    public ResponseEntity<ApiResponse> loginWithPhone(
            @Valid @RequestBody LoginWithPhoneRequest request,
            HttpServletRequest req,
            HttpServletResponse resp) {
        return authService.loginWithPhone(request, req, resp);
    }

    @PostMapping("/verify-phone")
    public ResponseEntity<ApiResponse> verifyPhone(@Valid @RequestBody VerifyPhoneRequest request) {
        return authService.verifyPhone(request);
    }

    @PostMapping("/register-with-phone")
    public ResponseEntity<ApiResponse> registerWithPhone(
            @Valid @RequestBody RegisterWithPhoneRequest request,
            HttpServletRequest req,
            HttpServletResponse resp) {
        return authService.registerWithPhone(request, req, resp);
    }
}
