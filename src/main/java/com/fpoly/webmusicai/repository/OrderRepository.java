package com.fpoly.webmusicai.repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.fpoly.webmusicai.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Integer> {
	Optional<Order> findByOrderCode(String orderCode);

	List<Order> findByUserUsernameOrderByCreatedAtDesc(String username);

	List<Order> findByStatus(String status);

	long countByStatus(String status);

	@Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status = 'SUCCESS'")
	Long getTotalRevenue();

	@Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status = 'SUCCESS' AND o.createdAt >= :from AND o.createdAt <= :to")
	Long getRevenueBetween(@Param("from") Date from, @Param("to") Date to);

	@Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status = 'SUCCESS' AND o.createdAt >= :from")
	Long getRevenueFrom(@Param("from") Date from);

	@Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status = 'SUCCESS' AND o.createdAt <= :to")
	Long getRevenueTo(@Param("to") Date to);
}