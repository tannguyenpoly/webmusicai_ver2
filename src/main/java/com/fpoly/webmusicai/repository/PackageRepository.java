package com.fpoly.webmusicai.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.fpoly.webmusicai.entity.Package;

public interface PackageRepository extends JpaRepository<Package, Integer> {
	
}