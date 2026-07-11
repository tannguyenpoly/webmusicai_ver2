package com.fpoly.webmusicai.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.springframework.web.multipart.MultipartFile;
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
	com.fpoly.webmusicai.repository.FavoriteRepository favoriteRepo;

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
		boolean isOwnerOrAdmin = username.equals(currentUsername) || SecurityContextHolder.getContext().getAuthentication().getAuthorities()
				.stream().anyMatch(a -> a.getAuthority().contains("ADMIN"));

		Optional<User> userOpt = userRepo.findById(username);
		if (!userOpt.isPresent()) {
			return ResponseEntity.badRequest().body(Map.of("message", "Không tìm thấy người dùng!"));
		}
		User user = userOpt.get();
		Map<String, Object> profile = new HashMap<>();
		profile.put("username", user.getUsername());
		profile.put("fullname", user.getFullname());
		
		if (isOwnerOrAdmin) {
			profile.put("email", user.getEmail());
			profile.put("token_balance", user.getTokenBalance());
		}
		
		String photo = user.getPhoto();
		if (photo == null || photo.trim().isEmpty()) {
			String name = user.getFullname() != null ? user.getFullname() : user.getUsername();
			photo = "https://ui-avatars.com/api/?name=" + URLEncoder.encode(name, StandardCharsets.UTF_8) + "&background=16a34a&color=fff&rounded=true";
		}
		profile.put("photo", photo);

		long totalSongs = songRepo.countByUserUsername(username);
		long completedSongs = songRepo.countByUserUsernameAndStatus(username, "COMPLETED");
		long pendingSongs = songRepo.countByUserUsernameAndStatus(username, "PENDING");
		long totalFavorites = favoriteRepo.countByUserUsername(username);

		profile.put("total_songs", totalSongs);
		profile.put("completed_songs", completedSongs);
		profile.put("pending_songs", pendingSongs);
		profile.put("total_favorites", totalFavorites);

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

	@PostMapping("/{username}/avatar")
	public ResponseEntity<?> uploadAvatar(@PathVariable String username,
			@RequestParam("file") MultipartFile file) {
		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		if (!username.equals(currentUsername) && SecurityContextHolder.getContext().getAuthentication().getAuthorities()
				.stream().noneMatch(a -> a.getAuthority().contains("ADMIN"))) {
			return ResponseEntity.status(403).body(Map.of("message", "Không có quyền truy cập!"));
		}

		if (file == null || file.isEmpty()) {
			return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng chọn file ảnh hợp lệ!"));
		}

		String contentType = file.getContentType();
		if (contentType == null || !contentType.startsWith("image/")) {
			return ResponseEntity.badRequest().body(Map.of("message", "Chỉ chấp nhận file định dạng hình ảnh (.jpg, .png, .webp, .gif)!"));
		}

		try {
			String originalFilename = file.getOriginalFilename();
			String ext = ".png";
			if (originalFilename != null && originalFilename.contains(".")) {
				ext = originalFilename.substring(originalFilename.lastIndexOf("."));
			}

			String newFileName = "avatar-" + username + "-" + System.currentTimeMillis() + ext;

			Path uploadDir = Paths.get("src/main/resources/static/images/avatars");
			if (!Files.exists(uploadDir)) {
				Files.createDirectories(uploadDir);
			}
			Path filePath = uploadDir.resolve(newFileName);
			Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

			try {
				Path targetDir = Paths.get("target/classes/static/images/avatars");
				if (!Files.exists(targetDir)) {
					Files.createDirectories(targetDir);
				}
				Files.copy(filePath, targetDir.resolve(newFileName), StandardCopyOption.REPLACE_EXISTING);
			} catch (Exception ignored) {}

			String avatarUrl = "/images/avatars/" + newFileName;

			Optional<User> userOpt = userRepo.findById(username);
			if (userOpt.isPresent()) {
				User user = userOpt.get();
				user.setPhoto(avatarUrl);
				userRepo.save(user);
			}

			return ResponseEntity.ok(Map.of("message", "Tải ảnh đại diện thành công!", "photo", avatarUrl));
		} catch (Exception e) {
			return ResponseEntity.internalServerError().body(Map.of("message", "Lỗi lưu file ảnh: " + e.getMessage()));
		}
	}

	@PutMapping("/{username}/change-password")
	public ResponseEntity<?> changePassword(@PathVariable String username,
			@Valid @RequestBody ChangePasswordRequest request, BindingResult bindingResult) {

		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		if (!username.equals(currentUsername)) {
			return ResponseEntity.status(403).body(Map.of("message", "Không có quyền truy cập!"));
		}

		if (bindingResult.hasErrors()) {
			String firstError = bindingResult.getFieldErrors().get(0).getDefaultMessage();
			return ResponseEntity.badRequest().body(Map.of("message", firstError));
		}

		if (request.getOldPassword() == null || request.getOldPassword().trim().isEmpty()
				|| request.getNewPassword() == null || request.getNewPassword().trim().isEmpty()
				|| request.getConfirmNewPassword() == null || request.getConfirmNewPassword().trim().isEmpty()) {
			return ResponseEntity.badRequest().body(Map.of("message", "Các trường không được để trống!"));
		}

		if (!request.getNewPassword().equals(request.getConfirmNewPassword())) {
			return ResponseEntity.badRequest().body(Map.of("message", "Mật khẩu mới và xác nhận mật khẩu không khớp!"));
		}

		Optional<User> userOpt = userRepo.findById(username);
		if (!userOpt.isPresent()) {
			return ResponseEntity.badRequest().body(Map.of("message", "Không tìm thấy người dùng!"));
		}

		User user = userOpt.get();
		String currentPassword = user.getPassword();

		if (!passwordEncoder.matches(request.getOldPassword(), currentPassword)) {
			return ResponseEntity.badRequest().body(Map.of("message", "Mật khẩu hiện tại không đúng!"));
		}

		user.setPassword(passwordEncoder.encode(request.getNewPassword()));
		userRepo.save(user);

		return ResponseEntity.ok(Map.of("message", "Đổi mật khẩu thành công!"));
	}

	@GetMapping("/{username}/songs")
	public ResponseEntity<?> getMySongs(@PathVariable String username, @RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "10") int size) {

		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		boolean isOwnerOrAdmin = username.equals(currentUsername) || SecurityContextHolder.getContext().getAuthentication().getAuthorities()
				.stream().anyMatch(a -> a.getAuthority().contains("ADMIN"));

		Pageable pageable = PageRequest.of(page, size);
		Page<Song> songPage;
		if (isOwnerOrAdmin) {
			songPage = songRepo.findByUserUsernameOrderByCreatedAtDesc(username, pageable);
		} else {
			songPage = songRepo.findByUserUsernameAndIsPublicTrueOrderByCreatedAtDesc(username, pageable);
		}
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
	@Autowired
	com.fpoly.webmusicai.repository.FollowRepository followRepo;

	@PostMapping("/{username}/follow")
	public ResponseEntity<?> followUser(@PathVariable String username) {
		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		if (currentUsername == null || "anonymousUser".equalsIgnoreCase(currentUsername)) {
			return ResponseEntity.status(401).body(Map.of("message", "Vui lòng đăng nhập để thực hiện follow!"));
		}
		if (currentUsername.equals(username)) {
			return ResponseEntity.badRequest().body(Map.of("message", "Bạn không thể tự theo dõi chính mình!"));
		}

		Optional<User> followerOpt = userRepo.findById(currentUsername);
		Optional<User> followingOpt = userRepo.findById(username);
		if (followerOpt.isEmpty() || followingOpt.isEmpty()) {
			return ResponseEntity.badRequest().body(Map.of("message", "Người dùng không tồn tại!"));
		}

		Optional<com.fpoly.webmusicai.entity.Follow> followOpt = followRepo.findByFollowerAndFollowing(currentUsername, username);
		if (followOpt.isPresent()) {
			return ResponseEntity.badRequest().body(Map.of("message", "Bạn đã theo dõi người này rồi!"));
		}

		com.fpoly.webmusicai.entity.Follow follow = new com.fpoly.webmusicai.entity.Follow();
		follow.setFollower(followerOpt.get());
		follow.setFollowing(followingOpt.get());
		followRepo.save(follow);

		return ResponseEntity.ok(Map.of("message", "Theo dõi thành công!"));
	}

	@PostMapping("/{username}/unfollow")
	public ResponseEntity<?> unfollowUser(@PathVariable String username) {
		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		if (currentUsername == null || "anonymousUser".equalsIgnoreCase(currentUsername)) {
			return ResponseEntity.status(401).body(Map.of("message", "Vui lòng đăng nhập!"));
		}

		Optional<com.fpoly.webmusicai.entity.Follow> followOpt = followRepo.findByFollowerAndFollowing(currentUsername, username);
		if (followOpt.isEmpty()) {
			return ResponseEntity.badRequest().body(Map.of("message", "Bạn chưa theo dõi người này!"));
		}

		followRepo.delete(followOpt.get());
		return ResponseEntity.ok(Map.of("message", "Hủy theo dõi thành công!"));
	}

	@GetMapping("/{username}/follow-status")
	public ResponseEntity<?> getFollowStatus(@PathVariable String username) {
		String currentUsername = SecurityContextHolder.getContext().getAuthentication().getName();
		boolean isFollowing = false;
		if (currentUsername != null && !"anonymousUser".equalsIgnoreCase(currentUsername)) {
			isFollowing = followRepo.findByFollowerAndFollowing(currentUsername, username).isPresent();
		}

		long followersCount = followRepo.countFollowers(username);
		long followingCount = followRepo.countFollowing(username);

		return ResponseEntity.ok(Map.of(
			"isFollowing", isFollowing,
			"followersCount", followersCount,
			"followingCount", followingCount
		));
	}
}