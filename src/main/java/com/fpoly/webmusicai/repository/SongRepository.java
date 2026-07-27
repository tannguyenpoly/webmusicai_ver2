package com.fpoly.webmusicai.repository;

import java.sql.Date;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

import com.fpoly.webmusicai.entity.Song;

import jakarta.transaction.Transactional;
import jakarta.persistence.LockModeType;

public interface SongRepository extends JpaRepository<Song, Integer> {

	List<Song> findByIsPublicTrueOrderByCreatedAtDesc();

	List<Song> findByUserUsernameOrderByCreatedAtDesc(String username);

	Page<Song> findByUserUsernameOrderByCreatedAtDesc(String username, Pageable pageable);

	Page<Song> findByUserUsernameAndIsPublicTrueOrderByCreatedAtDesc(String username, Pageable pageable);

	List<Song> findByUserUsernameAndIsPublicTrueOrderByCreatedAtDesc(String username);

	long countByUserUsername(String username);

	long countByUserUsernameAndStatus(String username, String status);

	long countByUserUsernameAndIsPublicTrue(String username);

	long countByUserUsernameAndStatusAndIsPublicTrue(String username, String status);

	long countByStatus(String status);

	long countByIsPublicTrue();

	Page<Song> findByStatusOrderByCreatedAtDesc(String status, Pageable pageable);

	List<Song> findByParentIdOrderByCreatedAtDesc(Integer parentId);

	List<Song> findByStatusAndCreatedAtBefore(String status, java.util.Date cutoff);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT s FROM Song s WHERE s.id = :id")
	java.util.Optional<Song> findByIdForUpdate(@Param("id") Integer id);

	@Modifying
	@Query("UPDATE Song s SET s.parentId = null WHERE s.parentId = :songId")
	void detachRemixesFromParent(@Param("songId") Integer songId);

	@Modifying
	@Query(value = "DELETE FROM SongGenres WHERE song_id = :songId", nativeQuery = true)
	void deleteSongGenresBySongId(@Param("songId") Integer songId);

	@Modifying
	@Transactional
	@Query("DELETE FROM Song s WHERE s.status = 'FAILED' AND s.createdAt < :cutoff")
	void deleteOldFailedSongs(@Param("cutoff") Date cutoff);

	@Modifying
	@Transactional
	@Query("DELETE FROM Song s WHERE s.status = 'PENDING' AND s.createdAt < :cutoff")
	void deleteStuckPendingSongs(@Param("cutoff") Date cutoff);
}
