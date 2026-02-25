package com.project.skin_me.service.promotion;

import com.project.skin_me.dto.PromotionDto;
import com.project.skin_me.model.Promotion;
import com.project.skin_me.request.CreatePromotionRequest;
import com.project.skin_me.request.UpdatePromotionRequest;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface IPromotionService {
    PromotionDto createPromotion(CreatePromotionRequest request);
    PromotionDto updatePromotion(Long id, UpdatePromotionRequest request);
    PromotionDto getPromotionById(Long id);
    List<PromotionDto> getAllPromotions();
    /** Paginated: only fetches the requested page from DB (e.g. 25 per page). */
    Page<PromotionDto> getAllPromotions(Pageable pageable);
    List<PromotionDto> getActivePromotions();
    PromotionDto getActivePromotionByProductId(Long productId);
    void deletePromotion(Long id);
    PromotionDto convertToDto(Promotion promotion);
    BigDecimal calculateDiscountedPrice(Long productId);
}
