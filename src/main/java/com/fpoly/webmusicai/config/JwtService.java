package com.fpoly.webmusicai.config;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.security.Key;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import com.fpoly.webmusicai.entity.User;

@Service
public class JwtService {

	private final Key key;
	private final long expiration;

	public JwtService(
			@Value("${jwt.secret:}") String configuredSecret,
			@Value("${jwt.expiration:86400000}") long expiration) {
		this.expiration = expiration;
		this.key = configuredSecret == null || configuredSecret.isBlank()
				? Keys.secretKeyFor(SignatureAlgorithm.HS256)
				: Keys.hmacShaKeyFor(configuredSecret.getBytes(StandardCharsets.UTF_8));
	}

	// Tạo token từ username
	public String generateToken(User user) {
		int tokenVersion = user.getTokenVersion() != null ? user.getTokenVersion() : 0;
		return Jwts.builder()
				.setClaims(Map.of("ver", tokenVersion))
				.setSubject(user.getUsername()).setIssuedAt(new Date())
				.setExpiration(new Date(System.currentTimeMillis() + expiration))
				.signWith(key, SignatureAlgorithm.HS256).compact();
	}

	// Lấy username từ token
	public String extractUsername(String token) {
		return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody().getSubject();
	}

	public int extractTokenVersion(String token) {
		Object value = Jwts.parserBuilder().setSigningKey(key).build()
				.parseClaimsJws(token).getBody().get("ver");
		return value instanceof Number number ? number.intValue() : -1;
	}

	// Kiểm tra token hợp lệ không
	public boolean isTokenValid(String token) {
		try {
			Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token);
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}
