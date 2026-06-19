package com.fpoly.webmusicai.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.fpoly.webmusicai.entity.Package;
import com.fpoly.webmusicai.repository.PackageRepository;

@CrossOrigin("*")
@RestController
@RequestMapping("/api/packages")
public class PackageRestController {

	@Autowired
	PackageRepository packageRepo;

	@GetMapping
	public ResponseEntity<List<Package>> getAllPackages() {
		return ResponseEntity.ok(packageRepo.findAll());
	}
}