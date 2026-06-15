package com.fpoly.webmusicai.controller;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.fpoly.webmusicai.entity.*;
import com.fpoly.webmusicai.repository.*;
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

	@PostMapping("/generate")
	public ResponseEntity<?> generateMusic(@RequestBody Map<String, String> requestData) {

		// ← Lấy username từ JWT token thay vì từ Body
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		String username = auth.getName();

		String prompt = requestData.get("prompt");
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
		song.setTitle("AI Song - " + new Date().getTime());
		song.setPrompt(prompt);
		song.setStatus("PENDING");
		song.setIsPublic(false);
		song.setUser(user);
		songRepo.save(song);

		new Thread(() -> {
			try {
				String audioUrl = musicService.generateMusic(prompt, isInstrumental);
				song.setAudioUrl(audioUrl);
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
		return songRepo.findById(id).map(song -> {
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
}