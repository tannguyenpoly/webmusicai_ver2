package com.fpoly.webmusicai.service;

import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.fpoly.webmusicai.entity.Genre;
import com.fpoly.webmusicai.entity.MusicAnalysisHistory;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.GenreRepository;
import com.fpoly.webmusicai.repository.MusicAnalysisHistoryRepository;
import com.fpoly.webmusicai.repository.UserRepository;

@Service
public class MusicReferenceAnalysisService {
    private static final long MAX_FILE_SIZE = 50L * 1000 * 1000;
    private final MusicAnalysisService analysisService;
    private final MusicAnalysisHistoryRepository historyRepository;
    private final GenreRepository genreRepository;
    private final UserRepository userRepository;
    private final TierAccessService tierAccessService;

    public MusicReferenceAnalysisService(MusicAnalysisService analysisService,
            MusicAnalysisHistoryRepository historyRepository, GenreRepository genreRepository,
            UserRepository userRepository, TierAccessService tierAccessService) {
        this.analysisService = analysisService;
        this.historyRepository = historyRepository;
        this.genreRepository = genreRepository;
        this.userRepository = userRepository;
        this.tierAccessService = tierAccessService;
    }

    public boolean isConfigured() {
        return analysisService.isConfigured();
    }

    @Transactional
    public Map<String, Object> analyze(String username, MultipartFile file) {
        User user = userRepository.findById(username)
                .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản."));
        tierAccessService.requireReferenceAnalysis(user);
        validate(file);
        String hash = sha256(file);
        MusicAnalysisHistory cached = historyRepository.findByUserUsernameAndFileHash(username, hash).orElse(null);
        if (cached != null) return toMap(cached, true);

        MusicAnalysisService.AnalysisResult result = analysisService.analyze(file);
        MusicAnalysisHistory history = new MusicAnalysisHistory();
        history.setUser(user);
        history.setFileName(safeFileName(file.getOriginalFilename()));
        history.setFileHash(hash);
        history.setDetectedLabel(result.label());
        history.setConfidence(result.confidence());
        history.setMatchedGenre(findMatchingGenre(result.label()));
        return toMap(historyRepository.save(history), false);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> history(String username) {
        return historyRepository.findTop6ByUserUsernameOrderByCreatedAtDesc(username).stream()
                .map(item -> toMap(item, true)).toList();
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Hãy chọn một file nhạc.");
        if (file.getSize() > MAX_FILE_SIZE) throw new IllegalArgumentException("File nhạc tối đa 50 MB.");
        String name = safeFileName(file.getOriginalFilename()).toLowerCase(Locale.ROOT);
        boolean validExtension = name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".m4a")
                || name.endsWith(".ogg") || name.endsWith(".aac") || name.endsWith(".flac");
        if (!validExtension) throw new IllegalArgumentException("Chỉ nhận MP3, WAV, M4A, OGG, AAC hoặc FLAC.");
    }

    private Genre findMatchingGenre(String label) {
        return genreRepository.findAll().stream()
                .filter(genre -> normalize(genre.getName()).equals(normalize(label)))
                .findFirst().orElse(null);
    }

    private Map<String, Object> toMap(MusicAnalysisHistory item, boolean cached) {
        Genre genre = item.getMatchedGenre();
        return Map.of("id", item.getId(), "fileName", item.getFileName(), "label", item.getDetectedLabel(),
                "confidence", item.getConfidence() == null ? 0d : item.getConfidence(), "cached", cached,
                "createdAt", item.getCreatedAt(), "genre", genre == null ? Map.of() : Map.of("id", genre.getId(), "name", genre.getName()));
    }

    private String sha256(MultipartFile file) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(file.getBytes());
            StringBuilder value = new StringBuilder();
            for (byte item : digest) value.append(String.format("%02x", item));
            return value.toString();
        } catch (Exception error) { throw new IllegalStateException("Không thể đọc file nhạc."); }
    }

    private String safeFileName(String name) { return name == null || name.isBlank() ? "reference-audio" : name; }
    private String normalize(String value) { return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""); }
}
