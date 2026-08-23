package com.fpoly.webmusicai.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.nio.file.StandardCopyOption;
import java.net.URI;
import org.springframework.web.multipart.MultipartFile;
import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.fpoly.webmusicai.entity.Favorite;
import com.fpoly.webmusicai.entity.Song;
import com.fpoly.webmusicai.entity.LikeCount;
import com.fpoly.webmusicai.entity.SongComment;
import com.fpoly.webmusicai.entity.SongListenHistory;
import com.fpoly.webmusicai.entity.SongTag;
import com.fpoly.webmusicai.entity.Tag;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.entity.Genre;
import com.fpoly.webmusicai.dto.GenerateSongRequest;
import com.fpoly.webmusicai.repository.FavoriteRepository;
import com.fpoly.webmusicai.repository.SongCommentRepository;
import com.fpoly.webmusicai.repository.SongRepository;
import com.fpoly.webmusicai.repository.SongListenHistoryRepository;
import com.fpoly.webmusicai.repository.SongTagRepository;
import com.fpoly.webmusicai.repository.TagRepository;
import com.fpoly.webmusicai.repository.UserRepository;
import com.fpoly.webmusicai.repository.GenreRepository;
import com.fpoly.webmusicai.repository.PlaylistSongRepository;
import com.fpoly.webmusicai.repository.AlbumSongRepository;
import com.fpoly.webmusicai.service.MusicJobService;
import com.fpoly.webmusicai.service.MusicGeneratorService;
import com.fpoly.webmusicai.service.SongGenerationService;
import com.fpoly.webmusicai.service.SongGenerationTicket;
import com.fpoly.webmusicai.service.SongCancellationResult;
import com.fpoly.webmusicai.service.SongNotificationService;
import com.fpoly.webmusicai.service.AudioStorageService;
import com.fpoly.webmusicai.service.TierAccessService;
import com.fpoly.webmusicai.exception.TierRestrictedException;
import com.fpoly.webmusicai.service.SpamProtectionService;
import com.fpoly.webmusicai.service.music.GenerationSpec;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.core.task.TaskRejectedException;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

@Slf4j
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
    SongListenHistoryRepository songListenHistoryRepo;

    @Autowired
    UserRepository userRepo;

    @Autowired
    GenreRepository genreRepo;

    @Autowired
    PlaylistSongRepository playlistSongRepo;

    @Autowired
    AlbumSongRepository albumSongRepo;

    @Autowired
    MusicJobService musicJobService;

    @Autowired
    MusicGeneratorService musicGeneratorService;

    @Autowired
    SongGenerationService songGenerationService;

    @Autowired
    AudioStorageService audioStorageService;

    @Autowired
    SongNotificationService songNotificationService;

    @Autowired
    SongTagRepository songTagRepo;

    @Autowired
    TagRepository tagRepo;

    @Autowired
    SpamProtectionService spamProtectionService;

    @Autowired
    TierAccessService tierAccessService;

    @GetMapping("/ai-status")
    public ResponseEntity<?> getAiStatus() {
        Map<String, Object> providers = musicGeneratorService.getCapabilities();
        boolean online = providers.values().stream()
                .filter(Map.class::isInstance).map(Map.class::cast)
                .anyMatch(item -> Boolean.TRUE.equals(item.get("available")));
        return ResponseEntity.status(online ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of(
                        "online", online,
                        "providers", providers,
                        "message", online
                                ? "Hệ thống AI sẵn sàng"
                                : "Hệ thống AI đang ngoại tuyến. Vui lòng bật lại máy chủ Colab."));
    }

    @GetMapping("/public")
    public ResponseEntity<?> getPublicSongs() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) ? auth.getName() : null;

        List<Song> songs = songRepo.findByIsPublicTrueOrderByCreatedAtDesc();
        if (songs.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<Integer> songIds = songs.stream().map(Song::getId).toList();

        Map<Integer, Long> likeCounts = favoriteRepo.countLikesBySongIds(songIds).stream()
                .collect(Collectors.toMap(LikeCount::getSongId, LikeCount::getLikeCount));

        Set<Integer> likedByCurrentUser = (username != null)
                ? favoriteRepo.findLikedSongIdsByUser(username, songIds)
                : Collections.emptySet();

        List<Map<String, Object>> result = songs.stream().map(song -> {
            Map<String, Object> songMap = song.toMap(); 
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

    @GetMapping("/by-tag")
    public ResponseEntity<?> getSongsByTag(@RequestParam Integer tagId) {
        List<Integer> songIds = songTagRepo.findSongIdsByTagId(tagId);
        if (songIds.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }
        return ResponseEntity.ok(songRepo.findAllById(songIds).stream()
                .filter(song -> Boolean.TRUE.equals(song.getIsPublic()))
                .map(Song::toMap)
                .toList());
    }

    @GetMapping("/my-favorites")
    public ResponseEntity<?> getMyFavoriteSongs() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Vui lòng đăng nhập để xem danh sách yêu thích."));
        }
        String username = auth.getName();
        
        List<Song> favoriteSongs = favoriteRepo.findByUserUsernameOrderByCreatedAtDesc(username)
                .stream()
                .map(Favorite::getSong)
                .toList();

        if (favoriteSongs.isEmpty()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        List<Integer> songIds = favoriteSongs.stream().map(Song::getId).toList();

        Map<Integer, Long> likeCounts = favoriteRepo.countLikesBySongIds(songIds).stream()
                .collect(Collectors.toMap(LikeCount::getSongId, LikeCount::getLikeCount));

        List<Map<String, Object>> result = favoriteSongs.stream().map(song -> {
            Map<String, Object> songMap = song.toMap();
            songMap.put("total_likes", likeCounts.getOrDefault(song.getId(), 0L));
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

    @GetMapping("/my-songs")
    public ResponseEntity<?> getMySongs(@RequestParam(defaultValue = "0") int page,
                                        @RequestParam(defaultValue = "10") int size) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Vui lòng đăng nhập để xem nhạc của bạn."));
        }
        String username = auth.getName();
        Pageable pageable = PageRequest.of(page, size);
        Page<Song> songPage = songRepo.findByUserUsernameOrderByCreatedAtDesc(username, pageable);

        Page<Map<String, Object>> mapPage = songPage.map(Song::toMap);
        return ResponseEntity.ok(mapPage);
    }

    @PostMapping("/generate")
    public ResponseEntity<?> generateMusic(@Valid @RequestBody GenerateSongRequest requestData) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || "anonymousUser".equals(authentication.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Vui lòng đăng nhập để tạo nhạc."));
        }
        String finalUsername = authentication.getName();

        try {
            User requestingUser = userRepo.findById(finalUsername)
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản."));
            tierAccessService.requireGenerationAccess(requestingUser, requestData.getProvider(),
                    requestData.getDurationSeconds(), requestData.getVocalMode());
            Genre selectedGenre = null;
            String effectivePrompt = requestData.getPrompt().trim();
            if (requestData.getGenreId() != null) {
                selectedGenre = genreRepo.findById(requestData.getGenreId())
                        .orElseThrow(() -> new IllegalArgumentException("Thể loại đã chọn không tồn tại"));
                effectivePrompt = "Thể loại " + selectedGenre.getName() + ". " + effectivePrompt;
            }
            GenerationSpec spec = generationSpec(requestData);
            musicGeneratorService.validateCapabilities(spec);
            if (!musicGeneratorService.isAvailable(spec.provider())) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("message",
                                "Hệ thống AI đang ngoại tuyến. Yêu cầu chưa được tạo và token không bị trừ."));
            }
            SongGenerationTicket ticket = songGenerationService.createPendingSong(
                    finalUsername, effectivePrompt, requestData.getTitle(), spec);
            songGenerationService.assignGenre(ticket.songId(), selectedGenre);
            try {
                musicJobService.submit(
                        ticket.songId(),
                        effectivePrompt,
                        requestData.getTitle(),
                        spec);
            } catch (TaskRejectedException e) {
                songGenerationService.failAndRefund(ticket.songId(), "Hàng đợi AI đang đầy");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("message", "Hệ thống AI đang bận, token đã được hoàn lại"));
            }

            return ResponseEntity.ok(Map.of(
                    "message", "Đã nhận yêu cầu! Lõi AI cá nhân đang xử lý...",
                    "songId", ticket.songId(),
                    "remaining_tokens", ticket.remainingTokens()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (TierRestrictedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}/status")
    public ResponseEntity<?> getSongStatus(@PathVariable Integer id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) ? auth.getName() : null;
        String finalUsername = currentUser;

        return songRepo.findById(id).map(song -> {
            boolean isOwner = finalUsername != null && song.getUser().getUsername().equals(finalUsername);
            boolean isPublic = Boolean.TRUE.equals(song.getIsPublic());
            boolean isAdmin = auth != null && auth.getAuthorities().stream()
                    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            if (!isOwner && !isAdmin && !isPublic) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Bạn không có quyền xem bài nhạc riêng tư này!");
            }

            Map<String, Object> result = new HashMap<>();
            result.put("id", song.getId());
            result.put("title", song.getTitle());
            result.put("prompt", song.getPrompt());
            result.put("status", song.getStatus());
            result.put("created_at", song.getCreatedAt());

            // Bổ sung thông tin về lượt thích và người tạo
            long totalLikes = favoriteRepo.countBySongId(id);
            boolean likedByMe = currentUser != null && favoriteRepo.existsByUserUsernameAndSongId(currentUser, id);
            result.put("total_likes", totalLikes);
            result.put("liked_by_me", likedByMe);
            result.put("username", song.getUser().getUsername());
            result.put("fullname", song.getUser().getFullname());
            result.put("coverUrl", song.getCoverUrl());
            result.put("listenCount", song.getListenCount() != null ? song.getListenCount() : 0);

            switch (song.getStatus()) {
                case "COMPLETED" -> {
                    result.put("message", "Nhạc đã sẵn sàng!");
                    result.put("audioUrl", song.getAudioUrl());
                    result.put("is_public", song.getIsPublic());
                }
                case "PENDING" -> result.put("message", "Đang xử lý, vui lòng chờ...");
                case "FAILED" -> result.put("message", "Gen nhạc thất bại, vui lòng thử lại.");
                case "CANCELLED" -> result.put("message", "Đã dừng tạo nhạc và hoàn lại token.");
                default -> result.put("message", "Trạng thái không xác định");
            }

            return ResponseEntity.ok(result);
        }).orElse(ResponseEntity.notFound().build());
    }

    @Transactional
    @PostMapping("/{id}/play")
    public ResponseEntity<?> incrementListenCount(@PathVariable Integer id) {
        return songRepo.findById(id).map(song -> {
            int currentCount = song.getListenCount() != null ? song.getListenCount() : 0;
            song.setListenCount(currentCount + 1);
            songRepo.save(song);

            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            User listener = null;
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) {
                listener = userRepo.findById(auth.getName()).orElse(null);
            }
            SongListenHistory history = new SongListenHistory();
            history.setSong(song);
            history.setUser(listener);
            songListenHistoryRepo.save(history);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("listenCount", song.getListenCount());
            return ResponseEntity.ok(response);
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/setting")
    public ResponseEntity<?> renameSong(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Vui lòng đăng nhập!");
        }
        String username = auth.getName();

        return songRepo.findById(id).map(song -> {
            boolean isOwner = song.getUser() != null && song.getUser().getUsername().equals(username);
            boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

            if (!isOwner && !isAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Bạn không có quyền chỉnh sửa bài viết này!");
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

            if (body.containsKey("prompt")) {
                String newPrompt = (String) body.get("prompt");
                String oldPrompt = song.getPrompt();
                song.setPrompt(newPrompt != null ? newPrompt.trim() : "");
                changes.put("old_prompt", oldPrompt);
                changes.put("new_prompt", song.getPrompt());
            }

            if (body.containsKey("is_public") || body.containsKey("isPublic")) {
                if (!"COMPLETED".equals(song.getStatus())) {
                    return ResponseEntity.badRequest().body("Chỉ có thể public bài nhạc đã hoàn thành!");
                }
                Object val = body.containsKey("is_public") ? body.get("is_public") : body.get("isPublic");
                boolean newIsPublic = Boolean.parseBoolean(val.toString());
                song.setIsPublic(newIsPublic);
                changes.put("is_public", newIsPublic);
            }

            if (body.containsKey("cover_url") || body.containsKey("coverUrl")) {
                Object val = body.containsKey("cover_url") ? body.get("cover_url") : body.get("coverUrl");
                song.setCoverUrl(val != null ? val.toString().trim() : null);
                changes.put("cover_url", song.getCoverUrl());
            }

            songRepo.save(song);
            if (Boolean.TRUE.equals(song.getIsPublic())) {
                songNotificationService.notifyFollowersForPublicSong(song.getId());
            }
            log.info("User {} cập nhật bài {}: {}", username, id, changes);

            Map<String, Object> response = new HashMap<>();
            response.put("message", "Cập nhật thành công!");
            response.put("id", song.getId());
            response.putAll(changes);

            return ResponseEntity.ok(response);

        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/visibility")
    public ResponseEntity<?> toggleVisibility(@PathVariable Integer id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Vui lòng đăng nhập!"));
        }
        String username = auth.getName();

        return songRepo.findById(id).map(song -> {
            if (!song.getUser().getUsername().equals(username)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Bạn không có quyền đổi trạng thái bài hát này!"));
            }
            if (!"COMPLETED".equals(song.getStatus())) {
                return ResponseEntity.badRequest().body(Map.of("message", "Chỉ có thể đổi quyền riêng tư của bài hát đã hoàn thành!"));
            }

            boolean currentPublic = song.getIsPublic() != null && song.getIsPublic();
            song.setIsPublic(!currentPublic);
            songRepo.save(song);
            if (Boolean.TRUE.equals(song.getIsPublic())) {
                songNotificationService.notifyFollowersForPublicSong(song.getId());
            }

            log.info("User {} đổi trạng thái công khai bài #{}: {}", username, id, song.getIsPublic());
            return ResponseEntity.ok(Map.of("id", song.getId(), "isPublic", song.getIsPublic(), "message", song.getIsPublic() ? "Đã công khai bài hát" : "Đã chuyển thành riêng tư"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelGeneration(@PathVariable Integer id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        
        String finalUsername = (username != null && !"anonymousUser".equals(username)) ? username : null;
        try {
            SongCancellationResult result =
                    songGenerationService.cancelAndRefund(id, finalUsername, isAdmin);
            boolean workerInterrupted = musicJobService.cancel(id);
            return ResponseEntity.ok(Map.of(
                    "message", "Đã dừng tạo nhạc và hoàn lại 1 token",
                    "songId", result.songId(),
                    "status", result.status(),
                    "remaining_tokens", result.remainingTokens(),
                    "worker_interrupted", workerInterrupted));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("message", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("message", e.getMessage()));
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteSong(@PathVariable Integer id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) ? auth.getName() : null;
        String finalUsername = username;

        if (finalUsername == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Vui lòng đăng nhập!"));
        }

        boolean isAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        return songRepo.findById(id).map(song -> {
            boolean isOwner = song.getUser() != null && song.getUser().getUsername().equals(finalUsername);
            if (!isOwner && !isAdmin) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Bạn không có quyền xóa bài nhạc này!"));
            }

            favoriteRepo.deleteBySongId(id);
            commentRepo.deleteBySongId(id);
            playlistSongRepo.deleteBySongId(id);
            albumSongRepo.deleteBySongId(id);
            songTagRepo.deleteBySongId(id);
            songListenHistoryRepo.deleteBySongId(id);
            songRepo.deleteSongGenresBySongId(id);
            songRepo.detachRemixesFromParent(id);
            songRepo.delete(song);

            log.info("User {} xóa bài nhạc #{}", username, id);
            return ResponseEntity.ok(Map.of("message", "Đã xóa bài nhạc thành công!", "id", id));
        }).orElse(ResponseEntity.notFound().build());
    }

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
                songNotificationService.notifyNewLike(song, user);
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

    @GetMapping("/{id}/comments")
    public ResponseEntity<?> getComments(@PathVariable Integer id,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "10") int size) {
        if (!songRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<SongComment> topLevelCommentsPage = commentRepo.findBySongIdAndParentIdIsNullOrderByCreatedAtDesc(id, pageable);

        List<SongComment> topLevelComments = topLevelCommentsPage.getContent();
        if (!topLevelComments.isEmpty()) {
            List<Integer> topLevelIds = topLevelComments.stream().map(SongComment::getId).toList();
            List<SongComment> replies = commentRepo.findByParentIdInOrderByCreatedAtAsc(topLevelIds);

            Map<Integer, List<SongComment>> repliesMap = replies.stream()
                    .collect(Collectors.groupingBy(SongComment::getParentId));

            topLevelComments.forEach(comment -> comment.setReplies(repliesMap.getOrDefault(comment.getId(), Collections.emptyList())));
        }

        Page<Map<String, Object>> resultPage = topLevelCommentsPage.map(SongComment::toMap);

        // Tạo một Map tùy chỉnh để trả về, thay vì trả về đối tượng Page trực tiếp.
        // Cấu trúc này ổn định và tương thích với frontend hiện tại.
        Map<String, Object> response = new HashMap<>();
        response.put("content", resultPage.getContent());
        response.put("number", resultPage.getNumber());
        response.put("totalPages", resultPage.getTotalPages());
        response.put("totalElements", resultPage.getTotalElements());
        response.put("size", resultPage.getSize());
        response.put("last", resultPage.isLast());
        response.put("first", resultPage.isFirst());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/{id}/comments")
    public ResponseEntity<?> createComment(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        String content = (String) body.get("content");
        if (content == null || content.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Nội dung bình luận không được để trống!"));
        }

        long waitSeconds = spamProtectionService.remainingSeconds(username, "comment", 3000);
        if (waitSeconds > 0) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("message", "Bạn thao tác quá nhanh. Vui lòng chờ " + waitSeconds + " giây rồi bình luận tiếp."));
        }

        return songRepo.findById(id).map(song -> {
            // Kiểm tra chống gửi lặp lại bình luận cùng nội dung trong thời gian ngắn (3 giây)
            Page<SongComment> recentComments = commentRepo.findBySongIdAndParentIdIsNullOrderByCreatedAtDesc(id, PageRequest.of(0, 1));
            if (!recentComments.isEmpty()) {
                SongComment latest = recentComments.getContent().get(0);
                if (latest.getUser() != null 
                        && username.equals(latest.getUser().getUsername())
                        && content.trim().equalsIgnoreCase(latest.getContent().trim())
                        && latest.getCreatedAt() != null
                        && (System.currentTimeMillis() - latest.getCreatedAt().getTime()) < 3000) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", "Vui lòng không gửi lại bình luận trùng lặp!"));
                }
            }

            User user = userRepo.findById(username).orElseThrow();
            SongComment comment = new SongComment();
            comment.setSong(song);
            comment.setUser(user);
            comment.setContent(content.trim());

            // Xử lý bình luận trả lời
            if (body.containsKey("parent_id") && body.get("parent_id") != null) {
                try {
                    Integer parentId = Integer.parseInt(String.valueOf(body.get("parent_id")));
                    if (commentRepo.existsById(parentId)) {
                        comment.setParentId(parentId);
                    }
                } catch (NumberFormatException e) {
                    // Bỏ qua nếu parent_id không hợp lệ
                }
            }

            commentRepo.save(comment);
            log.info("User {} bình luận bài #{}", username, id);
            
            songNotificationService.notifyNewComment(song, user, comment);

            return ResponseEntity.status(HttpStatus.CREATED).body(comment.toMap());
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

    @PostMapping("/{id}/remix")
    public ResponseEntity<?> remixSong(@PathVariable Integer id, @RequestBody Map<String, String> body) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();

        Song original = songRepo.findById(id).orElse(null);
        if (original == null) {
            return ResponseEntity.notFound().build();
        }

        boolean isOwner = original.getUser().getUsername().equals(username);
        if (!Boolean.TRUE.equals(original.getIsPublic()) && !isOwner) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Không thể remix bài nhạc riêng tư!"));
        }
        if (!"COMPLETED".equals(original.getStatus())) {
            return ResponseEntity.badRequest().body(Map.of("message", "Chỉ remix được bài đã hoàn thành!"));
        }

        String remixPrompt = body.getOrDefault("prompt", "Remix lại theo phong cách mới từ: " + original.getPrompt());
        String customTitle = body.get("title");
        if (remixPrompt == null || remixPrompt.isBlank() || remixPrompt.length() > 1000) {
            return ResponseEntity.badRequest().body(Map.of("message", "Prompt remix phải có từ 1 đến 1000 ký tự"));
        }
        if (customTitle != null && customTitle.length() > 255) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tên bản remix không được vượt quá 255 ký tự"));
        }
        boolean instrumental = Boolean.parseBoolean(body.getOrDefault("instrumental", "true"));
        GenerationSpec spec = new GenerationSpec(body.getOrDefault("provider", "audiocraft"), remixPrompt.trim(), instrumental,
                body.get("lyrics"), body.getOrDefault("vocalMode", instrumental ? "instrumental" : "own-lyrics"),
                body.getOrDefault("vocalLanguage", "Tiếng Việt"), body.getOrDefault("vocalGender", "auto"),
                parseDurationSeconds(body.get("durationSeconds")));

        try {
            User requestingUser = userRepo.findById(username)
                    .orElseThrow(() -> new IllegalStateException("Không tìm thấy tài khoản."));
            tierAccessService.requireGenerationAccess(requestingUser, spec.provider(), spec.durationSeconds(), spec.vocalMode());
            musicGeneratorService.validateCapabilities(spec);
            if (!musicGeneratorService.isAvailable(spec.provider())) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("message",
                                "Hệ thống AI đang ngoại tuyến. Yêu cầu chưa được tạo và token không bị trừ."));
            }
            SongGenerationTicket ticket = songGenerationService.createPendingRemix(
                    username, original, remixPrompt, customTitle, spec);
            try {
                musicJobService.submit(
                        ticket.songId(), remixPrompt.trim(), customTitle, spec);
            } catch (TaskRejectedException e) {
                songGenerationService.failAndRefund(ticket.songId(), "Hàng đợi AI đang đầy");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("message", "Hệ thống AI đang bận, token đã được hoàn lại"));
            }
            return ResponseEntity.ok(Map.of(
                    "message", "Đã nhận yêu cầu remix! AI đang xử lý...",
                    "songId", ticket.songId(),
                    "parent_id", ticket.parentId(),
                    "remaining_tokens", ticket.remainingTokens()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
        } catch (TierRestrictedException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", e.getMessage()));
        }
    }

    @GetMapping("/{id}/remixes")
    public ResponseEntity<?> getRemixes(@PathVariable Integer id) {
        if (!songRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        List<Song> remixes = songRepo.findByParentIdOrderByCreatedAtDesc(id);
        return ResponseEntity.ok(remixes);
    }

    // ==========================================
    // 9. DOWNLOAD BÀI HÁT
    // ==========================================
    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadSong(@PathVariable Integer id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUser = (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) ? auth.getName() : null;

        Optional<Song> songOpt = songRepo.findById(id);
        if (songOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Song song = songOpt.get();

        boolean isOwner = currentUser != null && song.getUser() != null && song.getUser().getUsername().equals(currentUser);
        boolean isPublic = song.getIsPublic() != null && song.getIsPublic();
        boolean isAdmin = auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isOwner && !isAdmin && !isPublic) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }

        if (!"COMPLETED".equals(song.getStatus()) || song.getAudioUrl() == null || song.getAudioUrl().isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        try {
            if (song.getAudioUrl().startsWith("/media/audio/")) {
                String storedFilename = song.getAudioUrl().substring("/media/audio/".length());
                Resource storedAudio = audioStorageService.load(storedFilename);
                String filename = sanitizeAudioFilename(song.getTitle(), storedFilename);
                MediaType mediaType = storedFilename.toLowerCase(Locale.ROOT).endsWith(".mp3")
                        ? MediaType.parseMediaType("audio/mpeg")
                        : MediaType.parseMediaType("audio/wav");
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                        .contentType(mediaType)
                        .contentLength(storedAudio.contentLength())
                        .body(storedAudio);
            }

            if (song.getAudioUrl().startsWith("http://") || song.getAudioUrl().startsWith("https://")) {
                return ResponseEntity.status(HttpStatus.FOUND)
                        .location(URI.create(song.getAudioUrl()))
                        .build();
            }

            // Hỗ trợ ngược dữ liệu cũ đang lưu dạng data:audio/...;base64
            String[] parts = song.getAudioUrl().split(",");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Định dạng audioUrl không hợp lệ.");
            }
            String base64Data = parts[1];
            byte[] audioBytes = Base64.getDecoder().decode(base64Data);

            ByteArrayResource resource = new ByteArrayResource(audioBytes);

            // Làm sạch tên file
            String originalTitle = song.getTitle() != null ? song.getTitle() : "untitled";
            String filename = originalTitle.replaceAll("[^a-zA-Z0-9.\\-_]+", "_") + ".wav";

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                    .contentType(MediaType.parseMediaType("audio/wav"))
                    .contentLength(audioBytes.length)
                    .body(resource);

        } catch (Exception e) {
            log.error("Lỗi khi giải mã hoặc tạo file download cho bài hát ID {}: {}", id, e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private String sanitizeAudioFilename(String title, String storedFilename) {
        String extension = storedFilename.contains(".")
                ? storedFilename.substring(storedFilename.lastIndexOf('.'))
                : ".wav";
        String safeTitle = title != null ? title : "untitled";
        return safeTitle.replaceAll("[^a-zA-Z0-9.\\-_]+", "_") + extension;
    }

    @PostMapping("/{id}/cover")
    public ResponseEntity<?> uploadCover(@PathVariable Integer id,
                                         @RequestParam("file") MultipartFile file) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getName())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Vui lòng đăng nhập!"));
        }
        String username = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        Optional<Song> songOpt = songRepo.findById(id);
        if (songOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Song song = songOpt.get();
        boolean isOwner = song.getUser() != null && song.getUser().getUsername().equals(username);

        if (!isOwner && !isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Bạn không có quyền đổi ảnh bìa cho bài hát này!"));
        }

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Vui lòng chọn file ảnh hợp lệ!"));
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return ResponseEntity.badRequest().body(Map.of("message", "Chỉ chấp nhận file định dạng hình ảnh (.jpg, .png, .webp, .gif)!"));
        }

        try {
            String originalFilename = file.getOriginalFilename();
            String ext = ".png";
            if (originalFilename != null && originalFilename.contains(".")) {
                ext = originalFilename.substring(originalFilename.lastIndexOf("."));
            }

            String newFileName = "cover-" + id + "-" + System.currentTimeMillis() + ext;

            Path uploadDir = Paths.get("src/main/resources/static/images/covers");
            if (!Files.exists(uploadDir)) {
                Files.createDirectories(uploadDir);
            }
            Path filePath = uploadDir.resolve(newFileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

            try {
                Path targetDir = Paths.get("target/classes/static/images/covers");
                if (!Files.exists(targetDir)) {
                    Files.createDirectories(targetDir);
                }
                Files.copy(filePath, targetDir.resolve(newFileName), StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception ignored) {}

            String coverUrl = "/images/covers/" + newFileName;
            song.setCoverUrl(coverUrl);
            songRepo.save(song);

            log.info("User {} tải ảnh bìa mới cho bài hát ID {}: {}", username, id, coverUrl);
            return ResponseEntity.ok(Map.of("message", "Tải ảnh bìa thành công!", "coverUrl", coverUrl));
        } catch (Exception e) {
            log.error("Lỗi lưu file ảnh bìa: {}", e.getMessage());
            return ResponseEntity.internalServerError().body(Map.of("message", "Lỗi lưu file ảnh: " + e.getMessage()));
        }
    }

    @GetMapping("/{id}/tags")
    public ResponseEntity<?> getTags(@PathVariable Integer id) {
        if (!songRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(songTagRepo.findBySongId(id).stream()
                .map(songTag -> Map.of("id", songTag.getId(), "tagId", songTag.getTag().getId(),
                        "tag", songTag.getTag().getName()))
                .toList());
    }

    @PostMapping("/{id}/tags")
    @Transactional
    public ResponseEntity<?> setTags(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getName())) ? auth.getName() : null;
        String finalUsername = username;

        if (finalUsername == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "Vui lòng đăng nhập"));
        }
        Song song = songRepo.findById(id).orElse(null);
        if (song == null) {
            return ResponseEntity.notFound().build();
        }
        boolean admin = auth != null && auth.getAuthorities().stream().anyMatch(role -> "ROLE_ADMIN".equals(role.getAuthority()));
        if (!admin && (song.getUser() == null || !finalUsername.equals(song.getUser().getUsername()))) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Không có quyền cập nhật tag"));
        }
        Object rawTagIds = body.get("tagIds");
        if (!(rawTagIds instanceof List<?> values)) {
            return ResponseEntity.badRequest().body(Map.of("message", "tagIds không hợp lệ"));
        }
        List<Integer> tagIds = values.stream().map(value -> Integer.valueOf(value.toString())).distinct().toList();
        List<Tag> tags = tagRepo.findAllById(tagIds);
        if (tags.size() != tagIds.size()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Có tag không tồn tại"));
        }
        songTagRepo.deleteBySongId(id);
        tags.forEach(tag -> songTagRepo.save(new SongTag(null, id, tag)));
        return ResponseEntity.ok(Map.of("message", "Đã cập nhật tag"));
    }

    @GetMapping("/admin/list")
    public ResponseEntity<?> getSongsForAdmin(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "newest") String filter,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "Yêu cầu quyền Quản trị viên."));
        }

        Pageable pageable = PageRequest.of(page, size);
        Page<Song> songPage;

        LocalDateTime startDateTime = (startDate != null) ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = (endDate != null) ? endDate.plusDays(1).atStartOfDay().minusSeconds(1) : null;

        switch (filter) {
            case "top_listens":
                songPage = songRepo.findFilteredByListenCount(startDateTime, endDateTime, pageable);
                break;
            case "top_likes":
                Page<Object[]> likedPage = songRepo.findFilteredByLikes(startDateTime, endDateTime, pageable);
                Page<Map<String, Object>> result = likedPage.map(obj -> {
                    Song song = (Song) obj[0];
                    Long likeCount = (Long) obj[1];
                    Map<String, Object> songMap = song.toMap();
                    songMap.put("total_likes", likeCount);
                    return songMap;
                });
                return ResponseEntity.ok(result);
            case "trending":
                LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
                // Không có khoảng thời gian thì mặc định xem tương tác trong 7 ngày gần nhất.
                // Nếu Admin đã chọn ngày/tuần/tháng/năm, "xu hướng" tôn trọng đúng khoảng đó.
                LocalDateTime trendStartDate = startDateTime == null ? sevenDaysAgo : startDateTime;
                LocalDateTime trendEndDate = endDateTime == null ? LocalDateTime.now() : endDateTime;
                songPage = songRepo.findTrendingByRecentEngagement(trendStartDate, trendEndDate, pageable);
                break;
            case "oldest":
                pageable = PageRequest.of(page, size, Sort.by("createdAt").ascending());
                songPage = songRepo.findFiltered(startDateTime, endDateTime, pageable);
                break;
            case "newest":
            default:
                pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
                songPage = songRepo.findFiltered(startDateTime, endDateTime, pageable);
                break;
        }

        return ResponseEntity.ok(songPage.map(Song::toMap));
    }

    private GenerationSpec generationSpec(GenerateSongRequest request) {
        String provider = request.getProvider() == null || request.getProvider().isBlank()
                ? "audiocraft" : request.getProvider().trim();
        return new GenerationSpec(provider, request.getPrompt().trim(), request.isInstrumental(), request.getLyrics(),
                request.getVocalMode(), request.getVocalLanguage(), request.getVocalGender(),
                normalizeDuration(request.getDurationSeconds()));
    }

    private Integer parseDurationSeconds(String value) {
        try {
            return normalizeDuration(value == null ? null : Integer.valueOf(value));
        } catch (NumberFormatException error) {
            return 30;
        }
    }

    private Integer normalizeDuration(Integer durationSeconds) {
        if (durationSeconds == null) return 30;
        return Math.max(15, Math.min(durationSeconds, 240));
    }
}
