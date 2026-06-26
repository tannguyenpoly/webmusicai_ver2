package com.fpoly.webmusicai.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.fpoly.webmusicai.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Integer> {
	Optional<Order> findByOrderCode(String orderCode);

	List<Order> findByUserUsernameOrderByCreatedAtDesc(String username);

	List<Order> findByStatus(String status);
}