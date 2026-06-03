package com.project.skin_me.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Widens {@code payments.method} and {@code payments.status} so values like
 * {@code CASH}, {@code CREDIT_CARD}, and {@code SUCCESS} are not truncated
 * (common when the column was created as a short MySQL ENUM).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public void run(ApplicationArguments args) {
        widenColumn("method");
        widenColumn("status");
        widenKhqrPayWayPublicKey();
    }

    private void widenColumn(String column) {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE payments MODIFY COLUMN " + column + " VARCHAR(32) NOT NULL");
            log.info("payments.{} column widened to VARCHAR(32)", column);
        } catch (Exception e) {
            log.warn("Could not widen payments.{} (run manually if POS cash pay fails): {}",
                    column, e.getMessage());
        }
    }

    private void widenKhqrPayWayPublicKey() {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE khqr_bank_accounts MODIFY COLUMN payway_public_key VARCHAR(512)");
            log.info("khqr_bank_accounts.payway_public_key column widened to VARCHAR(512)");
        } catch (Exception e) {
            log.warn("Could not widen khqr_bank_accounts.payway_public_key: {}", e.getMessage());
        }
    }
}
