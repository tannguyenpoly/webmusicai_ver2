package com.fpoly.webmusicai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableWebSecurity
@EnableAsync
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable());
        http.cors(cors -> cors.disable());

        // CẤU HÌNH QUAN TRỌNG (DÀNH CHO SPRING SECURITY 6):
        // Yêu cầu hệ thống TỰ ĐỘNG lưu trữ Security Context vào Session thay vì bắt buộc lưu thủ công.
        http.securityContext(context -> context.requireExplicitSave(false));

        http.authorizeHttpRequests(auth -> auth
                // 1. MỞ KHÓA VIEW GIAO DIỆN (Của bạn Thiện): Cho phép vào xem các trang web và file tĩnh công khai
                .requestMatchers("/", "/index.html", "/login", "/register", "/js/**", "/css/**", "/images/**", "/favicon.ico").permitAll()

                // 2. MỞ KHÓA API BACKEND (Giữ nguyên gốc cấu hình ban đầu của nhóm để không lỗi AuthController)
                .requestMatchers("/api/auth/**",
                        "/api/songs/public"
                ).permitAll()
                .requestMatchers("/api/songs/*/status").permitAll()

                // 3. PHÂN QUYỀN TRANG ADMIN
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // Các liên kết khác bắt buộc đăng nhập
                .anyRequest().authenticated()
        );

        return http.build();
    }

    // Bean cốt lõi cung cấp quyền cho AuthController xử lý token - KHÔNG ĐƯỢC XÓA DÒNG NÀY
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }
}