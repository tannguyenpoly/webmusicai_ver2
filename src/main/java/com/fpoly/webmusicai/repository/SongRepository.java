package com.fpoly.webmusicai.repository;

import java.sql.Date;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fpoly.webmusicai.entity.Song;

import jakarta.transaction.Transactional;

public interface SongRepository extends JpaRepository<Song, Integer> {
    
    // Tìm tất cả bài hát đang bật Public (để hiện lên trang chủ) - Sắp xếp mới nhất
    List<Song> findByIsPublicTrueOrderByCreatedAtDesc();
    
    // Tìm tất cả bài hát do 1 User tạo ra (để hiện trong trang Lịch sử cá nhân)
    List<Song> findByUserUsernameOrderByCreatedAtDesc(String username);
    
 // Xóa các bài nhạc FAILED quá 7 ngày
    @Modifying
    @Transactional
    @Query("DELETE FROM Song s WHERE s.status = 'FAILED' AND s.createdAt < :cutoff")
    void deleteOldFailedSongs(@Param("cutoff") Date cutoff);

    // Xóa các bài PENDING quá 1 giờ (bị treo)
    @Modifying
    @Transactional
    @Query("DELETE FROM Song s WHERE s.status = 'PENDING' AND s.createdAt < :cutoff")
    void deleteStuckPendingSongs(@Param("cutoff") Date cutoff);
    
    
}