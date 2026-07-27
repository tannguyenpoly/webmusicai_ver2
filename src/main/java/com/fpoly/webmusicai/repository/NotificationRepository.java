package com.fpoly.webmusicai.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.fpoly.webmusicai.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Integer> {
    List<Notification> findByUserUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

    long countByUserUsernameAndReadFalse(String username);

    List<Notification> findByUserUsernameAndReadFalse(String username);

    boolean existsByUserUsernameAndTypeAndRefId(String username, String type, Integer refId);
}
