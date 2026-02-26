package com.project.skin_me.service.auth;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.project.skin_me.enums.ActivityType;
import com.project.skin_me.model.Activity;
import com.project.skin_me.model.Role;
import com.project.skin_me.model.User;
import com.project.skin_me.repository.ActivityRepository;
import com.project.skin_me.repository.RoleRepository;
import com.project.skin_me.repository.UserRepository;
import com.project.skin_me.request.LoginRequest;
import com.project.skin_me.request.SignupRequest;
import com.project.skin_me.response.ApiResponse;
import com.project.skin_me.response.JwtResponse;
import com.project.skin_me.security.jwt.JwtUtils;
import com.project.skin_me.security.user.ShopUserDetails;
import com.project.skin_me.service.email.EmailService;
import com.project.skin_me.service.notification.NotificationService;
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

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashSet;
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

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String googleClientSecret;

    @Value("${app.oauth.redirect-uri:https://skinme.store/oauth-callback}")
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
    public ResponseEntity<ApiResponse> googleLogin(String code, HttpServletRequest request, HttpServletResponse response) {
        try {
            // Validate code parameter
            if (code == null || code.isBlank()) {
                logger.warn("Google login failed: Authorization code is missing");
                return ResponseEntity.status(BAD_REQUEST)
                        .body(ApiResponse.ofKey("api.auth.google.code.required", null));
            }

            logger.debug("Processing Google OAuth2 login with code: {}", code.substring(0, Math.min(10, code.length())) + "...");
            
            // Exchange authorization code for tokens
            GoogleTokenResponse tokenResponse;
            try {
                tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                        new NetHttpTransport(),
                        JacksonFactory.getDefaultInstance(),
                        "https://oauth2.googleapis.com/token",
                        googleClientId,
                        googleClientSecret,
                        code,
                        googleRedirectUri).execute();
            } catch (Exception e) {
                logger.error("Failed to exchange Google authorization code: {}", e.getMessage());
                return ResponseEntity.status(UNAUTHORIZED)
                        .body(ApiResponse.ofKey("api.auth.google.code.invalid", null));
            }

            // Parse ID token
            GoogleIdToken idToken;
            try {
                idToken = tokenResponse.parseIdToken();
            } catch (Exception e) {
                logger.error("Failed to parse Google ID token: {}", e.getMessage());
                return ResponseEntity.status(UNAUTHORIZED)
                        .body(ApiResponse.ofKey("api.auth.google.token.failed", null));
            }

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
            String newPassword = UUID.randomUUID().toString();
            user.setPassword(passwordEncoder.encode(newPassword));
            userRepository.save(user);
            recordPasswordReset(user.getId(), email);
            logger.info("Password reset successful for email: {}", email);
            return ResponseEntity.ok(ApiResponse.ofKey("api.auth.password.reset.success", null));
        } catch (Exception e) {
            logger.error("Forgot password failed for email: {}. Error: {}", email, e.getMessage(), e);
            return ResponseEntity.status(INTERNAL_SERVER_ERROR)
                    .body(ApiResponse.ofKey("api.error.generic", new Object[]{e.getMessage()}, null));
        }
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