package com.project.skin_me.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import com.project.skin_me.model.Category;

import java.util.List;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Category findByname(String name);

    boolean existsByName(String name);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.brands")
    List<Category> findAllWithBrands();

    @Query(value = "SELECT DISTINCT c FROM Category c LEFT JOIN FETCH c.brands",
           countQuery = "SELECT COUNT(DISTINCT c) FROM Category c")
    Page<Category> findAllWithBrands(Pageable pageable);

    @Query("SELECT c FROM Category c LEFT JOIN FETCH c.brands WHERE c.id = :id")
    Category findByIdWithBrands(Long id);
}
