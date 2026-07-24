package com.fpoly.webmusicai.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fpoly.webmusicai.entity.Playlist;

public interface PlaylistRepository extends JpaRepository<Playlist, Integer> {

    List<Playlist> findByUserUsernameOrderByCreatedAtDesc(String username);

    Page<Playlist> findByUserUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    List<Playlist> findByUserUsernameAndIsPublicTrueOrderByCreatedAtDesc(String username);

    List<Playlist> findByIsPublicTrueOrderByCreatedAtDesc();

    List<Playlist> findByNameContainingIgnoreCase(String keyword);
}
