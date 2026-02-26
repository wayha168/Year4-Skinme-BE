package com.project.skin_me.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * One-time migration: ensure category.image column is LONGTEXT so it can store
 * long URLs or legacy base64 data. Safe to run multiple times (idempotent for MySQL).
 */
@Component
@Order(Integer.MIN_VALUE)
public class CategoryImageColumnMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public CategoryImageColumnMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            jdbcTemplate.execute("ALTER TABLE category MODIFY COLUMN image LONGTEXT");
        } catch (Exception e) {
            // Ignore: table might not exist yet (Hibernate will create it), or already LONGTEXT
        }
    }
}
