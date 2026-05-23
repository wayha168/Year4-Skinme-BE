package com.project.skin_me.service.order;

import com.project.skin_me.enums.PromotionType;
import com.project.skin_me.model.Promotion;
import com.project.skin_me.repository.PromotionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * White-box unit tests for checkout delivery-fee logic.
 * Techniques: equivalence partitioning, boundary value analysis, decision-table paths, mocking.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeliveryFeeServiceTest {

    @Mock
    private PromotionRepository promotionRepository;

    @InjectMocks
    private DeliveryFeeService deliveryFeeService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(deliveryFeeService, "subtotalThreshold", new BigDecimal("50"));
        ReflectionTestUtils.setField(deliveryFeeService, "configuredDeliveryFee", new BigDecimal("1.00"));
        when(promotionRepository.findActivePromotionsByType(eq(PromotionType.FREE_DELIVERY), any(LocalDateTime.class)))
                .thenReturn(Collections.emptyList());
    }

    @Nested
    @DisplayName("computeDeliveryFee — happy path")
    class ComputeDeliveryFeeHappy {

        @Test
        @DisplayName("TC-WB-01: subtotal at threshold → zero fee (boundary)")
        void subtotalAtThreshold_returnsZero() {
            assertThat(deliveryFeeService.computeDeliveryFee(new BigDecimal("50")))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("TC-WB-02: subtotal above threshold → zero fee")
        void subtotalAboveThreshold_returnsZero() {
            assertThat(deliveryFeeService.computeDeliveryFee(new BigDecimal("100.00")))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("TC-WB-03: active free-delivery promo with no minimum → zero fee below threshold")
        void freeDeliveryPromoNoMinimum_waivesFee() {
            Promotion promo = freeDeliveryPromo(null);
            when(promotionRepository.findActivePromotionsByType(eq(PromotionType.FREE_DELIVERY), any(LocalDateTime.class)))
                    .thenReturn(List.of(promo));

            assertThat(deliveryFeeService.computeDeliveryFee(new BigDecimal("10.00")))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("TC-WB-04: free-delivery promo with min met → zero fee")
        void freeDeliveryPromoMinMet_waivesFee() {
            Promotion promo = freeDeliveryPromo(new BigDecimal("25.00"));
            when(promotionRepository.findActivePromotionsByType(eq(PromotionType.FREE_DELIVERY), any(LocalDateTime.class)))
                    .thenReturn(List.of(promo));

            assertThat(deliveryFeeService.computeDeliveryFee(new BigDecimal("25.00")))
                    .isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("computeDeliveryFee — sad path")
    class ComputeDeliveryFeeSad {

        @Test
        @DisplayName("TC-WB-05: subtotal below threshold, no promo → configured fee")
        void subtotalBelowThreshold_noPromo_chargesFee() {
            assertThat(deliveryFeeService.computeDeliveryFee(new BigDecimal("49.99")))
                    .isEqualByComparingTo(new BigDecimal("1.00"));
        }

        @Test
        @DisplayName("TC-WB-06: null subtotal treated as zero → fee charged")
        void nullSubtotal_chargesFee() {
            assertThat(deliveryFeeService.computeDeliveryFee(null))
                    .isEqualByComparingTo(new BigDecimal("1.00"));
        }

        @Test
        @DisplayName("TC-WB-07: free-delivery promo min not met → fee still charged")
        void freeDeliveryMinNotMet_chargesFee() {
            Promotion promo = freeDeliveryPromo(new BigDecimal("30.00"));
            when(promotionRepository.findActivePromotionsByType(eq(PromotionType.FREE_DELIVERY), any(LocalDateTime.class)))
                    .thenReturn(List.of(promo));

            assertThat(deliveryFeeService.computeDeliveryFee(new BigDecimal("29.99")))
                    .isEqualByComparingTo(new BigDecimal("1.00"));
        }

        @ParameterizedTest(name = "subtotal={0} just below threshold")
        @CsvSource({"0", "1", "49.99"})
        @DisplayName("TC-WB-08: boundary values just under threshold (BVA)")
        void justBelowThreshold_chargesFee(String subtotal) {
            assertThat(deliveryFeeService.computeDeliveryFee(new BigDecimal(subtotal)))
                    .isEqualByComparingTo(new BigDecimal("1.00"));
        }
    }

    @Nested
    @DisplayName("qualifiesForFreeDelivery")
    class QualifiesForFreeDelivery {

        @Test
        @DisplayName("TC-WB-09: no active promos → false")
        void noPromos_returnsFalse() {
            assertThat(deliveryFeeService.qualifiesForFreeDelivery(new BigDecimal("100"))).isFalse();
        }

        @Test
        @DisplayName("TC-WB-10: promo with zero minimum → true for any subtotal")
        void promoWithZeroMinimum_returnsTrue() {
            Promotion promo = freeDeliveryPromo(BigDecimal.ZERO);
            when(promotionRepository.findActivePromotionsByType(eq(PromotionType.FREE_DELIVERY), any(LocalDateTime.class)))
                    .thenReturn(List.of(promo));

            assertThat(deliveryFeeService.qualifiesForFreeDelivery(new BigDecimal("0.01"))).isTrue();
        }

        @Test
        @DisplayName("TC-WB-11: first promo fails min, second succeeds (decision table)")
        void multiplePromos_secondQualifies() {
            Promotion strict = freeDeliveryPromo(new BigDecimal("100"));
            Promotion lenient = freeDeliveryPromo(new BigDecimal("10"));
            when(promotionRepository.findActivePromotionsByType(eq(PromotionType.FREE_DELIVERY), any(LocalDateTime.class)))
                    .thenReturn(List.of(strict, lenient));

            assertThat(deliveryFeeService.qualifiesForFreeDelivery(new BigDecimal("15"))).isTrue();
        }

        @Test
        @DisplayName("TC-WB-12: custom configured fee when threshold null in config fallback")
        void customDeliveryFeeAmount() {
            ReflectionTestUtils.setField(deliveryFeeService, "configuredDeliveryFee", new BigDecimal("2.50"));
            assertThat(deliveryFeeService.computeDeliveryFee(new BigDecimal("10")))
                    .isEqualByComparingTo(new BigDecimal("2.50"));
        }
    }

    private static Promotion freeDeliveryPromo(BigDecimal minimumOrderAmount) {
        Promotion p = new Promotion();
        p.setPromotionType(PromotionType.FREE_DELIVERY);
        p.setFreeDelivery(true);
        p.setMinimumOrderAmount(minimumOrderAmount);
        return p;
    }
}
