package com.fpoly.webmusicai.controller;

import java.security.Principal;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fpoly.webmusicai.exception.TierRestrictedException;
import com.fpoly.webmusicai.service.MusicReferenceAnalysisService;

@RestController
@RequestMapping("/api/music-analysis")
public class MusicAnalysisController {
    private final MusicReferenceAnalysisService referenceAnalysisService;

    public MusicAnalysisController(MusicReferenceAnalysisService referenceAnalysisService) {
        this.referenceAnalysisService = referenceAnalysisService;
    }

    @GetMapping("/status")
    public ResponseEntity<?> status() {
        return ResponseEntity.ok(Map.of("configured", referenceAnalysisService.isConfigured()));
    }

    @PostMapping("/reference")
    public ResponseEntity<?> analyze(@RequestParam("file") MultipartFile file, Principal principal) {
        try {
            return ResponseEntity.ok(referenceAnalysisService.analyze(principal.getName(), file));
        } catch (TierRestrictedException error) {
            return ResponseEntity.status(403).body(Map.of("message", error.getMessage()));
        } catch (IllegalArgumentException error) {
            return ResponseEntity.badRequest().body(Map.of("message", error.getMessage()));
        } catch (IllegalStateException error) {
            return ResponseEntity.status(503).body(Map.of("message", error.getMessage()));
        }
    }

    @GetMapping("/history")
    public ResponseEntity<?> history(Principal principal) {
        return ResponseEntity.ok(referenceAnalysisService.history(principal.getName()));
    }
}
