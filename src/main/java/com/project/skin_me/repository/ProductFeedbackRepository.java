package com.project.skin_me.repository;

import com.project.skin_me.model.ProductFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProductFeedbackRepository extends JpaRepository<ProductFeedback, Long> {

    Optional<ProductFeedback> findByUser_IdAndProduct_Id(Long userId, Long productId);

    Page<ProductFeedback> findByProduct_IdAndVisibleOnFrontendTrueOrderByCreatedAtDesc(Long productId,
            Pageable pageable);

    Page<ProductFeedback> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByVisibleOnFrontendTrue();
}
