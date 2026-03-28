package com.trustai.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ensure threat-monitoring tables exist on startup for legacy databases.
 */
@Component
@Order(0)
public class SecuritySchemaInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SecuritySchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;

    public SecuritySchemaInitializer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        ensureSecurityEventTable();
        ensureSecurityRuleTable();
        seedDefaultSecurityRules();
    }

    private void ensureSecurityEventTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS security_event (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            company_id BIGINT,
              event_type VARCHAR(64) NOT NULL,
              file_path VARCHAR(512),
              target_addr VARCHAR(256),
              employee_id VARCHAR(128),
              hostname VARCHAR(128),
              file_size BIGINT,
              severity VARCHAR(20) DEFAULT 'medium',
              status VARCHAR(20) DEFAULT 'pending',
              source VARCHAR(64) DEFAULT 'agent',
              operator_id BIGINT,
              event_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
              create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);
        log.info("Schema check complete: security_event");
    }

    private void ensureSecurityRuleTable() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS security_detection_rule (
              id BIGINT AUTO_INCREMENT PRIMARY KEY,
              name VARCHAR(100) NOT NULL,
              sensitive_extensions VARCHAR(500) DEFAULT '.docx,.pdf,.xlsx,.pptx,.key,.csv,.sql,.env,.pem,.pfx',
              sensitive_paths VARCHAR(1000) DEFAULT 'C:/Users,/home,/Documents,/Desktop',
              alert_threshold_bytes BIGINT DEFAULT 1048576,
              enabled TINYINT(1) DEFAULT 1,
              description VARCHAR(500),
              create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
              update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """);
        log.info("Schema check complete: security_detection_rule");
    }

    private void seedDefaultSecurityRules() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM security_detection_rule", Integer.class);
        if (count != null && count > 0) {
            return;
        }

        jdbcTemplate.update("""
            INSERT INTO security_detection_rule
            (name, sensitive_extensions, sensitive_paths, alert_threshold_bytes, enabled, description, create_time, update_time)
            VALUES
            ('默认文件窃取检测规则', '.docx,.pdf,.xlsx,.pptx,.key,.csv,.sql,.env,.pem,.pfx,.db,.bak', 'C:/Users,/home,/Documents,/Desktop,/confidential', 102400, 1, '检测常见敏感文档类型的非授权外传行为', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
            ('源代码泄露规则', '.java,.py,.go,.ts,.js,.env,.yml,.yaml,.json,.xml,.properties', '/src,/source,/code,/project', 51200, 1, '检测源码目录下文件的批量外传', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
            ('高价值数据规则', '.sql,.bak,.dump,.tar,.zip', '/backup,/export,/archive', 524288, 1, '检测数据库备份、归档文件的异常传输', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
            """);
        log.info("Seeded default security_detection_rule records");
    }
}
