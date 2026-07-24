package com.fpoly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.fpoly.webmusicai.config.JwtService;
import com.fpoly.webmusicai.entity.User;

class WebmusicaiApplicationTests {

	@Test
	void jwtContainsUsernameAndTokenVersion() {
		JwtService jwtService = new JwtService(
				"01234567890123456789012345678901",
				60_000);
		User user = new User();
		user.setUsername("demo_user");
		user.setTokenVersion(3);

		String token = jwtService.generateToken(user);

		assertTrue(jwtService.isTokenValid(token));
		assertEquals("demo_user", jwtService.extractUsername(token));
		assertEquals(3, jwtService.extractTokenVersion(token));
	}

	@Test
	void jwtSignedByAnotherKeyIsRejected() {
		User user = new User();
		user.setUsername("demo_user");
		user.setTokenVersion(0);
		JwtService issuer = new JwtService("01234567890123456789012345678901", 60_000);
		JwtService verifier = new JwtService("abcdefghijklmnopqrstuvwxyzABCDEF", 60_000);

		assertFalse(verifier.isTokenValid(issuer.generateToken(user)));
	}

}
