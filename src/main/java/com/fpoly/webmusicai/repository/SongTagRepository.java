package com.fpoly.webmusicai.repository;

import com.fpoly.webmusicai.entity.SongTag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

public interface SongTagRepository extends JpaRepository<SongTag, Integer> {
    @Transactional
    void deleteBySongId(Integer songId);
}