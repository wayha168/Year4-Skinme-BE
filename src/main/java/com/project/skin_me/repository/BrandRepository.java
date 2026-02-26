package com.project.skin_me.repository;

import com.project.skin_me.model.Brand;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface BrandRepository extends JpaRepository<Brand, Long> {

    Optional<Brand> findByNameAndCategoryId(String name, Long categoryId);

    List<Brand> findByCategoryId(Long categoryId);

    List<Brand> findByCategory_Name(String categoryName);

    boolean existsByNameAndCategoryId(String name, Long categoryId);

    @Query("SELECT b FROM Brand b LEFT JOIN FETCH b.category ORDER BY b.category.name, b.name")
    List<Brand> findAllWithCategory();

    @Query("SELECT b FROM Brand b LEFT JOIN FETCH b.category WHERE b.id = :id")
    Optional<Brand> findByIdWithCategory(Long id);
}
