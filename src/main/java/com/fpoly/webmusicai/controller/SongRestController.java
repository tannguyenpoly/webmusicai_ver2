package com.fpoly.webmusicai.controller;

import java.util.*;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fpoly.webmusicai.entity.*;
import com.fpoly.webmusicai.repository.*;
import com.fpoly.webmusicai.service.MusicGeneratorService;

@CrossOrigin("*")
@RestController
@RequestMapping("/api/songs")
public class SongRestController {

    @Autowired SongRepository songRepo;
    @Autowired UserRepository userRepo;
    @Autowired TransactionRepository transRepo;
    @Autowired MusicGeneratorService musicService;

    // 1. API lấy nhạc cho Trang Chủ (Chỉ lấy bài Public)
    @GetMapping("/public")
    public ResponseEntity<List<Song>> getPublicSongs() {
        return ResponseEntity.ok(songRepo.findByIsPublicTrueOrderByCreatedAtDesc());
    }

    // 2. API Yêu cầu AI Tạo Nhạc
    @PostMapping("/generate")
    public ResponseEntity<?> generateMusic(@RequestBody Map<String, String> requestData) {
        // Tạm thời lấy username từ JSON. (Khi ráp Security vào, ta sẽ lấy từ Token)
        String username = requestData.get("username"); 
        String prompt = requestData.get("prompt");

        Optional<User> userOpt = userRepo.findById(username);
        if (!userOpt.isPresent()) return ResponseEntity.badRequest().body("User không tồn tại!");

        User user = userOpt.get();
        if (user.getTokenBalance() < 1) {
            return ResponseEntity.badRequest().body("Bạn không đủ Token để tạo nhạc!");
        }

        // BƯỚC 1: Trừ token của User
        user.setTokenBalance(user.getTokenBalance() - 1);
        userRepo.save(user);

        // BƯỚC 2: Ghi vào lịch sử giao dịch (Bảng Transactions)
        Transaction trans = new Transaction();
        trans.setUser(user);
        trans.setAmount(-1);
        trans.setDescription("Tạo nhạc với nội dung: " + prompt);
        transRepo.save(trans);

        // BƯỚC 3: Tạo bài hát lưu vào DB với trạng thái "Đang chờ - PENDING"
        Song song = new Song();
        song.setTitle("AI Song - " + new Date().getTime());
        song.setPrompt(prompt);
        song.setStatus("PENDING");
        song.setIsPublic(false);
        song.setUser(user);
        songRepo.save(song);

        // BƯỚC 4: Chạy AI ngầm (Dùng Thread để Server không bị đơ khi đợi AI 5 giây)
        new Thread(() -> {
            String audioUrl = musicService.generateMusic(prompt);
            song.setAudioUrl(audioUrl);
            song.setStatus("COMPLETED");
            songRepo.save(song); // Cập nhật lại trạng thái thành công
        }).start();

        // BƯỚC 5: Trả về phản hồi cho Postman/Giao diện ngay lập tức
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Đã nhận yêu cầu! AI đang xử lý...");
        response.put("songId", song.getId());
        response.put("remaining_tokens", user.getTokenBalance());
        
        return ResponseEntity.ok(response);
    }
}