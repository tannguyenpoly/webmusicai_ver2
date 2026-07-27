package com.fpoly.webmusicai.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fpoly.webmusicai.entity.Friendship;

public interface FriendshipRepository extends JpaRepository<Friendship, Integer> {

    @Query("""
            SELECT f FROM Friendship f
            WHERE (f.requester.username = :first AND f.addressee.username = :second)
               OR (f.requester.username = :second AND f.addressee.username = :first)
            """)
    Optional<Friendship> findBetween(
            @Param("first") String first,
            @Param("second") String second);

    @Query("""
            SELECT f FROM Friendship f
            WHERE f.status = 'ACCEPTED'
              AND (f.requester.username = :username OR f.addressee.username = :username)
            ORDER BY f.respondedAt DESC, f.createdAt DESC
            """)
    List<Friendship> findAcceptedFor(@Param("username") String username);

    List<Friendship> findByAddresseeUsernameAndStatusOrderByCreatedAtDesc(
            String username, String status);
}
