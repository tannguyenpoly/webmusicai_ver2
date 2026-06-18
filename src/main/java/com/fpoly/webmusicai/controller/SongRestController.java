package com.fpoly.webmusicai.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.webmusicai.entity.Song;
import com.fpoly.webmusicai.entity.Transaction;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.SongRepository;
import com.fpoly.webmusicai.repository.TransactionRepository;
import com.fpoly.webmusicai.repository.UserRepository;
import com.fpoly.webmusicai.service.MusicGeneratorService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@CrossOrigin("*")
@RestController
@RequestMapping("/api/songs")
public class SongRestController {

	@Autowired
	SongRepository songRepo;
	@Autowired
	UserRepository userRepo;
	@Autowired
	TransactionRepository transRepo;
	@Autowired
	MusicGeneratorService musicService;

	@GetMapping("/public")
	public ResponseEntity<List<Song>> getPublicSongs() {
		return ResponseEntity.ok(songRepo.findByIsPublicTrueOrderByCreatedAtDesc());
	}

	@Transactional(rollbackFor = Exception.class)
	@PostMapping("/generate")
	public ResponseEntity<?> generateMusic(@RequestBody Map<String, String> requestData) {
		// Sử dụng cú pháp gọn gàng của Hòa
				String username = SecurityContextHolder.getContext().getAuthentication().getName();
		String prompt = requestData.get("prompt");
		String title = requestData.get("title");
		final boolean isInstrumental = Boolean.parseBoolean(requestData.getOrDefault("instrumental", "true"));

		Optional<User> userOpt = userRepo.findById(username);
		if (!userOpt.isPresent())
			return ResponseEntity.badRequest().body("User không tồn tại!");

		User user = userOpt.get();
		if (user.getTokenBalance() < 1)
			return ResponseEntity.badRequest().body("Bạn không đủ Token!");

		user.setTokenBalance(user.getTokenBalance() - 1);
		userRepo.save(user);

		Transaction trans = new Transaction();
		trans.setUser(user);
		trans.setAmount(-1);
		trans.setDescription("Tạo nhạc: " + prompt);
		transRepo.save(trans);

		Song song = new Song();
		song.setTitle(title != null && !title.isBlank() ? title : "Đang tạo...");
		song.setPrompt(prompt);
		song.setStatus("PENDING");
		song.setIsPublic(false);
		song.setUser(user);
		songRepo.save(song);

		new Thread(() -> {
			try {
				Map audioUrl = musicService.generateMusic(prompt, isInstrumental);
				song.setAudioUrl(title);
				song.setStatus("COMPLETED");
				songRepo.save(song);
			} catch (Exception e) {
				song.setStatus("FAILED");
				songRepo.save(song);
				log.error("Lỗi gen nhạc: {}", e.getMessage());
			}
		}).start();

		Map<String, Object> response = new HashMap<>();
		response.put("message", "Đã nhận yêu cầu! AI đang xử lý...");
		response.put("songId", song.getId());
		response.put("remaining_tokens", user.getTokenBalance());
		return ResponseEntity.ok(response);
	}

	@GetMapping("/{id}/status")
	public ResponseEntity<?> getSongStatus(@PathVariable Integer id) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		String currentUser = auth.getName();

		return songRepo.findById(id).map(song -> {
			boolean isOwner = song.getUser().getUsername().equals(currentUser);
			boolean isPublic = song.getIsPublic();
			boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
			if (!isOwner && !isAdmin) {
				return ResponseEntity.status(403).body("Bạn không có quyền xem bài nhạc này!");
			}
			if (!isPublic && !isOwner && !isAdmin) {
				return ResponseEntity.status(403).body("Bạn không có quyền xem bài nhạc này!");
			}
			Map<String, Object> result = new HashMap<>();
			result.put("id", song.getId());
			result.put("title", song.getTitle());
			result.put("prompt", song.getPrompt());
			result.put("status", song.getStatus());
			result.put("created_at", song.getCreatedAt());

			switch (song.getStatus()) {
			case "COMPLETED" -> {
				result.put("message", " Nhạc đã sẵn sàng!");
				result.put("audio_url", song.getAudioUrl());
				result.put("is_public", song.getIsPublic());
			}
			case "PENDING" -> {
				result.put("message", " Đang xử lý, vui lòng chờ...");
			}
			case "FAILED" -> {
				result.put("message", " Gen nhạc thất bại, vui lòng thử lại.");
			}
			default -> {
				result.put("message", "Trạng thái không xác định");
			}
			}

			return ResponseEntity.ok(result);
		}).orElse(ResponseEntity.notFound().build());
	}

	@PutMapping("/{id}/setting")
	public ResponseEntity<?> renameSong(@PathVariable Integer id, @RequestBody Map<String, Object> body) {

		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		if (auth == null || !auth.isAuthenticated()) {
			return ResponseEntity.status(401).body("Vui lòng đăng nhập!");
		}
		String username = auth.getName();

		return songRepo.findById(id).map(song -> {
			// Chỉ chủ bài nhạc mới được sửa
			if (!song.getUser().getUsername().equals(username)) {
				return ResponseEntity.status(403).body("Bạn không có quyền sửa bài nhạc này!");
			}

			Map<String, Object> changes = new HashMap<>();

			// Đổi tên (nếu có gửi "title")
			if (body.containsKey("title")) {
				String newTitle = (String) body.get("title");
				if (newTitle == null || newTitle.isBlank()) {
					return ResponseEntity.badRequest().body("Tên bài nhạc không được để trống!");
				}
				String oldTitle = song.getTitle();
				song.setTitle(newTitle.trim());
				changes.put("old_title", oldTitle);
				changes.put("new_title", song.getTitle());
			}

			// Đổi trạng thái public/private (nếu có gửi "is_public")
			if (body.containsKey("is_public")) {
				// Chỉ cho public khi bài đã COMPLETED
				if (!"COMPLETED".equals(song.getStatus())) {
					return ResponseEntity.badRequest().body("Chỉ có thể public bài nhạc đã hoàn thành!");
				}
				boolean newIsPublic = Boolean.parseBoolean(body.get("is_public").toString());
				song.setIsPublic(newIsPublic);
				changes.put("is_public", newIsPublic);
			}

			songRepo.save(song);
			log.info("User {} cập nhật bài {}: {}", username, id, changes);

			Map<String, Object> response = new HashMap<>();
			response.put("message", "Cập nhật thành công!");
			response.put("id", song.getId());
			response.putAll(changes);

			return ResponseEntity.ok(response);

		}).orElse(ResponseEntity.notFound().build());
	}
}