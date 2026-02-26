package com.project.skin_me.repository;

import com.project.skin_me.model.Activity;
import com.project.skin_me.enums.ActivityType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ActivityRepository extends JpaRepository<Activity,Long> {
    List<Activity> findByUserId(Long userId);
    
    List<Activity> findByActivityType(ActivityType activityType);
    
    List<Activity> findByUserIdOrderByTimestampDesc(Long userId);
    
    List<Activity> findAllByOrderByTimestampDesc();
    
    @Query("SELECT a FROM Activity a LEFT JOIN FETCH a.user u LEFT JOIN FETCH u.roles ORDER BY a.timestamp DESC")
    List<Activity> findAllWithUserOrderByTimestampDesc();
    
    List<Activity> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
    
    List<Activity> findByUserIdAndActivityType(Long userId, ActivityType activityType);
}
