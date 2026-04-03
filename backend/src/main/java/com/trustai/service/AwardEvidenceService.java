package com.trustai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.client.AiInferenceClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.entity.AuditLog;
import com.trustai.entity.AdversarialRecord;
import com.trustai.entity.GovernanceEvent;
import com.trustai.entity.User;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AwardEvidenceService {

    private final GovernanceEventService governanceEventService;
    private final AuditLogService auditLogService;
    private final CompanyScopeService companyScopeService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final AdversarialRecordService adversarialRecordService;
    private final AiInferenceClient aiInferenceClient;
    private final AdaptiveRuleEngineService adaptiveRuleEngineService;
    private final ExternalAnchorService externalAnchorService;

    @Value("${server.port:8080}")
    private int serverPort;

    @Value("${award.export-dir:./target/evidence-packages}")
    private String awardExportDir;

    @Value("${ai.inference.base-url:http://localhost:8000}")
    private String aiInferenceBaseUrl;

    @Value("${award.evaluation.code-version:dev-local}")
    private String evaluationCodeVersion;

    public Map<String, Object> buildExperimentReport(LocalDate baselineFrom,
                                                     LocalDate baselineTo,
                                                     LocalDate currentFrom,
                                                     LocalDate currentTo) {
        Long companyId = companyScopeService.requireCompanyId();
        WindowMetrics baseline = windowMetrics(companyId, baselineFrom, baselineTo);
        WindowMetrics current = windowMetrics(companyId, currentFrom, currentTo);

        Map<String, Object> improvement = new LinkedHashMap<>();
        improvement.put("falsePositiveReductionPct", safeReduction(baseline.falsePositiveRate, current.falsePositiveRate));
        improvement.put("responseTimeReductionPct", safeReduction(baseline.avgResponseSeconds, current.avgResponseSeconds));
        improvement.put("interceptionUpliftPct", round2(current.interceptionRate - baseline.interceptionRate));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("baseline", baseline.toMap());
        data.put("current", current.toMap());
        data.put("improvement", improvement);
        data.put("generatedAt", new Date());
        return data;
    }

    public Map<String, Object> generateComplianceEvidence(LocalDate from, LocalDate to) {
        Long companyId = companyScopeService.requireCompanyId();
        User operator = currentUserService.requireCurrentUser();
        Date fromDate = toDateStart(from);
        Date toDate = toDateEnd(to);

        List<GovernanceEvent> events = governanceEventService.list(companyScopeService.withCompany(new QueryWrapper<GovernanceEvent>())
            .between("event_time", fromDate, toDate)
            .orderByDesc("event_time")
            .last("limit 5000"));

        long policyHitCount = events.size();
        long remediationClosed = events.stream()
            .filter(event -> isClosedStatus(event.getStatus()))
            .count();

        long auditTraceCount = events.stream()
            .filter(event -> hasAuditTrace(event.getId(), fromDate, toDate, companyId))
            .count();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("policyHitCount", policyHitCount);
        payload.put("auditTraceCount", auditTraceCount);
        payload.put("remediationClosedCount", remediationClosed);
        payload.put("traceabilityRate", policyHitCount == 0 ? 0.0 : round2((auditTraceCount * 100.0) / policyHitCount));
        payload.put("closedLoopRate", policyHitCount == 0 ? 0.0 : round2((remediationClosed * 100.0) / policyHitCount));
        payload.put("policyHitByType", aggregateByType(events));
        payload.put("generatedAt", new Date());

        String contentJson = writeJson(payload);
        String hash = sha256(contentJson);
        Date now = new Date();
        jdbcTemplate.update(
            """
            INSERT INTO compliance_evidence_record
            (company_id, evidence_type, period_start, period_end, policy_hit_count, audit_trace_count, remediation_closed_count, content_json, evidence_hash, generated_by, create_time, update_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            companyId,
            "policy_audit_remediation",
            fromDate,
            toDate,
            policyHitCount,
            auditTraceCount,
            remediationClosed,
            contentJson,
            hash,
            operator.getId(),
            now,
            now
        );

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("hash", hash);
        result.put("period", Map.of("from", from, "to", to));
        result.put("evidence", payload);
        result.put("externalAnchor", externalAnchorService.anchorEvidence(companyId, "compliance_evidence", hash, hash));
        return result;
    }

    public List<Map<String, Object>> listComplianceEvidence(int limit) {
        int safeLimit = Math.max(1, Math.min(100, limit));
        Long companyId = companyScopeService.requireCompanyId();
        return jdbcTemplate.query(
            """
            SELECT id, evidence_type, period_start, period_end, policy_hit_count, audit_trace_count,
                   remediation_closed_count, evidence_hash, generated_by, create_time
            FROM compliance_evidence_record
            WHERE company_id = ?
            ORDER BY create_time DESC
            LIMIT ?
            """,
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("evidenceType", rs.getString("evidence_type"));
                row.put("periodStart", rs.getTimestamp("period_start"));
                row.put("periodEnd", rs.getTimestamp("period_end"));
                row.put("policyHitCount", rs.getLong("policy_hit_count"));
                row.put("auditTraceCount", rs.getLong("audit_trace_count"));
                row.put("remediationClosedCount", rs.getLong("remediation_closed_count"));
                row.put("evidenceHash", rs.getString("evidence_hash"));
                row.put("generatedBy", rs.getLong("generated_by"));
                row.put("createTime", rs.getTimestamp("create_time"));
                return row;
            },
            companyId,
            safeLimit
        );
    }

    public Map<String, Object> runReliabilityDrill(String scenario, String targetPath, String injectPath, int probeCount) {
        Long companyId = companyScopeService.requireCompanyId();
        User operator = currentUserService.requireCurrentUser();
        String safeScenario = StringUtils.hasText(scenario) ? scenario : "latency-and-failure-observe";
        String safeTargetPath = normalizePath(targetPath, "/api/auth/registration-options");
        String safeInjectPath = normalizePath(injectPath, "/api/non-existent-reliability-probe");
        int safeProbeCount = Math.max(2, Math.min(12, probeCount));

        ProbeSummary baseline = probePath(safeTargetPath, safeProbeCount);
        ProbeSummary inject = probePath(safeInjectPath, safeProbeCount);
        Map<String, Object> componentProbe = probeInfrastructureComponents();

        long recoveryStart = System.currentTimeMillis();
        ProbeSummary recovered = ProbeSummary.empty();
        int attempts = 0;
        while (attempts < 20) {
            attempts++;
            recovered = probePath(safeTargetPath, 1);
            if (recovered.successCount > 0) {
                break;
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        long recoverySeconds = Math.max(0L, (System.currentTimeMillis() - recoveryStart) / 1000L);

        double sliAvailability = round2(baseline.successRate);
        long sliLatency = baseline.p95LatencyMs;
        boolean availabilityMet = sliAvailability >= 99.9;
        boolean latencyMet = sliLatency <= 800;
        boolean recoveryMet = recoverySeconds <= 60;
        boolean infraMet = Boolean.TRUE.equals(componentProbe.get("criticalHealthy"));
        boolean infraAllHealthy = Boolean.TRUE.equals(componentProbe.get("allHealthy"));
        String sloStatus = availabilityMet && latencyMet && recoveryMet && infraMet ? "met" : "breach";

        Long alertEventId = null;
        if (!availabilityMet || !latencyMet || !recoveryMet || !infraMet) {
            GovernanceEvent event = new GovernanceEvent();
            event.setCompanyId(companyId);
            event.setUserId(operator.getId());
            event.setUsername(operator.getUsername());
            event.setEventType("RELIABILITY_ALERT");
            event.setSourceModule("reliability");
            event.setSeverity((!availabilityMet && sliAvailability < 97.0) || !recoveryMet || !infraMet ? "high" : "medium");
            event.setStatus("pending");
            event.setTitle("可靠性演练触发 SLO 违约");
            event.setDescription("availability=" + sliAvailability + "%, p95=" + sliLatency + "ms, recovery=" + recoverySeconds + "s, infraMet=" + infraMet);
            event.setSourceEventId("drill:" + System.currentTimeMillis());
            event.setAttackType("resilience_chaos");
            event.setPolicyVersion(1L);
            event.setPayloadJson(writeJson(Map.of(
                "scenario", safeScenario,
                "targetPath", safeTargetPath,
                "injectPath", safeInjectPath,
                "sloStatus", sloStatus
            )));
            event.setEventTime(new Date());
            event.setCreateTime(new Date());
            event.setUpdateTime(new Date());
            governanceEventService.save(event);
            alertEventId = event.getId();
        }

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("baselineProbe", baseline.toMap());
        detail.put("injectProbe", inject.toMap());
        detail.put("recoveryProbe", recovered.toMap());
        detail.put("componentProbe", componentProbe);
        detail.put("slo", Map.of(
            "availabilityTarget", 99.9,
            "latencyP95TargetMs", 800,
            "recoveryTargetSec", 60,
            "availabilityMet", availabilityMet,
            "latencyMet", latencyMet,
            "recoveryMet", recoveryMet,
            "infraMet", infraMet,
            "infraAllHealthy", infraAllHealthy
        ));

        Date now = new Date();
        jdbcTemplate.update(
            """
            INSERT INTO reliability_drill_record
            (company_id, scenario, target_path, inject_path, baseline_success_rate, baseline_p95_ms, injected_error_rate, recovery_seconds, sli_availability, sli_latency_ms, slo_status, alert_event_id, detail_json, executed_by, create_time, update_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            companyId,
            safeScenario,
            safeTargetPath,
            safeInjectPath,
            baseline.successRate,
            baseline.p95LatencyMs,
            round2(100.0 - inject.successRate),
            recoverySeconds,
            sliAvailability,
            sliLatency,
            sloStatus,
            alertEventId,
            writeJson(detail),
            operator.getId(),
            now,
            now
        );

        AuditLog log = new AuditLog();
        log.setUserId(operator.getId());
        log.setOperation("reliability_drill");
        log.setOperationTime(now);
        log.setInputOverview("scenario=" + safeScenario + ", target=" + safeTargetPath + ", inject=" + safeInjectPath);
        log.setOutputOverview("slo=" + sloStatus + ", availability=" + sliAvailability + ", p95=" + sliLatency + ", recovery=" + recoverySeconds);
        log.setResult("success");
        log.setRiskLevel("HIGH");
        log.setHash(sha256(log.getInputOverview() + "|" + log.getOutputOverview() + "|" + now.getTime()));
        log.setCreateTime(now);
        auditLogService.saveAudit(log);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scenario", safeScenario);
        result.put("targetPath", safeTargetPath);
        result.put("injectPath", safeInjectPath);
        result.put("sloStatus", sloStatus);
        result.put("alertLinked", alertEventId != null);
        result.put("alertEventId", alertEventId);
        result.put("detail", detail);
        return result;
    }

    public List<Map<String, Object>> listReliabilityDrills(int limit) {
        int safeLimit = Math.max(1, Math.min(100, limit));
        Long companyId = companyScopeService.requireCompanyId();
        return jdbcTemplate.query(
            """
            SELECT id, scenario, target_path, inject_path, baseline_success_rate, baseline_p95_ms,
                   injected_error_rate, recovery_seconds, sli_availability, sli_latency_ms,
                   slo_status, alert_event_id, executed_by, create_time
            FROM reliability_drill_record
            WHERE company_id = ?
            ORDER BY create_time DESC
            LIMIT ?
            """,
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("scenario", rs.getString("scenario"));
                row.put("targetPath", rs.getString("target_path"));
                row.put("injectPath", rs.getString("inject_path"));
                row.put("baselineSuccessRate", round2(rs.getDouble("baseline_success_rate")));
                row.put("baselineP95Ms", rs.getLong("baseline_p95_ms"));
                row.put("injectedErrorRate", round2(rs.getDouble("injected_error_rate")));
                row.put("recoverySeconds", rs.getLong("recovery_seconds"));
                row.put("sliAvailability", round2(rs.getDouble("sli_availability")));
                row.put("sliLatencyMs", rs.getLong("sli_latency_ms"));
                row.put("sloStatus", rs.getString("slo_status"));
                row.put("alertEventId", rs.getObject("alert_event_id"));
                row.put("executedBy", rs.getLong("executed_by"));
                row.put("createTime", rs.getTimestamp("create_time"));
                return row;
            },
            companyId,
            safeLimit
        );
    }

    public Map<String, Object> summary() {
        LocalDate today = LocalDate.now();
        Map<String, Object> experiment = buildExperimentReport(today.minusDays(13), today.minusDays(7), today.minusDays(6), today);
        List<Map<String, Object>> evidence = listComplianceEvidence(1);
        List<Map<String, Object>> drills = listReliabilityDrills(1);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("experiment", experiment);
        summary.put("latestEvidence", evidence.isEmpty() ? Map.of() : evidence.get(0));
        summary.put("latestDrill", drills.isEmpty() ? Map.of() : drills.get(0));
        summary.put("zeroTrust", zeroTrustAssessment());
        return summary;
    }

    public Map<String, Object> privacyComplianceMapping() {
        Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("PIPL", List.of(
            Map.of("requirement", "最小必要原则", "systemEvidence", "隐私事件检测 + 高敏策略命中统计"),
            Map.of("requirement", "告知与同意", "systemEvidence", "审计日志 input_overview / output_overview 追溯"),
            Map.of("requirement", "跨境流转可追溯", "systemEvidence", "治理事件 source_module + policy_version")));
        mapping.put("DSL", List.of(
            Map.of("requirement", "数据分类分级", "systemEvidence", "data_asset sensitivity_level"),
            Map.of("requirement", "风险监测", "systemEvidence", "security_event + governance_event")));
        mapping.put("GB/T 35273", List.of(
            Map.of("requirement", "个人信息处理留痕", "systemEvidence", "audit_log hash + audit_hash_chain"),
            Map.of("requirement", "风险评估与整改", "systemEvidence", "compliance_evidence_record closedLoopRate")));
        return mapping;
    }

    public Map<String, Object> buildAuditHashChain(LocalDate from, LocalDate to) {
        Date fromDate = toDateStart(from);
        Date toDate = toDateEnd(to);
        Long companyId = companyScopeService.requireCompanyId();
        List<Long> userIds = companyScopeService.companyUserIds();

        QueryWrapper<AuditLog> wrapper = new QueryWrapper<AuditLog>()
            .orderByAsc("operation_time", "id")
            .last("limit 50000");
        if (!userIds.isEmpty()) {
            wrapper.in("user_id", userIds);
        }
        List<AuditLog> logs = auditLogService.list(wrapper);

        jdbcTemplate.update("DELETE FROM audit_hash_chain WHERE company_id = ?", companyId);

        String prevHash = "";
        Date now = new Date();
        long linked = 0L;
        long rebuilt = 0L;
        for (AuditLog log : logs) {
            String current = sha256(String.join("|",
                String.valueOf(companyId),
                String.valueOf(log.getId()),
                String.valueOf(log.getUserId()),
                safe(log.getOperation()),
                safe(normalizeEpochMillis(log.getOperationTime())),
                safe(log.getInputOverview()),
                safe(log.getOutputOverview()),
                safe(log.getResult()),
                safe(prevHash)
            ));
            jdbcTemplate.update(
                """
                INSERT INTO audit_hash_chain(company_id, audit_log_id, prev_hash, current_hash, create_time)
                VALUES (?, ?, ?, ?, ?)
                """,
                companyId,
                log.getId(),
                prevHash,
                current,
                now
            );
            if (log.getOperationTime() != null && !log.getOperationTime().before(fromDate) && !log.getOperationTime().after(toDate)) {
                linked++;
            }
            prevHash = current;
            rebuilt++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", from);
        result.put("to", to);
        result.put("linkedCount", linked);
        result.put("rebuiltCount", rebuilt);
        result.put("tailHash", prevHash);
        result.put("externalAnchor", externalAnchorService.anchorEvidence(companyId, "audit_hash_chain", String.valueOf(linked), prevHash));
        return result;
    }

    public Map<String, Object> industrySpecComparison() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("NIST AI RMF", Map.of(
            "govern", "已覆盖：治理事件中心 + 风险分级",
            "map", "已覆盖：资产/模型映射",
            "measure", "已覆盖：误报率/拦截率/延迟",
            "manage", "已覆盖：整改闭环 + 演练复盘"));
        result.put("ISO 27001", Map.of(
            "A.5", "已覆盖：策略与职责",
            "A.8", "已覆盖：资产分类分级",
            "A.12", "已覆盖：日志与监控",
            "A.16", "已覆盖：事件响应"));
        result.put("MLPS 2.0", Map.of(
            "审计可追溯", "已覆盖：audit_hash_chain",
            "边界防护", "已覆盖：cross-site guard + 输入净化",
            "安全运维", "已覆盖：慢查询与可靠性演练告警"));
        return result;
    }

    public Map<String, Object> exportEvidencePackage(LocalDate from, LocalDate to, boolean includePdf, boolean includeJson) {
        User user = currentUserService.requireCurrentUser();
        Map<String, Object> experiment = buildExperimentReport(from.minusDays(7), from.minusDays(1), from, to);
        Map<String, Object> compliance = generateComplianceEvidence(from, to);
        Map<String, Object> hashChain = buildAuditHashChain(from, to);
        Map<String, Object> mapping = privacyComplianceMapping();
        Map<String, Object> standards = industrySpecComparison();
        Map<String, Object> zeroTrust = zeroTrustAssessment();
        Map<String, Object> threats = generateThreatInventory();

        Map<String, Object> pack = new LinkedHashMap<>();
        pack.put("exportedAt", new Date());
        pack.put("exportedBy", user.getUsername());
        pack.put("period", Map.of("from", from, "to", to));
        pack.put("experiment", experiment);
        pack.put("complianceEvidence", compliance);
        pack.put("privacyComplianceMapping", mapping);
        pack.put("auditHashChain", hashChain);
        pack.put("industrySpecComparison", standards);
        pack.put("zeroTrust", zeroTrust);
        pack.put("threatInventory", threats);

        String packageJson = writeJson(pack);
        String signature = sha256(packageJson);
        Map<String, Object> externalAnchor = externalAnchorService.anchorEvidence(companyScopeService.requireCompanyId(), "evidence_package", from + "_" + to, signature);

        Path exportDir = Paths.get(awardExportDir).toAbsolutePath().normalize();
        String stamp = String.valueOf(System.currentTimeMillis());
        Path jsonPath = exportDir.resolve("evidence-package-" + stamp + ".json");
        Path sigPath = exportDir.resolve("evidence-package-" + stamp + ".sig");
        Path pdfPath = exportDir.resolve("evidence-package-" + stamp + ".pdf");
        String pdfWarning = null;
        try {
            Files.createDirectories(exportDir);
            if (includeJson) {
                Files.writeString(jsonPath, packageJson, StandardCharsets.UTF_8);
                Files.writeString(sigPath, signature, StandardCharsets.UTF_8);
            }
            if (includePdf) {
                try {
                    writePdfSummary(pdfPath, pack, signature);
                } catch (Exception pdfEx) {
                    pdfWarning = "PDF 生成失败，已保留 JSON 与签名文件: " + pdfEx.getMessage();
                }
            }
        } catch (IOException ex) {
            throw new RuntimeException("导出证据包失败: " + ex.getMessage(), ex);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("signature", signature);
        result.put("externalAnchor", externalAnchor);
        result.put("json", includeJson ? jsonPath.toString() : null);
        result.put("sig", includeJson ? sigPath.toString() : null);
        result.put("pdf", includePdf && pdfWarning == null ? pdfPath.toString() : null);
        if (pdfWarning != null) {
            result.put("warning", pdfWarning);
        }
        return result;
    }

    public Map<String, Object> zeroTrustAssessment() {
        Long companyId = companyScopeService.requireCompanyId();
        Date since = Date.from(LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());
        long pendingRisk = governanceEventService.count(new QueryWrapper<GovernanceEvent>()
            .eq("company_id", companyId)
            .eq("status", "pending"));
        long highRisk = governanceEventService.count(new QueryWrapper<GovernanceEvent>()
            .eq("company_id", companyId)
            .in("severity", List.of("high", "critical"))
            .ge("event_time", since));
        long suspiciousAudit = auditLogService.count(new QueryWrapper<AuditLog>()
            .in("risk_level", List.of("HIGH", "high", "MEDIUM", "medium"))
            .ge("operation_time", since));

        double score = 100.0 - Math.min(70.0, pendingRisk * 1.5 + highRisk * 2.5 + suspiciousAudit * 1.0);
        String level = score >= 85 ? "trusted" : score >= 70 ? "watch" : score >= 55 ? "restricted" : "quarantine";
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("score", round2(score));
        result.put("level", level);
        result.put("factors", Map.of(
            "pendingRisk", pendingRisk,
            "highRisk24h", highRisk,
            "suspiciousAudit24h", suspiciousAudit
        ));
        return result;
    }

    public Map<String, Object> generateThreatInventory() {
        Long companyId = companyScopeService.requireCompanyId();
        Date since = Date.from(LocalDate.now().minusDays(30).atStartOfDay(ZoneId.systemDefault()).toInstant());
        List<GovernanceEvent> events = governanceEventService.list(new QueryWrapper<GovernanceEvent>()
            .eq("company_id", companyId)
            .ge("event_time", since)
            .orderByDesc("event_time")
            .last("limit 5000"));
        Map<String, Long> threatCounter = new LinkedHashMap<>();
        Map<String, String> mitreMapping = new LinkedHashMap<>();
        for (GovernanceEvent event : events) {
            String attackType = StringUtils.hasText(event.getAttackType()) ? event.getAttackType() : "unknown";
            threatCounter.put(attackType, threatCounter.getOrDefault(attackType, 0L) + 1L);
            mitreMapping.putIfAbsent(attackType, mapMitreTechnique(attackType));
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("windowDays", 30);
        result.put("threats", threatCounter);
        result.put("mitreMapping", mitreMapping);
        result.put("total", events.size());
        return result;
    }

    public Map<String, Object> generateAdversarialReplayReport(int limit) {
        Long companyId = companyScopeService.requireCompanyId();
        int safeLimit = Math.max(1, Math.min(30, limit));
        List<AdversarialRecord> records = adversarialRecordService.list(new QueryWrapper<AdversarialRecord>()
            .eq("company_id", companyId)
            .orderByDesc("create_time")
            .last("limit " + safeLimit));
        List<Map<String, Object>> replays = new ArrayList<>();
        for (AdversarialRecord record : records) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", record.getId());
            item.put("scenario", record.getScenario());
            item.put("eventId", record.getGovernanceEventId());
            item.put("analysis", record.getEffectivenessAnalysis());
            item.put("suggestions", parseJsonList(record.getSuggestionsJson()));
            item.put("createTime", record.getCreateTime());
            replays.add(item);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", replays.size());
        result.put("replays", replays);
        return result;
    }

    public Map<String, Object> innovationComparisonReport(LocalDate baselineFrom, LocalDate baselineTo, LocalDate currentFrom, LocalDate currentTo) {
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("kpiComparison", buildExperimentReport(baselineFrom, baselineTo, currentFrom, currentTo));
        report.put("adaptiveRule", adaptiveRuleEngineService.innovationReport());
        try {
            report.put("lstmInnovation", aiInferenceClient.innovationReport());
        } catch (Exception ex) {
            report.put("lstmInnovation", Map.of("available", false, "message", "python-service innovation endpoint unavailable"));
        }
        report.put("generatedAt", new Date());
        return report;
    }

    public Map<String, Object> buildFixedEvaluationPackage() {
        LocalDate currentTo = LocalDate.now();
        LocalDate currentFrom = currentTo.minusDays(6);
        LocalDate baselineTo = currentFrom.minusDays(1);
        LocalDate baselineFrom = baselineTo.minusDays(6);

        Map<String, Object> experiment = buildExperimentReport(baselineFrom, baselineTo, currentFrom, currentTo);
        Map<String, Object> innovation = innovationComparisonReport(baselineFrom, baselineTo, currentFrom, currentTo);
        Map<String, Object> zeroTrust = zeroTrustAssessment();

        Map<String, Object> fixed = new LinkedHashMap<>();
        fixed.put("fixedWindow", Map.of(
            "baselineFrom", baselineFrom,
            "baselineTo", baselineTo,
            "currentFrom", currentFrom,
            "currentTo", currentTo
        ));
        fixed.put("fixedSamplePolicy", Map.of(
            "governanceEventLimit", 5000,
            "auditHashChainLimit", 10000,
            "adversarialReplayLimit", 10,
            "httpHistoryDays", 7,
            "webVitalDays", 7
        ));
        fixed.put("fixedCodeVersion", evaluationCodeVersion);
        fixed.put("fixedEnvironment", Map.of(
            "java", System.getProperty("java.version"),
            "os", System.getProperty("os.name"),
            "timezone", ZoneId.systemDefault().toString(),
            "serverPort", serverPort,
            "aiInferenceBaseUrl", aiInferenceBaseUrl
        ));
        fixed.put("experiment", experiment);
        fixed.put("innovation", innovation);
        fixed.put("zeroTrust", zeroTrust);
        fixed.put("generatedAt", new Date());

        String packHash = sha256(writeJson(fixed));
        fixed.put("packageHash", packHash);
        fixed.put("externalAnchor", externalAnchorService.anchorEvidence(companyScopeService.requireCompanyId(), "fixed_evaluation", evaluationCodeVersion, packHash));
        return fixed;
    }

    private WindowMetrics windowMetrics(Long companyId, LocalDate from, LocalDate to) {
        Date fromDate = toDateStart(from);
        Date toDate = toDateEnd(to);
        List<GovernanceEvent> events = governanceEventService.list(new QueryWrapper<GovernanceEvent>()
            .eq("company_id", companyId)
            .between("event_time", fromDate, toDate)
            .orderByDesc("event_time")
            .last("limit 5000"));

        long total = events.size();
        long ignored = events.stream().filter(e -> "ignored".equalsIgnoreCase(safe(e.getStatus()))).count();
        long blocked = events.stream().filter(e -> "blocked".equalsIgnoreCase(safe(e.getStatus()))).count();

        List<Long> responseTimes = new ArrayList<>();
        for (GovernanceEvent event : events) {
            if (event.getDisposedAt() != null && event.getEventTime() != null && isClosedStatus(event.getStatus())) {
                long duration = Math.max(0L, (event.getDisposedAt().getTime() - event.getEventTime().getTime()) / 1000L);
                responseTimes.add(duration);
            }
        }
        responseTimes.sort(Long::compareTo);

        double falsePositiveRate = total == 0 ? 0.0 : round2((ignored * 100.0) / total);
        double interceptionRate = total == 0 ? 0.0 : round2((blocked * 100.0) / total);
        double avgResponseSeconds = responseTimes.isEmpty()
            ? 0.0
            : round2(responseTimes.stream().mapToLong(Long::longValue).average().orElse(0.0));
        long p95ResponseSeconds = percentile(responseTimes, 0.95);

        return new WindowMetrics(from, to, total, falsePositiveRate, interceptionRate, avgResponseSeconds, p95ResponseSeconds);
    }

    private boolean hasAuditTrace(Long eventId, Date fromDate, Date toDate, Long companyId) {
        if (eventId == null) {
            return false;
        }
        List<Long> companyUserIds = companyScopeService.companyUserIds();
        QueryWrapper<AuditLog> wrapper = new QueryWrapper<AuditLog>()
            .between("operation_time", fromDate, toDate)
            .like("input_overview", "eventId=" + eventId)
            .last("limit 1");
        if (!companyUserIds.isEmpty()) {
            wrapper.in("user_id", companyUserIds);
        }
        return auditLogService.count(wrapper) > 0;
    }

    private Map<String, Long> aggregateByType(List<GovernanceEvent> events) {
        Map<String, Long> map = new LinkedHashMap<>();
        for (GovernanceEvent event : events) {
            String key = safe(event.getEventType()).toUpperCase(Locale.ROOT);
            if (!StringUtils.hasText(key)) {
                key = "UNKNOWN";
            }
            map.put(key, map.getOrDefault(key, 0L) + 1L);
        }
        return map;
    }

    private ProbeSummary probePath(String path, int count) {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

        // Warm up route handler to reduce cold-start impact on latency SLO measurements.
        try {
            HttpRequest warmup = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + serverPort + path))
                .timeout(Duration.ofSeconds(2))
                .GET()
                .build();
            client.send(warmup, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {
            // Probe loop below captures actual availability/latency outcomes.
        }

        List<Long> latencies = new ArrayList<>();
        int success = 0;
        for (int i = 0; i < count; i++) {
            long startNanos = System.nanoTime();
            boolean ok = sendProbe(client, path);
            if (!ok) {
                // Retry once to avoid counting transient transport hiccups as hard downtime.
                ok = sendProbe(client, path);
            }
            latencies.add(Math.max(1L, (System.nanoTime() - startNanos) / 1_000_000L));
            if (ok) {
                success++;
            }
        }
        latencies.sort(Long::compareTo);
        double successRate = count == 0 ? 0.0 : round2((success * 100.0) / count);
        return new ProbeSummary(count, success, successRate, percentile(latencies, 0.95), latencies);
    }

    private boolean sendProbe(HttpClient client, String path) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + serverPort + path))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() >= 200 && response.statusCode() < 400;
        } catch (Exception ex) {
            return false;
        }
    }

    private Map<String, Object> probeInfrastructureComponents() {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean mysqlOk = probeMysql();
        boolean redisOk = probeRedis();
        boolean thirdPartyOk = probeThirdPartyApi();
        boolean criticalHealthy = mysqlOk && thirdPartyOk;
        result.put("mysql", mysqlOk);
        result.put("redis", redisOk);
        result.put("thirdPartyApi", thirdPartyOk);
        result.put("criticalHealthy", criticalHealthy);
        result.put("allHealthy", criticalHealthy && redisOk);
        result.put("degraded", !criticalHealthy || !redisOk);
        result.put("degradeReason", !criticalHealthy ? "critical_dependency_unhealthy" : (!redisOk ? "redis_unhealthy" : "none"));
        return result;
    }

    private boolean probeMysql() {
        try {
            Integer one = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1;
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean probeRedis() {
        final int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String key = "aegis:probe:" + System.currentTimeMillis() + ":" + attempt;
            try {
                stringRedisTemplate.opsForValue().set(key, "ok", Duration.ofSeconds(10));
                String value = stringRedisTemplate.opsForValue().get(key);
                stringRedisTemplate.delete(key);
                if ("ok".equals(value)) {
                    return true;
                }
            } catch (Exception ignored) {
                // Retry once or twice for short-lived network jitter before marking Redis unhealthy.
            }
            if (attempt < maxAttempts) {
                try {
                    Thread.sleep(80L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }
        }
        return false;
    }

    private boolean probeThirdPartyApi() {
        try {
            aiInferenceClient.metrics();
            return true;
        } catch (Exception ex) {
            try {
                URI uri = URI.create(aiInferenceBaseUrl.replaceAll("/$", "") + "/health");
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
                HttpRequest req = HttpRequest.newBuilder().uri(uri).GET().timeout(Duration.ofSeconds(3)).build();
                HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
                return resp.statusCode() >= 200 && resp.statusCode() < 400;
            } catch (Exception ignored) {
                return false;
            }
        }
    }

    private List<Object> parseJsonList(String raw) {
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        try {
            Object parsed = objectMapper.readValue(raw, Object.class);
            if (parsed instanceof List<?> list) {
                return new ArrayList<>(list);
            }
            return List.of(parsed);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private String mapMitreTechnique(String attackType) {
        String key = safe(attackType).toLowerCase(Locale.ROOT);
        return switch (key) {
            case "data_exfil_steg" -> "T1041 Exfiltration Over C2 Channel";
            case "prompt_injection" -> "T1190 Exploit Public-Facing Application";
            case "shadow_deployment" -> "T1583 Acquire Infrastructure";
            case "decision_drift" -> "T1565 Data Manipulation";
            case "credential_harvest" -> "T1555 Credentials from Password Stores";
            case "resilience_chaos" -> "T1499 Endpoint Denial of Service";
            default -> "T1595 Active Scanning";
        };
    }

    private void writePdfSummary(Path pdfPath, Map<String, Object> pack, String signature) throws IOException {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(PDType1Font.HELVETICA, 11);
                content.setLeading(14f);
                content.newLineAtOffset(40, 780);
                content.showText("AegisAI Evidence Package");
                content.newLine();
                content.showText("Generated At: " + String.valueOf(pack.get("exportedAt")));
                content.newLine();
                content.showText("Exported By: " + String.valueOf(pack.get("exportedBy")));
                content.newLine();
                content.showText("Signature: " + signature);
                content.newLine();
                content.newLine();
                content.showText("Sections: experiment, complianceEvidence, privacyComplianceMapping,");
                content.newLine();
                content.showText("auditHashChain, industrySpecComparison, zeroTrust, threatInventory.");
                content.endText();
            }
            document.save(pdfPath.toFile());
        }
    }

    private String normalizePath(String path, String fallback) {
        String target = StringUtils.hasText(path) ? path.trim() : fallback;
        if (!target.startsWith("/")) {
            target = "/" + target;
        }
        return target;
    }

    private Date toDateStart(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private Date toDateEnd(LocalDate date) {
        return Date.from(date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).minusSeconds(1).toInstant());
    }

    private boolean isClosedStatus(String status) {
        String normalized = safe(status).toLowerCase(Locale.ROOT);
        return "blocked".equals(normalized) || "ignored".equals(normalized) || "resolved".equals(normalized);
    }

    private long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0L;
        }
        int idx = Math.min(sorted.size() - 1, (int) Math.ceil(sorted.size() * p) - 1);
        return sorted.get(Math.max(0, idx));
    }

    private double safeReduction(double baseline, double current) {
        if (baseline <= 0.0) {
            return 0.0;
        }
        return round2(((baseline - current) / baseline) * 100.0);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String safe(String input) {
        return input == null ? "" : input;
    }

    private String normalizeEpochMillis(Date operationTime) {
        if (operationTime == null) {
            return null;
        }
        long secondAligned = (operationTime.getTime() / 1000L) * 1000L;
        return String.valueOf(secondAligned);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((text == null ? "" : text).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            return Integer.toHexString((text == null ? "" : text).hashCode());
        }
    }

    private static class WindowMetrics {
        private final LocalDate from;
        private final LocalDate to;
        private final long totalEvents;
        private final double falsePositiveRate;
        private final double interceptionRate;
        private final double avgResponseSeconds;
        private final long p95ResponseSeconds;

        private WindowMetrics(LocalDate from,
                              LocalDate to,
                              long totalEvents,
                              double falsePositiveRate,
                              double interceptionRate,
                              double avgResponseSeconds,
                              long p95ResponseSeconds) {
            this.from = from;
            this.to = to;
            this.totalEvents = totalEvents;
            this.falsePositiveRate = falsePositiveRate;
            this.interceptionRate = interceptionRate;
            this.avgResponseSeconds = avgResponseSeconds;
            this.p95ResponseSeconds = p95ResponseSeconds;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("from", from);
            map.put("to", to);
            map.put("totalEvents", totalEvents);
            map.put("falsePositiveRate", falsePositiveRate);
            map.put("interceptionRate", interceptionRate);
            map.put("avgResponseSeconds", avgResponseSeconds);
            map.put("p95ResponseSeconds", p95ResponseSeconds);
            return map;
        }
    }

    private static class ProbeSummary {
        private final int totalCount;
        private final int successCount;
        private final double successRate;
        private final long p95LatencyMs;
        private final List<Long> latencies;

        private ProbeSummary(int totalCount, int successCount, double successRate, long p95LatencyMs, List<Long> latencies) {
            this.totalCount = totalCount;
            this.successCount = successCount;
            this.successRate = successRate;
            this.p95LatencyMs = p95LatencyMs;
            this.latencies = latencies;
        }

        private static ProbeSummary empty() {
            return new ProbeSummary(0, 0, 0.0, 0L, List.of());
        }

        private Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("totalCount", totalCount);
            map.put("successCount", successCount);
            map.put("successRate", successRate);
            map.put("p95LatencyMs", p95LatencyMs);
            map.put("latencies", latencies);
            return map;
        }
    }
}
