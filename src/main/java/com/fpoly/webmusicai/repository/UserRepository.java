package com.fpoly.webmusicai.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fpoly.webmusicai.entity.User;

public interface UserRepository extends JpaRepository<User, String> {
    // Không cần viết gì thêm, JpaRepository đã bao thầu hết các lệnh CRUD cơ bản
}