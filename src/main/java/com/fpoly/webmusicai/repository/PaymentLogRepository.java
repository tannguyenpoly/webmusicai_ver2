package com.fpoly.webmusicai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.fpoly.webmusicai.entity.PaymentLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.fpoly.webmusicai.entity.PaymentLog;

public interface PaymentLogRepository extends JpaRepository<PaymentLog, Integer> {
    boolean existsByTransactionId(String transactionId);
    @Query("SELECT p FROM PaymentLog p WHERE " +
           "(:keyword IS NULL OR p.orderCode LIKE %:keyword% OR p.transactionId LIKE %:keyword% OR p.content LIKE %:keyword%)")
    Page<PaymentLog> findByKeyword(@Param("keyword") String keyword, Pageable pageable);

}
