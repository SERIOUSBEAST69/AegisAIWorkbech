package com.trustai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class AwardSchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AwardSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public AwardSchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        ensureComplianceEvidenceTable();
        ensureReliabilityDrillTable();
        ensureApiMetricHistoryTable();
        ensureWebVitalMetricTable();
        ensureSlowQueryLogTable();
        ensureAuditHashChainTable();
        ensureExternalAnchorRecordTable();
    }

    private void ensureComplianceEvidenceTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS compliance_evidence_record (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              company_id BIGINT NOT NULL,
              evidence_type VARCHAR(64) NOT NULL,
              period_start TIMESTAMP NULL,
              period_end TIMESTAMP NULL,
              policy_hit_count BIGINT DEFAULT 0,
              audit_trace_count BIGINT DEFAULT 0,
              remediation_closed_count BIGINT DEFAULT 0,
              content_json LONGTEXT,
              evidence_hash VARCHAR(128),
              generated_by BIGINT,
              create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);
        ensureIndex("compliance_evidence_record", "idx_cer_company_time", "company_id, create_time");
        ensureIndex("compliance_evidence_record", "idx_cer_type", "evidence_type");
        log.info("Schema check complete: compliance_evidence_record");
    }

    private void ensureReliabilityDrillTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS reliability_drill_record (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              company_id BIGINT NOT NULL,
              scenario VARCHAR(64) NOT NULL,
              target_path VARCHAR(255),
              inject_path VARCHAR(255),
              baseline_success_rate DOUBLE DEFAULT 0,
              baseline_p95_ms BIGINT DEFAULT 0,
              injected_error_rate DOUBLE DEFAULT 0,
              recovery_seconds BIGINT DEFAULT 0,
              sli_availability DOUBLE DEFAULT 0,
              sli_latency_ms BIGINT DEFAULT 0,
              slo_status VARCHAR(32) DEFAULT 'unknown',
              alert_event_id BIGINT,
              detail_json LONGTEXT,
              executed_by BIGINT,
              create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);
        ensureIndex("reliability_drill_record", "idx_rdr_company_time", "company_id, create_time");
        ensureIndex("reliability_drill_record", "idx_rdr_scenario", "scenario");
        log.info("Schema check complete: reliability_drill_record");
    }

    private void ensureApiMetricHistoryTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS api_metric_history (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              company_id BIGINT NOT NULL,
              api VARCHAR(255) NOT NULL,
              total BIGINT DEFAULT 0,
              success BIGINT DEFAULT 0,
              fail BIGINT DEFAULT 0,
              p50 BIGINT DEFAULT 0,
              p95 BIGINT DEFAULT 0,
              p99 BIGINT DEFAULT 0,
              max BIGINT DEFAULT 0,
              sampled_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);
        ensureIndex("api_metric_history", "idx_amh_company_time", "company_id, sampled_at");
        ensureIndex("api_metric_history", "idx_amh_api", "api");
        log.info("Schema check complete: api_metric_history");
    }

    private void ensureWebVitalMetricTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS web_vital_metric (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              company_id BIGINT NOT NULL,
              metric_name VARCHAR(32) NOT NULL,
              metric_value DOUBLE DEFAULT 0,
              rating VARCHAR(32),
              metric_id VARCHAR(128),
              navigation_type VARCHAR(32),
              path VARCHAR(255),
              event_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);
        ensureIndex("web_vital_metric", "idx_wvm_company_time", "company_id, event_time");
        ensureIndex("web_vital_metric", "idx_wvm_metric", "metric_name");
        log.info("Schema check complete: web_vital_metric");
    }

    private void ensureSlowQueryLogTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS slow_query_log (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              company_id BIGINT NOT NULL,
              mapper_method VARCHAR(255) NOT NULL,
              elapsed_ms BIGINT NOT NULL,
              args_digest VARCHAR(128),
              query_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);
        ensureIndex("slow_query_log", "idx_sql_company_time", "company_id, query_time");
        ensureIndex("slow_query_log", "idx_sql_method", "mapper_method");
        log.info("Schema check complete: slow_query_log");
    }

    private void ensureAuditHashChainTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS audit_hash_chain (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              company_id BIGINT NOT NULL,
              audit_log_id BIGINT NOT NULL,
              prev_hash VARCHAR(128),
              current_hash VARCHAR(128) NOT NULL,
              create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);
        ensureIndex("audit_hash_chain", "idx_ahc_company_time", "company_id, create_time");
        ensureIndex("audit_hash_chain", "idx_ahc_audit_log", "audit_log_id");
        log.info("Schema check complete: audit_hash_chain");
    }

    private void ensureExternalAnchorRecordTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS external_anchor_record (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              company_id BIGINT NOT NULL,
              evidence_type VARCHAR(64) NOT NULL,
              evidence_ref VARCHAR(128),
              payload_hash VARCHAR(128) NOT NULL,
              provider VARCHAR(64) NOT NULL,
              source_time VARCHAR(64),
              nonce VARCHAR(64),
              signature_base64 LONGTEXT,
              key_fingerprint VARCHAR(128),
              verify_status VARCHAR(20) DEFAULT 'unknown',
              detail_json LONGTEXT,
              create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);
        ensureIndex("external_anchor_record", "idx_ext_anchor_company_time", "company_id, create_time");
        ensureIndex("external_anchor_record", "idx_ext_anchor_payload", "payload_hash");
        ensureIndex("external_anchor_record", "idx_ext_anchor_evidence", "evidence_type, evidence_ref");
        log.info("Schema check complete: external_anchor_record");
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
}
