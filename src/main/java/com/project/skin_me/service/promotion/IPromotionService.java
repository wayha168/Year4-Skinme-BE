package com.project.skin_me.service.promotion;

import com.project.skin_me.dto.PromotionDto;
import com.project.skin_me.model.Promotion;
import com.project.skin_me.request.CreatePromotionRequest;
import com.project.skin_me.request.UpdatePromotionRequest;

import java.math.BigDecimal;
import java.util.List;

public interface IPromotionService {
    PromotionDto createPromotion(CreatePromotionRequest request);
    PromotionDto updatePromotion(Long id, UpdatePromotionRequest request);
    PromotionDto getPromotionById(Long id);
    List<PromotionDto> getAllPromotions();
    List<PromotionDto> getActivePromotions();
    PromotionDto getActivePromotionByProductId(Long productId);
    void deletePromotion(Long id);
    PromotionDto convertToDto(Promotion promotion);
    BigDecimal calculateDiscountedPrice(Long productId);
}
