package com.fpoly.webmusicai.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.fpoly.webmusicai.entity.SongComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SongCommentRepository extends JpaRepository<SongComment, Integer> {
    List<SongComment> findBySongIdOrderByCreatedAtDesc(Integer songId);
    long countBySongId(Integer songId);
    Page<SongComment> findBySongIdAndParentIdIsNullOrderByCreatedAtDesc(Integer songId, Pageable pageable);
    List<SongComment> findByParentIdInOrderByCreatedAtAsc(List<Integer> parentIds);
}