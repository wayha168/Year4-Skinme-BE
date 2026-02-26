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
}
