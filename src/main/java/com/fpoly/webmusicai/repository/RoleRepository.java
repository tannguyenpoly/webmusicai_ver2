package com.fpoly.webmusicai.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fpoly.webmusicai.entity.Role;

public interface RoleRepository extends JpaRepository<Role, String> {
}