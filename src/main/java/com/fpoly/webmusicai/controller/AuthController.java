package com.fpoly.webmusicai.controller;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
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
	public ResponseEntity<?> login(@RequestBody Map<String, String> loginData) {
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

			boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
			response.put("isAdmin", isAdmin);

			return ResponseEntity.ok(response);

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
}