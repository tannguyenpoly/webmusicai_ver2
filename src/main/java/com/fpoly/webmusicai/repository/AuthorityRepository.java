package com.fpoly.webmusicai.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fpoly.webmusicai.entity.Authority;

public interface AuthorityRepository extends JpaRepository<Authority, Integer> {
}