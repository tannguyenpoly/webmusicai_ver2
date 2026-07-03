package com.fpoly.webmusicai.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fpoly.webmusicai.entity.PlaylistSong;

public interface PlaylistSongRepository extends JpaRepository<PlaylistSong, Integer> {

    List<PlaylistSong> findByPlaylistIdOrderBySortOrderAsc(Integer playlistId);

    boolean existsByPlaylistIdAndSongId(Integer playlistId, Integer songId);

    void deleteByPlaylistIdAndSongId(Integer playlistId, Integer songId);

    @Modifying
    @Query(value = "DELETE FROM Playlist_Songs WHERE song_id = :songId", nativeQuery = true)
    void deleteBySongId(@Param("songId") Integer songId);
}
