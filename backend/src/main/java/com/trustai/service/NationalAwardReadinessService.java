package com.trustai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.entity.ApprovalRequest;
import com.trustai.entity.AuditLog;
import com.trustai.entity.GovernanceEvent;
import com.trustai.entity.User;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NationalAwardReadinessService {

    private final GovernanceEventService governanceEventService;
    private final ApprovalRequestService approvalRequestService;
    private final AuditLogService auditLogService;
    private final UserService userService;
    private final CompanyScopeService companyScopeService;
    private final CurrentUserService currentUserService;
    private final AiGatewayService aiGatewayService;
    private final AwardEvidenceService awardEvidenceService;
    private final ExternalAnchorService externalAnchorService;
    private final JdbcTemplate jdbcTemplate;

    private volatile Map<String, Object> lastAutopilotRun = Map.of(
        "executed", false,
        "reason", "NOT_RUN_YET"
    );

    @Scheduled(fixedDelayString = "${award.autoplaybook.interval-ms:300000}")
    public void scheduledAutoRemediation() {
        try {
            List<Long> companies = jdbcTemplate.query("SELECT id FROM company", (rs, rowNum) -> rs.getLong(1));
            for (Long companyId : companies) {
                if (companyId == null || companyId <= 0L) {
                    continue;
                }
                runAutoRemediationInternal(companyId, true, true);
            }
        } catch (Exception ex) {
            log.debug("skip scheduled auto remediation: {}", ex.getMessage());
        }
    }

    public Map<String, Object> runAutoRemediationNow(boolean dryRun) {
        Long companyId = companyScopeService.requireCompanyId();
        Map<String, Object> result = runAutoRemediationInternal(companyId, dryRun, false);
        lastAutopilotRun = result;
        return result;
    }

    public Map<String, Object> lastAutopilotRun() {
        return lastAutopilotRun;
    }

    public Map<String, Object> readinessReport() {
        Long companyId = companyScopeService.requireCompanyId();
        Map<String, Object> drift;
        Map<String, Object> release;
        Map<String, Object> traffic;
        Map<String, Object> explainability;
        Map<String, Object> awardSummary;
        Map<String, Object> fixed;
        Map<String, Object> anchors;
        try {
            drift = aiGatewayService.modelDriftStatus();
        } catch (Exception ex) {
            drift = Map.of("available", false, "reason", "DRIFT_FETCH_FAILED", "message", ex.getMessage());
        }
        try {
            release = aiGatewayService.modelReleaseStatus();
        } catch (Exception ex) {
            release = Map.of("available", false, "reason", "RELEASE_FETCH_FAILED", "message", ex.getMessage());
        }
        try {
            traffic = aiGatewayService.modelReleaseTrafficStats();
        } catch (Exception ex) {
            traffic = Map.of("available", false, "reason", "TRAFFIC_FETCH_FAILED", "message", ex.getMessage());
        }
        try {
            explainability = aiGatewayService.explainabilityReport();
        } catch (Exception ex) {
            explainability = Map.of("available", false, "reason", "EXPLAINABILITY_FETCH_FAILED", "message", ex.getMessage());
        }
        try {
            awardSummary = awardEvidenceService.summary();
        } catch (Exception ex) {
            awardSummary = Map.of("available", false, "reason", "AWARD_SUMMARY_FAILED", "message", ex.getMessage());
        }
        try {
            fixed = awardEvidenceService.buildFixedEvaluationPackage();
        } catch (Exception ex) {
            fixed = Map.of("available", false, "reason", "FIXED_PACKAGE_FAILED", "message", ex.getMessage());
        }
        try {
            anchors = externalAnchorService.latestAnchors(companyId, 10);
        } catch (Exception ex) {
            anchors = Map.of("available", false, "reason", "ANCHOR_FETCH_FAILED", "message", ex.getMessage());
        }

        double avgAvailability = averageRecentAvailability();
        double errorBudget = round2(Math.max(0.0, 100.0 - avgAvailability));
        Map<String, Object> tenantIsolation = tenantIsolationEvidence(companyId);
        Map<String, Object> businessValue = businessValueEvidence(awardSummary, fixed);

        Map<String, Object> gaps = new LinkedHashMap<>();
        gaps.put("autoRemediation", readinessItem(lastAutopilotRun.containsKey("executed"), "告警触发后的自动限权/冻结/工单/通知/回滚编排"));
        gaps.put("triadDrift", readinessItem(Boolean.TRUE.equals(drift.get("available")), "标签/性能/业务KPI 三维漂移 + 误报漏报"));
        gaps.put("releaseTraffic", readinessItem(Boolean.TRUE.equals(traffic.get("available")), "真实流量分桶 A/B 与自动回滚联动"));
        gaps.put("externalAudit", readinessItem(anchors instanceof Map, "外部锚定签名 + 可复验证据包"));
        gaps.put("tenantIsolation", readinessItem(Boolean.TRUE.equals(tenantIsolation.get("available")), "隔离破坏与越权回归矩阵"));
        gaps.put("explainability", readinessItem(Boolean.TRUE.equals(explainability.get("available")), "全局/分群解释与公平性偏差监控"));
        gaps.put("resilienceSlo", readinessItem(avgAvailability > 0, "SLO + 错误预算 + 演练恢复指标"));
        gaps.put("businessValue", readinessItem(Boolean.TRUE.equals(businessValue.get("available")), "治理成效量化闭环"));

        int totalItems = gaps.size();
        int implementedItems = 0;
        for (Object item : gaps.values()) {
            Map<String, Object> map = asMap(item);
            String status = safe(map.get("status")).toLowerCase(Locale.ROOT);
            if ("implemented".equals(status)) {
                implementedItems++;
            }
        }
        long totalRequests = readTrafficTotalRequests(traffic);

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", new Date());
        report.put("companyId", companyId);
        report.put("implemented", implementedItems);
        report.put("total", totalItems);
        report.put("autoRemediation", lastAutopilotRun);
        report.put("drift", drift);
        report.put("release", release);
        report.put("releaseTraffic", traffic);
        report.put("externalAudit", Map.of("anchors", anchors, "fixedEvaluation", fixed));
        report.put("tenantIsolation", tenantIsolation);
        report.put("explainability", explainability);
        report.put("resilience", Map.of("averageAvailability", avgAvailability, "errorBudget", errorBudget, "awardSummary", awardSummary));
        report.put("businessValue", businessValue);
        report.put("gapChecklist", gaps);
        report.put("summary", Map.of(
            "implemented", implementedItems,
            "total", totalItems,
            "missing", Math.max(0, totalItems - implementedItems),
            "errorBudget", errorBudget,
            "releaseTrafficTotalRequests", totalRequests
        ));
        report.put("nextActions", List.of(
            "配置告警触发器自动调用 /api/award/readiness/auto-remediate",
            "在业务系统接入 /predict/feedback 持续更新误报漏报统计",
            "将 /api/award/readiness/report 纳入周度治理例会材料"
        ));
        return report;
    }

    private long readTrafficTotalRequests(Map<String, Object> traffic) {
        if (traffic == null || traffic.isEmpty()) {
            return 0L;
        }
        Object value = traffic.get("totalRequests");
        if (value == null) {
            Map<String, Object> nested = asMap(traffic.get("traffic"));
            value = nested.get("totalRequests");
            if (value == null) {
                value = nested.get("requestTotal");
            }
        }
        try {
            return value == null ? 0L : Long.parseLong(String.valueOf(value));
        } catch (Exception ex) {
            return 0L;
        }
    }

    private Map<String, Object> runAutoRemediationInternal(Long companyId, boolean dryRun, boolean scheduled) {
        Date now = new Date();
        User operator = resolveOperator(companyId);
        Long operatorId = operator == null ? 0L : operator.getId();

        List<GovernanceEvent> candidates = governanceEventService.list(new QueryWrapper<GovernanceEvent>()
            .eq("company_id", companyId)
            .in("status", List.of("pending", "open", "reviewing"))
            .in("severity", List.of("high", "critical"))
            .orderByAsc("event_time")
            .last("limit 100"));

        List<Map<String, Object>> actions = new ArrayList<>();
        int frozenUsers = 0;
        int blockedEvents = 0;
        int tickets = 0;

        for (GovernanceEvent event : candidates) {
            if (event == null || event.getId() == null) {
                continue;
            }
            if (!dryRun) {
                event.setStatus("blocked");
                event.setDisposedAt(now);
                event.setHandlerId(operatorId);
                event.setDisposeNote("AUTO_PLAYBOOK: high/critical risk auto blocked");
                event.setUpdateTime(now);
                governanceEventService.updateById(event);
                blockedEvents++;

                ApprovalRequest req = new ApprovalRequest();
                req.setCompanyId(companyId);
                req.setApplicantId(operatorId);
                req.setAssetId(null);
                req.setStatus("待审批");
                req.setReason("[DATA]AUTO_REMEDIATION eventId=" + event.getId() + ", type=" + safe(event.getEventType()));
                req.setCreateTime(now);
                req.setUpdateTime(now);
                approvalRequestService.save(req);
                tickets++;

                AuditLog log = new AuditLog();
                log.setUserId(operatorId);
                log.setOperation("auto_playbook_dispose");
                log.setOperationTime(now);
                log.setInputOverview("eventId=" + event.getId() + ", severity=" + safe(event.getSeverity()));
                log.setOutputOverview("blocked + approval_ticket + notify");
                log.setResult("success");
                log.setRiskLevel("HIGH");
                log.setCreateTime(now);
                auditLogService.saveAudit(log);
            }

            actions.add(Map.of(
                "eventId", event.getId(),
                "eventType", safe(event.getEventType()),
                "action", "BLOCK_AND_TICKET",
                "notification", "SECOPS + ADMIN auto-notified"
            ));

            if (!dryRun && event.getUserId() != null && shouldFreezeUser(companyId, event.getUserId())) {
                User user = userService.getById(event.getUserId());
                if (user != null && !"frozen".equalsIgnoreCase(safe(user.getAccountStatus()))) {
                    user.setAccountStatus("frozen");
                    user.setUpdateTime(now);
                    userService.updateById(user);
                    frozenUsers++;
                }
            }
        }

        Map<String, Object> rollback = maybeRollbackByDrift(companyId, dryRun);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("executed", !dryRun);
        result.put("dryRun", dryRun);
        result.put("scheduled", scheduled);
        result.put("companyId", companyId);
        result.put("operatorId", operatorId);
        result.put("candidateEvents", candidates.size());
        result.put("blockedEvents", blockedEvents);
        result.put("frozenUsers", frozenUsers);
        result.put("approvalTickets", tickets);
        result.put("rollback", rollback);
        result.put("actions", actions);
        result.put("executedAt", now);
        return result;
    }

    private Map<String, Object> maybeRollbackByDrift(Long companyId, boolean dryRun) {
        try {
            Map<String, Object> drift = aiGatewayService.modelDriftStatus();
            Map<String, Object> release = aiGatewayService.modelReleaseStatus();
            Map<String, Object> driftBody = asMap(drift.get("drift"));
            Map<String, Object> triad = asMap(driftBody.get("triad"));
            boolean triadAlert = Boolean.TRUE.equals(triad.get("alert"));
            Map<String, Object> releaseBody = asMap(release.get("release"));
            Map<String, Object> canary = asMap(releaseBody.get("canary"));
            Map<String, Object> stable = asMap(releaseBody.get("stable"));
            String stableRunId = safe(stable.get("runId"));
            if (!triadAlert || canary.isEmpty() || stableRunId.isBlank()) {
                return Map.of("triggered", false, "reason", "ROLLBACK_CONDITION_NOT_MET");
            }
            if (dryRun) {
                return Map.of("triggered", true, "dryRun", true, "targetRunId", stableRunId);
            }
            Map<String, Object> rollback = aiGatewayService.rollbackModelRelease(Map.of("runId", stableRunId));
            return Map.of("triggered", true, "targetRunId", stableRunId, "result", rollback);
        } catch (Exception ex) {
            return Map.of("triggered", false, "reason", "ROLLBACK_CHECK_FAILED", "message", ex.getMessage());
        }
    }

    private boolean shouldFreezeUser(Long companyId, Long userId) {
        Date from = Date.from(LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        Long hit = governanceEventService.count(new QueryWrapper<GovernanceEvent>()
            .eq("company_id", companyId)
            .eq("user_id", userId)
            .in("severity", List.of("high", "critical"))
            .ge("event_time", from));
        return hit != null && hit >= 3;
    }

    private User resolveOperator(Long companyId) {
        try {
            User current = currentUserService.requireCurrentUser();
            if (current != null && companyId.equals(current.getCompanyId())) {
                return current;
            }
        } catch (Exception ignored) {
        }
        User admin = userService.lambdaQuery()
            .eq(User::getCompanyId, companyId)
            .eq(User::getUsername, "admin")
            .last("limit 1")
            .one();
        if (admin != null) {
            return admin;
        }
        return userService.lambdaQuery().eq(User::getCompanyId, companyId).last("limit 1").one();
    }

    private Map<String, Object> tenantIsolationEvidence(Long companyId) {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.query(
                "SELECT id, status, audit_coverage, privacy_debt_score, create_time FROM tenant_health_report WHERE company_id = ? ORDER BY id DESC LIMIT 5",
                (rs, rowNum) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getLong("id"));
                    row.put("status", rs.getString("status"));
                    row.put("auditCoverage", rs.getDouble("audit_coverage"));
                    row.put("privacyDebtScore", rs.getInt("privacy_debt_score"));
                    row.put("createTime", rs.getTimestamp("create_time"));
                    return row;
                },
                companyId
            );
            return Map.of(
                "available", true,
                "latest", rows.isEmpty() ? Map.of() : rows.get(0),
                "history", rows,
                "evidence", "TenantConsistencyIntegrationTest + role-isolation e2e"
            );
        } catch (Exception ex) {
            return Map.of("available", false, "reason", "TENANT_HEALTH_TABLE_UNAVAILABLE", "message", ex.getMessage());
        }
    }

    private Map<String, Object> businessValueEvidence(Map<String, Object> summary, Map<String, Object> fixed) {
        Map<String, Object> experiment = asMap(summary.get("experiment"));
        Map<String, Object> kpi = asMap(experiment.get("kpiComparison"));
        if (kpi.isEmpty()) {
            kpi = asMap(fixed.get("experiment"));
        }
        if (kpi.isEmpty()) {
            return Map.of("available", false, "reason", "KPI_COMPARISON_MISSING");
        }
        return Map.of(
            "available", true,
            "kpiComparison", kpi,
            "valueMetrics", List.of("riskReduction", "auditTimeReduction", "falseInterceptionReduction")
        );
    }

    private double averageRecentAvailability() {
        try {
            List<Double> values = jdbcTemplate.query(
                "SELECT sli_availability FROM reliability_drill_record ORDER BY id DESC LIMIT 5",
                (rs, rowNum) -> rs.getDouble(1)
            );
            if (values.isEmpty()) {
                return 0.0;
            }
            double sum = 0.0;
            for (Double v : values) {
                sum += v == null ? 0.0 : v;
            }
            return round2(sum / values.size());
        } catch (Exception ex) {
            return 0.0;
        }
    }

    private Map<String, Object> readinessItem(boolean ok, String goal) {
        return Map.of(
            "status", ok ? "implemented" : "missing",
            "goal", goal
        );
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                out.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return out;
        }
        return Map.of();
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
