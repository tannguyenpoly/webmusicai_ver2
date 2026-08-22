package com.fpoly.webmusicai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.transaction.Transactional;

import com.fpoly.webmusicai.entity.SongListenHistory;

public interface SongListenHistoryRepository extends JpaRepository<SongListenHistory, Long> {

    @Modifying
    @Transactional
    @Query("DELETE FROM SongListenHistory s WHERE s.song.id = :songId")
    void deleteBySongId(@Param("songId") Integer songId);
}
