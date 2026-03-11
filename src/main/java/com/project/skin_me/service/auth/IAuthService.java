package com.project.skin_me.service.auth;

import com.project.skin_me.request.LoginRequest;
import com.project.skin_me.request.LoginWithPhoneRequest;
import com.project.skin_me.request.RegisterWithPhoneRequest;
import com.project.skin_me.request.SendOtpRequest;
import com.project.skin_me.request.SignupRequest;
import com.project.skin_me.request.VerifyPhoneRequest;
import com.project.skin_me.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;

public interface IAuthService {
    ResponseEntity<ApiResponse> login(LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse response);
    ResponseEntity<ApiResponse> googleLogin(String code, HttpServletRequest request, HttpServletResponse response);
    ResponseEntity<ApiResponse> signup(SignupRequest signupRequest);
    ResponseEntity<ApiResponse> logout();
    ResponseEntity<ApiResponse> resetPassword(String email, String token, String newPassword, String confirmPassword);
    ResponseEntity<ApiResponse> forgotPassword(String email);

    ResponseEntity<ApiResponse> sendOtp(SendOtpRequest request);
    ResponseEntity<ApiResponse> loginWithPhone(LoginWithPhoneRequest request, HttpServletRequest req, HttpServletResponse resp);
    ResponseEntity<ApiResponse> verifyPhone(VerifyPhoneRequest request);
    ResponseEntity<ApiResponse> registerWithPhone(RegisterWithPhoneRequest request, HttpServletRequest req, HttpServletResponse resp);
}