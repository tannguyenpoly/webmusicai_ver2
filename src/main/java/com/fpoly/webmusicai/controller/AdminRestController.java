package com.fpoly.webmusicai.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.fpoly.webmusicai.entity.Song;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.SongRepository;
import com.fpoly.webmusicai.repository.UserRepository;
import com.fpoly.webmusicai.service.SongService;

@CrossOrigin("*")
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')") // BẢO MẬT: Yêu cầu quyền ADMIN cho tất cả API trong Controller này
public class AdminRestController {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private SongRepository songRepo;

    @Autowired
    private SongService songService;

    // ============ QUẢN LÝ USER (đã có sẵn) ============

    @GetMapping("/users")
    public ResponseEntity<?> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String keyword) {

        Pageable pageable = PageRequest.of(page, size);
        Page<User> users;

        if (keyword != null && !keyword.trim().isEmpty()) {
            users = userRepo.findByUsernameContainingIgnoreCaseOrFullnameContainingIgnoreCase(
                keyword.trim(), keyword.trim(), pageable);
        } else {
            users = userRepo.findAll(pageable);
        }

        return ResponseEntity.ok(users);
    }

    @PutMapping("/users/{username}/toggle-status")
    public ResponseEntity<?> toggleUserStatus(@PathVariable String username) {
        // CẢI TIẾN: Ngăn admin tự khóa tài khoản của chính mình
        String currentAdminUsername = SecurityContextHolder.getContext().getAuthentication().getName();
        if (username.equalsIgnoreCase(currentAdminUsername)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Không thể tự khóa tài khoản của chính mình."));
        }

        return userRepo.findById(username).map(user -> {
            user.setEnabled(!user.getEnabled());
            userRepo.save(user);
            String status = user.getEnabled() ? "mở khóa" : "khóa";
            return ResponseEntity.ok(Map.of(
                "message", "Đã " + status + " tài khoản " + username,
                "enabled", user.getEnabled()
            ));
        }).orElse(ResponseEntity.badRequest().body(Map.of("message", "Không tìm thấy người dùng: " + username)));
    }

    // ============ QUẢN LÝ SONGS (MỚI) ============

    // Xem toàn bộ bài nhạc trong hệ thống, có thể lọc theo status
    @GetMapping("/songs")
    public ResponseEntity<?> getAllSongs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String status) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Song> songs;

        if (status != null && !status.trim().isEmpty()) {
            songs = songRepo.findByStatusOrderByCreatedAtDesc(status.trim(), pageable);
        } else {
            songs = songRepo.findAll(pageable);
        }

        return ResponseEntity.ok(songs);
    }

    // Xóa bài nhạc (vi phạm bản quyền, nội dung xấu...)
    @DeleteMapping("/songs/{id}")
    public ResponseEntity<?> deleteSong(@PathVariable Integer id) {
        try {
            songService.deleteSongAndAssociatedData(id);
            return ResponseEntity.ok(Map.of("message", "Đã xóa thành công bài nhạc #" + id + " và các dữ liệu liên quan."));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("message", "Lỗi máy chủ khi xóa bài hát."));
        }
    }

    // Admin có quyền force public/private bất kỳ bài nào
    @PutMapping("/songs/{id}/toggle-public")
    public ResponseEntity<?> adminTogglePublic(@PathVariable Integer id) {
        return songRepo.findById(id).map(song -> {
            song.setIsPublic(!song.getIsPublic());
            songRepo.save(song);
            return ResponseEntity.ok(Map.of(
                "message", "Đã cập nhật trạng thái public",
                "id", song.getId(),
                "is_public", song.getIsPublic()
            ));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ============ THỐNG KÊ CHI TIẾT (MỞ RỘNG) ============

    @GetMapping("/statistics")
    public ResponseEntity<?> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        // Thống kê user
        stats.put("totalUsers", userRepo.count());

        // Thống kê songs theo trạng thái
        stats.put("totalSongs", songRepo.count());
        stats.put("completedSongs", songRepo.countByStatus("COMPLETED"));
        stats.put("pendingSongs", songRepo.countByStatus("PENDING"));
        stats.put("failedSongs", songRepo.countByStatus("FAILED"));
        stats.put("publicSongs", songRepo.countByIsPublicTrue());

        // Tỷ lệ thành công
        long total = songRepo.count();
        long completed = songRepo.countByStatus("COMPLETED");
        double successRate = total > 0 ? (completed * 100.0 / total) : 0;
        stats.put("successRate", Math.round(successRate * 10) / 10.0); // 1 chữ số sau phẩy

        return ResponseEntity.ok(stats);
    }
}