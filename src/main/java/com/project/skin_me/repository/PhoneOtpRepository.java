package com.project.skin_me.repository;

import com.project.skin_me.model.PhoneOtp;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PhoneOtpRepository extends JpaRepository<PhoneOtp, Long> {

    Optional<PhoneOtp> findByPhone(String phone);

    void deleteByPhone(String phone);
}
