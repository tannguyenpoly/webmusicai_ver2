package com.fpoly.webmusicai.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.fpoly.webmusicai.entity.SongComment;

public interface SongCommentRepository extends JpaRepository<SongComment, Integer> {
    List<SongComment> findBySongIdOrderByCreatedAtDesc(Integer songId);
    long countBySongId(Integer songId);
}