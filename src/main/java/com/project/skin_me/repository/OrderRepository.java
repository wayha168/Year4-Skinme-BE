package com.project.skin_me.repository;

import com.project.skin_me.enums.OrderStatus;
import com.project.skin_me.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    List<Order> findByUserId(Long userId);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.user")
    List<Order> findAllWithUser();

    Page<Order> findByUserId(Long userId, Pageable pageable);

    long countByOrderStatus(OrderStatus status);

    long countByUser_IdAndOrderStatus(Long userId, OrderStatus status);

    Optional<Order> findByTransactionRef(String transactionRef);

    Optional<Order> findByStripeSessionId(String sessionId);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderItems oi LEFT JOIN FETCH oi.product p LEFT JOIN FETCH p.brand WHERE o.orderId = :id")
    Optional<Order> findByIdWithOrderItemsAndProducts(@Param("id") Long id);

    @Query(value = "SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderItems oi LEFT JOIN FETCH oi.product p LEFT JOIN FETCH p.brand",
           countQuery = "SELECT COUNT(o) FROM Order o")
    Page<Order> findAllWithOrderItems(Pageable pageable);

    @Query(value = "SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.user LEFT JOIN FETCH o.orderItems oi LEFT JOIN FETCH oi.product p LEFT JOIN FETCH p.brand",
           countQuery = "SELECT COUNT(DISTINCT o) FROM Order o")
    Page<Order> findAllWithOrderItemsAndUser(Pageable pageable);

    @Query(value = "SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.orderItems oi LEFT JOIN FETCH oi.product p LEFT JOIN FETCH p.brand WHERE o.user.id = :userId",
           countQuery = "SELECT COUNT(o) FROM Order o WHERE o.user.id = :userId")
    Page<Order> findByUserIdWithOrderItems(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT COALESCE(SUM(o.orderTotalAmount), 0) FROM Order o")
    BigDecimal sumOrderTotalAmount();

    @Query("SELECT o.orderStatus, COUNT(o) FROM Order o GROUP BY o.orderStatus")
    List<Object[]> countGroupByOrderStatus();

    @Query(value = "SELECT YEAR(o.order_date) AS y, MONTH(o.order_date) AS m, COALESCE(SUM(o.order_total_amount), 0) FROM orders o WHERE o.order_date >= :since GROUP BY YEAR(o.order_date), MONTH(o.order_date) ORDER BY y, m", nativeQuery = true)
    List<Object[]> sumRevenueByMonthSince(@Param("since") java.sql.Date since);
}
