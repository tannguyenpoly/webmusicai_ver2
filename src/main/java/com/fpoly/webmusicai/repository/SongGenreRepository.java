package com.fpoly.webmusicai.repository;

import com.fpoly.webmusicai.entity.SongGenre;
import com.fpoly.webmusicai.entity.SongGenre.SongGenreId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SongGenreRepository extends JpaRepository<SongGenre, SongGenreId> {
    // Bạn có thể thêm các phương thức truy vấn tùy chỉnh tại đây nếu cần
    List<SongGenre> findBySongId(Integer songId);
}