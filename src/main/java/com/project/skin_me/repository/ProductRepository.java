package com.project.skin_me.repository;

import java.util.List;

import com.project.skin_me.dto.ProductOptionDto;
import com.project.skin_me.enums.ProductStatus;
import com.project.skin_me.model.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Query("SELECT new com.project.skin_me.dto.ProductOptionDto(p.id, p.name, p.price) FROM Product p")
    List<ProductOptionDto> findAllProductOptions();

    List<Product> findByCategory_Name(String categoryName);

    List<Product> findByCategory_Id(Long categoryId);

    List<Product> findByBrand_Category_Name(String categoryName);

    List<Product> findByBrand_Name(String brandName);

    List<Product> findByBrand_Id(Long brandId);

    List<Product> findByName(String name);

    List<Product> findByStatus(ProductStatus status);

    List<Product> findByProductType(String productType);

    List<Product> findByCategory_NameAndBrand_Name(String categoryName, String brandName);

    List<Product> findByBrand_Category_NameAndBrand_Name(String categoryName, String brandName);

    List<Product> findByBrand_NameAndName(String brandName, String productName);

    List<Product> findByProductTypeAndName(String productType, String name);

    Long countByBrand_NameAndName(String brandName, String productName);

    boolean existsByNameAndBrand_Id(String name, Long brandId);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.brand b LEFT JOIN FETCH p.category")
    List<Product> findAllWithImages();

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.brand b LEFT JOIN FETCH p.category")
    List<Product> findAllWithCategory();

    @Query(value = "SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.brand b LEFT JOIN FETCH p.category",
           countQuery = "SELECT COUNT(DISTINCT p) FROM Product p")
    Page<Product> findAllWithCategory(Pageable pageable);

    @Query("SELECT DISTINCT p FROM Product p JOIN FETCH p.brand INNER JOIN FETCH p.category c WHERE c.name = :categoryName")
    List<Product> findByCategoryNameWithCategory(String categoryName);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.category c LEFT JOIN FETCH p.brand WHERE c.id = :categoryId")
    List<Product> findByCategoryIdWithCategory(@Param("categoryId") Long categoryId);

    @Query("SELECT DISTINCT p FROM Product p LEFT JOIN FETCH p.brand b LEFT JOIN FETCH p.category WHERE b.id = :brandId")
    List<Product> findByBrandIdWithBrand(@Param("brandId") Long brandId);
}
