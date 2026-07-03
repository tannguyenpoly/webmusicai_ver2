package com.fpoly.webmusicai.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import org.springframework.transaction.annotation.Transactional;
import com.fpoly.webmusicai.entity.ChangePasswordRequest;
import com.fpoly.webmusicai.entity.Song;
import com.fpoly.webmusicai.entity.Transaction;
import com.fpoly.webmusicai.entity.UpdateProfileRequest;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.SongRepository;
import com.fpoly.webmusicai.repository.TransactionRepository;
import com.fpoly.webmusicai.repository.UserRepository;

import jakarta.validation.Valid;

@CrossOrigin("*")
@RestController
@RequestMapping("/api/users")
public class UserRestController {

	@Autowired
	UserRepository userRepo;

	@Autowired
	SongRepository songRepo;

	@Autowired
	TransactionRepository transRepo;

	@Autowired
	PasswordEncoder passwordEncoder;

	@GetMapping("/auth-session")
	public ResponseEntity<?> getAuthSession() {
		String username = SecurityContextHolder.getContext().getAuthentication().getName();
		if (username == null || "anonymousUser".equalsIgnoreCase(username)) {
			return ResponseEntity.status(401).body(Map.of("message", "Chưa đăng nhập hệ thống"));
		}

		Optional<User> userOpt = userRepo.findById(username);
		if (userOpt.isPresent()) {
			User user = userOpt.get();
			Map<String, Object> sessionData = new HashMap<>();
			sessionData.put("username", user.getUsername());
			sessionData.put("fullname", user.getFullname());
			sessionData.put("email", user.getEmail());
			sessionData.put("token_balance", user.getTokenBalance());
			return ResponseEntity.ok(sessionData);
		}
		return ResponseEntity.status(404).body(Map.of("message", "Tài khoản session không tồn tại trong DB"));
	}

	@GetMapping("/{username}/profile")
	public ResponseEntity<?> getProfile(@PathVariable String username) {
		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		if (!username.equals(currentUsername) && SecurityContextHolder.getContext().getAuthentication().getAuthorities()
				.stream().noneMatch(a -> a.getAuthority().contains("ADMIN"))) {
			return ResponseEntity.status(403).body(Map.of("message", "Không có quyền truy cập!"));
		}

		Optional<User> userOpt = userRepo.findById(username);
		if (!userOpt.isPresent()) {
			return ResponseEntity.badRequest().body(Map.of("message", "Không tìm thấy người dùng!"));
		}
		User user = userOpt.get();
		Map<String, Object> profile = new HashMap<>();
		profile.put("username", user.getUsername());
		profile.put("fullname", user.getFullname());
		profile.put("email", user.getEmail());
		profile.put("photo", user.getPhoto());
		profile.put("token_balance", user.getTokenBalance());
		return ResponseEntity.ok(profile);
	}

	@PutMapping("/{username}/profile")
	public ResponseEntity<?> updateProfile(@PathVariable String username,
			@Valid @RequestBody UpdateProfileRequest request, BindingResult bindingResult) {

		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		if (!username.equals(currentUsername)) {
			return ResponseEntity.status(403).body(Map.of("message", "Không có quyền truy cập!"));
		}

		if (bindingResult.hasErrors()) {
			Map<String, String> errors = new HashMap<>();
			for (FieldError error : bindingResult.getFieldErrors()) {
				errors.put(error.getField(), error.getDefaultMessage());
			}
			return ResponseEntity.badRequest().body(errors);
		}

		Optional<User> userOpt = userRepo.findById(username);
		if (!userOpt.isPresent()) {
			return ResponseEntity.badRequest().body(Map.of("message", "Không tìm thấy người dùng!"));
		}

		User user = userOpt.get();
		user.setFullname(request.getFullname());
		user.setEmail(request.getEmail());
		if (request.getPhoto() != null) {
			user.setPhoto(request.getPhoto());
		}
		userRepo.save(user);

		return ResponseEntity.ok(Map.of("message", "Cập nhật hồ sơ thành công!"));
	}

	@PutMapping("/{username}/change-password")
	public ResponseEntity<?> changePassword(@PathVariable String username,
			@Valid @RequestBody ChangePasswordRequest request, BindingResult bindingResult) {

		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		if (!username.equals(currentUsername)) {
			return ResponseEntity.status(403).body(Map.of("message", "Không có quyền truy cập!"));
		}

		if (bindingResult.hasErrors()) {
			return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng nhập đầy đủ thông tin!"));
		}

		Optional<User> userOpt = userRepo.findById(username);
		if (!userOpt.isPresent()) {
			return ResponseEntity.badRequest().body(Map.of("message", "Không tìm thấy người dùng!"));
		}

		User user = userOpt.get();
		String currentPassword = user.getPassword();

		if (!passwordEncoder.matches(request.getOldPassword(), currentPassword)) {
			return ResponseEntity.badRequest().body(Map.of("message", "Mật khẩu cũ không chính xác!"));
		}

		user.setPassword(passwordEncoder.encode(request.getNewPassword()));
		userRepo.save(user);

		return ResponseEntity.ok(Map.of("message", "Đổi mật khẩu thành công!"));
	}

	@GetMapping("/{username}/songs")
	public ResponseEntity<?> getMySongs(@PathVariable String username, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {

		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		if (!username.equals(currentUsername)) {
			return ResponseEntity.status(403).body(Map.of("message", "Không có quyền truy cập!"));
		}

		Pageable pageable = PageRequest.of(page, size);
		Page<Song> songPage = songRepo.findByUserUsernameOrderByCreatedAtDesc(username, pageable);
		return ResponseEntity.ok(songPage);
	}

	@GetMapping("/{username}/transactions")
	public ResponseEntity<?> getMyTransactions(@PathVariable String username,
			@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {

		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		if (!username.equals(currentUsername)) {
			return ResponseEntity.status(403).body(Map.of("message", "Không có quyền truy cập!"));
		}

		Pageable pageable = PageRequest.of(page, size);
		Page<Transaction> transPage = transRepo.findByUserUsernameOrderByCreatedAtDesc(username, pageable);
		return ResponseEntity.ok(transPage);
	}

	@Transactional(rollbackFor = Exception.class)
	@PostMapping("/{username}/deposit")
	public ResponseEntity<?> depositToken(@PathVariable String username, @RequestBody Map<String, Integer> request) {

		if (SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
				.noneMatch(a -> a.getAuthority().contains("ADMIN"))) {
			return ResponseEntity.status(403).body(Map.of("message", "Không có quyền thực hiện!"));
		}

		Optional<User> userOpt = userRepo.findById(username);
		if (!userOpt.isPresent()) {
			return ResponseEntity.badRequest().body(Map.of("message", "Không tìm thấy người dùng!"));
		}

		Integer amount = request.getOrDefault("amount", 0);
		if (amount <= 0) {
			return ResponseEntity.badRequest().body(Map.of("message", "Số token phải lớn hơn 0!"));
		}

		User user = userOpt.get();
		user.setTokenBalance(user.getTokenBalance() + amount);
		userRepo.save(user);

		Transaction trans = new Transaction();
		trans.setUser(user);
		trans.setAmount(amount);
		trans.setDescription("Nạp " + amount + " token vào tài khoản");
		transRepo.save(trans);

		return ResponseEntity
				.ok(Map.of("message", "Nạp thành công " + amount + " token!", "token_balance", user.getTokenBalance()));
	}

	@GetMapping("/me")
	public ResponseEntity<?> getMyProfile(Authentication authentication) {
		String username = authentication.getName();

		User user = userRepo.findById(username).orElseThrow(() -> new RuntimeException("User không tồn tại"));

		List<Song> songs = songRepo.findByUserUsernameOrderByCreatedAtDesc(username);

		// Tóm tắt thống kê
		long completed = songs.stream().filter(s -> "COMPLETED".equals(s.getStatus())).count();
		long pending = songs.stream().filter(s -> "PENDING".equals(s.getStatus())).count();
		long failed = songs.stream().filter(s -> "FAILED".equals(s.getStatus())).count();

		Map<String, Object> response = new HashMap<>();
		response.put("username", user.getUsername());
		response.put("fullname", user.getFullname());
		response.put("token_balance", user.getTokenBalance());
		response.put("stats",
				Map.of("total", songs.size(), "completed", completed, "pending", pending, "failed", failed));
		response.put("songs", songs);

		return ResponseEntity.ok(response);
	}
}