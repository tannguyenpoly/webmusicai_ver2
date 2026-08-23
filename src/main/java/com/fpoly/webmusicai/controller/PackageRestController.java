package com.fpoly.webmusicai.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fpoly.webmusicai.entity.Package;
import com.fpoly.webmusicai.repository.PackageRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/packages")
public class PackageRestController {

	@Autowired
	PackageRepository packageRepo;

	@GetMapping
	public ResponseEntity<List<Package>> getAllPackages() {
		return ResponseEntity.ok(packageRepo.findAll());
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> getPackageById(@PathVariable Integer id) {
		return packageRepo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@PutMapping("/{id}")
	public ResponseEntity<?> updatePackage(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
		return packageRepo.findById(id).map(pkg -> {

			if (body.containsKey("tokens")) {
				Integer tokens = parseIntSafe(body.get("tokens"));
				if (tokens == null || tokens <= 0) {
					return ResponseEntity.badRequest().body(Map.of("message", "Số token phải lớn hơn 0!"));
				}
				pkg.setTokens(tokens);
			}

			if (body.containsKey("price")) {
				Integer price = parseIntSafe(body.get("price"));
				if (price == null || price <= 0) {
					return ResponseEntity.badRequest().body(Map.of("message", "Giá tiền phải lớn hơn 0!"));
				}
				pkg.setPrice(price);
			}

			if (body.containsKey("description")) {
				pkg.setDescription((String) body.get("description"));
			}
			if (body.containsKey("durationDays")) {
				Integer durationDays = parseIntSafe(body.get("durationDays"));
				if (durationDays == null || durationDays <= 0) {
					return ResponseEntity.badRequest().body(Map.of("message", "Thời hạn gói phải lớn hơn 0 ngày!"));
				}
				pkg.setDurationDays(durationDays);
			}

			packageRepo.save(pkg);
			log.info("Đã cập nhật gói #{}", id);

			return ResponseEntity.ok(pkg);

		}).orElse(ResponseEntity.notFound().build());
	}

	private Integer parseIntSafe(Object value) {
		if (value == null)
			return null;
		try {
			if (value instanceof Integer)
				return (Integer) value;
			if (value instanceof Number)
				return ((Number) value).intValue();
			return Integer.parseInt(value.toString().trim());
		} catch (NumberFormatException e) {
			return null;
		}
	}

}
