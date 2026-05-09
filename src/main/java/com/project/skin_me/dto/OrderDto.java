package com.project.skin_me.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderDto {

    private Long orderId;
    private Long userId;
    private String userEmail;
    private String userName;
    private LocalDate orderDate;
    /** Line items sum before delivery fee (null on legacy orders). */
    private BigDecimal itemsSubtotalAmount;
    /** Delivery fee included in {@link #totalAmount} when applicable. */
    private BigDecimal deliveryFeeAmount;
    private BigDecimal totalAmount;
    private String orderStatus;
    private String trackingNumber;
    private List<OrderItemDto> orderItems;

    // Delivery address
    private String deliveryStreet;
    private String deliveryCity;
    private String deliveryProvince;
    private String deliveryPostalCode;
    private String deliveryAddressFull;
    private Double deliveryLatitude;
    private Double deliveryLongitude;

    /** VET, JNT (J&T), DHL — set at checkout or when marking delivered. */
    private String logisticCompany;
}
