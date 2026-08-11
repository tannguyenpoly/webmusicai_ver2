package com.fpoly.webmusicai.controller;

import com.fpoly.webmusicai.entity.Song;
import com.fpoly.webmusicai.service.SongUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/songs")
public class SongUploadController {

    @Autowired
    private SongUploadService songUploadService;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadMusic(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Vui lòng đăng nhập để thực hiện chức năng này."));
        }
        if (file.isEmpty() || title == null || title.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng nhập tiêu đề và chọn file nhạc."));
        }
        try {
            Song savedSong = songUploadService.processAndSaveUpload(file, title, authentication.getName());
            return ResponseEntity.ok(savedSong);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("message", "Lỗi khi lưu file: " + e.getMessage()));
        }
    }
}