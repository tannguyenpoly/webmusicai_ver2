package com.fpoly.webmusicai.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fpoly.webmusicai.entity.AlbumSong;

public interface AlbumSongRepository extends JpaRepository<AlbumSong, Integer> {

    List<AlbumSong> findByAlbumIdOrderByTrackNumberAsc(Integer albumId);

    boolean existsByAlbumIdAndSongId(Integer albumId, Integer songId);

    void deleteByAlbumIdAndSongId(Integer albumId, Integer songId);
}
