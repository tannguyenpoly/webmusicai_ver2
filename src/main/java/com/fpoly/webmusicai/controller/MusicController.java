package com.fpoly.webmusicai.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.fpoly.webmusicai.service.MusicAnalysisService;


@RestController
@RequestMapping("/api/music")
public class MusicController {

    private final MusicAnalysisService analysisService;

    public MusicController(MusicAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping("/analyze")
    public ResponseEntity<?> analyzeFile(@RequestParam("audioFile") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body("Please select a file to upload.");
        }

        try {
            // Gọi phương thức đã được sửa lại trong MusicAnalysisService
            String genre = analysisService.analyzeGenre(file);
            if (genre != null) {
                return ResponseEntity.ok(java.util.Map.of("genre", genre));
            }
            return ResponseEntity.status(503).body("Dịch vụ phân tích không khả dụng hoặc không nhận dạng được thể loại.");
        } catch (Exception e) {
             return ResponseEntity.internalServerError().body("Lỗi trong quá trình phân tích: " + e.getMessage());
        }
    }
}
