package com.project.skin_me.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PosCalculateResultDto {
    private BigDecimal subtotal;
    private BigDecimal deliveryFee;
    private BigDecimal total;
    private int itemCount;
    private List<PosLineDetailDto> lines;
}
