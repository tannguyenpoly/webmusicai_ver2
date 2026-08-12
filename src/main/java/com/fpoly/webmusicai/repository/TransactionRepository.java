package com.fpoly.webmusicai.repository;

import java.util.List;

import com.fpoly.webmusicai.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fpoly.webmusicai.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Integer>, JpaSpecificationExecutor<Transaction> {
    
    List<Transaction> findByUserUsernameOrderByCreatedAtDesc(String username);
    Page<Transaction> findByUserUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    @Query("""
            SELECT t.user.username AS username, COALESCE(SUM(-t.amount), 0) AS usedTokens
            FROM Transaction t
            WHERE t.amount < 0
              AND (:fromDate IS NULL OR t.createdAt >= :fromDate)
              AND (:toDate IS NULL OR t.createdAt <= :toDate)
            GROUP BY t.user.username
            """)
    List<UserTokenUsageProjection> summarizeUsageForAdmin(
            @Param("fromDate") java.util.Date fromDate,
            @Param("toDate") java.util.Date toDate);
}
