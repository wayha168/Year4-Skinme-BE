package com.project.skin_me.config;

import com.project.skin_me.security.jwt.AuthTokenFilter;
import com.project.skin_me.security.jwt.JwtAuthEntryPoint;
import com.project.skin_me.security.user.ShopUserDetailsService;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.savedrequest.HttpSessionRequestCache;
import org.springframework.security.web.savedrequest.RequestCache;
import org.springframework.security.web.savedrequest.SavedRequest;
import com.project.skin_me.filter.UserActivityFilter;
import com.project.skin_me.repository.UserRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

        private final ShopUserDetailsService userDetailsService;
        private final JwtAuthEntryPoint jwtAuthEntryPoint;
        private final AuthTokenFilter jwtFilter;
        private final UserRepository userRepository;

        private static final String[] PUBLIC_API = {
                        "/api/v1/users/**", "/api/v1/products/**", "/api/v1/categories/**",
                        "/api/v1/images/**", "/api/v1/payment/webhook", "/api/v1/auth/**",
                        "/api/v1/popular/**", "/api/v1/chat/**", "/v3/api-docs/**",
                        "/swagger-ui/**", "/swagger-ui.html", "/swagger-resources/**",
                        "/webjars/**", "/login-page", "/signup", "/reset-password", "/logout",
                        "/css/**", "/js/**", "/ws-endpoint/**", "/sockjs-node/**", "/",
                        "/.well-known/**"
        };

        private static final String[] SECURED_API = {
                        "/api/v1/carts/**", "/api/v1/favorites/**", "/api/v1/cartItems/**",
                        "/api/v1/payment/**", "/api/v1/orders/**", "/api/v1/popular/user/**"
        };

        private static final String[] ADMIN_URLS = {
                        "/api/v1/admin/**"
        };

        @Bean
        public RequestCache requestCache() {
                HttpSessionRequestCache requestCache = new HttpSessionRequestCache();
                requestCache.setRequestMatcher(request -> {
                        String path = request.getRequestURI();
                        // Don't save requests for system URLs, Chrome DevTools, favicons, etc.
                        return !path.contains("/.well-known") &&
                               !path.contains("devtools") &&
                               !path.contains("favicon") &&
                               !path.contains(".ico") &&
                               !path.startsWith("/api/") &&
                               !path.startsWith("/v3/api-docs") &&
                               !path.startsWith("/swagger") &&
                               !path.startsWith("/webjars") &&
                               !path.startsWith("/css") &&
                               !path.startsWith("/js") &&
                               !path.startsWith("/images");
                });
                return requestCache;
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http, RequestCache requestCache) throws Exception {
                http
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .exceptionHandling(e -> e.authenticationEntryPoint(authenticationEntryPoint()))
                                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                                .requestCache(cache -> cache.requestCache(requestCache))
                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers("/css/**", "/js/**", "/images/**", "/webjars/**", "/favicon.ico").permitAll()
                                                .requestMatchers(PUBLIC_API).permitAll()
                                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                                .requestMatchers(ADMIN_URLS).hasRole("ADMIN")
                                                .requestMatchers(SECURED_API).authenticated()
                                                .requestMatchers("/dashboard", "/views/**").authenticated()
                                                .anyRequest().authenticated())
                                .formLogin(form -> form
                                                .loginPage("/login-page")
                                                .loginProcessingUrl("/login")
                                                .defaultSuccessUrl("/dashboard", true)
                                                .successHandler((request, response, authentication) -> {
                                                        // Check if there's a saved request (original URL before login)
                                                        SavedRequest savedRequest = requestCache.getRequest(request, response);
                                                        
                                                        if (savedRequest != null) {
                                                                // Redirect to the originally requested page
                                                                String redirectUrl = savedRequest.getRedirectUrl();
                                                                if (redirectUrl != null && isValidRedirectUrl(redirectUrl)) {
                                                                        response.sendRedirect(redirectUrl);
                                                                        return;
                                                                }
                                                        }
                                                        
                                                        // Default redirect to dashboard/homepage
                                                        response.sendRedirect("/dashboard");
                                                })
                                                .failureUrl("/login-page?error=true")
                                                .permitAll())
                                .logout(logout -> logout
                                                .logoutUrl("/logout")
                                                .logoutSuccessHandler(logoutSuccessHandler())
                                                .addLogoutHandler(customLogoutHandler())
                                                .deleteCookies("JSESSIONID", "token", "XSRF-TOKEN", "_csrf")
                                                .invalidateHttpSession(true)
                                                .clearAuthentication(true)
                                                .permitAll())
                                .csrf(csrf -> csrf.ignoringRequestMatchers(
                                                "/api/**",
                                                "/v3/api-docs/**",
                                                "/swagger-ui/**",
                                                "/ws-endpoint/**"))
                                .authenticationProvider(daoAuthProvider());

                http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
                
                // Add user activity filter after authentication
                http.addFilterAfter(new UserActivityFilter(userRepository), 
                        UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        @Bean
        public DaoAuthenticationProvider daoAuthProvider() {
                DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
                provider.setUserDetailsService(userDetailsService);
                provider.setPasswordEncoder(passwordEncoder());
                return provider;
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }

        @Bean
        public AuthenticationManager authManager(AuthenticationConfiguration config) throws Exception {
                return config.getAuthenticationManager();
        }

        @Bean
        public AuthenticationEntryPoint authenticationEntryPoint() {
                return (request, response, authException) -> {
                        String requestUri = request.getRequestURI();
                        
                        // For API requests, return JSON error
                        if (requestUri.startsWith("/api/")) {
                                jwtAuthEntryPoint.commence(request, response, authException);
                        } else {
                                // For web requests, always redirect to login page
                                // Spring Security will handle saving the original request
                                new LoginUrlAuthenticationEntryPoint("/login-page")
                                                .commence(request, response, authException);
                        }
                };
        }

        @Bean
        public ModelMapper modelMapper() {
                return new ModelMapper();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration config = new CorsConfiguration();
                config.setAllowedOrigins(List.of(
                                "http://localhost:5173",
                                "http://localhost:8800",
                                "https://skinme.store",
                                "https://www.skinme.store",
                                "https://backend.skinme.store"));
                config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
                config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
                config.setExposedHeaders(List.of("Authorization"));
                config.setAllowCredentials(true);
                config.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", config);
                return source;
        }

        @Bean
        public OpenAPI apiDocs() {
                return new OpenAPI()
                                .info(new Info()
                                                .title("Skin Me API")
                                                .version("1.0")
                                                .description("API documentation for Skin Me project"))
                                .addServersItem(new io.swagger.v3.oas.models.servers.Server()
                                                .url("https://backend.skinme.store")
                                                .description("Production server"))
                                .addServersItem(new io.swagger.v3.oas.models.servers.Server()
                                                .url("http://localhost:8800")
                                                .description("Local server"))
                                .components(new Components()
                                                .addSecuritySchemes("bearerAuth",
                                                                new SecurityScheme()
                                                                                .type(SecurityScheme.Type.HTTP)
                                                                                .scheme("bearer")
                                                                                .bearerFormat("JWT")))
                                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
        }

        @Bean
        public LogoutHandler customLogoutHandler() {
            return (HttpServletRequest request, HttpServletResponse response, 
                    org.springframework.security.core.Authentication authentication) -> {
                // Clear all cookies
                Cookie[] cookies = request.getCookies();
                if (cookies != null) {
                    for (Cookie cookie : cookies) {
                        cookie.setValue("");
                        cookie.setPath("/");
                        cookie.setMaxAge(0);
                        cookie.setHttpOnly(true);
                        cookie.setSecure(true);
                        response.addCookie(cookie);
                    }
                }
                
                // Clear session
                if (request.getSession(false) != null) {
                    request.getSession().invalidate();
                }
                
                // Clear Security Context
                org.springframework.security.core.context.SecurityContextHolder.clearContext();
            };
        }

        @Bean
        public LogoutSuccessHandler logoutSuccessHandler() {
            return (HttpServletRequest request, HttpServletResponse response, 
                    org.springframework.security.core.Authentication authentication) -> {
                // Clear all cookies again to be sure
                Cookie[] cookies = request.getCookies();
                if (cookies != null) {
                    for (Cookie cookie : cookies) {
                        Cookie deleteCookie = new Cookie(cookie.getName(), null);
                        deleteCookie.setPath("/");
                        deleteCookie.setMaxAge(0);
                        deleteCookie.setHttpOnly(true);
                        deleteCookie.setSecure(true);
                        response.addCookie(deleteCookie);
                    }
                }
                
                // Clear any remaining session data
                if (request.getSession(false) != null) {
                    request.getSession().invalidate();
                }
                
                // Clear Security Context
                org.springframework.security.core.context.SecurityContextHolder.clearContext();
                
                // Always redirect to login page after logout
                response.sendRedirect("/login-page?logout=true");
            };
        }

        /**
         * Check if a redirect URL is valid and safe to redirect to
         */
        private boolean isValidRedirectUrl(String url) {
                if (url == null || url.isEmpty()) {
                        return false;
                }
                
                // Must be a relative URL starting with /
                if (!url.startsWith("/")) {
                        return false;
                }
                
                // Exclude system URLs, Chrome DevTools, favicons, API endpoints, etc.
                String lowerUrl = url.toLowerCase();
                return !lowerUrl.contains("/login-page") &&
                       !lowerUrl.contains("/.well-known") &&
                       !lowerUrl.contains("devtools") &&
                       !lowerUrl.contains("favicon") &&
                       !lowerUrl.contains(".ico") &&
                       !lowerUrl.startsWith("/api/") &&
                       !lowerUrl.startsWith("/v3/api-docs") &&
                       !lowerUrl.startsWith("/swagger") &&
                       !lowerUrl.startsWith("/webjars") &&
                       !lowerUrl.startsWith("/css") &&
                       !lowerUrl.startsWith("/js") &&
                       !lowerUrl.startsWith("/images");
        }
}
