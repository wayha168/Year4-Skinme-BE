package com.project.skin_me.repository;

import com.project.skin_me.model.Cart;
import com.project.skin_me.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;


public interface CartRepository extends JpaRepository<Cart,Long> {

    Optional<Cart> findByUserIdAndActive(Long userId, boolean active);

    Cart findByUserId(Long userId);
    
    @Query("SELECT DISTINCT c FROM Cart c " +
           "LEFT JOIN FETCH c.user u " +
           "LEFT JOIN FETCH u.roles " +
           "LEFT JOIN FETCH c.items i " +
           "LEFT JOIN FETCH i.product p " +
           "LEFT JOIN FETCH p.category " +
           "WHERE c.user.id = :userId")
    Cart findByUserIdWithRelations(@Param("userId") Long userId);
    
    @Query("SELECT DISTINCT c FROM Cart c " +
           "LEFT JOIN FETCH c.user u " +
           "LEFT JOIN FETCH u.roles " +
           "LEFT JOIN FETCH c.items i " +
           "LEFT JOIN FETCH i.product p " +
           "LEFT JOIN FETCH p.category " +
           "WHERE c.user.id = :userId AND c.active = :active")
    Optional<Cart> findByUserIdAndActiveWithRelations(@Param("userId") Long userId, @Param("active") boolean active);
}
