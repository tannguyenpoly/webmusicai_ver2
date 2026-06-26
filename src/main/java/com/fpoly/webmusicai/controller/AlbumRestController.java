package com.fpoly.webmusicai.controller;

import java.util.List;
import java.util.Map;

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
@CrossOrigin("*")
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

    // ============ CREATE ============
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
        album.setUser(user);

        albumRepo.save(album);
        log.info("User {} tạo album: {}", username, album.getTitle());

        return ResponseEntity.ok(album);
    }

    // ============ READ - ALL (Public) ============
    @GetMapping
    public ResponseEntity<List<Album>> getAllAlbums() {
        return ResponseEntity.ok(albumRepo.findAll());
    }

    // ============ READ - ONE (Public) ============
    @GetMapping("/{id}")
    public ResponseEntity<?> getAlbumById(@PathVariable Integer id) {
        return albumRepo.findById(id).map(album -> {
            List<AlbumSong> songs = albumSongRepo.findByAlbumIdOrderByTrackNumberAsc(id);
            return ResponseEntity.ok(Map.of("album", album, "songs", songs));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ============ READ - BY USER (Public) ============
    @GetMapping("/user/{username}")
    public ResponseEntity<?> getAlbumsByUser(@PathVariable String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Album> albums = albumRepo.findByUserUsernameOrderByCreatedAtDesc(username, pageable);
        return ResponseEntity.ok(albums);
    }

    // ============ UPDATE ============
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

            albumRepo.save(album);
            log.info("Đã cập nhật album #{}", id);

            return ResponseEntity.ok(album);
        }).orElse(ResponseEntity.notFound().build());
    }

    // ============ DELETE ============
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

    // ============ ADD SONG TO ALBUM ============
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

    // ============ REMOVE SONG FROM ALBUM ============
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
}
