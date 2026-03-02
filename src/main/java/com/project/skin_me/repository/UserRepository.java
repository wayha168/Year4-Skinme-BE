package com.project.skin_me.repository;

import com.project.skin_me.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    /** Count users registered after the given time (for dashboard stats without loading all users). */
    long countByRegistrationDateAfter(LocalDateTime dateTime);
}