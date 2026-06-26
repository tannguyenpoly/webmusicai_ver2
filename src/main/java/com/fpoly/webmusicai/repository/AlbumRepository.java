package com.fpoly.webmusicai.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fpoly.webmusicai.entity.Album;

public interface AlbumRepository extends JpaRepository<Album, Integer> {

    List<Album> findByUserUsernameOrderByCreatedAtDesc(String username);

    Page<Album> findByUserUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    List<Album> findByTitleContainingIgnoreCase(String keyword);
}
