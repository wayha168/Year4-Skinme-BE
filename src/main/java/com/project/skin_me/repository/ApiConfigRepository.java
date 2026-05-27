package com.project.skin_me.repository;

import com.project.skin_me.model.config.ApiConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ApiConfigRepository extends JpaRepository<ApiConfig, Long> {
}