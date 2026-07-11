package com.fpoly.webmusicai.repository;

import com.fpoly.webmusicai.entity.Follow;
import com.fpoly.webmusicai.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FollowRepository extends JpaRepository<Follow, Integer> {
    
    @Query("SELECT f FROM Follow f WHERE f.follower.username = :follower AND f.following.username = :following")
    Optional<Follow> findByFollowerAndFollowing(@Param("follower") String follower, @Param("following") String following);
    
    @Query("SELECT COUNT(f) FROM Follow f WHERE f.following.username = :username")
    long countFollowers(@Param("username") String username);

    @Query("SELECT COUNT(f) FROM Follow f WHERE f.follower.username = :username")
    long countFollowing(@Param("username") String username);

    @Query("SELECT f.following FROM Follow f WHERE f.follower.username = :username")
    List<User> findFollowingList(@Param("username") String username);

    @Query("SELECT f.follower FROM Follow f WHERE f.following.username = :username")
    List<User> findFollowersList(@Param("username") String username);
}
