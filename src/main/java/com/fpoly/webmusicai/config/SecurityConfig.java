package com.fpoly.webmusicai.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;


@Configuration
@EnableWebSecurity
@EnableAsync
public class SecurityConfig {

	@Autowired
	private JwtFilter jwtFilter;

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable());
		http.cors(cors -> cors.disable());

		// 1. GIỮ NGUYÊN KIẾN TRÚC JWT CỦA HEAD
		http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

		http.authorizeHttpRequests(auth -> auth
				// 2. GỘP DANH SÁCH MỞ KHÓA CỦA HÒA (Frontend) VÀ HEAD (Backend API)
				.requestMatchers("/", "/index.html", "/login", "/register", "/js/**", "/css/**", "/images/**",
						"/favicon.ico")
				.permitAll().requestMatchers("/api/auth/**", "/api/songs/public", "/api/songs/*/status").permitAll()
				.anyRequest().authenticated());

		// 3. GIỮ NGUYÊN FILTER JWT
		http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}

	// 4. SỬ DỤNG ENCODER MỚI CỦA HÒA
	@Bean
	public PasswordEncoder passwordEncoder() {
		return org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}
}