package com.fpoly.webmusicai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

import com.fpoly.webmusicai.entity.Authority;

public interface AuthorityRepository extends JpaRepository<Authority, Integer> {
    Optional<Authority> findByUserUsernameAndRoleId(String username, String roleId);
}
