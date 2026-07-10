package com.project.skin_me.scheduler;

import com.project.skin_me.service.promotion.IPromotionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically marks promotions as inactive once their deadline has passed.
 */
@Component
public class PromotionExpirationScheduler {

    private static final Logger logger = LoggerFactory.getLogger(PromotionExpirationScheduler.class);

    private final IPromotionService promotionService;

    public PromotionExpirationScheduler(IPromotionService promotionService) {
        this.promotionService = promotionService;
    }

    /** Every 5 minutes: set active=false for promotions past deadline. */
    @Scheduled(fixedRate = 300_000)
    public void deactivateExpiredPromotions() {
        try {
            int updated = promotionService.deactivateExpiredPromotions();
            if (updated > 0) {
                logger.debug("Promotion expiration job deactivated {} promotion(s)", updated);
            }
        } catch (Exception e) {
            logger.error("Failed to deactivate expired promotions: {}", e.getMessage(), e);
        }
    }
}
