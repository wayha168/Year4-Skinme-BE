package com.project.skin_me.repository;

import com.project.skin_me.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserId(Long userId);

    Optional<Order> findByTransactionRef(String transactionRef);

    Optional<Order> findByStripeSessionId(String sessionId);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderItems oi LEFT JOIN FETCH oi.product p LEFT JOIN FETCH p.brand WHERE o.orderId = :id")
    Optional<Order> findByIdWithOrderItemsAndProducts(@Param("id") Long id);
}
