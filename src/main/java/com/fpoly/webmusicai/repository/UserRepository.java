package com.fpoly.webmusicai.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fpoly.webmusicai.entity.User;

public interface UserRepository extends JpaRepository<User, String> {
    Page<User> findByUsernameContainingIgnoreCaseOrFullnameContainingIgnoreCase(String username, String fullname, Pageable pageable);
}