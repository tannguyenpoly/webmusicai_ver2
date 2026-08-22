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
@RestController
@RequestMapping("/api/genres")
public class GenreRestController {

	@Autowired
	GenreRepository genreRepo;

	@PostMapping
	public ResponseEntity<?> createGenre(@RequestBody Map<String, String> body) {
		String name = body.get("name");

		if (name == null || name.isBlank()) {
			return ResponseEntity.badRequest().body(Map.of("message", "Tên thể loại không được để trống!"));
		}
		if (genreRepo.existsByNameIgnoreCase(name.trim())) {
			return ResponseEntity.badRequest().body(Map.of("message", "Thể loại này đã tồn tại!"));
		}
		if (name.trim().length() > 50) {
			return ResponseEntity.badRequest().body(Map.of("message", "Tên thể loại tối đa 50 ký tự!"));
		}
		String description = body.get("description");
		if (description != null && description.length() > 255) {
			return ResponseEntity.badRequest().body(Map.of("message", "Mô tả thể loại tối đa 255 ký tự!"));
		}

		Genre genre = new Genre();
		genre.setName(name.trim());
		genre.setDescription(body.get("description"));
		genre.setMinTier(normalizeMinTier(body.get("minTier")));

		genreRepo.save(genre);
		log.info("Đã tạo thể loại mới: {} ({})", genre.getName(), genre.getMinTier());

		return ResponseEntity.ok(genre);
	}

	@GetMapping
	public ResponseEntity<List<Genre>> getAllGenres() {
		return ResponseEntity.ok(genreRepo.findAll());
	}

	@GetMapping("/{id}")
	public ResponseEntity<?> getGenreById(@PathVariable Integer id) {
		return genreRepo.findById(id).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
	}

	@PutMapping("/{id}")
	public ResponseEntity<?> updateGenre(@PathVariable Integer id, @RequestBody Map<String, String> body) {
		return genreRepo.findById(id).map(genre -> {

			if (body.containsKey("name")) {
				String name = body.get("name");
				if (name == null || name.isBlank()) {
					return ResponseEntity.badRequest().body(Map.of("message", "Tên thể loại không được để trống!"));
				}
				genreRepo.findByNameIgnoreCase(name.trim()).ifPresent(existing -> {
					if (!existing.getId().equals(id)) {
						throw new IllegalArgumentException("Tên thể loại đã được sử dụng!");
					}
				});
				if (name.trim().length() > 50) {
					return ResponseEntity.badRequest().body(Map.of("message", "Tên thể loại tối đa 50 ký tự!"));
				}
				genre.setName(name.trim());
			}
			if (body.containsKey("description")) {
				String description = body.get("description");
				if (description != null && description.length() > 255) {
					return ResponseEntity.badRequest().body(Map.of("message", "Mô tả thể loại tối đa 255 ký tự!"));
				}
				genre.setDescription(description);
			}
			if (body.containsKey("minTier")) {
				genre.setMinTier(normalizeMinTier(body.get("minTier")));
			}

			genreRepo.save(genre);
			log.info("Đã cập nhật thể loại #{}", id);

			return ResponseEntity.ok(genre);

		}).orElse(ResponseEntity.notFound().build());
	}

	private String normalizeMinTier(String value) {
		if (value == null || value.isBlank()) return "FREE";
		String tier = value.trim().toUpperCase();
		if ("CREATOR".equals(tier)) return "CREATOR";
		if ("PRO".equals(tier) || "STUDIO".equals(tier)) return "PRO";
		return "FREE";
	}

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
			return ResponseEntity.badRequest()
					.body(Map.of("message", "Không thể xóa! Vẫn còn bài nhạc đang sử dụng thể loại này."));
		}
	}
}
