package com.fpoly.webmusicai.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import com.fpoly.webmusicai.config.JwtService;
import com.fpoly.webmusicai.entity.Authority;
import com.fpoly.webmusicai.entity.Role;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.AuthorityRepository;
import com.fpoly.webmusicai.repository.RoleRepository;
import com.fpoly.webmusicai.repository.UserRepository;
import com.fpoly.webmusicai.service.MailService;

@CrossOrigin("*")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	@Autowired
	AuthenticationManager authManager;

	@Autowired
	UserRepository userRepo;

	@Autowired
	RoleRepository roleRepo;

	@Autowired
	AuthorityRepository authorityRepo;

	@Autowired
	MailService mailService;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	JwtService jwtService;

	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody Map<String, String> loginData, HttpServletResponse httpResponse) {
		String username = loginData.get("username");
		String password = loginData.get("password");

		if (username == null || username.trim().isEmpty()) {
			return ResponseEntity.badRequest().body("Tên đăng nhập không được để trống!");
		}
		if (password == null || password.trim().isEmpty()) {
			return ResponseEntity.badRequest().body("Mật khẩu không được để trống!");
		}

		try {
			Authentication auth = authManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
			SecurityContextHolder.getContext().setAuthentication(auth);

			User user = userRepo.findById(username).get();
			String token = jwtService.generateToken(username);

			Map<String, Object> response = new HashMap<>();
			response.put("message", "Đăng nhập thành công!");
			response.put("token", token);
			response.put("username", user.getUsername());
			response.put("fullname", user.getFullname());
			response.put("token_balance", user.getTokenBalance());

			boolean isAdmin = auth.getAuthorities().stream()
					.anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN") || a.getAuthority().equalsIgnoreCase("ADMIN"));
			response.put("isAdmin", isAdmin);

			Cookie cookie = new Cookie("jwt_token", token);
			cookie.setPath("/");
			cookie.setHttpOnly(false);
			cookie.setMaxAge(86400);
			httpResponse.addCookie(cookie);

			return ResponseEntity.ok(response);

		} catch (DisabledException e) {
			return ResponseEntity.status(403).body("Tài khoản đã bị khóa!");
		} catch (AuthenticationException e) {
			return ResponseEntity.status(401).body("Sai tài khoản hoặc mật khẩu");
		}
	}

	@PostMapping("/register")
	public ResponseEntity<?> register(@RequestBody Map<String, String> data) {
		String username = data.get("username");
		String password = data.get("password");
		String fullname = data.get("fullname");
		String email = data.get("email");

		if (username == null || username.trim().isEmpty()) {
			return ResponseEntity.badRequest().body("Tên đăng nhập không được để trống!");
		}
		if (username.contains(" ")) {
			return ResponseEntity.badRequest().body("Tên đăng nhập không được chứa khoảng trắng!");
		}
		if (fullname == null || fullname.trim().isEmpty()) {
			return ResponseEntity.badRequest().body("Họ tên không được để trống!");
		}
		if (email == null || email.trim().isEmpty()) {
			return ResponseEntity.badRequest().body("Email không được để trống!");
		}
		if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
			return ResponseEntity.badRequest().body("Email không đúng định dạng!");
		}
		if (password == null || password.trim().isEmpty()) {
			return ResponseEntity.badRequest().body("Mật khẩu không được để trống!");
		}
		if (password.length() < 6) {
			return ResponseEntity.badRequest().body("Mật khẩu phải có ít nhất 6 ký tự!");
		}

		if (userRepo.existsById(username)) {
			return ResponseEntity.badRequest().body("Username đã tồn tại!");
		}

		User user = new User();
		user.setUsername(username);
		user.setPassword(passwordEncoder.encode(password));
		user.setFullname(fullname);
		user.setEmail(email);
		try {
			user.setPhoto("https://ui-avatars.com/api/?name=" + URLEncoder.encode(fullname, StandardCharsets.UTF_8) + "&background=16a34a&color=fff&rounded=true");
		} catch (Exception e) {
			user.setPhoto(null);
		}
		user.setTokenBalance(5); 
		user.setEnabled(true);
		userRepo.save(user);

		Role role = roleRepo.findById("USER").orElseThrow();
		Authority authority = new Authority();
		authority.setUser(user);
		authority.setRole(role);
		authorityRepo.save(authority);

		mailService.sendWelcomeEmail(email, fullname, username);

		return ResponseEntity
				.ok(Map.of("message", "Đăng ký thành công! Kiểm tra email của bạn.", "username", username));
	}

	private static class OtpData {
		String code;
		long expiryTime;

		OtpData(String code, long expiryTime) {
			this.code = code;
			this.expiryTime = expiryTime;
		}
	}

	private final Map<String, OtpData> otpStorage = new ConcurrentHashMap<>();

	@PostMapping("/forgot-password")
	public ResponseEntity<?> forgotPassword(@RequestBody Map<String, String> data) {
		String email = data.get("email");
		if (email == null || email.trim().isEmpty()) {
			return ResponseEntity.badRequest().body("Email không được để trống!");
		}

		List<User> users = userRepo.findByEmail(email.trim());
		if (users.isEmpty()) {
			return ResponseEntity.badRequest().body("Email không tồn tại trong hệ thống!");
		}

		String otp = String.format("%06d", new Random().nextInt(900000) + 100000);
		long expiry = System.currentTimeMillis() + (5 * 60 * 1000);

		otpStorage.put(email.trim().toLowerCase(), new OtpData(otp, expiry));

		mailService.sendResetPasswordOtp(email.trim(), otp);

		return ResponseEntity.ok(Map.of("message", "Mã xác nhận (OTP) 6 chữ số đã được gửi tới email của bạn."));
	}

	@PostMapping("/reset-password")
	public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> data) {
		String email = data.get("email");
		String otp = data.get("otp");
		String newPassword = data.get("newPassword");

		if (email == null || email.trim().isEmpty()) {
			return ResponseEntity.badRequest().body("Email không được để trống!");
		}
		if (otp == null || otp.trim().isEmpty()) {
			return ResponseEntity.badRequest().body("Mã xác nhận không được để trống!");
		}
		if (newPassword == null || newPassword.trim().isEmpty() || newPassword.length() < 6) {
			return ResponseEntity.badRequest().body("Mật khẩu mới phải có ít nhất 6 ký tự!");
		}

		String key = email.trim().toLowerCase();
		OtpData storedOtp = otpStorage.get(key);

		if (storedOtp == null || !storedOtp.code.equals(otp.trim())) {
			return ResponseEntity.badRequest().body("Mã xác nhận (OTP) không chính xác!");
		}

		if (System.currentTimeMillis() > storedOtp.expiryTime) {
			otpStorage.remove(key);
			return ResponseEntity.badRequest().body("Mã xác nhận (OTP) đã hết hạn! Vui lòng yêu cầu mã mới.");
		}

		List<User> users = userRepo.findByEmail(email.trim());
		if (users.isEmpty()) {
			return ResponseEntity.badRequest().body("Không tìm thấy tài khoản người dùng!");
		}

		String encodedPassword = passwordEncoder.encode(newPassword);
		for (User user : users) {
			user.setPassword(encodedPassword);
			userRepo.save(user);
		}

		otpStorage.remove(key);

		return ResponseEntity.ok(Map.of("message", "Đặt lại mật khẩu thành công! Bạn có thể đăng nhập bằng mật khẩu mới."));
	}
}