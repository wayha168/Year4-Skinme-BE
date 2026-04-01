package com.project.skin_me.service.auth;

import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.project.skin_me.enums.ActivityType;
import com.project.skin_me.model.Activity;
import com.project.skin_me.model.PhoneOtp;
import com.project.skin_me.model.PhoneVerification;
import com.project.skin_me.model.Role;
import com.project.skin_me.model.User;
import com.project.skin_me.repository.ActivityRepository;
import com.project.skin_me.repository.PhoneOtpRepository;
import com.project.skin_me.repository.PhoneVerificationRepository;
import com.project.skin_me.repository.RoleRepository;
import com.project.skin_me.repository.UserRepository;
import com.project.skin_me.request.LoginRequest;
import com.project.skin_me.request.LoginWithPhoneRequest;
import com.project.skin_me.request.RegisterWithPhoneRequest;
import com.project.skin_me.request.SendOtpRequest;
import com.project.skin_me.request.SignupRequest;
import com.project.skin_me.request.VerifyPhoneRequest;
import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.response.JwtResponse;
import com.project.skin_me.security.jwt.JwtUtils;
import com.project.skin_me.security.user.ShopUserDetails;
import com.project.skin_me.service.email.EmailService;
import com.project.skin_me.service.notification.NotificationService;
import com.project.skin_me.service.sms.SmsService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.*;

@Service
@RequiredArgsConstructor
public class AuthService implements IAuthService {
    private static final Logger logger = LoggerFactory.getLogger(AuthService.class);

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final ActivityRepository activityRepository;
    private final EmailService emailService;
    private final NotificationService notificationService;
    private final PhoneOtpRepository phoneOtpRepository;
    private final PhoneVerificationRepository phoneVerificationRepository;
    private final SmsService smsService;

    private static final int OTP_EXPIRY_MINUTES = 10;
    private static final int PHONE_VERIFICATION_EXPIRY_MINUTES = 15;
    private static final int OTP_LENGTH = 6;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    /** When true, send-otp returns the OTP in response.data.otp when SMS delivery fails (dev/testing only). */
    @Value("${app.sms.dev.return-otp-when-delivery-fails:false}")
    private boolean returnOtpWhenDeliveryFails;

    /** Must match the redirect URI used by the frontend in the Google auth request and in Google Cloud Console. */
    @Value("${spring.security.oauth2.client.registration.google.redirect-uri}")
    private String googleRedirectUri;

    @Override
    @Transactional
    public ResponseEntity<ApiResponse> login(LoginRequest loginRequest, HttpServletRequest request, HttpServletResponse response) {
        try {
            logger.debug("Attempting login for email: {}", loginRequest.getEmail());
            
            // Check if user exists and validate login method
            Optional<User> userOptional = userRepository.findByEmail(loginRequest.getEmail());
            if (userOptional.isPresent()) {
                User user = userOptional.get();
                // Check if user registered via Google (has googleId but no password set)
                if (user.getGoogleId() != null && !user.getGoogleId().isEmpty()) {
                    // User registered via Google - check if they're trying to login with password
                    // Allow login if password is set (user may have set password later)
                    if (user.getPassword() == null || user.getPassword().isEmpty()) {
                        logger.warn("Login failed: User registered via Google, please use Google login. Email: {}", loginRequest.getEmail());
                        return ResponseEntity.status(UNAUTHORIZED)
                                .body(ApiResponse.ofKey("api.auth.google.required", null));
                    }
                }
            }
            
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            loginRequest.getEmail(), loginRequest.getPassword()));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtils.generateTokenForUser(authentication);
            ShopUserDetails userDetails = (ShopUserDetails) authentication.getPrincipal();

            // Get IP address from request
            String ipAddress = getClientIpAddress(request);
            recordLogin(userDetails.getId(), ipAddress);

            Set<String> roles = userDetails.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .collect(Collectors.toSet());

            Cookie cookie = new Cookie("token", jwt);
            cookie.setHttpOnly(true);
            cookie.setSecure(true);
            cookie.setPath("/");
            cookie.setMaxAge(7 * 24 * 60 * 60);
            cookie.setAttribute("SameSite", "Strict");
            response.addCookie(cookie);

            JwtResponse jwtResponse = new JwtResponse(userDetails.getId(), jwt, roles);
            logger.info("User logged in: {}", userDetails.getEmail());
            
            // Send WebSocket notification for successful login
            try {
                notificationService.notifyUser(
                    userDetails.getId().toString(),
                    "Welcome Back!",
                    "You have successfully logged in to your account.",
                    "AUTH"
                );
            } catch (Exception e) {
                logger.warn("Failed to send login notification: {}", e.getMessage());
            }
            
            return ResponseEntity.ok(ApiResponse.ofKey("api.auth.login.success", jwtResponse));

        } catch (AuthenticationException e) {
            logger.warn("Login failed for email: {}. Error: {}", loginRequest.getEmail(), e.getMessage());
            return ResponseEntity.status(UNAUTHORIZED)
                    .body(ApiResponse.ofKey("api.auth.login.failed", null));
        } catch (Exception e) {
            logger.error("Unexpected error during login for email: {}. Error: {}", loginRequest.getEmail(),
                    e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    @Override
    @Transactional
    public ResponseEntity<ApiResponse> googleLogin(String code, String credential, String redirectUri, HttpServletRequest request, HttpServletResponse response) {
        try {
            final GoogleIdToken idToken;
            if (code != null && !code.isBlank()) {
                logger.debug("Google login: auth-code flow (prefix={}...)", code.substring(0, Math.min(8, code.length())));
                try {
                    idToken = exchangeAuthCodeForGoogleIdToken(code.trim(), redirectUri);
                } catch (TokenResponseException e) {
                    logger.error(
                            "Google token endpoint rejected auth code (redirect_uri must match frontend; codes are single-use and expire quickly): {}",
                            e.getMessage());
                    return ResponseEntity.status(UNAUTHORIZED)
                            .body(ApiResponse.ofKey("api.auth.google.code.invalid", null));
                } catch (IOException e) {
                    logger.error("Google auth code exchange failed: {}", e.getMessage(), e);
                    return ResponseEntity.status(UNAUTHORIZED)
                            .body(ApiResponse.ofKey("api.auth.google.code.invalid", null));
                }
            } else if (credential != null && !credential.isBlank()) {
                logger.debug("Google login: ID token (credential) flow");
                try {
                    idToken = verifyGoogleCredentialJwt(credential.trim());
                } catch (GeneralSecurityException | IOException e) {
                    logger.error("Google credential verification failed: {}", e.getMessage());
                    return ResponseEntity.status(UNAUTHORIZED)
                            .body(ApiResponse.ofKey("api.auth.google.credential.invalid", null));
                }
                if (idToken == null) {
                    return ResponseEntity.status(UNAUTHORIZED)
                            .body(ApiResponse.ofKey("api.auth.google.credential.invalid", null));
                }
            } else {
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.google.payload.required", null));
            }

            return completeGoogleLoginWithIdToken(idToken, request, response);

        } catch (IllegalArgumentException e) {
            logger.error("Google login failed - invalid argument: {}", e.getMessage(), e);
            return ResponseEntity.status(BAD_REQUEST)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        } catch (RuntimeException e) {
            logger.error("Google login failed - runtime error: {}", e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        } catch (Exception e) {
            logger.error("Google login failed - unexpected error: {}", e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    /**
     * Auth-code flow: exchange at Google's token endpoint. {@code redirectUri} must match the client that issued the code
     * (often {@code postmessage} for GIS popup).
     */
    private GoogleIdToken exchangeAuthCodeForGoogleIdToken(String code, String redirectUri) throws IOException {
        String redirectUriForToken = (redirectUri != null && !redirectUri.isBlank())
                ? redirectUri.trim()
                : googleRedirectUri;
        logger.debug("Google token exchange using redirect_uri={}", redirectUriForToken);

        GoogleTokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                new NetHttpTransport(),
                JacksonFactory.getDefaultInstance(),
                "https://oauth2.googleapis.com/token",
                googleClientId,
                googleClientSecret,
                code,
                redirectUriForToken).execute();

        GoogleIdToken idToken = tokenResponse.parseIdToken();
        if (idToken == null) {
            throw new IOException("No ID token in Google token response");
        }
        return idToken;
    }

    /**
     * ID-token flow (Sign in with Google button / {@code google.accounts.id}): verify JWT using Google's certs; no client secret.
     */
    private GoogleIdToken verifyGoogleCredentialJwt(String credentialJwt) throws GeneralSecurityException, IOException {
        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                new NetHttpTransport(),
                JacksonFactory.getDefaultInstance())
                .setAudience(Collections.singletonList(googleClientId))
                .build();
        return verifier.verify(credentialJwt);
    }

    private ResponseEntity<ApiResponse> completeGoogleLoginWithIdToken(GoogleIdToken idToken, HttpServletRequest request, HttpServletResponse response) {
            GoogleIdToken.Payload payload = idToken.getPayload();

            // Extract user information
            String email = payload.getEmail();
            String googleId = payload.getSubject();
            
            // Validate required fields
            if (email == null || email.isBlank()) {
                logger.error("Google login failed: Email is missing from Google ID token");
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.google.email.required", null));
            }
            
            if (googleId == null || googleId.isBlank()) {
                logger.error("Google login failed: Google ID is missing from ID token");
                return ResponseEntity.status(BAD_REQUEST)
                        .body(new ApiResponse("Google ID is required but not provided", null));
            }

            String firstName = payload.get("given_name") != null ? payload.get("given_name").toString() : "";
            String lastName = payload.get("family_name") != null ? payload.get("family_name").toString() : "";
            logger.debug("Google user info: email={}, googleId={}", email, googleId);

            // Find or create user
            User user = userRepository.findByEmail(email).orElseGet(() -> {
                Role userRole = roleRepository.findByName("ROLE_USER")
                        .orElseThrow(() -> {
                            logger.error("Default role ROLE_USER not found");
                            return new RuntimeException("Default role ROLE_USER not found.");
                        });
                User newUser = new User();
                newUser.setEmail(email);
                newUser.setGoogleId(googleId);
                newUser.setFirstName(firstName);
                newUser.setLastName(lastName);
                // Don't set password for Google users - they authenticate via Google
                newUser.setEnabled(true);
                newUser.setRegistrationDate(LocalDateTime.now());
                newUser.setIsOnline(false);
                newUser.setRoles(new HashSet<>(Collections.singletonList(userRole)));
                logger.info("Creating new user for Google login: {}", email);
                return registerUser(newUser);
            });
            
            // Update Google ID if user exists but doesn't have it set (for existing email/password users)
            if (user.getGoogleId() == null || user.getGoogleId().isEmpty()) {
                logger.info("Linking Google account to existing user: {}", email);
                user.setGoogleId(googleId);
                // Update name if not set
                if ((user.getFirstName() == null || user.getFirstName().isEmpty()) && !firstName.isEmpty()) {
                    user.setFirstName(firstName);
                }
                if ((user.getLastName() == null || user.getLastName().isEmpty()) && !lastName.isEmpty()) {
                    user.setLastName(lastName);
                }
                userRepository.save(user);
            } else if (!user.getGoogleId().equals(googleId)) {
                // Google ID mismatch - security concern
                logger.warn("Google ID mismatch for user: {}. Expected: {}, Got: {}", email, user.getGoogleId(), googleId);
                return ResponseEntity.status(UNAUTHORIZED)
                        .body(ApiResponse.ofKey("api.auth.google.verify.failed", null));
            }

            // Ensure user is enabled
            if (!user.isEnabled()) {
                logger.warn("Google login failed: User account is disabled: {}", email);
                return ResponseEntity.status(UNAUTHORIZED)
                        .body(ApiResponse.ofKey("api.auth.account.disabled", null));
            }

            // Create authentication and generate JWT
            ShopUserDetails userDetails = ShopUserDetails.buildUserDetails(user);
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            String jwt = jwtUtils.generateTokenForUser(authentication);

            // Record login activity
            String ipAddress = getClientIpAddress(request);
            recordLogin(user.getId(), ipAddress);

            // Set JWT cookie
            Cookie cookie = new Cookie("token", jwt);
            cookie.setHttpOnly(true);
            cookie.setSecure(true);
            cookie.setPath("/");
            cookie.setMaxAge(7 * 24 * 60 * 60);
            cookie.setAttribute("SameSite", "Strict");
            response.addCookie(cookie);

            Set<String> roles = userDetails.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .collect(Collectors.toSet());

            JwtResponse jwtResponse = new JwtResponse(user.getId(), jwt, roles);
            logger.info("Google login successful for user: {}", email);
            
            // Send WebSocket notification for successful Google login
            try {
                notificationService.notifyUser(
                    user.getId().toString(),
                    "Welcome Back!",
                    "You have successfully logged in with Google.",
                    "AUTH"
                );
            } catch (Exception e) {
                logger.warn("Failed to send Google login notification: {}", e.getMessage());
            }
            
            return ResponseEntity.ok(ApiResponse.ofKey("api.auth.google.success", jwtResponse));
    }

    @Override
    @Transactional
    public ResponseEntity<ApiResponse> signup(SignupRequest signupRequest) {
        try {
            // Validate that password and confirmPassword are provided and match
            if (signupRequest.getPassword() == null || signupRequest.getPassword().isBlank()) {
                logger.warn("Signup failed: Password is required for email: {}", signupRequest.getEmail());
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.password.required", null));
            }
            if (signupRequest.getConfirmPassword() == null || signupRequest.getConfirmPassword().isBlank()) {
                logger.warn("Signup failed: Confirm password is required for email: {}", signupRequest.getEmail());
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.confirmPassword.required", null));
            }
            if (!signupRequest.getPassword().equals(signupRequest.getConfirmPassword())) {
                logger.warn("Signup failed: Passwords do not match for email: {}", signupRequest.getEmail());
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.passwords.mismatch", null));
            }

            // Check if email already exists
            if (userRepository.existsByEmail(signupRequest.getEmail())) {
                logger.warn("Signup failed: Email already exists: {}", signupRequest.getEmail());
                return ResponseEntity.status(CONFLICT)
                        .body(ApiResponse.ofKey("api.auth.email.exists", null));
            }

            // Retrieve default user role
            Role userRole = roleRepository.findByName("ROLE_USER")
                    .orElseThrow(() -> {
                        logger.error("Default role ROLE_USER not found");
                        return new RuntimeException("Default role ROLE_USER not found.");
                    });

            // Create new user
            User newUser = new User();
            newUser.setFirstName(signupRequest.getFirstName());
            newUser.setLastName(signupRequest.getLastName());
            newUser.setEmail(signupRequest.getEmail());
            newUser.setPassword(signupRequest.getPassword());
            newUser.setConfirmPassword(signupRequest.getConfirmPassword());
            newUser.setEnabled(true);
            newUser.setRegistrationDate(LocalDateTime.now());
            newUser.setIsOnline(false);
            newUser.setRoles(new HashSet<>(Collections.singletonList(userRole)));

            User savedUser = registerUser(newUser);
            logger.info("User registered successfully: {}", signupRequest.getEmail());
            
            // Send WebSocket notification for successful signup
            try {
                notificationService.notifyUser(
                    savedUser.getId().toString(),
                    "Welcome to SkinMe!",
                    "Your account has been created successfully. Welcome aboard!",
                    "AUTH"
                );
            } catch (Exception e) {
                logger.warn("Failed to send signup notification: {}", e.getMessage());
            }
            
            return ResponseEntity.status(CREATED)
                    .body(ApiResponse.ofKey("api.auth.signup.success", savedUser));
        } catch (Exception e) {
            logger.error("Signup failed for email: {}. Error: {}", signupRequest.getEmail(), e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    @Override
    @Transactional
    public ResponseEntity<ApiResponse> logout() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.getPrincipal() instanceof ShopUserDetails) {
                ShopUserDetails userDetails = (ShopUserDetails) authentication.getPrincipal();
                recordLogout(userDetails.getId());
                SecurityContextHolder.clearContext();
                logger.info("User logged out: {}", userDetails.getEmail());
            } else {
                logger.info("User logged out (no user details available)");
            }
            return ResponseEntity.ok(ApiResponse.ofKey("api.auth.logout.success", null));
        } catch (Exception e) {
            logger.error("Logout failed: {}", e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    @Override
    @Transactional
    public ResponseEntity<ApiResponse> resetPassword(String email, String token, String newPassword, String confirmPassword) {
        try {
            if (token == null || token.isBlank()) {
                logger.warn("Password reset failed: Reset token is required for email: {}", email);
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.reset.token.required", null));
            }
            if (newPassword == null || newPassword.isBlank()) {
                logger.warn("Password reset failed: New password is required for email: {}", email);
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.newPassword.required", null));
            }
            if (confirmPassword == null || confirmPassword.isBlank()) {
                logger.warn("Password reset failed: Confirm password is required for email: {}", email);
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.confirmPassword.required", null));
            }
            if (!newPassword.equals(confirmPassword)) {
                logger.warn("Password reset failed: Passwords do not match for email: {}", email);
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.passwords.mismatch", null));
            }

            Optional<User> optionalUser = userRepository.findByEmail(email);
            if (optionalUser.isEmpty()) {
                logger.warn("Password reset failed: Invalid email: {}", email);
                return ResponseEntity.status(NOT_FOUND)
                        .body(ApiResponse.ofKey("api.auth.email.invalid", null));
            }

            User user = optionalUser.get();
            
            // Validate reset token
            if (user.getResetToken() == null || !user.getResetToken().equals(token)) {
                logger.warn("Password reset failed: Invalid reset token for email: {}", email);
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.reset.token.invalid", null));
            }
            
            // Check if token has expired (1 hour expiry)
            if (user.getResetTokenExpiry() == null || user.getResetTokenExpiry().isBefore(LocalDateTime.now())) {
                logger.warn("Password reset failed: Reset token expired for email: {}", email);
                // Clear expired token
                user.setResetToken(null);
                user.setResetTokenExpiry(null);
                userRepository.save(user);
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.reset.token.expired", null));
            }
            
            // Reset password and clear token
            user.setPassword(passwordEncoder.encode(newPassword));
            user.setResetToken(null);
            user.setResetTokenExpiry(null);
            userRepository.save(user);
            recordPasswordReset(user.getId(), email);
            logger.info("Password reset successful for email: {}", email);
            return ResponseEntity.ok(ApiResponse.ofKey("api.auth.password.reset.success", null));
        } catch (Exception e) {
            logger.error("Password reset failed for email: {}. Error: {}", email, e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }


    @Override
    @Transactional
    public ResponseEntity<ApiResponse> forgotPassword(String email) {
        try {
            Optional<User> optionalUser = userRepository.findByEmail(email);
            if (optionalUser.isEmpty()) {
                logger.warn("Forgot password failed: Invalid email: {}", email);
                return ResponseEntity.status(NOT_FOUND)
                        .body(ApiResponse.ofKey("api.auth.email.invalid", null));
            }
            User user = optionalUser.get();
            String resetToken = UUID.randomUUID().toString().replace("-", "");
            user.setResetToken(resetToken);
            user.setResetTokenExpiry(LocalDateTime.now().plusHours(1));
            userRepository.save(user);
            emailService.sendPasswordResetEmail(email, resetToken);
            logger.info("Password reset email sent for email: {}", email);
            return ResponseEntity.ok(ApiResponse.ofKey("api.auth.forgot.password.sent", null));
        } catch (Exception e) {
            logger.error("Forgot password failed for email: {}. Error: {}", email, e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    @Override
    @Transactional
    public ResponseEntity<ApiResponse> sendOtp(SendOtpRequest request) {
        try {
            String phone = normalizePhone(request.getPhone());
            if (phone.length() < 8) {
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.phone.invalid", null));
            }
            String code = generateOtpCode();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiresAt = now.plusMinutes(OTP_EXPIRY_MINUTES);

            phoneOtpRepository.deleteByPhone(phone);
            PhoneOtp otp = PhoneOtp.builder()
                    .phone(phone)
                    .code(code)
                    .expiresAt(expiresAt)
                    .createdAt(now)
                    .build();
            phoneOtpRepository.save(otp);

            boolean sent = smsService.sendOtp(phone, code);
            if (!sent) {
                logger.warn("SMS delivery failed for phone: {} - OTP was logged above for testing", phone);
                Object data = returnOtpWhenDeliveryFails ? Map.of("otp", code) : null;
                return ResponseEntity.ok(ApiResponse.ofKey("api.auth.otp.sent.check.logs", data));
            }
            logger.info("OTP sent to phone: {} (expires in {} min)", phone, OTP_EXPIRY_MINUTES);
            return ResponseEntity.ok(ApiResponse.ofKey("api.auth.otp.sent", null));
        } catch (Exception e) {
            logger.error("Send OTP failed: {}", e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    @Override
    @Transactional
    public ResponseEntity<ApiResponse> loginWithPhone(LoginWithPhoneRequest request, HttpServletRequest req, HttpServletResponse resp) {
        try {
            String phone = normalizePhone(request.getPhone());
            String code = request.getOtp().trim();

            Optional<PhoneOtp> otpOpt = phoneOtpRepository.findByPhone(phone);
            if (otpOpt.isEmpty()) {
                logger.warn("Login with phone failed: No OTP found for phone: {}", phone);
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.otp.invalid", null));
            }
            PhoneOtp otp = otpOpt.get();
            if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
                phoneOtpRepository.delete(otp);
                logger.warn("Login with phone failed: OTP expired for phone: {}", phone);
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.otp.expired", null));
            }
            if (!otp.getCode().equals(code)) {
                logger.warn("Login with phone failed: Invalid OTP for phone: {}", phone);
                return ResponseEntity.status(UNAUTHORIZED)
                        .body(ApiResponse.ofKey("api.auth.otp.invalid", null));
            }
            phoneOtpRepository.delete(otp);

            User user = userRepository.findByPhone(phone).orElseGet(() -> {
                Role userRole = roleRepository.findByName("ROLE_USER")
                        .orElseThrow(() -> new RuntimeException("Default role ROLE_USER not found."));
                String phoneDigits = phone.replaceAll("[^0-9]", "");
                String syntheticEmail = "phone_" + phoneDigits + "@skinme.phone";
                if (userRepository.existsByEmail(syntheticEmail)) {
                    throw new IllegalStateException("Phone user email collision: " + syntheticEmail);
                }
                String last5 = phoneDigits.length() >= 5
                        ? phoneDigits.substring(phoneDigits.length() - 5)
                        : phoneDigits;
                String generatedName = "user" + last5;
                User newUser = new User();
                newUser.setPhone(phone);
                newUser.setEmail(syntheticEmail);
                newUser.setFirstName(generatedName);
                newUser.setLastName("");
                newUser.setPassword(passwordEncoder.encode(UUID.randomUUID().toString()));
                newUser.setEnabled(true);
                newUser.setRegistrationDate(LocalDateTime.now());
                newUser.setIsOnline(false);
                newUser.setRoles(new HashSet<>(Collections.singletonList(userRole)));
                logger.info("Creating new user for phone login: {} (firstName: {})", phone, generatedName);
                return registerUser(newUser);
            });

            if (!user.isEnabled()) {
                return ResponseEntity.status(UNAUTHORIZED)
                        .body(ApiResponse.ofKey("api.auth.account.disabled", null));
            }

            ShopUserDetails userDetails = ShopUserDetails.buildUserDetails(user);
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtils.generateTokenForUser(authentication);

            String ipAddress = getClientIpAddress(req);
            recordLogin(user.getId(), ipAddress);

            Set<String> roles = userDetails.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .collect(Collectors.toSet());

            Cookie cookie = new Cookie("token", jwt);
            cookie.setHttpOnly(true);
            cookie.setSecure(true);
            cookie.setPath("/");
            cookie.setMaxAge(7 * 24 * 60 * 60);
            cookie.setAttribute("SameSite", "Strict");
            resp.addCookie(cookie);

            JwtResponse jwtResponse = new JwtResponse(userDetails.getId(), jwt, roles);
            logger.info("User logged in with phone: {}", phone);

            try {
                notificationService.notifyUser(
                        user.getId().toString(),
                        "Welcome Back!",
                        "You have successfully logged in with your phone.",
                        "AUTH"
                );
            } catch (Exception e) {
                logger.warn("Failed to send phone login notification: {}", e.getMessage());
            }

            return ResponseEntity.ok(ApiResponse.ofKey("api.auth.login.success", jwtResponse));
        } catch (IllegalStateException e) {
            logger.error("Phone login error: {}", e.getMessage());
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        } catch (Exception e) {
            logger.error("Login with phone failed: {}", e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    @Override
    @Transactional
    public ResponseEntity<ApiResponse> verifyPhone(VerifyPhoneRequest request) {
        try {
            String phone = normalizePhone(request.getPhone());
            String code = request.getOtp().trim();

            if (phone.length() < 8) {
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.phone.invalid", null));
            }

            Optional<PhoneOtp> otpOpt = phoneOtpRepository.findByPhone(phone);
            if (otpOpt.isEmpty()) {
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.otp.invalid", null));
            }
            PhoneOtp otp = otpOpt.get();
            if (otp.getExpiresAt().isBefore(LocalDateTime.now())) {
                phoneOtpRepository.delete(otp);
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.otp.expired", null));
            }
            if (!otp.getCode().equals(code)) {
                return ResponseEntity.status(UNAUTHORIZED)
                        .body(ApiResponse.ofKey("api.auth.otp.invalid", null));
            }
            phoneOtpRepository.delete(otp);

            phoneVerificationRepository.deleteByPhone(phone);
            String token = UUID.randomUUID().toString();
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime expiresAt = now.plusMinutes(PHONE_VERIFICATION_EXPIRY_MINUTES);
            PhoneVerification pv = PhoneVerification.builder()
                    .phone(phone)
                    .token(token)
                    .expiresAt(expiresAt)
                    .createdAt(now)
                    .build();
            phoneVerificationRepository.save(pv);

            Map<String, String> data = Map.of("phoneVerificationToken", token);
            return ResponseEntity.ok(ApiResponse.ofKey("api.auth.phone.verified", data));
        } catch (Exception e) {
            logger.error("Verify phone failed: {}", e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    @Override
    @Transactional
    public ResponseEntity<ApiResponse> registerWithPhone(RegisterWithPhoneRequest request, HttpServletRequest req, HttpServletResponse resp) {
        try {
            String phone = normalizePhone(request.getPhone());
            if (phone.length() < 8) {
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.phone.invalid", null));
            }
            if (request.getPassword() == null || request.getPassword().isBlank()) {
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.password.required", null));
            }
            if (request.getConfirmPassword() == null || !request.getPassword().equals(request.getConfirmPassword())) {
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.passwords.mismatch", null));
            }

            Optional<PhoneVerification> pvOpt = phoneVerificationRepository.findByToken(request.getPhoneVerificationToken());
            if (pvOpt.isEmpty()) {
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.phone.verification.token.invalid", null));
            }
            PhoneVerification pv = pvOpt.get();
            if (pv.getExpiresAt().isBefore(LocalDateTime.now())) {
                phoneVerificationRepository.delete(pv);
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.phone.verification.expired", null));
            }
            if (!normalizePhone(pv.getPhone()).equals(phone)) {
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.phone.mismatch", null));
            }

            if (userRepository.existsByPhone(phone)) {
                return ResponseEntity.status(CONFLICT)
                        .body(ApiResponse.ofKey("api.auth.phone.already.registered", null));
            }
            if (userRepository.existsByEmail(request.getEmail())) {
                return ResponseEntity.status(CONFLICT)
                        .body(ApiResponse.ofKey("api.auth.email.exists", null));
            }

            Role userRole = roleRepository.findByName("ROLE_USER")
                    .orElseThrow(() -> new RuntimeException("Default role ROLE_USER not found."));

            User newUser = new User();
            newUser.setPhone(phone);
            newUser.setFirstName(request.getFirstName() != null ? request.getFirstName().trim() : "");
            newUser.setLastName(request.getLastName() != null ? request.getLastName().trim() : "");
            newUser.setEmail(request.getEmail().trim());
            newUser.setPassword(request.getPassword());
            newUser.setConfirmPassword(request.getConfirmPassword());
            newUser.setEnabled(true);
            newUser.setRegistrationDate(LocalDateTime.now());
            newUser.setIsOnline(false);
            newUser.setRoles(new HashSet<>(Collections.singletonList(userRole)));

            User savedUser = registerUser(newUser);
            phoneVerificationRepository.delete(pv);

            ShopUserDetails userDetails = ShopUserDetails.buildUserDetails(savedUser);
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            String jwt = jwtUtils.generateTokenForUser(authentication);

            String ipAddress = getClientIpAddress(req);
            recordLogin(savedUser.getId(), ipAddress);

            Set<String> roles = userDetails.getAuthorities().stream()
                    .map(a -> a.getAuthority())
                    .collect(Collectors.toSet());

            Cookie cookie = new Cookie("token", jwt);
            cookie.setHttpOnly(true);
            cookie.setSecure(true);
            cookie.setPath("/");
            cookie.setMaxAge(7 * 24 * 60 * 60);
            cookie.setAttribute("SameSite", "Strict");
            resp.addCookie(cookie);

            JwtResponse jwtResponse = new JwtResponse(userDetails.getId(), jwt, roles);
            logger.info("User registered with phone and logged in: {}", phone);

            try {
                notificationService.notifyUser(
                        savedUser.getId().toString(),
                        "Welcome to SkinMe!",
                        "Your account has been created successfully.",
                        "AUTH"
                );
            } catch (Exception e) {
                logger.warn("Failed to send signup notification: {}", e.getMessage());
            }

            return ResponseEntity.status(CREATED)
                    .body(ApiResponse.ofKey("api.auth.register.with.phone.success", jwtResponse));
        } catch (IllegalArgumentException e) {
            logger.warn("Register with phone validation: {}", e.getMessage());
            return ResponseEntity.status(CONFLICT)
                    .body(ApiResponse.ofKey("api.auth.email.exists", null));
        } catch (Exception e) {
            logger.error("Register with phone failed: {}", e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
    }

    private static String normalizePhone(String phone) {
        if (phone == null) return "";
        return phone.replaceAll("[\\s-]", "").trim();
    }

    private static String generateOtpCode() {
        int n = (int) Math.pow(10, OTP_LENGTH - 1);
        return String.valueOf(n + RANDOM.nextInt(9 * n));
    }

    @Transactional
    public User registerUser(User user) {
        logger.debug("Registering user with email: {}", user.getEmail());
        if (userRepository.existsByEmail(user.getEmail())) {
            logger.warn("Email already exists: {}", user.getEmail());
            throw new IllegalArgumentException("Email already exists: " + user.getEmail());
        }

        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }

        if (user.getRegistrationDate() == null) {
            user.setRegistrationDate(LocalDateTime.now());
        }
        user.setIsOnline(false);
        logger.debug("Setting isOnline to false for user: {}", user.getEmail());
        User savedUser = userRepository.save(user);

        Activity activity = new Activity();
        activity.setUser(savedUser);
        activity.setActivityType(ActivityType.REGISTER);
        activity.setTimestamp(LocalDateTime.now());
        activity.setDetails("User registered with email: " + user.getEmail());
        activityRepository.save(activity);

        logger.info("User registered successfully: {}", user.getEmail());
        return savedUser;
    }

    @Transactional
    public void recordLogin(Long userId) {
        recordLogin(userId, null);
    }

    @Transactional
    public void recordLogin(Long userId, String ipAddress) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            LocalDateTime now = LocalDateTime.now();
            user.setLastLogin(now);
            user.setLastActivity(now);
            if (ipAddress != null && !ipAddress.isEmpty()) {
                user.setLastIpAddress(ipAddress);
            }
            logger.debug("Before setting isOnline to true for user ID: {}, current isOnline: {}", userId,
                    user.isOnline());
            user.setIsOnline(true);
            logger.debug("After setting isOnline to true for user ID: {}", userId);
            userRepository.save(user);
            logger.debug("User saved with isOnline: {} for user ID: {}", user.isOnline(), userId);

            Activity activity = new Activity();
            activity.setUser(user);
            activity.setActivityType(ActivityType.LOGIN);
            activity.setTimestamp(now);
            activity.setDetails("User logged in from IP: " + (ipAddress != null ? ipAddress : "unknown"));
            activityRepository.save(activity);

            logger.info("Login recorded for user ID: {} from IP: {}", userId, ipAddress);
        } else {
            logger.error("User not found with ID: {}", userId);
            throw new RuntimeException("User not found with ID: " + userId);
        }
    }

    @Transactional
    public void recordLogout(Long userId) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            logger.debug("Before setting isOnline to false for user ID: {}, current isOnline: {}", userId,
                    user.isOnline());
            user.setIsOnline(false);
            user.setLastIpAddress(null); // Clear IP on logout
            logger.debug("After setting isOnline to false for user ID: {}", userId);
            userRepository.save(user);
            logger.debug("User saved with isOnline: {} for user ID: {}", user.isOnline(), userId);

            Activity activity = new Activity();
            activity.setUser(user);
            activity.setActivityType(ActivityType.LOGOUT);
            activity.setTimestamp(LocalDateTime.now());
            activity.setDetails("User logged out");
            activityRepository.save(activity);

            logger.info("Logout recorded for user ID: {}", userId);
        } else {
            logger.error("User not found with ID: {}", userId);
            throw new RuntimeException("User not found with ID: " + userId);
        }
    }

    /**
     * Get client IP address from HttpServletRequest
     */
    public String getClientIpAddress(HttpServletRequest request) {
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

    @Transactional
    public void recordPasswordReset(Long userId, String email) {
        Optional<User> userOptional = userRepository.findById(userId);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            Activity activity = new Activity();
            activity.setUser(user);
            activity.setActivityType(ActivityType.PASSWORD_RESET);
            activity.setTimestamp(LocalDateTime.now());
            activity.setDetails("Password reset for email: " + email);
            activityRepository.save(activity);

            logger.info("Password reset recorded for user ID: {}", userId);
        } else {
            logger.error("User not found with ID: {}", userId);
            throw new RuntimeException("User not found with ID: " + userId);
        }
    }
}