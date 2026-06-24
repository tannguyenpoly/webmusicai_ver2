package com.fpoly.webmusicai.repository;

import com.fpoly.webmusicai.entity.PlaylistSong;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface PlaylistSongRepository extends JpaRepository<PlaylistSong, Integer> {
    @Transactional
    void deleteBySongId(Integer songId);
}