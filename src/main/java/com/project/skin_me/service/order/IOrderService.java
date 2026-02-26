package com.project.skin_me.service.order;

import com.project.skin_me.dto.OrderDto;
import com.project.skin_me.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface IOrderService {
    Order placeOrderItem(Long userId);
    OrderDto getOrder(Long orderId);
    Order getOrderById(Long orderId);
    List<OrderDto> getUserOrders(Long userId);
    List<OrderDto> getAllUserOrders();
    /** Paginated: only fetches the requested page from DB. */
    Page<OrderDto> getAllUserOrders(Pageable pageable);
    /** Paginated: only fetches the requested page from DB for the user. */
    Page<OrderDto> getUserOrders(Long userId, Pageable pageable);
    OrderDto convertToDto(Order order);
    void updateOrder(Order order);
    Optional<Order> getOrderByStripeSessionId(String sessionId);
    void confirmOrderPayment(Order order);
    Order markAsShipped(Long orderId, String trackingNumber);
    Order markAsDelivered(Long orderId);
}