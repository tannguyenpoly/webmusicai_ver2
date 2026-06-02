package com.fpoly.webmusicai.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fpoly.webmusicai.entity.Song;

public interface SongRepository extends JpaRepository<Song, Integer> {
    
    // Tìm tất cả bài hát đang bật Public (để hiện lên trang chủ) - Sắp xếp mới nhất
    List<Song> findByIsPublicTrueOrderByCreatedAtDesc();
    
    // Tìm tất cả bài hát do 1 User tạo ra (để hiện trong trang Lịch sử cá nhân)
    List<Song> findByUserUsernameOrderByCreatedAtDesc(String username);
}