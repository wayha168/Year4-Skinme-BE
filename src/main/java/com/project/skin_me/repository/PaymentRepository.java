package com.project.skin_me.repository;

import com.project.skin_me.enums.OrderStatus;
import com.project.skin_me.model.Order;
import com.project.skin_me.model.Payment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByTransactionRef(String transactionRef);

    @Query("SELECT p FROM Payment p WHERE p.order.user.id = :userId")
    List<Payment> findByOrderUserId(@Param("userId") Long userId);

    @Query(value = "SELECT p FROM Payment p WHERE p.order.user.id = :userId",
           countQuery = "SELECT COUNT(p) FROM Payment p WHERE p.order.user.id = :userId")
    Page<Payment> findByOrderUserId(@Param("userId") Long userId, Pageable pageable);

    long countByStatus(OrderStatus status);

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p")
    BigDecimal sumAllAmounts();

    @Query("SELECT COALESCE(SUM(p.amount), 0) FROM Payment p WHERE p.order.user.id = :userId")
    BigDecimal sumAmountsByUserId(@Param("userId") Long userId);
    
    @Query("SELECT p FROM Payment p WHERE p.order = :order")
    Optional<Payment> findByOrder(@Param("order") Order order);
}
