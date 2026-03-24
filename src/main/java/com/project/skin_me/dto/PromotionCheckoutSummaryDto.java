package com.project.skin_me.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Active store-wide and product promotions grouped for checkout / storefront.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PromotionCheckoutSummaryDto {

    private List<PromotionDto> productDiscounts = new ArrayList<>();
    private List<PromotionDto> freeDeliveryOffers = new ArrayList<>();
    private List<PromotionDto> minimumOrderDiscounts = new ArrayList<>();
}
