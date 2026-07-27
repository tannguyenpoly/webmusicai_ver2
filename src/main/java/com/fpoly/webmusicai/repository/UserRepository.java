package com.fpoly.webmusicai.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import com.fpoly.webmusicai.entity.User;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Date;

public interface UserRepository extends JpaRepository<User, String> {
    Page<User> findByUsernameContainingIgnoreCaseOrFullnameContainingIgnoreCase(String username, String fullname, Pageable pageable);
    List<User> findByEmail(String email);
    Optional<User> findFirstByEmail(String email);
    List<User> findByEmailIgnoreCase(String email);
    Optional<User> findFirstByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM User u WHERE u.username = :username")
    Optional<User> findByUsernameForUpdate(@Param("username") String username);

    @Modifying
    @Query("UPDATE User u SET u.lastSeenAt = :lastSeenAt WHERE u.username = :username")
    int touchLastSeen(@Param("username") String username, @Param("lastSeenAt") Date lastSeenAt);
}
