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
@CrossOrigin("*")
@RestController
@RequestMapping("/api/packages")
public class PackageRestController {

	@Autowired
	PackageRepository packageRepo;

	// ============ READ - ALL (Public) ============
	@GetMapping
	public ResponseEntity<List<Package>> getAllPackages() {
		return ResponseEntity.ok(packageRepo.findAll());
	}

	// ============ READ - ONE (Public) ============
	@GetMapping("/{id}")
	public ResponseEntity<?> getPackageById(@PathVariable Integer id) {
		return packageRepo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	// ============ CREATE (Admin) ============
	@PostMapping
	public ResponseEntity<?> createPackage(@RequestBody Map<String, Object> body) {
		String name = (String) body.get("name");

		if (name == null || name.isBlank()) {
			return ResponseEntity.badRequest().body(Map.of("message", "Tên gói không được để trống!"));
		}

		Integer tokens = parseIntSafe(body.get("tokens"));
		Integer price = parseIntSafe(body.get("price"));

		if (tokens == null || tokens <= 0) {
			return ResponseEntity.badRequest().body(Map.of("message", "Số token phải lớn hơn 0!"));
		}
		if (price == null || price <= 0) {
			return ResponseEntity.badRequest().body(Map.of("message", "Giá tiền phải lớn hơn 0!"));
		}

		Package pkg = new Package();
		pkg.setName(name.trim());
		pkg.setTokens(tokens);
		pkg.setPrice(price);
		pkg.setDescription((String) body.get("description"));

		packageRepo.save(pkg);
		log.info("Đã tạo gói mới: {} - {} token - {}đ", pkg.getName(), pkg.getTokens(), pkg.getPrice());

		return ResponseEntity.ok(pkg);
	}

	// ============ UPDATE (Admin) ============
	@PutMapping("/{id}")
	public ResponseEntity<?> updatePackage(@PathVariable Integer id, @RequestBody Map<String, Object> body) {
		return packageRepo.findById(id).map(pkg -> {

			if (body.containsKey("name")) {
				String name = (String) body.get("name");
				if (name == null || name.isBlank()) {
					return ResponseEntity.badRequest().body(Map.of("message", "Tên gói không được để trống!"));
				}
				pkg.setName(name.trim());
			}

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

			packageRepo.save(pkg);
			log.info("Đã cập nhật gói #{}", id);

			return ResponseEntity.ok(pkg);

		}).orElse(ResponseEntity.notFound().build());
	}

	// ============ DELETE (Admin) ============
	@DeleteMapping("/{id}")
	public ResponseEntity<?> deletePackage(@PathVariable Integer id) {
		if (!packageRepo.existsById(id)) {
			return ResponseEntity.badRequest().body(Map.of("message", "Gói không tồn tại!"));
		}

		try {
			packageRepo.deleteById(id);
			log.info("Đã xóa gói #{}", id);
			return ResponseEntity.ok(Map.of("message", "Đã xóa gói #" + id));
		} catch (Exception e) {
			// Nếu có Order nào đang tham chiếu gói này (FK constraint)
			return ResponseEntity.badRequest()
					.body(Map.of("message", "Không thể xóa! Đã có đơn hàng sử dụng gói này. Hãy ẩn gói thay vì xóa."));
		}
	}

	// Helper parse Integer an toàn (tránh lỗi ClassCastException khi JSON gửi số
	// dạng String)
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