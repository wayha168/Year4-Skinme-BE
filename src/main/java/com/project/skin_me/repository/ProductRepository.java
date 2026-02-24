package com.project.skin_me.repository;

import java.util.List;

import com.project.skin_me.enums.ProductStatus;
import com.project.skin_me.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByCategory_Name(String categoryName);

    List<Product> findByCategory_Id(Long categoryId);

    List<Product> findByBrand_Category_Name(String categoryName);

    List<Product> findByBrand_Name(String brandName);

    List<Product> findByBrand_Id(Long brandId);

    List<Product> findByName(String name);

    List<Product> findByStatus(ProductStatus status);

    List<Product> findByProductType(String productType);

    List<Product> findByBrand_Category_NameAndBrand_Name(String categoryName, String brandName);

    List<Product> findByBrand_NameAndName(String brandName, String productName);

    List<Product> findByProductTypeAndName(String productType, String name);

    Long countByBrand_NameAndName(String brandName, String productName);

    boolean existsByNameAndBrand_Id(String name, Long brandId);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.images LEFT JOIN FETCH p.brand b LEFT JOIN FETCH b.category")
    List<Product> findAllWithImages();

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.brand b LEFT JOIN FETCH b.category")
    List<Product> findAllWithCategory();

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.brand b LEFT JOIN FETCH b.category LEFT JOIN FETCH p.category WHERE p.category.name = :categoryName")
    List<Product> findByCategoryNameWithCategory(String categoryName);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE p.category.id = :categoryId")
    List<Product> findByCategoryIdWithCategory(Long categoryId);
}
