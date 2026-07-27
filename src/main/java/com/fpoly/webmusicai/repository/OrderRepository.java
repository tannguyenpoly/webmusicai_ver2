package com.fpoly.webmusicai.repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import com.fpoly.webmusicai.entity.Order;

public interface OrderRepository extends JpaRepository<Order, Integer> {
	Optional<Order> findByOrderCode(String orderCode);

	boolean existsByOrderCode(String orderCode);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT o FROM Order o WHERE o.orderCode = :orderCode")
	Optional<Order> findByOrderCodeForUpdate(@Param("orderCode") String orderCode);

	List<Order> findByUserUsernameOrderByCreatedAtDesc(String username);
	List<Order> findByUserUsernameAndStatusOrderByCreatedAtDesc(String username, String status);

	List<Order> findByStatus(String status);
	List<Order> findByStatusAndCreatedAtBefore(String status, Date cutoff);

	long countByStatus(String status);

	@Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status = 'SUCCESS'")
	Long getTotalRevenue();

	@Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status = 'SUCCESS' AND o.createdAt >= :from AND o.createdAt <= :to")
	Long getRevenueBetween(@Param("from") Date from, @Param("to") Date to);

	@Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status = 'SUCCESS' AND o.createdAt >= :from")
	Long getRevenueFrom(@Param("from") Date from);

	@Query("SELECT COALESCE(SUM(o.totalPrice), 0) FROM Order o WHERE o.status = 'SUCCESS' AND o.createdAt <= :to")
	Long getRevenueTo(@Param("to") Date to);

	@Query("SELECT o FROM Order o WHERE (:from IS NULL OR o.createdAt >= :from) AND (:to IS NULL OR o.createdAt <= :to) AND (:status IS NULL OR o.status = :status) ORDER BY o.createdAt DESC")
	List<Order> findFiltered(@Param("from") Date from, @Param("to") Date to, @Param("status") String status);
}
