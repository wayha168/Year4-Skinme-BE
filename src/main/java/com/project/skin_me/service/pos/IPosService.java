package com.project.skin_me.service.pos;

import com.project.skin_me.dto.PosCalculateResultDto;
import com.project.skin_me.dto.PosLineItemDto;
import com.project.skin_me.enums.PaymentMethod;
import com.project.skin_me.model.Order;
import com.project.skin_me.model.User;

import java.util.List;
import java.util.Map;

public interface IPosService {

    PosCalculateResultDto calculate(List<PosLineItemDto> items);

    Order createPosOrder(User cashier, List<PosLineItemDto> items, String fulfillmentType);

    Map<String, Object> completePayment(Order order, User cashier, PaymentMethod method, String cardLast4);

    String buildReceiptMarkdown(Order order, String cashierDisplayName);

    /** Pre-payment order summary for POS cash/card confirmation modals. */
    String buildOrderSummaryMarkdown(Order order, String cashierDisplayName);

    /**
     * Cancel POS/internal order that is still pending payment (prevents completing
     * later).
     */
    void cancelPosOrder(Long orderId);
}
