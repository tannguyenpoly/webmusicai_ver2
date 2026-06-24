package com.fpoly.webmusicai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.fpoly.webmusicai.entity.Favorite;

public interface FavoriteRepository extends JpaRepository<Favorite, Integer> {

    long countBySongId(Integer songId);

    @Transactional
    void deleteBySongId(Integer songId);

    void deleteByUserUsernameAndSongId(String username, Integer songId);

    boolean existsByUserUsernameAndSongId(String username, Integer songId);
}