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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fpoly.webmusicai.entity.Favorite;
import com.fpoly.webmusicai.entity.Song;
import com.fpoly.webmusicai.entity.SongComment;
import com.fpoly.webmusicai.entity.Transaction;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.FavoriteRepository;
import com.fpoly.webmusicai.repository.SongCommentRepository;
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
	FavoriteRepository favoriteRepo;

	@Autowired
	SongCommentRepository commentRepo;
	
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

	@GetMapping("/my-favorites")
	public ResponseEntity<?> getMyFavoriteSongs() {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		String username = auth.getName();

		if (username == null || "anonymousUser".equals(username)) {
			return ResponseEntity.status(401).body(Map.of("message", "Vui lòng đăng nhập để xem danh sách yêu thích."));
		}

		// Giả định FavoriteRepository có phương thức này
		List<Favorite> favorites = favoriteRepo.findByUserUsernameOrderByCreatedAtDesc(username);
		List<Song> favoriteSongs = favorites.stream().map(Favorite::getSong).toList();

		return ResponseEntity.ok(favoriteSongs);
	}

	@Transactional(rollbackFor = Exception.class)
	@PostMapping("/generate")
	public ResponseEntity<?> generateMusic(@RequestBody Map<String, String> requestData) {
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
		
		// [CẬP NHẬT NGHIỆP VỤ]: Tài khoản BASIC ép buộc Public, PRO được quyền Private
		boolean forcePublic = "BASIC".equals(user.getAccountTier());
		song.setIsPublic(forcePublic);
		
		song.setUser(user);
		songRepo.save(song);

		new Thread(() -> {
			try {
				Map result = musicService.generateMusic(prompt, isInstrumental);
				// [FIX BUG]: Đã sửa lỗi lưu audioUrl bằng title
				String audioUrl = (String) result.get("audio_url"); 
				String aiTitle = (String) result.get("title");
				song.setAudioUrl(audioUrl);
				song.setStatus("COMPLETED");
				if (title == null || title.isBlank()) {
					song.setTitle(aiTitle != null && !aiTitle.isBlank() ? aiTitle : "Bài hát không tên");
				}
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
				if (!isPublic) {
					return ResponseEntity.status(403).body("Bạn không có quyền xem bài nhạc riêng tư này!");
				}
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
				if (!"COMPLETED".equals(song.getStatus())) {
					return ResponseEntity.badRequest().body("Chỉ có thể public bài nhạc đã hoàn thành!");
				}
				
				boolean newIsPublic = Boolean.parseBoolean(body.get("is_public").toString());
				
				// [CẬP NHẬT NGHIỆP VỤ]: Chặn User BASIC chuyển nhạc sang Private (ẩn nhạc)
				User owner = song.getUser();
				if ("BASIC".equals(owner.getAccountTier()) && !newIsPublic) {
					return ResponseEntity.status(403).body(Map.of("message", "Tài khoản BASIC không được phép chuyển nhạc sang Riêng tư!"));
				}
				
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

	@Transactional
	@PostMapping("/{id}/like")
	public ResponseEntity<?> toggleLike(@PathVariable Integer id) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		String username = auth.getName();

		return songRepo.findById(id).map(song -> {
			boolean isOwner = song.getUser().getUsername().equals(username);
			if (!song.getIsPublic() && !isOwner) {
				return ResponseEntity.status(403).body(Map.of("message", "Không thể like bài nhạc riêng tư!"));
			}

			boolean alreadyLiked = favoriteRepo.existsByUserUsernameAndSongId(username, id);

			if (alreadyLiked) {
				favoriteRepo.deleteByUserUsernameAndSongId(username, id);
				long totalLikes = favoriteRepo.countBySongId(id);
				return ResponseEntity.ok(Map.of("message", "Đã bỏ thích bài nhạc", "liked", false, "total_likes", totalLikes));
			} else {
				User user = userRepo.findById(username).orElseThrow();
				Favorite fav = new Favorite();
				fav.setUser(user);
				fav.setSong(song);
				favoriteRepo.save(fav);

				long totalLikes = favoriteRepo.countBySongId(id);
				return ResponseEntity.ok(Map.of("message", "Đã thích bài nhạc", "liked", true, "total_likes", totalLikes));
			}
		}).orElse(ResponseEntity.notFound().build());
	}

	@GetMapping("/{id}/likes")
	public ResponseEntity<?> getLikeStatus(@PathVariable Integer id) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		String username = (auth != null) ? auth.getName() : null;

		if (!songRepo.existsById(id)) {
			return ResponseEntity.notFound().build();
		}

		long totalLikes = favoriteRepo.countBySongId(id);
		boolean liked = username != null && favoriteRepo.existsByUserUsernameAndSongId(username, id);

		return ResponseEntity.ok(Map.of("song_id", id, "total_likes", totalLikes, "liked_by_me", liked));
	}

	@GetMapping("/{id}/comments")
	public ResponseEntity<?> getComments(@PathVariable Integer id) {
		if (!songRepo.existsById(id)) {
			return ResponseEntity.notFound().build();
		}

		List<SongComment> comments = commentRepo.findBySongIdOrderByCreatedAtDesc(id);

		List<Map<String, Object>> result = comments.stream().map(c -> {
			Map<String, Object> item = new HashMap<>();
			item.put("id", c.getId());
			item.put("content", c.getContent());
			item.put("username", c.getUser().getUsername());
			item.put("fullname", c.getUser().getFullname());
			item.put("created_at", c.getCreatedAt());
			return item;
		}).toList();

		return ResponseEntity.ok(Map.of("song_id", id, "total_comments", result.size(), "comments", result));
	}

	@PostMapping("/{id}/comments")
	public ResponseEntity<?> createComment(@PathVariable Integer id, @RequestBody Map<String, String> body) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		String username = auth.getName();

		String content = body.get("content");
		if (content == null || content.isBlank()) {
			return ResponseEntity.badRequest().body(Map.of("message", "Nội dung bình luận không được để trống!"));
		}

		return songRepo.findById(id).map(song -> {
			User user = userRepo.findById(username).orElseThrow();

			SongComment comment = new SongComment();
			comment.setSong(song);
			comment.setUser(user);
			comment.setContent(content.trim());

			commentRepo.save(comment);
			log.info("User {} bình luận bài #{}", username, id);

			Map<String, Object> result = new HashMap<>();
			result.put("id", comment.getId());
			result.put("content", comment.getContent());
			result.put("username", username);
			result.put("created_at", comment.getCreatedAt());
			result.put("message", "Đã thêm bình luận!");

			return ResponseEntity.ok(result);
		}).orElse(ResponseEntity.notFound().build());
	}

	@PutMapping("/comments/{commentId}")
	public ResponseEntity<?> updateComment(@PathVariable Integer commentId, @RequestBody Map<String, String> body) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		String username = auth.getName();

		return commentRepo.findById(commentId).map(comment -> {
			if (!comment.getUser().getUsername().equals(username)) {
				return ResponseEntity.status(403).body(Map.of("message", "Bạn không có quyền sửa bình luận này!"));
			}

			String content = body.get("content");
			if (content == null || content.isBlank()) {
				return ResponseEntity.badRequest().body(Map.of("message", "Nội dung bình luận không được để trống!"));
			}

			comment.setContent(content.trim());
			commentRepo.save(comment);
			log.info("User {} sửa bình luận #{}", username, commentId);

			Map<String, Object> result = new HashMap<>();
			result.put("id", comment.getId());
			result.put("content", comment.getContent());
			result.put("message", "Đã cập nhật bình luận!");

			return ResponseEntity.ok(result);
		}).orElse(ResponseEntity.notFound().build());
	}

	@DeleteMapping("/comments/{commentId}")
	public ResponseEntity<?> deleteComment(@PathVariable Integer commentId) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		String username = auth.getName();
		boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

		return commentRepo.findById(commentId).map(comment -> {
			// Chỉ chủ comment hoặc admin mới xóa được
			if (!comment.getUser().getUsername().equals(username) && !isAdmin) {
				return ResponseEntity.status(403).body(Map.of("message", "Bạn không có quyền xóa bình luận này!"));
			}

			commentRepo.deleteById(commentId);
			return ResponseEntity.ok(Map.of("message", "Đã xóa bình luận!"));

		}).orElse(ResponseEntity.notFound().build());
	}

	@PostMapping("/{id}/remix")
	public ResponseEntity<?> remixSong(@PathVariable Integer id, @RequestBody Map<String, String> body) {
		Authentication auth = SecurityContextHolder.getContext().getAuthentication();
		String username = auth.getName();

		Song original = songRepo.findById(id).orElse(null);
		if (original == null) {
			return ResponseEntity.notFound().build();
		}

		// Chỉ remix được bài public hoặc bài của chính mình
		boolean isOwner = original.getUser().getUsername().equals(username);
		if (!original.getIsPublic() && !isOwner) {
			return ResponseEntity.status(403).body(Map.of("message", "Không thể remix bài nhạc riêng tư!"));
		}
		if (!"COMPLETED".equals(original.getStatus())) {
			return ResponseEntity.badRequest().body(Map.of("message", "Chỉ remix được bài đã hoàn thành!"));
		}

		User user = userRepo.findById(username).orElseThrow();
		if (user.getTokenBalance() < 1) {
			return ResponseEntity.badRequest().body(Map.of("message", "Bạn không đủ Token!"));
		}

		String remixPrompt = body.getOrDefault("prompt", "Remix lại theo phong cách mới từ: " + original.getPrompt());
		String customTitle = body.get("title");
		final boolean isInstrumental = Boolean.parseBoolean(body.getOrDefault("instrumental", "true"));

		user.setTokenBalance(user.getTokenBalance() - 1);
		userRepo.save(user);

		Transaction trans = new Transaction();
		trans.setUser(user);
		trans.setAmount(-1);
		trans.setDescription("Remix nhạc: " + original.getTitle());
		transRepo.save(trans);

		Song remixSong = new Song();
		remixSong.setTitle(customTitle != null && !customTitle.isBlank() ? customTitle : original.getTitle() + " (Remix)");
		remixSong.setPrompt(remixPrompt);
		remixSong.setStatus("PENDING");
		
		// [CẬP NHẬT NGHIỆP VỤ]: Tài khoản BASIC ép buộc Public
		boolean forcePublic = "BASIC".equals(user.getAccountTier());
		remixSong.setIsPublic(forcePublic);
		
		remixSong.setIsRemix(true);
		remixSong.setParentId(original.getId());
		remixSong.setUser(user);
		songRepo.save(remixSong);

		final String finalCustomTitle = customTitle;

		new Thread(() -> {
			try {
				Map result = musicService.generateMusic(remixPrompt, isInstrumental);

				String audioUrl = (String) result.get("audio_url");
				String aiTitle = (String) result.get("title");
				// [FIX BUG]: Xóa hoàn toàn dòng lấy và gán imageUrl cũ

				remixSong.setAudioUrl(audioUrl);
				remixSong.setStatus("COMPLETED");

				if (finalCustomTitle == null || finalCustomTitle.isBlank()) {
					remixSong.setTitle(aiTitle != null ? aiTitle : original.getTitle() + " (Remix)");
				}

				songRepo.save(remixSong);
				log.info("Remix xong! Title: {}", remixSong.getTitle());

			} catch (Exception e) {
				remixSong.setStatus("FAILED");
				songRepo.save(remixSong);
				log.error("Lỗi remix nhạc: {}", e.getMessage());
			}
		}).start();

		Map<String, Object> response = new HashMap<>();
		response.put("message", "Đã nhận yêu cầu remix! AI đang xử lý...");
		response.put("songId", remixSong.getId());
		response.put("parent_id", original.getId());
		response.put("remaining_tokens", user.getTokenBalance());
		return ResponseEntity.ok(response);
	}

	// Xem tất cả bản remix của 1 bài gốc
	@GetMapping("/{id}/remixes")
	public ResponseEntity<?> getRemixes(@PathVariable Integer id) {
		if (!songRepo.existsById(id)) {
			return ResponseEntity.notFound().build();
		}
		List<Song> remixes = songRepo.findByParentIdOrderByCreatedAtDesc(id);
		return ResponseEntity.ok(remixes);
	}
}