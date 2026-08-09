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
}