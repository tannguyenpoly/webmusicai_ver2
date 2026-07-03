package com.fpoly.webmusicai.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fpoly.webmusicai.entity.AlbumSong;

public interface AlbumSongRepository extends JpaRepository<AlbumSong, Integer> {

    List<AlbumSong> findByAlbumIdOrderByTrackNumberAsc(Integer albumId);

    boolean existsByAlbumIdAndSongId(Integer albumId, Integer songId);

    void deleteByAlbumIdAndSongId(Integer albumId, Integer songId);

    @Modifying
    @Query(value = "DELETE FROM Album_Songs WHERE song_id = :songId", nativeQuery = true)
    void deleteBySongId(@Param("songId") Integer songId);
}
