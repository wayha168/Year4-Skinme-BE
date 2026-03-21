package com.project.skin_me.service.delivery;

import com.project.skin_me.enums.LogisticCompany;
import com.project.skin_me.model.Order;

import java.util.Map;

public interface IDeliveryService {
    Order createShipment(Long orderId);
    Order markAsDelivered(Long orderId, LogisticCompany logisticCompany);
    Order trackOrder(Long orderId);
    Order updateDeliveryAddress(Long orderId, Map<String, Object> addressData);
    Order clearDeliveryAddress(Long orderId);
}
