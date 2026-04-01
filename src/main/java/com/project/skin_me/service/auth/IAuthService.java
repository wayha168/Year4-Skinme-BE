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
    /**
     * Google Sign-In: send either {@code code} (auth-code flow) or {@code credential}/{@code id_token} (ID token flow).
     *
     * @param redirectUri optional for code flow; must match the redirect_uri used when the code was issued (e.g.
     *                    {@code postmessage} for GIS popup). If null/blank, uses
     *                    {@code spring.security.oauth2.client.registration.google.redirect-uri}.
     */
    ResponseEntity<ApiResponse> googleLogin(String code, String credential, String redirectUri, HttpServletRequest request, HttpServletResponse response);
    ResponseEntity<ApiResponse> signup(SignupRequest signupRequest);
    ResponseEntity<ApiResponse> logout();
    ResponseEntity<ApiResponse> resetPassword(String email, String token, String newPassword, String confirmPassword);
    ResponseEntity<ApiResponse> forgotPassword(String email);

    ResponseEntity<ApiResponse> sendOtp(SendOtpRequest request);
    ResponseEntity<ApiResponse> loginWithPhone(LoginWithPhoneRequest request, HttpServletRequest req, HttpServletResponse resp);
    ResponseEntity<ApiResponse> verifyPhone(VerifyPhoneRequest request);
    ResponseEntity<ApiResponse> registerWithPhone(RegisterWithPhoneRequest request, HttpServletRequest req, HttpServletResponse resp);
}