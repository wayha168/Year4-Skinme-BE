package com.project.skin_me.repository;

import com.project.skin_me.model.Promotion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PromotionRepository extends JpaRepository<Promotion, Long> {
    
    List<Promotion> findByActiveTrue();
    
    List<Promotion> findByProductId(Long productId);
    
    @Query("SELECT p FROM Promotion p WHERE p.active = true AND p.startDate <= :now AND p.deadline >= :now")
    List<Promotion> findActivePromotions(@Param("now") LocalDateTime now);
    
    @Query("SELECT p FROM Promotion p WHERE p.product.id = :productId AND p.active = true AND p.startDate <= :now AND p.deadline >= :now")
    Optional<Promotion> findActivePromotionByProductId(@Param("productId") Long productId, @Param("now") LocalDateTime now);
    
    List<Promotion> findByDeadlineBefore(LocalDateTime dateTime);
    
    List<Promotion> findAllByOrderByCreatedAtDesc();
    
    Page<Promotion> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
