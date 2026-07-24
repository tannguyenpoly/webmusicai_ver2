package com.fpoly.webmusicai.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fpoly.webmusicai.entity.PaymentLog;

public interface PaymentLogRepository extends JpaRepository<PaymentLog, Integer> {
    boolean existsByTransactionId(String transactionId);
}
