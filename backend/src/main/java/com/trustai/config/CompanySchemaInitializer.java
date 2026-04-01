package com.trustai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
        ensureCompanyInviteTable();
        ensureRoleSelfRegisterChangeTable();
        ensureUnifiedEventTables();
        ensureUserRoleTable();
        ensureUserRecycleBinTable();
        ensureGovernanceChangeTables();
        ensureCompanyColumns();
        ensurePerformanceIndexes();
        ensureTenantForeignKeys();
        seedDefaultCompanies();
        bindLegacyUsersToDefaultCompany();
        seedDefaultSodRules();
    }

    private void ensureTenantForeignKeys() {
        ensureForeignKey("sys_user", "fk_user_company", "company_id", "company", "id");
        ensureForeignKey("role", "fk_role_company", "company_id", "company", "id");
        ensureForeignKey("risk_event", "fk_risk_company", "company_id", "company", "id");
        ensureForeignKey("approval_request", "fk_approval_company", "company_id", "company", "id");
        ensureForeignKey("data_asset", "fk_asset_company", "company_id", "company", "id");
        ensureForeignKey("governance_event", "fk_governance_company", "company_id", "company", "id");
        ensureForeignKey("privacy_event", "fk_privacy_company", "company_id", "company", "id");
        ensureForeignKey("security_event", "fk_security_company", "company_id", "company", "id");
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
        ensureIndex("ai_call_log", "idx_ai_call_company_time", "company_id, create_time");
        ensureIndex("ai_call_log", "idx_ai_call_company_user_time", "company_id, user_id, create_time");
        ensureIndex("permission", "idx_permission_company_code", "company_id, code");
        ensureIndex("role", "idx_role_company_code", "company_id, code");
        ensureIndex("company_invite_code", "idx_company_invite_code", "invite_code");
        ensureIndex("company_invite_code", "idx_company_invite_company_status", "company_id, status");
        ensureIndex("role_self_register_change", "idx_role_src_company_status", "company_id, status");
        ensureIndex("role_self_register_change", "idx_role_src_role", "role_id");
        ensureIndex("user_role", "idx_user_role_user", "user_id");
        ensureIndex("user_role", "idx_user_role_role", "role_id");
        ensureIndex("governance_event", "idx_governance_company", "company_id");
        ensureIndex("governance_event", "idx_governance_user", "user_id");
        ensureIndex("governance_event", "idx_governance_type", "event_type");
        ensureIndex("governance_event", "idx_governance_status", "status");
        ensureIndex("governance_event", "idx_governance_time", "event_time");
        ensureIndex("adversarial_record", "idx_adversarial_company", "company_id");
        ensureIndex("adversarial_record", "idx_adversarial_user", "user_id");
        ensureIndex("adversarial_record", "idx_adversarial_event", "governance_event_id");
        ensureIndex("user_recycle_bin", "idx_user_recycle_company_time", "company_id, deleted_at");
        ensureIndex("user_recycle_bin", "idx_user_recycle_user", "user_id");
        ensureIndex("governance_change_request", "idx_gov_change_company_status", "company_id, status, create_time");
        ensureIndex("sod_conflict_rule", "idx_sod_company_scene", "company_id, scenario, enabled");
    }

    private void ensureUserRoleTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_role (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    role_id BIGINT NOT NULL,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private void ensureUserRecycleBinTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS user_recycle_bin (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    company_id BIGINT,
                    user_id BIGINT,
                    username VARCHAR(50),
                    snapshot_json LONGTEXT,
                    deleted_by BIGINT,
                    delete_reason VARCHAR(200),
                    deleted_at TIMESTAMP NULL,
                    restore_status VARCHAR(20) DEFAULT 'deleted',
                    restored_by BIGINT,
                    restored_at TIMESTAMP NULL,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private void ensureGovernanceChangeTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS governance_change_request (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    company_id BIGINT,
                    module VARCHAR(32) NOT NULL,
                    action VARCHAR(20) NOT NULL,
                    target_id BIGINT,
                    payload_json LONGTEXT,
                    status VARCHAR(20) DEFAULT 'pending',
                    risk_level VARCHAR(20) DEFAULT 'HIGH',
                    requester_id BIGINT,
                    requester_role_code VARCHAR(50),
                    approver_id BIGINT,
                    approver_role_code VARCHAR(50),
                    approve_note VARCHAR(500),
                    approved_at TIMESTAMP NULL,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);

        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sod_conflict_rule (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    company_id BIGINT,
                    scenario VARCHAR(64) NOT NULL,
                    role_code_a VARCHAR(50) NOT NULL,
                    role_code_b VARCHAR(50) NOT NULL,
                    enabled TINYINT DEFAULT 1,
                    description VARCHAR(255),
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
    }

    private void seedDefaultSodRules() {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sod_conflict_rule WHERE company_id = 1 AND scenario = 'PRIVILEGE_CHANGE_REVIEW' AND role_code_a = 'ADMIN' AND role_code_b = 'ADMIN'",
                Integer.class
            );
            if (exists != null && exists > 0) {
                return;
            }
            jdbcTemplate.update(
                "INSERT INTO sod_conflict_rule(company_id, scenario, role_code_a, role_code_b, enabled, description, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                1L,
                "PRIVILEGE_CHANGE_REVIEW",
                "ADMIN",
                "ADMIN",
                1,
                "高敏权限变更禁止ADMIN同角色互审，需跨角色复核"
            );
        } catch (Exception ex) {
            log.debug("Skip SoD default rule seed: {}", ex.getMessage());
        }
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
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
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

    private void ensureCompanyInviteTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS company_invite_code (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              company_id BIGINT NOT NULL,
              invite_code VARCHAR(64) NOT NULL,
              status VARCHAR(20) DEFAULT 'active',
              created_by BIGINT,
              expires_at TIMESTAMP NULL,
                            disable_reason VARCHAR(255),
              create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);
                ensureColumn("company_invite_code", "disable_reason", "VARCHAR(255)");
        log.info("Schema check complete: company_invite_code");
    }

    private void ensureRoleSelfRegisterChangeTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS role_self_register_change (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              company_id BIGINT NOT NULL,
              role_id BIGINT NOT NULL,
              role_code VARCHAR(64) NOT NULL,
              requested_allow_self_register BOOLEAN NOT NULL,
              status VARCHAR(20) NOT NULL DEFAULT 'pending',
              requested_by BIGINT NOT NULL,
              reviewed_by BIGINT,
              review_note VARCHAR(255),
              create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);
        log.info("Schema check complete: role_self_register_change");
    }

    private void ensureCompanyColumns() {
        ensureColumn("sys_user", "company_id", "BIGINT");
        ensureColumn("sys_user", "nickname", "VARCHAR(50)");
        ensureColumn("sys_user", "avatar", "VARCHAR(255)");
        ensureColumn("sys_user", "device_id", "VARCHAR(128)");
        ensureColumn("sys_user", "organization_type", "VARCHAR(64)");
        ensureColumn("sys_user", "login_type", "VARCHAR(32)");
        ensureColumn("sys_user", "wechat_open_id", "VARCHAR(128)");
        ensureColumn("sys_user", "job_title", "VARCHAR(128)");
        ensureColumn("sys_user", "account_type", "VARCHAR(20)");
        ensureColumn("sys_user", "account_status", "VARCHAR(20)");
        ensureColumn("sys_user", "approved_by", "BIGINT");
        ensureColumn("sys_user", "reject_reason", "VARCHAR(255)");
        ensureColumn("sys_user", "approved_at", "TIMESTAMP");
        ensureColumn("sys_user", "last_policy_pull_time", "TIMESTAMP");
        ensureColumn("role", "company_id", "BIGINT NOT NULL");
        ensureColumn("role", "allow_self_register", "BOOLEAN DEFAULT FALSE");
        ensureColumn("role", "is_system", "BOOLEAN DEFAULT FALSE");
        ensureColumn("permission", "company_id", "BIGINT NOT NULL");
        ensureColumn("data_asset", "company_id", "BIGINT NOT NULL");
        ensureColumn("risk_event", "company_id", "BIGINT NOT NULL");
        ensureColumn("security_event", "company_id", "BIGINT NOT NULL");
        ensureColumn("security_event", "policy_version", "BIGINT");
        ensureColumn("approval_request", "company_id", "BIGINT NOT NULL");
        ensureColumn("approval_request", "process_instance_id", "VARCHAR(64)");
        ensureColumn("approval_request", "task_id", "VARCHAR(64)");
        ensureColumn("subject_request", "company_id", "BIGINT NOT NULL");
        ensureColumn("compliance_policy", "company_id", "BIGINT NOT NULL");
        ensureColumn("client_report", "company_id", "BIGINT NOT NULL");
        ensureColumn("client_report", "ip_address", "VARCHAR(64)");
        ensureColumn("client_scan_queue", "company_id", "BIGINT NOT NULL");
        ensureColumn("ai_call_log", "company_id", "BIGINT NOT NULL");
        ensureColumn("ai_call_log", "username", "VARCHAR(128)");
        ensureColumn("privacy_event", "company_id", "BIGINT NOT NULL");
        ensureColumn("privacy_event", "policy_version", "BIGINT");
        ensureColumn("privacy_event", "severity", "VARCHAR(20)");
        ensureColumn("sensitive_scan_task", "company_id", "BIGINT");
        ensureColumn("sensitive_scan_task", "user_id", "BIGINT");
        ensureColumn("sensitive_scan_task", "trace_json", "LONGTEXT");
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

    private void ensureForeignKey(String table, String keyName, String column, String refTable, String refColumn) {
        try {
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.table_constraints " +
                    "WHERE table_schema = DATABASE() AND table_name = ? AND constraint_name = ? AND constraint_type = 'FOREIGN KEY'",
                Integer.class,
                table,
                keyName
            );
            if (exists != null && exists > 0) {
                return;
            }
            jdbcTemplate.execute("ALTER TABLE " + table + " ADD CONSTRAINT " + keyName +
                " FOREIGN KEY (" + column + ") REFERENCES " + refTable + "(" + refColumn + ")");
        } catch (Exception ex) {
            log.debug("Skip FK migration for {}.{}: {}", table, keyName, ex.getMessage());
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
            jdbcTemplate.update("UPDATE sys_user SET account_type = 'real' WHERE account_type IS NULL OR account_type = ''");
            jdbcTemplate.update("UPDATE sys_user SET account_status = CASE WHEN status = 0 THEN 'disabled' ELSE 'active' END WHERE account_status IS NULL OR account_status = ''");
            jdbcTemplate.update("UPDATE role SET company_id = 1 WHERE company_id IS NULL");
            jdbcTemplate.update("UPDATE permission SET company_id = 1 WHERE company_id IS NULL");
            jdbcTemplate.update("UPDATE approval_request SET company_id = 1 WHERE company_id IS NULL");
            jdbcTemplate.update("UPDATE subject_request SET company_id = 1 WHERE company_id IS NULL");
            jdbcTemplate.update("UPDATE compliance_policy SET company_id = 1 WHERE company_id IS NULL");
            jdbcTemplate.update("UPDATE client_report SET company_id = 1 WHERE company_id IS NULL");
            jdbcTemplate.update("UPDATE client_scan_queue SET company_id = 1 WHERE company_id IS NULL");
            jdbcTemplate.update("UPDATE ai_call_log l LEFT JOIN sys_user u ON l.user_id = u.id SET l.company_id = COALESCE(l.company_id, u.company_id, 1)");
            jdbcTemplate.update("UPDATE ai_call_log l LEFT JOIN sys_user u ON l.user_id = u.id SET l.username = COALESCE(l.username, u.username) WHERE l.username IS NULL OR l.username = ''");
            jdbcTemplate.update("UPDATE privacy_event SET company_id = 1 WHERE company_id IS NULL");
            jdbcTemplate.update("UPDATE security_event SET policy_version = 1 WHERE policy_version IS NULL");
            jdbcTemplate.update("UPDATE privacy_event SET policy_version = 1 WHERE policy_version IS NULL");
        } catch (Exception ex) {
            log.debug("Skip binding legacy users to default company: {}", ex.getMessage());
        }
    }
}
