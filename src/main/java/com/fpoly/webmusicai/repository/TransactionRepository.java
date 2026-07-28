package com.fpoly.webmusicai.repository;

import java.util.List;

import com.fpoly.webmusicai.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fpoly.webmusicai.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Integer> {
    
    List<Transaction> findByUserUsernameOrderByCreatedAtDesc(String username);
    Page<Transaction> findByUserUsernameOrderByCreatedAtDesc(String username, Pageable pageable);
    @Query("SELECT t FROM Transaction t WHERE " +
           "(:username IS NULL OR t.user.username LIKE %:username%) AND " +
           "(:type = 'ALL' OR (:type = 'DEPOSIT' AND t.amount > 0) OR (:type = 'USAGE' AND t.amount < 0))")
    Page<Transaction> findFilteredTransactions(@Param("username") String username, @Param("type") String type, Pageable pageable);
}