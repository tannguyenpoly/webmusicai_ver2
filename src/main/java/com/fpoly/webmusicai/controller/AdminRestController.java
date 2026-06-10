package com.fpoly.webmusicai.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fpoly.webmusicai.entity.User;
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

    @GetMapping("/users")
    public ResponseEntity<?> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "") String keyword) {
        
        Pageable pageable = PageRequest.of(page, size);
        Page<User> users;
        
        if (keyword != null && !keyword.trim().isEmpty()) {
            users = userRepo.findByUsernameContainingIgnoreCaseOrFullnameContainingIgnoreCase(keyword.trim(), keyword.trim(), pageable);
        } else {
            users = userRepo.findAll(pageable);
        }
        
        return ResponseEntity.ok(users);
    }

    @PutMapping("/users/{username}/toggle-status")
    public ResponseEntity<?> toggleUserStatus(@PathVariable String username) {
        return userRepo.findById(username).map(user -> {
            user.setEnabled(!user.getEnabled()); // Đảo ngược trạng thái
            userRepo.save(user);
            String status = user.getEnabled() ? "mở khóa" : "khóa";
            return ResponseEntity.ok(Map.of("message", "Đã " + status + " tài khoản " + username, "enabled", user.getEnabled()));
        }).orElse(ResponseEntity.badRequest().body(Map.of("message", "Không tìm thấy người dùng: " + username)));
    }

    @GetMapping("/statistics")
    public ResponseEntity<?> getStatistics() {
        return ResponseEntity.ok(Map.of("totalUsers", userRepo.count(), "totalSongs", songRepo.count()));
    }
}