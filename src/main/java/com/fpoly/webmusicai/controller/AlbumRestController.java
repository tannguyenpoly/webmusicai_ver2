package com.fpoly.webmusicai.controller;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import com.fpoly.webmusicai.entity.Album;
import com.fpoly.webmusicai.entity.AlbumSong;
import com.fpoly.webmusicai.entity.Song;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.AlbumRepository;
import com.fpoly.webmusicai.repository.AlbumSongRepository;
import com.fpoly.webmusicai.repository.SongRepository;
import com.fpoly.webmusicai.repository.UserRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/albums")
public class AlbumRestController {

    @Autowired
    private AlbumRepository albumRepo;

    @Autowired
    private AlbumSongRepository albumSongRepo;

    @Autowired
    private SongRepository songRepo;

    @Autowired
    private UserRepository userRepo;

    @PostMapping
    public ResponseEntity<?> createAlbum(@RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        String title = (String) body.get("title");
        if (title == null || title.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "Tên album không được để trống!"));
        }

        User user = userRepo.findById(username).orElseThrow();

        Album album = new Album();
        album.setTitle(title.trim());
        album.setDescription((String) body.get("description"));
        album.setCoverUrl((String) body.get("cover_url"));
        Object visibility = body.containsKey("isPublic") ? body.get("isPublic") : body.get("is_public");
        album.setIsPublic(Boolean.TRUE.equals(visibility));
        album.setUser(user);

        albumRepo.save(album);
        log.info("User {} tạo album: {}", username, album.getTitle());

        return ResponseEntity.ok(album);
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getAllAlbums() {
        return ResponseEntity.ok(albumRepo.findByIsPublicTrueOrderByCreatedAtDesc().stream()
                .map(this::toAlbumSummary).toList());
    }

    @GetMapping("/public")
    public ResponseEntity<List<Map<String, Object>>> getPublicAlbums() {
        return ResponseEntity.ok(albumRepo.findByIsPublicTrueOrderByCreatedAtDesc().stream()
                .map(this::toAlbumSummary).toList());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getAlbumById(@PathVariable Integer id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = isAuthenticated(auth) ? auth.getName() : "anonymousUser";
        boolean isAdmin = isAdmin(auth);
        return albumRepo.findById(id).map(album -> {
            boolean isOwner = album.getUser() != null && username.equals(album.getUser().getUsername());
            if (!Boolean.TRUE.equals(album.getIsPublic()) && !isOwner && !isAdmin) {
                return ResponseEntity.status(403).body(Map.of("message", "Album này đang ở chế độ riêng tư"));
            }
            List<Map<String, Object>> songs = albumSongRepo.findByAlbumIdOrderByTrackNumberAsc(id).stream()
                    .map(AlbumSong::getSong)
                    .filter(song -> isOwner || isAdmin || Boolean.TRUE.equals(song.getIsPublic()))
                    .map(Song::toMap)
                    .toList();
            return ResponseEntity.ok(Map.of("album", toAlbumSummary(album), "songs", songs));
        }).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/user/{username}")
    public ResponseEntity<?> getAlbumsByUser(@PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean canSeePrivate = isAuthenticated(auth) && (username.equals(auth.getName()) || isAdmin(auth));
        Pageable pageable = PageRequest.of(page, size);
        Page<Album> albums = canSeePrivate
                ? albumRepo.findByUserUsernameOrderByCreatedAtDesc(username, pageable)
                : albumRepo.findByUserUsernameAndIsPublicTrueOrderByCreatedAtDesc(username, pageable);
        return ResponseEntity.ok(albums);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateAlbum(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        return albumRepo.findById(id).map(album -> {
            if (!album.getUser().getUsername().equals(username) && !isAdmin) {
                return ResponseEntity.status(403).body(Map.of("message", "Bạn không có quyền sửa album này!"));
            }

            if (body.containsKey("title")) {
                String title = (String) body.get("title");
                if (title == null || title.isBlank()) {
                    return ResponseEntity.badRequest().body(Map.of("message", "Tên album không được để trống!"));
                }
                album.setTitle(title.trim());
            }
            if (body.containsKey("description")) {
                album.setDescription((String) body.get("description"));
            }
            if (body.containsKey("cover_url")) {
                album.setCoverUrl((String) body.get("cover_url"));
            }
            if (body.containsKey("is_public") || body.containsKey("isPublic")) {
                Object visibility = body.containsKey("isPublic") ? body.get("isPublic") : body.get("is_public");
                album.setIsPublic(Boolean.TRUE.equals(visibility));
            }

            albumRepo.save(album);
            log.info("Đã cập nhật album #{}", id);

            return ResponseEntity.ok(album);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteAlbum(@PathVariable Integer id) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();
        boolean isAdmin = auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        return albumRepo.findById(id).map(album -> {
            if (!album.getUser().getUsername().equals(username) && !isAdmin) {
                return ResponseEntity.status(403).body(Map.of("message", "Bạn không có quyền xóa album này!"));
            }

            albumRepo.deleteById(id);
            log.info("Đã xóa album #{}", id);
            return ResponseEntity.ok(Map.of("message", "Đã xóa album #" + id));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{albumId}/songs/{songId}")
    public ResponseEntity<?> addSongToAlbum(@PathVariable Integer albumId, @PathVariable Integer songId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        return albumRepo.findById(albumId).map(album -> {
            if (!album.getUser().getUsername().equals(username)) {
                return ResponseEntity.status(403).body(Map.of("message", "Bạn không có quyền thêm nhạc vào album này!"));
            }

            Song song = songRepo.findById(songId).orElse(null);
            if (song == null) {
                return ResponseEntity.badRequest().body(Map.of("message", "Bài nhạc không tồn tại!"));
            }

            if (albumSongRepo.existsByAlbumIdAndSongId(albumId, songId)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Bài nhạc đã có trong album!"));
            }

            int nextTrack = albumSongRepo.findByAlbumIdOrderByTrackNumberAsc(albumId).size() + 1;

            AlbumSong albumSong = new AlbumSong();
            albumSong.setAlbum(album);
            albumSong.setSong(song);
            albumSong.setTrackNumber(nextTrack);
            albumSongRepo.save(albumSong);

            log.info("Thêm bài #{} vào album #{}", songId, albumId);
            return ResponseEntity.ok(Map.of("message", "Đã thêm bài hát vào album", "track_number", nextTrack));
        }).orElse(ResponseEntity.notFound().build());
    }

	@DeleteMapping("/{albumId}/songs/{songId}")
	@Transactional
    public ResponseEntity<?> removeSongFromAlbum(@PathVariable Integer albumId, @PathVariable Integer songId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth.getName();

        return albumRepo.findById(albumId).map(album -> {
            if (!album.getUser().getUsername().equals(username)) {
                return ResponseEntity.status(403).body(Map.of("message", "Bạn không có quyền xóa nhạc khỏi album này!"));
            }

            if (!albumSongRepo.existsByAlbumIdAndSongId(albumId, songId)) {
                return ResponseEntity.badRequest().body(Map.of("message", "Bài nhạc không có trong album!"));
            }

            albumSongRepo.deleteByAlbumIdAndSongId(albumId, songId);
            log.info("Xóa bài #{} khỏi album #{}", songId, albumId);
            return ResponseEntity.ok(Map.of("message", "Đã xóa bài hát khỏi album"));
        }).orElse(ResponseEntity.notFound().build());
    }

    private boolean isAuthenticated(Authentication auth) {
        return auth != null && auth.isAuthenticated()
                && auth.getAuthorities().stream().noneMatch(a -> "ROLE_ANONYMOUS".equals(a.getAuthority()));
    }

    private boolean isAdmin(Authentication auth) {
        return isAuthenticated(auth) && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private Map<String, Object> toAlbumSummary(Album album) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", album.getId());
        result.put("type", "ALBUM");
        result.put("title", album.getTitle());
        result.put("description", album.getDescription());
        result.put("coverUrl", album.getCoverUrl());
        result.put("isPublic", Boolean.TRUE.equals(album.getIsPublic()));
        result.put("createdAt", album.getCreatedAt());
        result.put("releaseDate", album.getReleaseDate());
        result.put("songCount", albumSongRepo.countByAlbumId(album.getId()));
        if (album.getUser() != null) {
            result.put("username", album.getUser().getUsername());
            result.put("authorName", album.getUser().getFullname());
            result.put("authorPhoto", album.getUser().getPhoto());
        }
        return result;
    }
}
