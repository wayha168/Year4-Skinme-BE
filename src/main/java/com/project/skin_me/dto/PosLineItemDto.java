package com.project.skin_me.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PosLineItemDto {
    private Long productId;
    private int quantity;
}
