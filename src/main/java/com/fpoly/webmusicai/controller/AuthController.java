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
	RoleRepository roleRepo; // ← thêm dòng này

	@Autowired
	AuthorityRepository authorityRepo; // ← thêm dòng này

	@Autowired
	MailService mailService;

	@Autowired
	PasswordEncoder passwordEncoder;
	@PostMapping("/login")
	public ResponseEntity<?> login(@RequestBody Map<String, String> loginData) {
		try {
			String username = loginData.get("username");
			String password = loginData.get("password");

			Authentication auth = authManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
			SecurityContextHolder.getContext().setAuthentication(auth);

			User user = userRepo.findById(username).get();

			Map<String, Object> response = new HashMap<>();
			response.put("message", "Đăng nhập qua Spring Security chuẩn thành công!");
			response.put("username", user.getUsername());
			response.put("fullname", user.getFullname());
			response.put("token_balance", user.getTokenBalance());
			response.put("token", "fake-jwt-token-123456");

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
	    String email    = data.get("email");

	    // Kiểm tra username đã tồn tại chưa
	    if (userRepo.existsById(username)) {
	        return ResponseEntity.badRequest().body("Username đã tồn tại!");
	    }

	    // Tạo user mới
	    User user = new User();
	    user.setUsername(username);
	    user.setPassword(passwordEncoder.encode(password));
	    user.setFullname(fullname);
	    user.setEmail(email);
	    user.setTokenBalance(5); // tặng 5 token miễn phí
	    user.setEnabled(true);
	    userRepo.save(user);

	    // Gán role USER
	    Role role = roleRepo.findById("USER").orElseThrow();
	    Authority authority = new Authority();
	    authority.setUser(user);
	    authority.setRole(role);
	    authorityRepo.save(authority);

		// Gửi email chào mừng (bất đồng bộ, không ảnh hưởng response)
		mailService.sendWelcomeEmail(email, fullname, username);

	    return ResponseEntity.ok(Map.of(
	        "message", "Đăng ký thành công! Kiểm tra email của bạn.",
	        "username", username
	    ));
	}
}