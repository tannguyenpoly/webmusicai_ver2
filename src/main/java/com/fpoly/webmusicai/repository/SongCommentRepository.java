package com.fpoly.webmusicai.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.fpoly.webmusicai.entity.SongComment;

public interface SongCommentRepository extends JpaRepository<SongComment, Integer> {
    List<SongComment> findBySongIdOrderByCreatedAtDesc(Integer songId);
    long countBySongId(Integer songId);

    @Transactional
    void deleteBySongId(Integer songId);
}