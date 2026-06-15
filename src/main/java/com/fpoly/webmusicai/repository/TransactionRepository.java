package com.fpoly.webmusicai.repository;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fpoly.webmusicai.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Integer> {
    
    List<Transaction> findByUserUsernameOrderByCreatedAtDesc(String username);
    Page<Transaction> findByUserUsernameOrderByCreatedAtDesc(String username, Pageable pageable);
}