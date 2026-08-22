package com.fpoly.webmusicai.controller;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.fpoly.webmusicai.entity.Playlist;
import com.fpoly.webmusicai.entity.PlaylistSong;
import com.fpoly.webmusicai.entity.Song;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.PlaylistRepository;
import com.fpoly.webmusicai.repository.PlaylistSongRepository;
import com.fpoly.webmusicai.repository.SongRepository;
import com.fpoly.webmusicai.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/playlists")
public class PlaylistRestController {

    @Autowired
    private PlaylistRepository playlistRepo;

    @Autowired
    private PlaylistSongRepository playlistSongRepo;

    @Autowired
    private SongRepository songRepo;

    @Autowired
    private UserRepository userRepo;

    @PostMapping
    public ResponseEntity<?> createPlaylist(@RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        String name = (String) body.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tên playlist không được để trống!"));
        }
        if (name.trim().length() > 100) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tên playlist tối đa 100 ký tự!"));
        }

        User user = userRepo.findById(username).orElseThrow();

        Playlist playlist = new Playlist();
        playlist.setName(name.trim());
        Object visibility = body.containsKey("isPublic") ? body.get("isPublic") : body.get("is_public");
        playlist.setIsPublic(Boolean.TRUE.equals(visibility));
        playlist.setUser(user);

        playlistRepo.save(playlist);
        log.info("User {} tạo playlist: {}", username, playlist.getName());

        return ResponseEntity.ok(playlist);
    }

    @GetMapping("/public")
    public ResponseEntity<List<Map<String, Object>>> getPublicPlaylists() {
        return ResponseEntity.ok(playlistRepo.findByIsPublicTrueOrderByCreatedAtDesc().stream()
                .map(this::toPlaylistSummary).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getPlaylistById(@PathVariable Integer id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth != null ? auth.getName() : "anonymousUser";
        boolean isAdmin = auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return playlistRepo.findById(id).map(playlist -> {
            boolean isOwner = playlist.getUser() != null
                    && playlist.getUser().getUsername().equals(username);
            if (!Boolean.TRUE.equals(playlist.getIsPublic()) && !isOwner && !isAdmin) {
                return ResponseEntity.status(403)
                        .body(Map.of("message", "Playlist này đang ở chế độ riêng tư"));
            }
            List<Map<String, Object>> songs = playlistSongRepo.findByPlaylistIdOrderBySortOrderAsc(id).stream()
                    .map(PlaylistSong::getSong)
                    .filter(song -> isOwner || isAdmin || Boolean.TRUE.equals(song.getIsPublic()))
                    .map(Song::toMap)
                    .toList();
            return ResponseEntity.ok(Map.of("playlist", toPlaylistSummary(playlist), "songs", songs));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/my")
    public ResponseEntity<?> getMyPlaylists(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        Pageable pageable = PageRequest.of(page, size);
        Page<Playlist> playlists = playlistRepo.findByUserUsernameOrderByCreatedAtDesc(username, pageable);
        return ResponseEntity.ok(playlists);
    }

    @GetMapping("/user/{username}")
    public ResponseEntity<List<Map<String, Object>>> getPlaylistsByUser(@PathVariable String username) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String currentUsername = auth != null ? auth.getName() : "anonymousUser";
        boolean canSeePrivate = username.equals(currentUsername)
                || (auth != null && auth.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())));
        List<Playlist> playlists = canSeePrivate
                ? playlistRepo.findByUserUsernameOrderByCreatedAtDesc(username)
                : playlistRepo.findByUserUsernameAndIsPublicTrueOrderByCreatedAtDesc(username);
        return ResponseEntity.ok(playlists.stream().map(this::toPlaylistSummary).toList());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updatePlaylist(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        return playlistRepo.findById(id).map(playlist -> {
            if (!playlist.getUser().getUsername().equals(username)) {
                return ResponseEntity.status(403).body(Map.of("message", "Bạn không có quyền sửa playlist này!"));
            }

            if (body.containsKey("name")) {
                String name = (String) body.get("name");
                if (name == null || name.isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Tên playlist không được để trống!"));
                }
                if (name.trim().length() > 100) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Tên playlist tối đa 100 ký tự!"));
                }
                playlist.setName(name.trim());
            }
            if (body.containsKey("is_public") || body.containsKey("isPublic")) {
                Object visibility = body.containsKey("isPublic") ? body.get("isPublic") : body.get("is_public");
                playlist.setIsPublic(Boolean.TRUE.equals(visibility));
            }

            playlistRepo.save(playlist);
            log.info("Đã cập nhật playlist #{}", id);

            return ResponseEntity.ok(playlist);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deletePlaylist(@PathVariable Integer id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        return playlistRepo.findById(id).map(playlist -> {
            if (!playlist.getUser().getUsername().equals(username)) {
                return ResponseEntity.status(403).body(Map.of("message", "Bạn không có quyền xóa playlist này!"));
            }

            playlistRepo.deleteById(id);
            log.info("Đã xóa playlist #{}", id);
            return ResponseEntity.ok(Map.of("message", "Đã xóa playlist #" + id));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{playlistId}/songs/{songId}")
    public ResponseEntity<?> addSongToPlaylist(@PathVariable Integer playlistId, @PathVariable Integer songId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        return playlistRepo.findById(playlistId).map(playlist -> {
            if (!playlist.getUser().getUsername().equals(username)) {
                return ResponseEntity.status(403).body(Map.of("message", "Bạn không có quyền thêm nhạc vào playlist này!"));
            }

            Song song = songRepo.findById(songId).orElse(null);
            if (song == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Bài nhạc không tồn tại!"));
            }
            boolean canUseSong = Boolean.TRUE.equals(song.getIsPublic())
                    || (song.getUser() != null && username.equals(song.getUser().getUsername()));
            if (!canUseSong) {
                return ResponseEntity.status(403)
                        .body(Map.of("message", "Không thể thêm bài hát riêng tư của người khác"));
            }

            if (playlistSongRepo.existsByPlaylistIdAndSongId(playlistId, songId)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Bài nhạc đã có trong playlist!"));
            }

            int nextOrder = playlistSongRepo.findByPlaylistIdOrderBySortOrderAsc(playlistId).size() + 1;

            PlaylistSong ps = new PlaylistSong();
            ps.setPlaylist(playlist);
            ps.setSong(song);
            ps.setSortOrder(nextOrder);
            playlistSongRepo.save(ps);

            log.info("Thêm bài #{} vào playlist #{}", songId, playlistId);
            return ResponseEntity.ok(Map.of("message", "Đã thêm bài hát vào playlist", "sort_order", nextOrder));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{playlistId}/songs/{songId}")
    @Transactional
    public ResponseEntity<?> removeSongFromPlaylist(@PathVariable Integer playlistId, @PathVariable Integer songId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        return playlistRepo.findById(playlistId).map(playlist -> {
            if (!playlist.getUser().getUsername().equals(username)) {
                return ResponseEntity.status(403).body(Map.of("message", "Bạn không có quyền xóa nhạc khỏi playlist này!"));
            }

            if (!playlistSongRepo.existsByPlaylistIdAndSongId(playlistId, songId)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Bài nhạc không có trong playlist!"));
            }

            playlistSongRepo.deleteByPlaylistIdAndSongId(playlistId, songId);
            log.info("Xóa bài #{} khỏi playlist #{}", songId, playlistId);
            return ResponseEntity.ok(Map.of("message", "Đã xóa bài hát khỏi playlist"));
        }).orElse(ResponseEntity.notFound().build());
    }

    private Map<String, Object> toPlaylistSummary(Playlist playlist) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", playlist.getId());
        result.put("type", "PLAYLIST");
        result.put("name", playlist.getName());
        result.put("title", playlist.getName());
        result.put("isPublic", Boolean.TRUE.equals(playlist.getIsPublic()));
        result.put("createdAt", playlist.getCreatedAt());
        result.put("songCount", playlistSongRepo.countByPlaylistId(playlist.getId()));
        if (playlist.getUser() != null) {
            result.put("username", playlist.getUser().getUsername());
            result.put("authorName", playlist.getUser().getFullname());
            result.put("authorPhoto", playlist.getUser().getPhoto());
        }
        return result;
    }
}
