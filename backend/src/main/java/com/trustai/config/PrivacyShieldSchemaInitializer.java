package com.trustai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class PrivacyShieldSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PrivacyShieldSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public PrivacyShieldSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        ensureUserDeviceIdColumn();
        ensurePrivacyEventTable();
    }

    private void ensureUserDeviceIdColumn() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'sys_user' AND column_name = 'device_id'",
                    Integer.class
            );
            if (count != null && count > 0) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE sys_user ADD COLUMN device_id VARCHAR(128) NULL COMMENT '设备ID' AFTER role_id");
            log.info("Schema check complete: sys_user.device_id");
        } catch (Exception ex) {
            try {
                jdbcTemplate.execute("ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS device_id VARCHAR(128)");
                log.info("Schema check complete: sys_user.device_id (fallback)");
            } catch (Exception ignored) {
                log.debug("Skip sys_user.device_id migration: {}", ignored.getMessage());
            }
        }
    }

    private void ensurePrivacyEventTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS privacy_event (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              user_id VARCHAR(128) NOT NULL,
              event_type VARCHAR(64) DEFAULT 'SENSITIVE_TEXT',
              content_masked TEXT,
              source VARCHAR(32) DEFAULT 'extension',
              action VARCHAR(32) DEFAULT 'detect',
              device_id VARCHAR(128),
              hostname VARCHAR(128),
              window_title VARCHAR(255),
              matched_types VARCHAR(255),
              event_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              INDEX idx_privacy_user(user_id),
              INDEX idx_privacy_source(source),
              INDEX idx_privacy_time(event_time)
            )
            """);
        log.info("Schema check complete: privacy_event");
    }
}
