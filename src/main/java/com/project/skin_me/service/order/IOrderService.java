package com.project.skin_me.service.order;

import com.project.skin_me.dto.OrderDto;
import com.project.skin_me.model.Order;

import java.util.List;
import java.util.Optional;

public interface IOrderService {
    Order placeOrderItem(Long userId);
    OrderDto getOrder(Long orderId);
    Order getOrderById(Long orderId);
    List<OrderDto> getUserOrders(Long userId);
    List<OrderDto> getAllUserOrders();
    OrderDto convertToDto(Order order);
    void updateOrder(Order order);
    Optional<Order> getOrderByStripeSessionId(String sessionId);
    void confirmOrderPayment(Order order);
    Order markAsShipped(Long orderId, String trackingNumber);
    Order markAsDelivered(Long orderId);
}