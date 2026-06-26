package com.fpoly.webmusicai.controller;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fpoly.webmusicai.entity.Order;
import com.fpoly.webmusicai.entity.Song;
import com.fpoly.webmusicai.entity.User;
import com.fpoly.webmusicai.repository.*;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@CrossOrigin("*")
@RestController
@RequestMapping("/api/reports")
public class ReportRestController {

    @Autowired
    private UserRepository userRepo;

    @Autowired
    private SongRepository songRepo;

    @Autowired
    private OrderRepository orderRepo;

    @Autowired
    private TransactionRepository transRepo;

    @Autowired
    private FavoriteRepository favoriteRepo;

    @Autowired
    private SongCommentRepository commentRepo;

    @Autowired
    private GenreRepository genreRepo;

    @Autowired
    private AlbumRepository albumRepo;

    @Autowired
    private PlaylistRepository playlistRepo;

    // ============ TỔNG QUAN HỆ THỐNG ============
    @GetMapping("/overview")
    public ResponseEntity<?> getOverview() {
        Map<String, Object> stats = new HashMap<>();

        long totalUsers = userRepo.count();
        long totalSongs = songRepo.count();
        long completed = songRepo.countByStatus("COMPLETED");
        long pending = songRepo.countByStatus("PENDING");
        long failed = songRepo.countByStatus("FAILED");
        long publicSongs = songRepo.countByIsPublicTrue();
        long totalAlbums = albumRepo.count();
        long totalPlaylists = playlistRepo.count();
        long totalGenres = genreRepo.count();
        long totalOrders = orderRepo.count();

        double successRate = totalSongs > 0 ? (completed * 100.0 / totalSongs) : 0;

        stats.put("totalUsers", totalUsers);
        stats.put("totalSongs", totalSongs);
        stats.put("completedSongs", completed);
        stats.put("pendingSongs", pending);
        stats.put("failedSongs", failed);
        stats.put("publicSongs", publicSongs);
        stats.put("totalAlbums", totalAlbums);
        stats.put("totalPlaylists", totalPlaylists);
        stats.put("totalGenres", totalGenres);
        stats.put("totalOrders", totalOrders);
        stats.put("successRate", Math.round(successRate * 10) / 10.0);

        return ResponseEntity.ok(stats);
    }

    // ============ DOANH THU ============
    @GetMapping("/revenue")
    public ResponseEntity<?> getRevenue() {
        List<Order> successOrders = orderRepo.findByStatus("SUCCESS");

        long totalRevenue = successOrders.stream().mapToLong(Order::getTotalPrice).sum();
        long totalOrders = successOrders.size();

        Map<String, Object> revenue = new HashMap<>();
        revenue.put("totalRevenue", totalRevenue);
        revenue.put("totalPaidOrders", totalOrders);

        return ResponseEntity.ok(revenue);
    }

    // ============ TOP BÀI HÁT YÊU THÍCH ============
    @GetMapping("/top-liked")
    public ResponseEntity<?> getTopLiked(@RequestParam(defaultValue = "10") int limit) {
        List<Song> allPublicSongs = songRepo.findByIsPublicTrueOrderByCreatedAtDesc();

        List<Map<String, Object>> topSongs = allPublicSongs.stream()
                .map(song -> {
                    long likes = favoriteRepo.countBySongId(song.getId());
                    long comments = commentRepo.countBySongId(song.getId());
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", song.getId());
                    item.put("title", song.getTitle());
                    item.put("username", song.getUser().getUsername());
                    item.put("total_likes", likes);
                    item.put("total_comments", comments);
                    return item;
                })
                .sorted((a, b) -> Long.compare((Long) b.get("total_likes"), (Long) a.get("total_likes")))
                .limit(limit)
                .collect(Collectors.toList());

        return ResponseEntity.ok(topSongs);
    }

    // ============ NGƯỜI DÙNG TÍCH CỰC ============
    @GetMapping("/top-users")
    public ResponseEntity<?> getTopUsers(@RequestParam(defaultValue = "10") int limit) {
        List<User> allUsers = userRepo.findAll(PageRequest.of(0, 100)).getContent();

        List<Map<String, Object>> topUsers = allUsers.stream()
                .map(user -> {
                    long songCount = songRepo.findByUserUsernameOrderByCreatedAtDesc(user.getUsername()).size();
                    long transactionCount = transRepo.findByUserUsernameOrderByCreatedAtDesc(user.getUsername()).size();
                    Map<String, Object> item = new HashMap<>();
                    item.put("username", user.getUsername());
                    item.put("fullname", user.getFullname());
                    item.put("token_balance", user.getTokenBalance());
                    item.put("total_songs", songCount);
                    item.put("total_transactions", transactionCount);
                    return item;
                })
                .sorted((a, b) -> Long.compare((Long) b.get("total_songs"), (Long) a.get("total_songs")))
                .limit(limit)
                .collect(Collectors.toList());

        return ResponseEntity.ok(topUsers);
    }

    // ============ THỐNG KÊ NGƯỜI DÙNG MỚI ============
    @GetMapping("/user-growth")
    public ResponseEntity<?> getUserGrowth() {
        long totalUsers = userRepo.count();
        long basicUsers = userRepo.findAll().stream()
                .filter(u -> "BASIC".equals(u.getAccountTier()))
                .count();
        long proUsers = totalUsers - basicUsers;

        Map<String, Object> growth = new HashMap<>();
        growth.put("totalUsers", totalUsers);
        growth.put("basicUsers", basicUsers);
        growth.put("proUsers", proUsers);

        return ResponseEntity.ok(growth);
    }
}
