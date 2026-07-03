package com.fpoly.webmusicai.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fpoly.webmusicai.entity.Order;
import com.fpoly.webmusicai.entity.Song;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.OrderRepository;
import com.fpoly.webmusicai.repository.SongRepository;
import com.fpoly.webmusicai.repository.UserRepository;

@CrossOrigin("*")
@RestController
@RequestMapping("/api/admin")
public class AdminRestController {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private SongRepository songRepo;

    @Autowired
    private OrderRepository orderRepo;

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

    @DeleteMapping("/songs/{id}")
    public ResponseEntity<?> deleteSong(@PathVariable Integer id) {
        if (!songRepo.existsById(id)) {
            return ResponseEntity.badRequest().body(Map.of("message", "Bài nhạc không tồn tại!"));
        }
        songRepo.deleteById(id);
        return ResponseEntity.ok(Map.of("message", "Đã xóa bài nhạc #" + id));
    }

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

    @GetMapping("/orders")
    public ResponseEntity<?> getAllOrders(
            @RequestParam(defaultValue = "") String status) {

        List<Order> orders;
        if (status != null && !status.trim().isEmpty()) {
            orders = orderRepo.findByStatus(status.trim());
        } else {
            orders = orderRepo.findAll();
        }
        orders.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()));
        return ResponseEntity.ok(orders);
    }

    @GetMapping("/statistics")
    public ResponseEntity<?> getStatistics() {
        Map<String, Object> stats = new HashMap<>();

        stats.put("totalUsers", userRepo.count());

        stats.put("totalSongs", songRepo.count());
        stats.put("completedSongs", songRepo.countByStatus("COMPLETED"));
        stats.put("pendingSongs", songRepo.countByStatus("PENDING"));
        stats.put("failedSongs", songRepo.countByStatus("FAILED"));
        stats.put("publicSongs", songRepo.countByIsPublicTrue());

        long total = songRepo.count();
        long completed = songRepo.countByStatus("COMPLETED");
        double successRate = total > 0 ? (completed * 100.0 / total) : 0;
        stats.put("successRate", Math.round(successRate * 10) / 10.0); // 1 chữ số sau phẩy

        return ResponseEntity.ok(stats);
    }
}