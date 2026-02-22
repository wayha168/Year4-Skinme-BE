package com.project.skin_me.repository;

import com.project.skin_me.model.PopularProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;


public interface PopularProductRepository extends JpaRepository<PopularProduct,Long> {

    Optional<PopularProduct> findByProductId(Long id);
    
    @Query("SELECT pp FROM PopularProduct pp JOIN FETCH pp.product p LEFT JOIN FETCH p.images ORDER BY pp.quantitySold DESC, pp.lastPurchasedDate DESC")
    List<PopularProduct> findTopPopularProductsWithRelations();
}
