package com.fpoly.webmusicai.repository;

import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import com.fpoly.webmusicai.entity.Favorite;
import com.fpoly.webmusicai.entity.LikeCount;

public interface FavoriteRepository extends JpaRepository<Favorite, Integer> {

    List<Favorite> findByUserUsernameOrderByCreatedAtDesc(String username);

    long countByUserUsername(String username);

    long countBySongId(Integer songId);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    void deleteByUserUsernameAndSongId(String username, Integer songId);

    @Modifying
    @Query(value = "DELETE FROM Favorites WHERE song_id = :songId", nativeQuery = true)
    void deleteBySongId(@Param("songId") Integer songId);

    boolean existsByUserUsernameAndSongId(String username, Integer songId);
    
    @Query("SELECT f.song.id as songId, COUNT(f.id) as likeCount FROM Favorite f WHERE f.song.id IN :songIds GROUP BY f.song.id")
    List<LikeCount> countLikesBySongIds(@Param("songIds") List<Integer> songIds);

    @Query("SELECT f.song.id FROM Favorite f WHERE f.user.username = :username AND f.song.id IN :songIds")
    Set<Integer> findLikedSongIdsByUser(@Param("username") String username, @Param("songIds") List<Integer> songIds);
}
