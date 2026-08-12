package com.fpoly.webmusicai.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.fpoly.webmusicai.entity.Genre;

import jakarta.persistence.Entity;

public interface GenreRepository extends JpaRepository<Genre, Integer> {

    Optional<Genre> findByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCase(String name);

    @Query("SELECT g.id, g.name, g.description, g.createdAt, COUNT(s.id) " +
           "FROM Genre g LEFT JOIN g.songs s " +
           "GROUP BY g.id, g.name, g.description, g.createdAt")
    List<Object[]> findUsageSummary();

//    List<Genre> findByNameContainingIgnoreCase(String keyword);
//
//    List<Genre> findAllByOrderByNameAsc();

}
