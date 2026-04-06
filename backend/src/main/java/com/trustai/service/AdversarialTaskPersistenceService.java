package com.trustai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AdversarialTaskPersistenceService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final JdbcTemplate jdbcTemplate;

    public AdversarialTaskPersistenceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void recordTaskStart(Long companyId,
                                Long userId,
                                String username,
                                String taskId,
                                String scenario,
                                Integer roundsPlanned,
                                Integer seed,
                                Map<String, Object> payload) {
        jdbcTemplate.update(
            """
                INSERT INTO adversarial_task(
                    company_id, task_id, scenario, status, rounds_planned, rounds_completed,
                    seed, created_by, created_username, raw_payload
                ) VALUES (?, ?, ?, 'queued', ?, 0, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    scenario = VALUES(scenario),
                    rounds_planned = VALUES(rounds_planned),
                    seed = VALUES(seed),
                    created_by = VALUES(created_by),
                    created_username = VALUES(created_username),
                    raw_payload = VALUES(raw_payload)
            """,
            companyId,
            taskId,
            safeText(scenario),
            safeInt(roundsPlanned, 12),
            seed,
            userId,
            safeText(username),
            toJson(payload)
        );
    }

    public void recordTaskStatus(Long companyId, String taskId, Map<String, Object> statusPayload) {
        String status = safeText(statusPayload.get("status"));
        int roundsPlanned = asInt(statusPayload.get("roundsPlanned"), 12);
        int completedRounds = asInt(statusPayload.get("completedRounds"), 0);

        jdbcTemplate.update(
            """
                INSERT INTO adversarial_task(
                    company_id, task_id, scenario, status, started_at, finished_at,
                    rounds_planned, rounds_completed, raw_payload
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    status = VALUES(status),
                    started_at = COALESCE(VALUES(started_at), started_at),
                    finished_at = COALESCE(VALUES(finished_at), finished_at),
                    rounds_planned = VALUES(rounds_planned),
                    rounds_completed = VALUES(rounds_completed),
                    raw_payload = VALUES(raw_payload)
            """,
            companyId,
            taskId,
            safeText(statusPayload.get("scenario")),
            status,
            parseTimestamp(statusPayload.get("startedAt")),
            parseTimestamp(statusPayload.get("finishedAt")),
            roundsPlanned,
            completedRounds,
            toJson(statusPayload)
        );

        Object battleObj = statusPayload.get("battle");
        if (battleObj instanceof Map<?, ?> battle) {
            Object roundsObj = battle.get("rounds");
            if (roundsObj instanceof List<?> rounds) {
                upsertRoundMetrics(companyId, taskId, rounds);
            }
        }
    }

    public void recordTaskLogs(Long companyId, String taskId, Map<String, Object> logsPayload) {
        Object eventLogsObj = logsPayload.get("eventLogs");
        if (eventLogsObj instanceof List<?> eventLogs) {
            upsertEventLogs(companyId, taskId, eventLogs);
        }

        Object trainLogsObj = logsPayload.get("trainingLogs");
        if (trainLogsObj instanceof List<?> trainLogs) {
            upsertModelUpdates(companyId, taskId, trainLogs);
        }

        Object roundsObj = logsPayload.get("rounds");
        if (roundsObj instanceof List<?> rounds) {
            upsertRoundMetrics(companyId, taskId, rounds);
        }
    }

    public void recordTaskReport(Long companyId, String taskId, Map<String, Object> reportPayload) {
        Object report = reportPayload.get("report");
        String reportFile = safeText(reportPayload.get("reportFile"));
        Timestamp generatedAt = null;
        if (report instanceof Map<?, ?> reportMap) {
            generatedAt = parseTimestamp(reportMap.get("generatedAt"));
            Object battleObj = reportMap.get("battle");
            if (battleObj instanceof Map<?, ?> battle) {
                Object roundsObj = battle.get("rounds");
                if (roundsObj instanceof List<?> rounds) {
                    upsertRoundMetrics(companyId, taskId, rounds);
                }
            }
            Object eventLogsObj = reportMap.get("eventLogs");
            if (eventLogsObj instanceof List<?> eventLogs) {
                upsertEventLogs(companyId, taskId, eventLogs);
            }
            Object trainingLogsObj = reportMap.get("trainingLogs");
            if (trainingLogsObj instanceof List<?> trainingLogs) {
                upsertModelUpdates(companyId, taskId, trainingLogs);
            }
        }

        jdbcTemplate.update(
            """
                INSERT INTO adversarial_report(
                    company_id, task_id, report_file, report_json, generated_at
                ) VALUES (?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE
                    report_file = VALUES(report_file),
                    report_json = VALUES(report_json),
                    generated_at = COALESCE(VALUES(generated_at), generated_at)
            """,
            companyId,
            taskId,
            reportFile,
            toJson(report),
            generatedAt
        );

        jdbcTemplate.update(
            """
                UPDATE adversarial_task
                SET status = 'completed', finished_at = COALESCE(finished_at, NOW())
                WHERE company_id = ? AND task_id = ?
            """,
            companyId,
            taskId
        );
    }

    private void upsertRoundMetrics(Long companyId, String taskId, List<?> rounds) {
        List<Map<String, Object>> rows = castObjectList(rounds);
        for (Map<String, Object> row : rows) {
            int roundNum = asInt(row.get("round_num"), asInt(row.get("roundNum"), 0));
            if (roundNum <= 0) {
                continue;
            }
            jdbcTemplate.update(
                """
                    INSERT INTO adversarial_round_metric(
                        company_id, task_id, round_num,
                        attack_success_rate, defense_intercept_rate, model_strength_score,
                        threshold_delta, strategy_delta, adaptive_threshold,
                        rule_id, token_features_json, explain_text, event_time, raw_payload
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        attack_success_rate = VALUES(attack_success_rate),
                        defense_intercept_rate = VALUES(defense_intercept_rate),
                        model_strength_score = VALUES(model_strength_score),
                        threshold_delta = VALUES(threshold_delta),
                        strategy_delta = VALUES(strategy_delta),
                        adaptive_threshold = VALUES(adaptive_threshold),
                        rule_id = VALUES(rule_id),
                        token_features_json = VALUES(token_features_json),
                        explain_text = VALUES(explain_text),
                        event_time = COALESCE(VALUES(event_time), event_time),
                        raw_payload = VALUES(raw_payload)
                """,
                companyId,
                taskId,
                roundNum,
                asDecimal(row.get("attack_success_rate")),
                asDecimal(row.get("defense_intercept_rate")),
                asInt(row.get("defense_strength_score"), asInt(row.get("model_strength_score"), 0)),
                asDecimal(row.get("threshold_delta")),
                asDecimal(row.get("strategy_delta")),
                asDecimal(row.get("adaptive_threshold")),
                safeText(firstNonNull(row.get("rule_id"), row.get("ruleId"))),
                toJson(row.get("token_features")),
                safeText(firstNonNull(row.get("explain"), row.get("explain_text"), row.get("narrative"))),
                parseTimestamp(firstNonNull(row.get("timestamp"), row.get("event_time"))),
                toJson(row)
            );
        }
    }

    private void upsertEventLogs(Long companyId, String taskId, List<?> eventLogs) {
        for (Map<String, Object> log : castObjectList(eventLogs)) {
            jdbcTemplate.update(
                """
                    INSERT INTO adversarial_event_log(
                        company_id, task_id, event_time, round_num, event_type,
                        rule_id, token_features_json, explain_text, raw_payload
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        rule_id = VALUES(rule_id),
                        token_features_json = VALUES(token_features_json),
                        explain_text = VALUES(explain_text),
                        raw_payload = VALUES(raw_payload)
                """,
                companyId,
                taskId,
                parseTimestamp(firstNonNull(log.get("time"), log.get("eventTime"))),
                asInt(log.get("round"), asInt(log.get("round_num"), 0)),
                safeText(firstNonNull(log.get("eventType"), log.get("event_type"))),
                safeText(firstNonNull(log.get("ruleId"), log.get("rule_id"))),
                toJson(firstNonNull(log.get("tokenFeatures"), log.get("token_features"))),
                safeText(firstNonNull(log.get("explain"), log.get("explain_text"))),
                toJson(log)
            );
        }
    }

    private void upsertModelUpdates(Long companyId, String taskId, List<?> updates) {
        for (Map<String, Object> log : castObjectList(updates)) {
            jdbcTemplate.update(
                """
                    INSERT INTO adversarial_model_update(
                        company_id, task_id, log_time, round_num, phase, message, raw_payload
                    ) VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        message = VALUES(message),
                        raw_payload = VALUES(raw_payload)
                """,
                companyId,
                taskId,
                parseTimestamp(firstNonNull(log.get("time"), log.get("logTime"))),
                asInt(firstNonNull(log.get("round"), log.get("round_num")), 0),
                safeText(log.get("phase")),
                safeText(log.get("message")),
                toJson(log)
            );
        }
    }

    private static Object firstNonNull(Object... values) {
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private static Integer safeInt(Integer value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    private static String safeText(Object value) {
        if (value == null) {
            return "";
        }
        return String.valueOf(value).trim();
    }

    private static Integer asInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return defaultValue;
        }
    }

    private static Double asDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ex) {
            return null;
        }
    }

    private static Timestamp parseTimestamp(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Timestamp ts) {
            return ts;
        }
        if (value instanceof java.util.Date date) {
            return new Timestamp(date.getTime());
        }
        if (value instanceof Number number) {
            long epoch = number.longValue();
            if (epoch <= 0) {
                return null;
            }
            if (String.valueOf(Math.abs(epoch)).length() <= 10) {
                epoch = epoch * 1000;
            }
            return new Timestamp(epoch);
        }

        String text = String.valueOf(value).trim();
        if (text.isEmpty() || "-".equals(text) || "null".equalsIgnoreCase(text)) {
            return null;
        }
        try {
            return Timestamp.from(Instant.parse(text));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Timestamp.from(OffsetDateTime.parse(text).toInstant());
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Timestamp.valueOf(LocalDateTime.parse(text));
        } catch (DateTimeParseException ignored) {
        }
        try {
            return Timestamp.valueOf(LocalDateTime.parse(text.replace("Z", "")));
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private static String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return String.valueOf(value);
        }
    }

    private static List<Map<String, Object>> castObjectList(List<?> raw) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object item : raw) {
            if (item instanceof Map<?, ?> map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) map;
                out.add(casted);
            }
        }
        return out;
    }
}
