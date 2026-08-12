package com.fpoly.webmusicai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import com.fpoly.webmusicai.entity.Package;

public interface PackageRepository extends JpaRepository<Package, Integer> {
	@Query("SELECT p.id, p.name, p.tokens, p.price, p.description, p.oldPrice, p.badge, p.tierCode, p.durationDays, " +
	       "COUNT(o.id), COALESCE(SUM(o.totalPrice), 0) " +
	       "FROM Package p LEFT JOIN Order o ON o.pkg.id = p.id AND o.status = 'SUCCESS' " +
	       "GROUP BY p.id, p.name, p.tokens, p.price, p.description, p.oldPrice, p.badge, p.tierCode, p.durationDays")
	java.util.List<Object[]> findSalesSummary();
}
