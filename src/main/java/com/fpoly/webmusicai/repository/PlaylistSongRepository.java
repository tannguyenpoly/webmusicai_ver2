package com.fpoly.webmusicai.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fpoly.webmusicai.entity.PlaylistSong;

public interface PlaylistSongRepository extends JpaRepository<PlaylistSong, Integer> {

    List<PlaylistSong> findByPlaylistIdOrderBySortOrderAsc(Integer playlistId);

    boolean existsByPlaylistIdAndSongId(Integer playlistId, Integer songId);

    void deleteByPlaylistIdAndSongId(Integer playlistId, Integer songId);
}
