package com.fpoly.webmusicai.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fpoly.webmusicai.entity.MusicAnalysisHistory;

public interface MusicAnalysisHistoryRepository extends JpaRepository<MusicAnalysisHistory, Integer> {
    Optional<MusicAnalysisHistory> findByUserUsernameAndFileHash(String username, String fileHash);

    List<MusicAnalysisHistory> findTop6ByUserUsernameOrderByCreatedAtDesc(String username);
}
