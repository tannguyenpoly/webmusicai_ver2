package com.fpoly.webmusicai.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fpoly.webmusicai.entity.Transaction;

public interface TransactionRepository extends JpaRepository<Transaction, Integer> {
    
    // Tìm lịch sử giao dịch của 1 User - Sắp xếp mới nhất
    List<Transaction> findByUserUsernameOrderByCreatedAtDesc(String username);
}