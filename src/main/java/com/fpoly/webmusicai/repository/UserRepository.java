package com.fpoly.webmusicai.repository;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fpoly.webmusicai.entity.User;

import java.util.List;

public interface UserRepository extends JpaRepository<User, String> {
    Page<User> findByUsernameContainingIgnoreCaseOrFullnameContainingIgnoreCase(String username, String fullname, Pageable pageable);
    List<User> findByEmail(String email);
    Optional<User> findFirstByEmail(String email);
}