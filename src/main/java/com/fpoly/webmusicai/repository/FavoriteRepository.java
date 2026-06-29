package com.fpoly.webmusicai.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import com.fpoly.webmusicai.entity.Favorite;

public interface FavoriteRepository extends JpaRepository<Favorite, Integer> {

    List<Favorite> findByUserUsernameOrderByCreatedAtDesc(String username);

    long countBySongId(Integer songId);

    void deleteByUserUsernameAndSongId(String username, Integer songId);

    boolean existsByUserUsernameAndSongId(String username, Integer songId);
}