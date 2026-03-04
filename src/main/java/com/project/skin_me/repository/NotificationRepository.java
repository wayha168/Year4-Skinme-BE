package com.project.skin_me.repository;

import com.project.skin_me.model.Notification;
import com.project.skin_me.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);

    boolean existsByUserAndActionUrl(User user, String actionUrl);

    long countByUserAndStatus(User user, String status);

    @Modifying
    @Query("UPDATE Notification n SET n.status = :toStatus WHERE n.user = :user AND n.status = :fromStatus")
    int markAllByUserAndStatus(User user, String fromStatus, String toStatus);
}
