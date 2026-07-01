package com.fpoly.webmusicai.controller;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.fpoly.webmusicai.entity.Favorite;
import com.fpoly.webmusicai.entity.Song;
import com.fpoly.webmusicai.entity.LikeCount;
import com.fpoly.webmusicai.entity.SongComment;
import com.fpoly.webmusicai.entity.Transaction;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.FavoriteRepository;
import com.fpoly.webmusicai.repository.SongCommentRepository;
import com.fpoly.webmusicai.repository.SongRepository;
import com.fpoly.webmusicai.repository.UserRepository;
import com.fpoly.webmusicai.repository.TransactionRepository;
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

    // ==========================================
    // 1. LẤY DANH SÁCH BÀI HÁT PUBLIC
    // ==========================================
    @GetMapping("/public")
    public ResponseEntity<?> getPublicSongs() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) ? auth.getName() : null;

        List<Song> songs = songRepo.findByIsPublicTrueOrderByCreatedAtDesc();
        if (songs.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<Integer> songIds = songs.stream().map(Song::getId).toList();

        // Lấy tổng số lượt thích cho tất cả bài hát trong 1 câu lệnh query
        Map<Integer, Long> likeCounts = favoriteRepo.countLikesBySongIds(songIds).stream()
                .collect(Collectors.toMap(LikeCount::getSongId, LikeCount::getLikeCount));

        // Lấy danh sách ID bài hát mà người dùng hiện tại đã thích
        Set<Integer> likedByCurrentUser = (username != null)
                ? favoriteRepo.findLikedSongIdsByUser(username, songIds)
                : Collections.emptySet();

        // Chuyển đổi Song thành Map để thêm thông tin 'total_likes' và 'liked_by_me'
        List<Map<String, Object>> result = songs.stream().map(song -> {
            Map<String, Object> songMap = song.toMap(); // Giả sử có phương thức toMap() trong Entity
            songMap.put("total_likes", likeCounts.getOrDefault(song.getId(), 0L));
            songMap.put("liked_by_me", likedByCurrentUser.contains(song.getId()));
            return songMap;
        }).toList();

        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .header("Vary", "Authorization")
                .body(result);
    }

    // ==========================================
    // 2. LẤY DANH SÁCH BÀI HÁT YÊU THÍCH CỦA TÔI
    // ==========================================
    @GetMapping("/my-favorites")
    public ResponseEntity<?> getMyFavoriteSongs() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Vui lòng đăng nhập để xem danh sách yêu thích."));
        }
        String username = auth.getName();
        
        // 1. Lấy danh sách các bài hát yêu thích của người dùng
        List<Song> favoriteSongs = favoriteRepo.findByUserUsernameOrderByCreatedAtDesc(username)
                .stream()
                .map(Favorite::getSong)
                .toList();

        if (favoriteSongs.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        // 2. Lấy ID của tất cả các bài hát để truy vấn số lượt thích một cách hiệu quả
        List<Integer> songIds = favoriteSongs.stream().map(Song::getId).toList();

        // 3. Lấy tổng số lượt thích cho tất cả các bài hát này trong một lần truy vấn
        Map<Integer, Long> likeCounts = favoriteRepo.countLikesBySongIds(songIds).stream()
                .collect(Collectors.toMap(LikeCount::getSongId, LikeCount::getLikeCount));

        // 4. Xây dựng kết quả trả về, thêm các thông tin cần thiết cho UI
        List<Map<String, Object>> result = favoriteSongs.stream().map(song -> {
            Map<String, Object> songMap = song.toMap();
            songMap.put("total_likes", likeCounts.getOrDefault(song.getId(), 0L));
            // Đối với trang "yêu thích của tôi", trường này luôn là true
            songMap.put("liked_by_me", true);
            return songMap;
        }).toList();

        return ResponseEntity.ok()
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .header("Expires", "0")
                .header("Vary", "Authorization")
                .body(result);
    }

    // ==========================================
    // 3. TẠO NHẠC MỚI TỪ GOOGLE COLAB AI
    // ==========================================
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/generate")
    public ResponseEntity<?> generateMusic(@RequestBody Map<String, String> requestData) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        String prompt = requestData.get("prompt");
        String title = requestData.get("title");

        Optional<User> userOpt = userRepo.findById(username);
        if (!userOpt.isPresent())
            return ResponseEntity.badRequest().body("User không tồn tại!");

        User user = userOpt.get();
        if (user.getTokenBalance() < 1)
            return ResponseEntity.badRequest().body("Bạn không đủ Token!");

        // Khấu trừ token và lưu giao dịch
        user.setTokenBalance(user.getTokenBalance() - 1);
        userRepo.save(user);

        Transaction trans = new Transaction();
        trans.setUser(user);
        trans.setAmount(-1);
        trans.setDescription("Tạo nhạc: " + prompt);
        transRepo.save(trans);

        // Lưu bản ghi ban đầu ở trạng thái PENDING
        Song song = new Song();
        song.setTitle(title != null && !title.isBlank() ? title : "Đang tạo...");
        song.setPrompt(prompt);
        song.setStatus("PENDING");

        boolean forcePublic = "BASIC".equals(user.getAccountTier());
        song.setIsPublic(forcePublic);
        song.setUser(user);
        songRepo.save(song);

        // Đồng bộ hóa việc lưu dữ liệu bằng Thread bất đồng bộ với lõi Colab
        final Integer currentSongId = song.getId();
        new Thread(() -> {
            try {
                // Gọi sang Colab thông qua kết nối mạng đồng bộ (Chờ xử lý mất 15-20s)
                Map<String, Object> result = musicService.generateMusic(prompt);

                String base64AudioUrl = (String) result.get("audio_url");
                String aiTitle = (String) result.get("title");

                // Tìm lại thực thể tươi mới từ DB để tránh lỗi ngắt kết nối thực thể (Detached Entity) trong Thread riêng
                Optional<Song> freshSongOpt = songRepo.findById(currentSongId);
                if (freshSongOpt.isPresent()) {
                    Song freshSong = freshSongOpt.get();
                    freshSong.setAudioUrl(base64AudioUrl);
                    freshSong.setStatus("COMPLETED");
                    if (title == null || title.isBlank()) {
                        freshSong.setTitle(aiTitle != null && !aiTitle.isBlank() ? aiTitle : "Bài hát không tên");
                    }
                    songRepo.saveAndFlush(freshSong);
                    log.info("Lõi AI Colab đã cập nhật trạng thái COMPLETED thành công cho bài hát ID: {}", freshSong.getId());
                }
            } catch (Exception e) {
                songRepo.findById(currentSongId).ifPresent(failedSong -> {
                    failedSong.setStatus("FAILED");
                    songRepo.saveAndFlush(failedSong);
                });
                log.error("Lỗi đồng bộ dữ liệu từ lõi AI Colab trong Thread tạo nhạc ngầm: {}", e.getMessage());
            }
        }).start();

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Đã nhận yêu cầu! Lõi AI cá nhân đang xử lý...");
        response.put("songId", song.getId());
        response.put("remaining_tokens", user.getTokenBalance());
        return ResponseEntity.ok(response);
    }

    // ==========================================
    // 4. KIỂM TRA TRẠNG THÁI TIẾN ĐỘ BÀI HÁT (POLLING)
    // ==========================================
    @GetMapping("/{id}/status")
    public ResponseEntity<?> getSongStatus(@PathVariable Integer id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = auth.getName();

        return songRepo.findById(id).map(song -> {
            boolean isOwner = song.getUser().getUsername().equals(currentUser);
            boolean isPublic = song.getIsPublic();
            boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            if (!isOwner && !isAdmin && !isPublic) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Bạn không có quyền xem bài nhạc riêng tư này!");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("id", song.getId());
            result.put("title", song.getTitle());
            result.put("prompt", song.getPrompt());
            result.put("status", song.getStatus());
            result.put("created_at", song.getCreatedAt());

            switch (song.getStatus()) {
                case "COMPLETED" -> {
                    result.put("message", "Nhạc đã sẵn sàng!");
                    result.put("audio_url", song.getAudioUrl());
                    result.put("is_public", song.getIsPublic());
                }
                case "PENDING" -> result.put("message", "Đang xử lý, vui lòng chờ...");
                case "FAILED" -> result.put("message", "Gen nhạc thất bại, vui lòng thử lại.");
                default -> result.put("message", "Trạng thái không xác định");
            }

            return ResponseEntity.ok(result);
        }).orElse(ResponseEntity.notFound().build());
    }

    // ==========================================
    // 5. CẬP NHẬT THIẾT LẬP (ĐỔI TÊN / CHẾ ĐỘ RIÊNG TƯ)
    // ==========================================
    @PutMapping("/{id}/setting")
    public ResponseEntity<?> renameSong(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Vui lòng đăng nhập!");
        }
        String username = auth.getName();

        return songRepo.findById(id).map(song -> {
            if (!song.getUser().getUsername().equals(username)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Bạn không có quyền sửa bài nhạc này!");
            }

            Map<String, Object> changes = new HashMap<>();

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

            if (body.containsKey("is_public")) {
                if (!"COMPLETED".equals(song.getStatus())) {
                    return ResponseEntity.badRequest().body("Chỉ có thể public bài nhạc đã hoàn thành!");
                }

                boolean newIsPublic = Boolean.parseBoolean(body.get("is_public").toString());
                User owner = song.getUser();
                if ("BASIC".equals(owner.getAccountTier()) && !newIsPublic) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Tài khoản BASIC không được phép chuyển nhạc sang Riêng tư!"));
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

    // ==========================================
    // 6. THÍCH / BỎ THÍCH BÀI HÁT (TOGGLE LIKE)
    // ==========================================
    @Transactional
    @PostMapping("/{id}/like")
    public ResponseEntity<?> toggleLike(@PathVariable Integer id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        return songRepo.findById(id).map(song -> {
            boolean isOwner = song.getUser().getUsername().equals(username);
            if (!song.getIsPublic() && !isOwner) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Không thể like bài nhạc riêng tư!"));
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

    // ==========================================
    // 7. QUẢN LÝ BÌNH LUẬN (COMMENTS)
    // ==========================================
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
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Bạn không có quyền sửa bình luận này!"));
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
            if (!comment.getUser().getUsername().equals(username) && !isAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Bạn không có quyền xóa bình luận này!"));
            }

            commentRepo.deleteById(commentId);
            return ResponseEntity.ok(Map.of("message", "Đã xóa bình luận!"));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ==========================================
    // 8. REMIX LẠI BÀI HÁT QUA COLAB AI
    // ==========================================
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/{id}/remix")
    public ResponseEntity<?> remixSong(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        Song original = songRepo.findById(id).orElse(null);
        if (original == null) {
            return ResponseEntity.notFound().build();
        }

        boolean isOwner = original.getUser().getUsername().equals(username);
        if (!original.getIsPublic() && !isOwner) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Không thể remix bài nhạc riêng tư!"));
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

        user.setTokenBalance(user.getTokenBalance() - 1);
        userRepo.save(user);

        Transaction trans = new Transaction();
        trans.setUser(user);
        trans.setAmount(-1);
        trans.setDescription("Remix nhạc từ bài: " + original.getTitle());
        transRepo.save(trans);

        Song remixSong = new Song();
        remixSong.setTitle(customTitle != null && !customTitle.isBlank() ? customTitle : original.getTitle() + " (Remix)");
        remixSong.setPrompt(remixPrompt);
        remixSong.setStatus("PENDING");

        boolean forcePublic = "BASIC".equals(user.getAccountTier());
        remixSong.setIsPublic(forcePublic);
        remixSong.setIsRemix(true);
        remixSong.setParentId(original.getId());
        remixSong.setUser(user);
        songRepo.save(remixSong);

        final String finalCustomTitle = customTitle;
        final Integer currentRemixId = remixSong.getId();

        new Thread(() -> {
            try {
                // Gọi sang Service kết nối lõi Colab AI sinh bản phối mới
                Map<String, Object> result = musicService.generateMusic(remixPrompt);

                String audioUrl = (String) result.get("audio_url");
                String aiTitle = (String) result.get("title");

                // Lấy dữ liệu fresh từ DB trong Thread độc lập trước khi thực hiện cập nhật
                Optional<Song> freshRemixOpt = songRepo.findById(currentRemixId);
                if (freshRemixOpt.isPresent()) {
                    Song freshRemix = freshRemixOpt.get();
                    freshRemix.setAudioUrl(audioUrl);
                    freshRemix.setStatus("COMPLETED");

                    if (finalCustomTitle == null || finalCustomTitle.isBlank()) {
                        freshRemix.setTitle(aiTitle != null ? aiTitle : original.getTitle() + " (Remix)");
                    }
                    songRepo.saveAndFlush(freshRemix);
                    log.info("Lõi AI Colab đã remix xong và cập nhật thành công bài ID: {}", freshRemix.getId());
                }
            } catch (Exception e) {
                songRepo.findById(currentRemixId).ifPresent(failedRemix -> {
                    failedRemix.setStatus("FAILED");
                    songRepo.saveAndFlush(failedRemix);
                });
                log.error("Lỗi remix nhạc từ Colab trong luồng chạy ngầm: {}", e.getMessage());
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