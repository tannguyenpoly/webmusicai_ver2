package com.fpoly.webmusicai.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fpoly.webmusicai.entity.SongTag;

public interface SongTagRepository extends JpaRepository<SongTag, Integer> {
    List<SongTag> findBySongId(Integer songId);
    void deleteBySongId(Integer songId);
    boolean existsBySongIdAndTagId(Integer songId, Integer tagId);

    @Modifying
    @Query("DELETE FROM SongTag st WHERE st.tag.id = :tagId")
    void deleteByTagId(@Param("tagId") Integer tagId);

    @Query("SELECT DISTINCT st.songId FROM SongTag st WHERE st.tag.id = :tagId")
    List<Integer> findSongIdsByTagId(@Param("tagId") Integer tagId);
}
