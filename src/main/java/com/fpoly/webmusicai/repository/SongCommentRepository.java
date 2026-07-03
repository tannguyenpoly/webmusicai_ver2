package com.fpoly.webmusicai.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.fpoly.webmusicai.entity.SongComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SongCommentRepository extends JpaRepository<SongComment, Integer> {
    List<SongComment> findBySongIdOrderByCreatedAtDesc(Integer songId);
    long countBySongId(Integer songId);
    @Modifying
    @Query(value = "DELETE FROM Song_Comments WHERE song_id = :songId", nativeQuery = true)
    void deleteBySongId(@Param("songId") Integer songId);
    Page<SongComment> findBySongIdAndParentIdIsNullOrderByCreatedAtDesc(Integer songId, Pageable pageable);
    List<SongComment> findByParentIdInOrderByCreatedAtAsc(List<Integer> parentIds);
}
