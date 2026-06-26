package com.fpoly.webmusicai.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fpoly.webmusicai.entity.Genre;
import com.fpoly.webmusicai.repository.GenreRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@CrossOrigin("*")
@RestController
@RequestMapping("/api/genres")
public class GenreRestController {

	@Autowired
	GenreRepository genreRepo;

	// ============ CREATE (Admin) ============
	@PostMapping
	public ResponseEntity<?> createGenre(@RequestBody Map<String, String> body) {
		String name = body.get("name");

		if (name == null || name.isBlank()) {
			return ResponseEntity.badRequest().body(Map.of("message", "Tên thể loại không được để trống!"));
		}
		if (genreRepo.existsByNameIgnoreCase(name.trim())) {
			return ResponseEntity.badRequest().body(Map.of("message", "Thể loại này đã tồn tại!"));
		}

		Genre genre = new Genre();
		genre.setName(name.trim());
		genre.setDescription(body.get("description"));

		genreRepo.save(genre);
		log.info("Đã tạo thể loại mới: {}", genre.getName());

		return ResponseEntity.ok(genre);
	}

	// ============ READ - ALL (Public) ============
	@GetMapping
	public ResponseEntity<List<Genre>> getAllGenres() {
		return ResponseEntity.ok(genreRepo.findAll());
	}

	// ============ READ - ONE (Public) ============
	@GetMapping("/{id}")
	public ResponseEntity<?> getGenreById(@PathVariable Integer id) {
		return genreRepo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	// ============ UPDATE (Admin) ============
	@PutMapping("/{id}")
	public ResponseEntity<?> updateGenre(@PathVariable Integer id, @RequestBody Map<String, String> body) {
		return genreRepo.findById(id).map(genre -> {

			if (body.containsKey("name")) {
				String name = body.get("name");
				if (name == null || name.isBlank()) {
					return ResponseEntity.badRequest().body(Map.of("message", "Tên thể loại không được để trống!"));
				}
				// Kiểm tra trùng tên với genre khác (không tính chính nó)
				genreRepo.findByNameIgnoreCase(name.trim()).ifPresent(existing -> {
					if (!existing.getId().equals(id)) {
						throw new IllegalArgumentException("Tên thể loại đã được sử dụng!");
					}
				});
				genre.setName(name.trim());
			}
			if (body.containsKey("description")) {
				genre.setDescription(body.get("description"));
			}

			genreRepo.save(genre);
			log.info("Đã cập nhật thể loại #{}", id);

			return ResponseEntity.ok(genre);

		}).orElse(ResponseEntity.notFound().build());
	}

	// ============ DELETE (Admin) ============
	@DeleteMapping("/{id}")
	public ResponseEntity<?> deleteGenre(@PathVariable Integer id) {
		if (!genreRepo.existsById(id)) {
			return ResponseEntity.badRequest().body(Map.of("message", "Thể loại không tồn tại!"));
		}

		try {
			genreRepo.deleteById(id);
			log.info("Đã xóa thể loại #{}", id);
			return ResponseEntity.ok(Map.of("message", "Đã xóa thể loại #" + id));
		} catch (Exception e) {
			// Nếu có Song nào đang dùng genre này (FK constraint)
			return ResponseEntity.badRequest()
					.body(Map.of("message", "Không thể xóa! Vẫn còn bài nhạc đang sử dụng thể loại này."));
		}
	}
}