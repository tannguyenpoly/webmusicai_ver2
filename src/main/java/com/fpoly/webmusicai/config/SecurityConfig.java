package com.fpoly.webmusicai.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletRequest;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Autowired
    private JwtFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .anyRequest().access(authorizationManager())
                )
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public AuthorizationManager<RequestAuthorizationContext> authorizationManager() {
        return (authentication, context) -> {
            HttpServletRequest request = context.getRequest();
            String path = request.getRequestURI();
            String method = request.getMethod();

            if (isPublicPath(path, method)) {
                return new AuthorizationDecision(true);
            }

            Authentication auth = authentication.get();

            if (isAdminPath(path, method)) {
                boolean isAdmin = auth != null && auth.isAuthenticated()
                        && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
                return new AuthorizationDecision(isAdmin);
            }

            boolean isRealUser = auth != null && auth.isAuthenticated()
                    && !auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ANONYMOUS"));
            return new AuthorizationDecision(isRealUser);
        };
    }

    private boolean isPublicPath(String path, String method) {
        if ("GET".equals(method) && path.equals("/api/orders/vnpay-callback")) {
            return true;
        }

        if (path.equals("/") || path.equals("/login") || path.equals("/register") || path.equals("/favorites") || path.equals("/profile") || path.equals("/orders")
                || path.startsWith("/js/") || path.startsWith("/css/") || path.startsWith("/images/") || path.startsWith("/song/")|| path.startsWith("/orders/")
                || path.equals("/favicon.ico")) {
            return true;
        }
        if (path.startsWith("/api/auth/")) {
            return true;
        }
        if ("GET".equals(method)) {
            if (path.equals("/api/songs/public")
                    || path.matches("/api/songs/\\d+/status")
                    || path.matches("/api/songs/\\d+/likes")
                    || path.matches("/api/songs/\\d+/comments")) {
                return true;
            }
            if (path.equals("/api/packages") || path.equals("/api/packages/")) {
                return true;
            }
            if (path.startsWith("/api/packages/") && path.split("/").length == 4) {
                return true;
            }
            if (path.equals("/api/genres") || path.equals("/api/genres/")) {
                return true;
            }
            if (path.startsWith("/api/genres/") && path.split("/").length == 4) {
                return true;
            }
            if (path.equals("/api/albums") || path.equals("/api/albums/")) {
                return true;
            }
            if (path.startsWith("/api/albums/")) {
                String[] parts = path.split("/");
                if (parts.length == 4) {
                    return true;
                }
                if (parts.length >= 5 && "user".equals(parts[3])) {
                    return true;
                }
            }
            if (path.equals("/api/playlists/public")) {
                return true;
            }
            if (path.startsWith("/api/playlists/")) {
                String[] parts = path.split("/");
                if (parts.length == 4 && !"my".equals(parts[3])) {
                    return true;
                }
                if (parts.length >= 5 && "user".equals(parts[3])) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isAdminPath(String path, String method) {
        if (path.equals("/admin") || path.startsWith("/api/reports/") || path.startsWith("/api/admin/")) {
            return true;
        }
        if (!"GET".equals(method)) {
            if (path.equals("/api/packages") || path.startsWith("/api/packages/")) {
                return true;
            }
            if (path.equals("/api/genres") || path.startsWith("/api/genres/")) {
                return true;
            }
        }
        return false;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}
