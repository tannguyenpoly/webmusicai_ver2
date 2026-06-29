package com.fpoly.webmusicai.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
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
				// Admin paths
				.requestMatchers("/admin/**", "/api/admin/**", "/api/reports/**").hasRole("ADMIN")
				.requestMatchers("/api/packages/**", "/api/genres/**").hasRole("ADMIN")
				
				// Public paths that anyone can access
				.requestMatchers(
						"/", "/login", "/register",
						"/js/**", "/css/**", "/images/**", "/favicon.ico",
						"/api/auth/**"
				).permitAll()
				
				// Public GET APIs
				.requestMatchers(
						"/api/songs/public", "/api/songs/{id}/status", "/api/songs/{id}/likes", "/api/songs/{id}/comments",
						"/api/packages", "/api/packages/{id}",
						"/api/genres", "/api/genres/{id}",
						"/api/albums/**", // Cho phép GET tất cả các đường dẫn con của albums
						"/api/playlists/public", "/api/playlists/{id}", "/api/playlists/user/{username}"
				).permitAll()

				// Cho phép các trang cá nhân khi đã đăng nhập
				.requestMatchers("/favorites", "/profile").authenticated()
				
				// All other requests must be authenticated
				.anyRequest().authenticated())
			.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
		return http.build();
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
