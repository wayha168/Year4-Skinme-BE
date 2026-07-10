package com.project.skin_me.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosLineDetailDto {
    private Long productId;
    private String productName;
    private String barcode;
    private int quantity;
    private BigDecimal unitPrice;
    private BigDecimal lineTotal;
    private int inventory;
}
