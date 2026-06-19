package com.fpoly.webmusicai.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
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

		http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

		http.authorizeHttpRequests(auth -> auth
				// Static resources + trang frontend
				.requestMatchers("/", "/index.html", "/login", "/register", "/js/**", "/css/**", "/images/**",
						"/favicon.ico")
				.permitAll()

				// Auth luôn public
				.requestMatchers("/api/auth/**").permitAll()

				// Chỉ GET mới public — xem nhạc, xem status, xem like/comment không cần login
				.requestMatchers(HttpMethod.GET, "/api/songs/public", "/api/songs/*/status", "/api/songs/*/likes",
						"/api/songs/*/comments", "/api/packages")
				.permitAll()

				// Mọi POST/PUT/DELETE vào like, comment, generate, remix... đều phải login
				.anyRequest().authenticated());

		http.addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	@Bean
	public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
		return config.getAuthenticationManager();
	}

	@Bean
	public PasswordEncoder passwordEncoder() {
		return org.springframework.security.crypto.factory.PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}
}