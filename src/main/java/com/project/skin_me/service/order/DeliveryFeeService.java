package com.project.skin_me.service.order;

import com.project.skin_me.enums.PromotionType;
import com.project.skin_me.model.Promotion;
import com.project.skin_me.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Applies a flat delivery fee when cart subtotal is below a threshold, unless an active
 * {@link PromotionType#FREE_DELIVERY} promotion applies to that subtotal (minimum order rules respected).
 */
@Service
@RequiredArgsConstructor
public class DeliveryFeeService {

    private final PromotionRepository promotionRepository;

    @Value("${app.checkout.delivery-fee.subtotal-threshold:50}")
    private BigDecimal subtotalThreshold;

    @Value("${app.checkout.delivery-fee.amount:1.00}")
    private BigDecimal configuredDeliveryFee;

    public boolean qualifiesForFreeDelivery(BigDecimal itemsSubtotal) {
        BigDecimal sub = itemsSubtotal != null ? itemsSubtotal : BigDecimal.ZERO;
        List<Promotion> promos = promotionRepository.findActivePromotionsByType(
                PromotionType.FREE_DELIVERY, LocalDateTime.now());
        for (Promotion p : promos) {
            BigDecimal min = p.getMinimumOrderAmount();
            if (min == null || min.compareTo(BigDecimal.ZERO) <= 0) {
                return true;
            }
            if (sub.compareTo(min) >= 0) {
                return true;
            }
        }
        return false;
    }

    public BigDecimal computeDeliveryFee(BigDecimal itemsSubtotal) {
        BigDecimal sub = itemsSubtotal != null ? itemsSubtotal : BigDecimal.ZERO;
        BigDecimal threshold = subtotalThreshold != null ? subtotalThreshold : new BigDecimal("50");
        if (sub.compareTo(threshold) >= 0) {
            return BigDecimal.ZERO;
        }
        if (qualifiesForFreeDelivery(sub)) {
            return BigDecimal.ZERO;
        }
        return configuredDeliveryFee != null ? configuredDeliveryFee : new BigDecimal("1.00");
    }
}
