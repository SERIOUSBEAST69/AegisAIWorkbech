package com.trustai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import java.util.stream.Collectors;

@Component
@Order(0)
public class CompanySchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(CompanySchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public CompanySchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        ensureCompanyTable();
                ensureUnifiedEventTables();
        ensureCompanyColumns();
        ensurePerformanceIndexes();
        seedDefaultCompanies();
        bindLegacyUsersToDefaultCompany();
    }

    private void ensurePerformanceIndexes() {
        ensureIndex("risk_event", "idx_risk_company_status_time", "company_id, status, create_time");
        ensureIndex("audit_log", "idx_audit_user_op_time", "user_id, operation_time");
        ensureIndex("model_call_stat", "idx_model_stat_user_date", "user_id, date");
        ensureIndex("security_event", "idx_sec_company_status_time", "company_id, status, event_time");
        ensureIndex("security_event", "idx_sec_company_severity_time", "company_id, severity, event_time");
        ensureIndex("privacy_event", "idx_privacy_company_time", "company_id, event_time");
        ensureIndex("privacy_event", "idx_privacy_company_user_time", "company_id, user_id, event_time");
        ensureIndex("client_report", "idx_client_report_company_scan", "company_id, scan_time");
        ensureIndex("client_report", "idx_client_report_company_client_scan", "company_id, client_id, scan_time");
        ensureIndex("client_scan_queue", "idx_client_queue_company_download", "company_id, download_time");
    }

        private void ensureUnifiedEventTables() {
                jdbcTemplate.execute("""
                        CREATE TABLE IF NOT EXISTS governance_event (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            company_id BIGINT,
                            user_id BIGINT,
                            username VARCHAR(128),
                            event_type VARCHAR(64) NOT NULL,
                            source_module VARCHAR(64) NOT NULL,
                            severity VARCHAR(20) DEFAULT 'medium',
                            status VARCHAR(20) DEFAULT 'pending',
                            title VARCHAR(255),
                            description TEXT,
                            source_event_id VARCHAR(64),
                            attack_type VARCHAR(64),
                            policy_version BIGINT,
                            payload_json LONGTEXT,
                            handler_id BIGINT,
                            dispose_note VARCHAR(500),
                            event_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            disposed_at TIMESTAMP NULL,
                            create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            INDEX idx_governance_company (company_id),
                            INDEX idx_governance_user (user_id),
                            INDEX idx_governance_type (event_type),
                            INDEX idx_governance_status (status),
                            INDEX idx_governance_time (event_time)
                        )
                        """);

                jdbcTemplate.execute("""
                        CREATE TABLE IF NOT EXISTS adversarial_record (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            company_id BIGINT,
                            user_id BIGINT,
                            username VARCHAR(128),
                            governance_event_id BIGINT,
                            scenario VARCHAR(64),
                            policy_version BIGINT,
                            result_json LONGTEXT,
                            effectiveness_analysis LONGTEXT,
                            suggestions_json LONGTEXT,
                            create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                            INDEX idx_adversarial_company (company_id),
                            INDEX idx_adversarial_user (user_id),
                            INDEX idx_adversarial_event (governance_event_id)
                        )
                        """);
        }

    private void ensureCompanyTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS company (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              company_code VARCHAR(64) NOT NULL,
              company_name VARCHAR(128) NOT NULL,
              status TINYINT DEFAULT 1,
              create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);
        log.info("Schema check complete: company");
    }

    private void ensureCompanyColumns() {
        ensureColumn("sys_user", "company_id", "BIGINT");
        ensureColumn("sys_user", "nickname", "VARCHAR(50)");
        ensureColumn("sys_user", "avatar", "VARCHAR(255)");
        ensureColumn("sys_user", "device_id", "VARCHAR(128)");
        ensureColumn("sys_user", "organization_type", "VARCHAR(64)");
        ensureColumn("sys_user", "login_type", "VARCHAR(32)");
        ensureColumn("sys_user", "wechat_open_id", "VARCHAR(128)");
        ensureColumn("sys_user", "account_type", "VARCHAR(20)");
        ensureColumn("sys_user", "account_status", "VARCHAR(20)");
        ensureColumn("sys_user", "approved_by", "BIGINT");
        ensureColumn("sys_user", "reject_reason", "VARCHAR(255)");
        ensureColumn("sys_user", "approved_at", "TIMESTAMP");
        ensureColumn("sys_user", "last_policy_pull_time", "TIMESTAMP");
        ensureColumn("role", "company_id", "BIGINT");
        ensureColumn("data_asset", "company_id", "BIGINT");
        ensureColumn("risk_event", "company_id", "BIGINT");
        ensureColumn("security_event", "company_id", "BIGINT");
        ensureColumn("security_event", "policy_version", "BIGINT");
        ensureColumn("approval_request", "company_id", "BIGINT");
        ensureColumn("approval_request", "process_instance_id", "VARCHAR(64)");
        ensureColumn("approval_request", "task_id", "VARCHAR(64)");
        ensureColumn("subject_request", "company_id", "BIGINT");
        ensureColumn("compliance_policy", "company_id", "BIGINT");
        ensureColumn("client_report", "company_id", "BIGINT");
        ensureColumn("client_scan_queue", "company_id", "BIGINT");
        ensureColumn("privacy_event", "company_id", "BIGINT");
        ensureColumn("privacy_event", "policy_version", "BIGINT");
        ensureColumn("privacy_event", "severity", "VARCHAR(20)");
    }

    private void ensureColumn(String table, String column, String type) {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.columns " +
                    "WHERE table_schema = DATABASE() AND table_name = ? AND column_name = ?",
                Integer.class,
                table,
                column
            );
            if (exists != null && exists > 0) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + type);
        } catch (Exception ex) {
            log.debug("Skip migration for {}.{}: {}", table, column, ex.getMessage());
        }
    }

    private void ensureIndex(String table, String indexName, String columns) {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.statistics " +
                    "WHERE table_schema = DATABASE() AND table_name = ? AND index_name = ?",
                Integer.class,
                table,
                indexName
            );
            if (exists != null && exists > 0) {
                return;
            }
            jdbcTemplate.execute("CREATE INDEX " + indexName + " ON " + table + " (" + columns + ")");
        } catch (Exception ex) {
            log.debug("Skip index migration for {}.{}: {}", table, indexName, ex.getMessage());
        }
    }

    private void seedDefaultCompanies() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM company", Integer.class);
        if (count != null && count > 0) {
            return;
        }
        jdbcTemplate.update("""
            INSERT INTO company (company_code, company_name, status, create_time, update_time)
            VALUES
            ('aegis-default', 'Aegis 默认公司', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
            ('aegis-sandbox', 'Aegis 沙箱公司', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """);
    }

    private void bindLegacyUsersToDefaultCompany() {
        try {
            jdbcTemplate.update("UPDATE sys_user SET company_id = 1 WHERE company_id IS NULL");
            jdbcTemplate.update("UPDATE sys_user SET account_type = 'demo' WHERE account_type IS NULL OR account_type = ''");
            jdbcTemplate.update("UPDATE sys_user SET account_status = CASE WHEN status = 0 THEN 'disabled' ELSE 'active' END WHERE account_status IS NULL OR account_status = ''");
            jdbcTemplate.update("UPDATE role SET company_id = 1 WHERE company_id IS NULL");
            jdbcTemplate.update("UPDATE approval_request SET company_id = 1 WHERE company_id IS NULL");
            jdbcTemplate.update("UPDATE subject_request SET company_id = 1 WHERE company_id IS NULL");
            jdbcTemplate.update("UPDATE compliance_policy SET company_id = 1 WHERE company_id IS NULL");
            jdbcTemplate.update("UPDATE client_report SET company_id = 1 WHERE company_id IS NULL");
            jdbcTemplate.update("UPDATE client_scan_queue SET company_id = 1 WHERE company_id IS NULL");
            jdbcTemplate.update("UPDATE privacy_event SET company_id = 1 WHERE company_id IS NULL");
            jdbcTemplate.update("UPDATE security_event SET policy_version = 1 WHERE policy_version IS NULL");
            jdbcTemplate.update("UPDATE privacy_event SET policy_version = 1 WHERE policy_version IS NULL");
            String usernames = DemoAccountCatalog.demoAccountSeeds().stream()
                .map(seed -> "'" + seed.username() + "'")
                .collect(Collectors.joining(","));
            if (!usernames.isBlank()) {
                jdbcTemplate.update("UPDATE sys_user SET company_id = 1 WHERE username IN (" + usernames + ")");
            }
        } catch (Exception ex) {
            log.debug("Skip binding legacy users to default company: {}", ex.getMessage());
        }
    }
}
