package com.fpoly.webmusicai.service;

import com.fpoly.webmusicai.entity.Genre;
import com.fpoly.webmusicai.entity.Song;
import com.fpoly.webmusicai.entity.SongGenre;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.GenreRepository;
import com.fpoly.webmusicai.repository.SongGenreRepository;
import com.fpoly.webmusicai.repository.SongRepository;
import com.fpoly.webmusicai.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class SongUploadService {

    private static final Logger logger = LoggerFactory.getLogger(SongUploadService.class);

    @Autowired private SongRepository songRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private GenreRepository genreRepository;
    @Autowired private SongGenreRepository songGenreRepository;
    @Autowired private MusicAnalysisService musicAnalysisService;

    @Value("${audio.storage.location:./uploads/audio}")
    private String storageLocation;

    @Transactional
    public Song processAndSaveUpload(MultipartFile file, String title, String username) throws IOException {
        // 1. Lưu file vào server
        Path uploadPath = Paths.get(storageLocation);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String uniqueFilename = UUID.randomUUID() + extension;
        Path filePath = uploadPath.resolve(uniqueFilename);
        Files.copy(file.getInputStream(), filePath);
        String audioUrl = "/uploads/audio/" + uniqueFilename;
        logger.info("Đã lưu file nhạc vào: {}", filePath);

        // 2. Tạo bản ghi Song trong DB
        User user = userRepository.findById(username)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy người dùng: " + username));

        Song newSong = new Song();
        newSong.setTitle(title);
        newSong.setUser(user);
        newSong.setAudioUrl(audioUrl);
        newSong.setStatus("COMPLETED");
        newSong.setIsPublic(false);
        newSong.setIsRemix(false);
        Song savedSong = songRepository.save(newSong);

        // 3. Gọi API phân tích thể loại và cập nhật
        try {
            String detectedGenreName = musicAnalysisService.analyzeGenre(filePath);

            if (detectedGenreName != null && !detectedGenreName.isBlank()) {
                String formattedGenreName = detectedGenreName.substring(0, 1).toUpperCase() + detectedGenreName.substring(1).toLowerCase();

                Genre genre = genreRepository.findByNameIgnoreCase(formattedGenreName)
                        .orElseGet(() -> genreRepository.save(new Genre(formattedGenreName)));

                logger.info("Đã gán thể loại '{}' cho bài hát ID {}", formattedGenreName, savedSong.getId());
                // Thêm genre vừa tạo vào đối tượng song để trả về cho client
                // mà không cần truy vấn lại DB
                if (savedSong.getGenres() != null) savedSong.getGenres().add(genre);
            }
        } catch (Exception e) {
            logger.error("Lỗi khi phân tích hoặc gán thể loại cho bài hát ID {}. Bài hát vẫn được tải lên.", savedSong.getId(), e);
        }

        return savedSong;
    }
}