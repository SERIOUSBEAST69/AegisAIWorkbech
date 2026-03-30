package com.trustai.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.exception.BizException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class OpsTelemetryService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    private volatile boolean opsTablesEnsured = false;

    public void persistApiMetricSnapshot(Long companyId, Map<String, Object> snapshot) {
        ensureOpsTables();
        Long scopedCompanyId = requireCompanyId(companyId);
        Object apis = snapshot == null ? null : snapshot.get("apis");
        if (!(apis instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        Date sampledAt = new Date();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> row)) {
                continue;
            }
            Object apiValue = row.containsKey("api") ? row.get("api") : "";
            String api = String.valueOf(apiValue == null ? "" : apiValue).trim();
            if (!StringUtils.hasText(api)) {
                continue;
            }
            jdbcTemplate.update(
                """
                INSERT INTO api_metric_history
                (company_id, api, total, success, fail, p50, p95, p99, max, sampled_at, create_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                scopedCompanyId,
                api,
                toLong(row.get("total")),
                toLong(row.get("success")),
                toLong(row.get("fail")),
                toLong(row.get("p50")),
                toLong(row.get("p95")),
                toLong(row.get("p99")),
                toLong(row.get("max")),
                sampledAt,
                sampledAt
            );
        }
    }

    public Map<String, Object> saveWebVital(Map<String, Object> payload, Long companyId) {
        ensureOpsTables();
        Long scopedCompanyId = requireCompanyId(companyId);
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        String metricName = String.valueOf(safePayload.getOrDefault("name", "unknown")).toUpperCase();
        double metricValue = toDouble(safePayload.get("value"));
        String rating = String.valueOf(safePayload.getOrDefault("rating", "unknown"));
        String metricId = String.valueOf(safePayload.getOrDefault("id", ""));
        String navigationType = String.valueOf(safePayload.getOrDefault("navigationType", "navigate"));
        String path = String.valueOf(safePayload.getOrDefault("path", "/"));
        Date now = new Date();

        jdbcTemplate.update(
            """
            INSERT INTO web_vital_metric
            (company_id, metric_name, metric_value, rating, metric_id, navigation_type, path, event_time, create_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            scopedCompanyId,
            metricName,
            metricValue,
            rating,
            metricId,
            navigationType,
            path,
            now,
            now
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("accepted", true);
        result.put("metric", metricName);
        result.put("value", metricValue);
        result.put("rating", rating);
        return result;
    }

    public Map<String, Object> webVitalSummary(Long companyId, int days) {
        ensureOpsTables();
        Long scopedCompanyId = requireCompanyId(companyId);
        int safeDays = Math.max(1, Math.min(30, days));
        LocalDate start = LocalDate.now().minusDays(safeDays - 1L);
        Date startDate = Date.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant());

        List<Map<String, Object>> metrics = jdbcTemplate.query(
            """
            SELECT metric_name, COUNT(1) AS sample_count, ROUND(AVG(metric_value), 2) AS avg_value,
                   ROUND(MAX(metric_value), 2) AS max_value,
                   SUM(CASE WHEN rating = 'good' THEN 1 ELSE 0 END) AS good_count,
                   SUM(CASE WHEN rating = 'needs-improvement' THEN 1 ELSE 0 END) AS ni_count,
                   SUM(CASE WHEN rating = 'poor' THEN 1 ELSE 0 END) AS poor_count
            FROM web_vital_metric
            WHERE company_id = ? AND event_time >= ?
            GROUP BY metric_name
            ORDER BY metric_name ASC
            """,
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                long sampleCount = rs.getLong("sample_count");
                row.put("name", rs.getString("metric_name"));
                row.put("sampleCount", sampleCount);
                row.put("avg", rs.getDouble("avg_value"));
                row.put("max", rs.getDouble("max_value"));
                row.put("good", rs.getLong("good_count"));
                row.put("needsImprovement", rs.getLong("ni_count"));
                row.put("poor", rs.getLong("poor_count"));
                row.put("goodRate", sampleCount == 0 ? 0.0 : round2(rs.getLong("good_count") * 100.0 / sampleCount));
                return row;
            },
            scopedCompanyId,
            startDate
        );

        List<Map<String, Object>> trend = jdbcTemplate.query(
            """
            SELECT event_time AS day_key, metric_name, ROUND(AVG(metric_value), 2) AS avg_value, COUNT(1) AS sample_count
            FROM web_vital_metric
            WHERE company_id = ? AND event_time >= ?
            GROUP BY event_time, metric_name
            ORDER BY event_time ASC, metric_name ASC
            """,
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("day", String.valueOf(rs.getTimestamp("day_key")));
                row.put("metric", rs.getString("metric_name"));
                row.put("avg", rs.getDouble("avg_value"));
                row.put("sampleCount", rs.getLong("sample_count"));
                return row;
            },
            scopedCompanyId,
            startDate
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", safeDays);
        result.put("summary", metrics);
        result.put("trend", trend);
        return result;
    }

    public Map<String, Object> httpHistory(Long companyId, int days, String apiKeyword) {
        ensureOpsTables();
        Long scopedCompanyId = requireCompanyId(companyId);
        int safeDays = Math.max(1, Math.min(30, days));
        LocalDate start = LocalDate.now().minusDays(safeDays - 1L);
        Date startDate = Date.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant());
        boolean hasApiKeyword = StringUtils.hasText(apiKeyword);

        List<Map<String, Object>> rows = jdbcTemplate.query(
            """
                        SELECT sampled_at AS day_key, api,
                   ROUND(AVG(p95), 2) AS p95_avg,
                   ROUND(AVG(p99), 2) AS p99_avg,
                   SUM(total) AS total_requests,
                   SUM(success) AS success_requests,
                   SUM(fail) AS fail_requests
            FROM api_metric_history
            WHERE company_id = ?
              AND sampled_at >= ?
              AND (? = 0 OR api LIKE ?)
                        GROUP BY sampled_at, api
                        ORDER BY sampled_at ASC, api ASC
            """,
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                long total = rs.getLong("total_requests");
                long success = rs.getLong("success_requests");
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("day", String.valueOf(rs.getTimestamp("day_key")));
                item.put("api", rs.getString("api"));
                item.put("p95", rs.getDouble("p95_avg"));
                item.put("p99", rs.getDouble("p99_avg"));
                item.put("total", total);
                item.put("success", success);
                item.put("fail", rs.getLong("fail_requests"));
                item.put("successRate", total == 0 ? 0.0 : round2((success * 100.0) / total));
                return item;
            },
            scopedCompanyId,
            startDate,
            hasApiKeyword ? 1 : 0,
            "%" + (hasApiKeyword ? apiKeyword.trim() : "") + "%"
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", safeDays);
        result.put("rows", rows);
        return result;
    }

    private void ensureOpsTables() {
        if (opsTablesEnsured) {
            return;
        }
        synchronized (this) {
            if (opsTablesEnsured) {
                return;
            }
            jdbcTemplate.execute(
                """
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
                """
            );
            jdbcTemplate.execute(
                """
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
                """
            );
            opsTablesEnsured = true;
        }
    }

    public Map<String, Object> slowQuerySummary(Long companyId, int days) {
        Long scopedCompanyId = requireCompanyId(companyId);
        int safeDays = Math.max(1, Math.min(30, days));
        LocalDate start = LocalDate.now().minusDays(safeDays - 1L);
        Date startDate = Date.from(start.atStartOfDay(ZoneId.systemDefault()).toInstant());

        List<Map<String, Object>> topMethods = jdbcTemplate.query(
            """
            SELECT mapper_method, COUNT(1) AS slow_count,
                   ROUND(AVG(elapsed_ms), 2) AS avg_elapsed,
                   MAX(elapsed_ms) AS max_elapsed
            FROM slow_query_log
            WHERE company_id = ? AND query_time >= ?
            GROUP BY mapper_method
            ORDER BY avg_elapsed DESC
            LIMIT 20
            """,
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("method", rs.getString("mapper_method"));
                row.put("slowCount", rs.getLong("slow_count"));
                row.put("avgElapsedMs", rs.getDouble("avg_elapsed"));
                row.put("maxElapsedMs", rs.getLong("max_elapsed"));
                return row;
            },
            scopedCompanyId,
            startDate
        );

        Long totalSlow = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM slow_query_log WHERE company_id = ? AND query_time >= ?",
            Long.class,
            scopedCompanyId,
            startDate
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("days", safeDays);
        result.put("slowQueryCount", totalSlow == null ? 0L : totalSlow);
        result.put("topMethods", topMethods);
        return result;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> parseJsonObject(String json) {
        if (!StringUtils.hasText(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private long toLong(Object value) {
        if (value instanceof Number num) {
            return num.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return 0L;
        }
    }

    private double toDouble(Object value) {
        if (value instanceof Number num) {
            return num.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ex) {
            return 0.0;
        }
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private Long requireCompanyId(Long companyId) {
        if (companyId == null || companyId <= 0) {
            throw new BizException(40100, "缺少合法租户标识");
        }
        return companyId;
    }
}
