package com.trustai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.client.AiInferenceClient;
import com.trustai.controller.AiGatewayController.BattleReq;
import com.trustai.controller.AiGatewayController.ChatReq;
import com.trustai.controller.AiGatewayController.Message;
import com.trustai.entity.AiCallLog;
import com.trustai.entity.AiModel;
import com.trustai.entity.AuditLog;
import com.trustai.entity.RiskEvent;
import com.trustai.entity.SecurityDetectionRule;
import com.trustai.entity.SecurityEvent;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class AiGatewayService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> TRUSTED_AI_MODEL_KEYS = List.of(
        "qwen", "tongyi", "通义",
        "wenxin", "ernie", "文心",
        "deepseek",
        "doubao", "豆包",
        "hunyuan", "混元", "tencent"
    );
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final AiCallAuditService aiCallAuditService;
    private final AiModelService aiModelService;
    private final AiModelAccessGuardService aiModelAccessGuardService;
    private final AiInferenceClient aiInferenceClient;
    private final SecurityEventService securityEventService;
    private final SecurityDetectionRuleService securityDetectionRuleService;
    private final RiskEventService riskEventService;
    private final AuditLogService auditLogService;
    private final CompanyScopeService companyScopeService;
    private final CurrentUserService currentUserService;
    private final PrivacyShieldConfigService privacyShieldConfigService;

    public AiGatewayService(AiCallAuditService aiCallAuditService,
                            AiModelService aiModelService,
                            AiModelAccessGuardService aiModelAccessGuardService,
                            AiInferenceClient aiInferenceClient,
                            SecurityEventService securityEventService,
                            SecurityDetectionRuleService securityDetectionRuleService,
                            RiskEventService riskEventService,
                            AuditLogService auditLogService,
                            CompanyScopeService companyScopeService,
                            CurrentUserService currentUserService,
                            PrivacyShieldConfigService privacyShieldConfigService) {
        this.aiCallAuditService = aiCallAuditService;
        this.aiModelService = aiModelService;
        this.aiModelAccessGuardService = aiModelAccessGuardService;
        this.aiInferenceClient = aiInferenceClient;
        this.securityEventService = securityEventService;
        this.securityDetectionRuleService = securityDetectionRuleService;
        this.riskEventService = riskEventService;
        this.auditLogService = auditLogService;
        this.companyScopeService = companyScopeService;
        this.currentUserService = currentUserService;
        this.privacyShieldConfigService = privacyShieldConfigService;
    }

    public Map<String, Object> modelMetrics() {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> metrics = aiInferenceClient.metrics();
            result.put("available", true);
            result.put("fetchedAt", System.currentTimeMillis());
            result.put("metrics", metrics);
        } catch (Exception ex) {
            result.put("available", false);
            result.put("reason", "PYTHON_SERVICE_UNAVAILABLE");
            result.put("message", ex.getMessage());
            result.put("metrics", Map.of());
        }
        return result;
    }

    public Map<String, Object> modelLineage() {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> lineage = aiInferenceClient.modelLineage();
            result.put("available", true);
            result.put("fetchedAt", System.currentTimeMillis());
            result.put("lineage", lineage);
        } catch (Exception ex) {
            result.put("available", false);
            result.put("reason", "PYTHON_SERVICE_UNAVAILABLE");
            result.put("message", ex.getMessage());
            result.put("lineage", Map.of());
        }
        return result;
    }

    public Map<String, Object> modelDriftStatus() {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> drift = aiInferenceClient.driftStatus();
            result.put("available", true);
            result.put("fetchedAt", System.currentTimeMillis());
            result.put("drift", drift);
        } catch (Exception ex) {
            result.put("available", false);
            result.put("reason", "PYTHON_SERVICE_UNAVAILABLE");
            result.put("message", ex.getMessage());
            result.put("drift", Map.of());
        }
        return result;
    }

    public Map<String, Object> explainabilityReport() {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> report = aiInferenceClient.explainabilityReport();
            result.put("available", true);
            result.put("fetchedAt", System.currentTimeMillis());
            result.put("report", report);
        } catch (Exception ex) {
            result.put("available", false);
            result.put("reason", "PYTHON_SERVICE_UNAVAILABLE");
            result.put("message", ex.getMessage());
            result.put("report", Map.of());
        }
        return result;
    }

    public Map<String, Object> dataFactoryBuild(Map<String, Object> payload) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> data = aiInferenceClient.buildDataFactory(payload == null ? Map.of() : payload);
            result.put("available", true);
            result.put("fetchedAt", System.currentTimeMillis());
            result.put("data", data);
        } catch (Exception ex) {
            result.put("available", false);
            result.put("reason", "PYTHON_SERVICE_UNAVAILABLE");
            result.put("message", ex.getMessage());
            result.put("data", Map.of());
        }
        return result;
    }

    public Map<String, Object> trainFactory(Map<String, Object> payload) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> data = aiInferenceClient.trainFactory(payload == null ? Map.of() : payload);
            result.put("available", true);
            result.put("fetchedAt", System.currentTimeMillis());
            result.put("data", data);
        } catch (Exception ex) {
            result.put("available", false);
            result.put("reason", "PYTHON_SERVICE_UNAVAILABLE");
            result.put("message", ex.getMessage());
            result.put("data", Map.of());
        }
        return result;
    }

    public Map<String, Object> trainAdversarialFeedback(Map<String, Object> payload) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> data = aiInferenceClient.trainAdversarialFeedback(payload == null ? Map.of() : payload);
            result.put("available", true);
            result.put("fetchedAt", System.currentTimeMillis());
            result.put("data", data);
        } catch (Exception ex) {
            result.put("available", false);
            result.put("reason", "PYTHON_SERVICE_UNAVAILABLE");
            result.put("message", ex.getMessage());
            result.put("data", Map.of());
        }
        return result;
    }

    public Map<String, Object> modelReleaseStatus() {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> release = aiInferenceClient.modelReleaseStatus();
            result.put("available", true);
            result.put("fetchedAt", System.currentTimeMillis());
            result.put("release", release);
        } catch (Exception ex) {
            result.put("available", false);
            result.put("reason", "PYTHON_SERVICE_UNAVAILABLE");
            result.put("message", ex.getMessage());
            result.put("release", Map.of());
        }
        return result;
    }

    public Map<String, Object> modelReleaseTrafficStats() {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> stats = aiInferenceClient.modelReleaseTrafficStats();
            long totalRequests = toLong(
                firstNonNull(
                    stats == null ? null : stats.get("totalRequests"),
                    stats == null ? null : stats.get("requestTotal"),
                    nested(stats, "traffic", "totalRequests"),
                    nested(stats, "traffic", "requestTotal"),
                    nested(stats, "summary", "totalRequests")
                ),
                0L
            );
            Object buckets = firstNonNull(
                stats == null ? null : stats.get("buckets"),
                stats == null ? null : stats.get("bucketStats"),
                nested(stats, "traffic", "buckets"),
                nested(stats, "traffic", "bucketStats"),
                nested(stats, "traffic", "variants")
            );
            int bucketCount = toSize(buckets);
            result.put("available", true);
            result.put("fetchedAt", System.currentTimeMillis());
            result.put("traffic", stats);
            result.put("totalRequests", totalRequests);
            result.put("bucketCount", bucketCount);
        } catch (Exception ex) {
            result.put("available", false);
            result.put("reason", "PYTHON_SERVICE_UNAVAILABLE");
            result.put("message", ex.getMessage());
            result.put("traffic", Map.of());
            result.put("totalRequests", 0L);
            result.put("bucketCount", 0);
        }
        return result;
    }

    public Map<String, Object> registerModelReleaseCandidate(Map<String, Object> payload) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> data = aiInferenceClient.registerModelReleaseCandidate(payload == null ? Map.of() : payload);
            result.put("available", true);
            result.put("fetchedAt", System.currentTimeMillis());
            result.put("data", data);
        } catch (Exception ex) {
            result.put("available", false);
            result.put("reason", "PYTHON_SERVICE_UNAVAILABLE");
            result.put("message", ex.getMessage());
            result.put("data", Map.of());
        }
        return result;
    }

    public Map<String, Object> promoteModelReleaseCanary(Map<String, Object> payload) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> data = aiInferenceClient.promoteModelReleaseCanary(payload == null ? Map.of() : payload);
            result.put("available", true);
            result.put("fetchedAt", System.currentTimeMillis());
            result.put("data", data);
        } catch (Exception ex) {
            result.put("available", false);
            result.put("reason", "PYTHON_SERVICE_UNAVAILABLE");
            result.put("message", ex.getMessage());
            result.put("data", Map.of());
        }
        return result;
    }

    public Map<String, Object> promoteModelReleaseStable(Map<String, Object> payload) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> data = aiInferenceClient.promoteModelReleaseStable(payload == null ? Map.of() : payload);
            result.put("available", true);
            result.put("fetchedAt", System.currentTimeMillis());
            result.put("data", data);
        } catch (Exception ex) {
            result.put("available", false);
            result.put("reason", "PYTHON_SERVICE_UNAVAILABLE");
            result.put("message", ex.getMessage());
            result.put("data", Map.of());
        }
        return result;
    }

    public Map<String, Object> rollbackModelRelease(Map<String, Object> payload) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> data = aiInferenceClient.rollbackModelRelease(payload == null ? Map.of() : payload);
            result.put("available", true);
            result.put("fetchedAt", System.currentTimeMillis());
            result.put("data", data);
        } catch (Exception ex) {
            result.put("available", false);
            result.put("reason", "PYTHON_SERVICE_UNAVAILABLE");
            result.put("message", ex.getMessage());
            result.put("data", Map.of());
        }
        return result;
    }

    public Map<String, Object> chat(ChatReq req) {
        Instant begin = Instant.now();
        Long companyId = companyScopeService.requireCompanyId();
        AiModel model = aiModelService.lambdaQuery()
            .eq(AiModel::getModelCode, req.getModel())
            .eq(companyId != null, AiModel::getCompanyId, companyId)
            .one();
        if (!isOfficialModel(model)) {
            throw new BizException(40000, "仅允许使用官方可信5个AI目录中的模型");
        }
        aiModelAccessGuardService.validate(model, req.getAssetId(), req.getAccessReason(), mergeMessages(req.getMessages()));

        String provider = model.getProvider() != null ? model.getProvider().toLowerCase(Locale.ROOT) : req.getProvider().toLowerCase(Locale.ROOT);
        ProviderConfig cfg = ProviderConfig.of(provider);
        String statusFlag = "success";
        if (cfg == null) {
            Map<String, Object> errorResp = Map.of("reason", "unsupported provider: " + provider);
            persistLog(req, model, provider, errorResp, begin, "fail");
            throw new BizException(40000, "不支持的模型供应商: " + provider);
        }
        if (cfg.apiKey == null || cfg.apiKey.isEmpty()) {
            Map<String, Object> errorResp = Map.of("reason", "apiKey missing for " + provider);
            persistLog(req, model, provider, errorResp, begin, "fail");
            throw new BizException(50000, "AI 网关未配置 API Key，请先完成供应商配置");
        }

        try {
            String body = cfg.payloadBuilder.build(req.getModel(), req.getMessages());
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(cfg.url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json");
            cfg.authApplier.apply(builder, cfg.apiKey);
            HttpRequest request = builder.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            HttpResponse<String> resp = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            // ── 响应泄露扫描：检测 AI 是否将用户隐私原样回传 ───────────────────
            String rawBody = aiModelAccessGuardService.scanResponseForExfiltration(resp.body(), req.getModel());
            Map<String, Object> map = new HashMap<>();
            map.put("provider", provider);
            map.put("model", req.getModel());
            map.put("status", resp.statusCode());
            map.put("raw", rawBody);
            statusFlag = resp.statusCode() >= 200 && resp.statusCode() < 300 ? "success" : "fail";
            persistLog(req, model, provider, map, begin, statusFlag);
            return map;
        } catch (Exception e) {
            Map<String, Object> errorResp = Map.of("reason", "invoke failed: " + e.getMessage());
            persistLog(req, model, provider, errorResp, begin, "fail");
            throw new BizException(50000, "AI 网关调用失败: " + e.getMessage());
        }
    }

    public List<Map<String, Object>> modelCatalog() {
        Long companyId = companyScopeService.requireCompanyId();
        return aiModelService.lambdaQuery()
            .eq(companyId != null, AiModel::getCompanyId, companyId)
            .eq(AiModel::getStatus, "enabled")
            .list()
            .stream()
            .filter(this::isOfficialModel)
            .map(item -> {
                Map<String, Object> model = new HashMap<>();
                model.put("id", item.getId());
                model.put("modelCode", item.getModelCode());
                model.put("modelName", item.getModelName());
                model.put("provider", item.getProvider());
                model.put("riskLevel", item.getRiskLevel());
                model.put("isolationLevel", item.getIsolationLevel() == null ? "L2" : item.getIsolationLevel());
                model.put("status", item.getStatus());
                return model;
            })
            .collect(Collectors.toList());
    }

    private boolean isOfficialModel(AiModel model) {
        if (model == null) {
            return false;
        }
        String name = normalizeLower(model.getModelName());
        String provider = normalizeLower(model.getProvider());
        String code = normalizeLower(model.getModelCode());
        for (String key : TRUSTED_AI_MODEL_KEYS) {
            if (name.contains(key) || provider.contains(key) || code.contains(key)) {
                return true;
            }
        }
        return false;
    }

    public Map<String, Object> adversarialMeta() {
        Map<String, Object> assessment = buildThreatAssessment(false);
        try {
            Map<String, Object> meta = aiInferenceClient.adversarialMeta();
            if (meta != null) {
                assessment.putAll(meta);
            }
            assessment.put("adversarialAvailable", true);
        } catch (Exception ex) {
            assessment.put("adversarialAvailable", false);
            assessment.put("adversarialError", ex.getMessage());
            assessment.putIfAbsent("scenarios", List.of(
                Map.of("code", "real-threat-check", "description", "基于当前真实日志做实时态势评估")
            ));
        }
        return assessment;
    }

    public Map<String, Object> adversarialRun(BattleReq req) {
        String scenario = req == null || req.getScenario() == null || req.getScenario().isBlank()
            ? "real-threat-check"
            : req.getScenario().trim();
        int rounds = req == null || req.getRounds() == null ? 10 : Math.max(1, Math.min(100, req.getRounds()));
        Integer seed = req == null ? null : req.getSeed();
        int effectiveSeed = seed == null ? ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE) : seed;

        Map<String, Object> assessment = buildThreatAssessment(true);
        long defenseStrengthScore = computeDefenseStrengthScore(assessment);
        long riskScoreAdjusted = Math.max(0L, Math.min(100L, toLong(assessment.get("riskScore"), 0L) - Math.max(0L, defenseStrengthScore - 50L) / 3L));
        assessment.put("defenseStrengthScore", defenseStrengthScore);
        assessment.put("riskScoreAdjusted", riskScoreAdjusted);
        assessment.put("hardeningStatus", "pending_manual_apply");
        assessment.put("hardeningActions", buildHardeningActions());
        if ("real-threat-check".equalsIgnoreCase(scenario)) {
            Map<String, Object> quick = new HashMap<>(assessment);
            List<String> suggestions = buildOptimizationSuggestions(quick, true);
            double attackSuccessRate = Math.max(0.05, Math.min(0.90, riskScoreAdjusted / 130.0 + (100.0 - defenseStrengthScore) / 260.0));
            int defenderScore = (int) Math.round(defenseStrengthScore + (1.0 - attackSuccessRate) * 28.0);
            int attackerScore = (int) Math.round(riskScoreAdjusted + attackSuccessRate * 35.0);
            String winner = attackSuccessRate >= 0.5 ? "攻击方" : "防御方";
            quick.put("scenario", "real-threat-check");
            quick.put("mode", "real-threat-assessment");
            quick.put("rounds", rounds);
            quick.put("seed", effectiveSeed);
            quick.put("ok", true);
            quick.put("battle", Map.of(
                "winner", winner,
                "total_rounds", 0,
                "attack_success_rate", attackSuccessRate,
                "attacker_final_score", attackerScore,
                "defender_final_score", defenderScore,
                "rounds", List.of(),
                "recommendations", suggestions,
                "defense_strength_score", defenseStrengthScore,
                "risk_score_adjusted", riskScoreAdjusted,
                "hardening_status", "pending_manual_apply"
            ));
            quick.put("optimizationSuggestions", suggestions);
            quick.put("suggestions", suggestions);
            return quick;
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("scenario", scenario);
            payload.put("rounds", rounds);
            payload.put("seed", effectiveSeed);
            Map<String, Object> remote = aiInferenceClient.adversarialRun(payload);
            return finalizeBattleResult(assessment, remote, true, null);
        } catch (Exception ex) {
            Map<String, Object> unavailable = new HashMap<>(assessment);
            unavailable.put("scenario", scenario);
            unavailable.put("rounds", rounds);
            unavailable.put("seed", effectiveSeed);
            unavailable.put("ok", false);
            unavailable.put("mode", "engine_unavailable");
            unavailable.put("error", ex.getMessage());
            List<String> suggestions = buildOptimizationSuggestions(unavailable, false);
            unavailable.put("battle", Map.of(
                "winner", "unknown",
                "total_rounds", 0,
                "attack_success_rate", 0,
                "attacker_final_score", 0,
                "defender_final_score", 0,
                "rounds", List.of(),
                "recommendations", suggestions
            ));
            unavailable.put("optimizationSuggestions", suggestions);
            unavailable.put("suggestions", suggestions);
            return unavailable;
        }
    }

    public Map<String, Object> adversarialApplyHardening(Map<String, Object> payload) {
        Map<String, Object> request = payload == null ? Map.of() : payload;
        int thresholdReductionPct = (int) Math.max(5, Math.min(35, toLong(request.get("thresholdReductionPct"), 15L)));

        SecurityDetectionRule rule = securityDetectionRuleService.lambdaQuery()
            .eq(SecurityDetectionRule::getEnabled, Boolean.TRUE)
            .orderByAsc(SecurityDetectionRule::getId)
            .last("limit 1")
            .one();

        Date now = new Date();
        Long beforeThreshold;
        if (rule == null) {
            rule = new SecurityDetectionRule();
            rule.setName("Adaptive Hardening Baseline");
            rule.setEnabled(true);
            rule.setDescription("由攻防演练人工确认后生成的基础防御规则");
            rule.setSensitiveExtensions(".doc,.docx,.pdf,.xlsx,.sql,.pem");
            rule.setSensitivePaths("/finance,/hr,/source-code");
            rule.setAlertThresholdBytes(1024L * 1024L);
            rule.setCreateTime(now);
            beforeThreshold = rule.getAlertThresholdBytes();
        } else {
            beforeThreshold = rule.getAlertThresholdBytes() == null ? 1024L * 1024L : rule.getAlertThresholdBytes();
            rule.setSensitiveExtensions(appendCsvToken(rule.getSensitiveExtensions(), ".sql"));
            rule.setSensitiveExtensions(appendCsvToken(rule.getSensitiveExtensions(), ".pem"));
            rule.setSensitivePaths(appendCsvToken(rule.getSensitivePaths(), "/source-code"));
            rule.setSensitivePaths(appendCsvToken(rule.getSensitivePaths(), "/finance"));
        }

        long afterThreshold = Math.max(64L * 1024L, Math.round(beforeThreshold * (100 - thresholdReductionPct) / 100.0));
        rule.setAlertThresholdBytes(afterThreshold);
        rule.setUpdateTime(now);
        securityDetectionRuleService.saveOrUpdate(rule);

        Map<String, Object> trainFeedback = Map.of();
        try {
            trainFeedback = aiInferenceClient.trainAdversarialFeedback(Map.of(
                "reason", "manual_defense_hardening",
                "sample_source", "adversarial_drill",
                "priority", "high"
            ));
        } catch (Exception ex) {
            Map<String, Object> failed = new HashMap<>();
            failed.put("queued", false);
            failed.put("message", ex.getMessage() == null ? "train/adversarial-feedback failed" : ex.getMessage());
            trainFeedback = failed;
        }

        Map<String, Object> assessment = buildThreatAssessment(true);
        long defenseStrengthScore = computeDefenseStrengthScore(assessment);

        Map<String, Object> result = new HashMap<>();
        result.put("applied", true);
        result.put("approvalMode", "manual_confirmed");
        result.put("ruleId", rule.getId());
        result.put("thresholdReductionPct", thresholdReductionPct);
        result.put("beforeAlertThresholdBytes", beforeThreshold);
        result.put("afterAlertThresholdBytes", afterThreshold);
        result.put("defenseStrengthScore", defenseStrengthScore);
        result.put("trainFeedback", trainFeedback);
        result.put("appliedAt", System.currentTimeMillis());
        return result;
    }

    private Map<String, Object> buildThreatAssessment(boolean immediateCheck) {
        Long companyId = companyScopeService.requireCompanyId();
        Date oneDayAgo = Date.from(LocalDate.now().minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant());

        long securityCritical = securityEventService.count(new QueryWrapper<SecurityEvent>()
            .eq("company_id", companyId)
            .in("severity", List.of("critical", "high"))
            .in("status", List.of("pending", "reviewing")));
        long securityPending = securityEventService.count(new QueryWrapper<SecurityEvent>()
            .eq("company_id", companyId)
            .eq("status", "pending"));
        long openRisk = riskEventService.count(new QueryWrapper<RiskEvent>()
            .eq("company_id", companyId)
            .in("status", List.of("open", "processing")));
        List<Long> userIds = companyScopeService.companyUserIds();
        long highRiskAudit = userIds.isEmpty() ? 0L : auditLogService.count(new QueryWrapper<AuditLog>()
            .in("user_id", userIds)
            .in("risk_level", List.of("HIGH", "high", "MEDIUM", "medium"))
            .ge("operation_time", oneDayAgo));

        List<SecurityEvent> latestEvents = securityEventService.list(new QueryWrapper<SecurityEvent>()
            .eq("company_id", companyId)
            .orderByDesc("event_time")
            .last("limit 8"));

        long weightedPressure = securityCritical * 4 + securityPending * 2 + openRisk * 3 + highRiskAudit;
        long normalized = Math.min(100, weightedPressure * 3);
        String level = normalized >= 70 ? "high" : (normalized >= 40 ? "medium" : "low");
        long policyVersion = privacyShieldConfigService.getConfigVersion();
        Map<String, Object> policyConfig = privacyShieldConfigService.getOrCreateConfig();

        Map<String, Object> assessment = new HashMap<>();
        assessment.put("companyId", companyId);
        assessment.put("mode", "real-threat-assessment");
        assessment.put("immediateCheck", immediateCheck);
        assessment.put("threatLevel", level);
        assessment.put("riskScore", normalized);
        assessment.put("checkedAt", System.currentTimeMillis());
        assessment.put("signals", Map.of(
            "securityCritical", securityCritical,
            "securityPending", securityPending,
            "openRiskEvents", openRisk,
            "highRiskAudit24h", highRiskAudit
        ));
        assessment.put("policyVersion", policyVersion);
        assessment.put("defenseProfile", Map.of(
            "monitorEnabled", toBoolean(policyConfig.get("monitorEnabled")),
            "predictEnabled", toBoolean(policyConfig.get("predictEnabled")),
            "dedupeSeconds", toLong(policyConfig.get("dedupeSeconds"), 60L),
            "syncIntervalSec", toLong(policyConfig.get("syncIntervalSec"), 60L),
            "sensitiveKeywordsSize", toSize(policyConfig.get("sensitiveKeywords")),
            "siteSelectorSize", toSize(policyConfig.get("siteSelectors"))
        ));
        assessment.put("recentSecurityEvents", latestEvents);
        return assessment;
    }

    private long computeDefenseStrengthScore(Map<String, Object> assessment) {
        List<SecurityDetectionRule> enabledRules = securityDetectionRuleService.lambdaQuery()
            .eq(SecurityDetectionRule::getEnabled, Boolean.TRUE)
            .list();
        int enabledRuleCount = enabledRules == null ? 0 : enabledRules.size();
        long avgThreshold = enabledRules == null || enabledRules.isEmpty()
            ? 1024L * 1024L
            : Math.max(1L, Math.round(enabledRules.stream()
                .map(SecurityDetectionRule::getAlertThresholdBytes)
                .filter(v -> v != null && v > 0)
                .mapToLong(Long::longValue)
                .average()
                .orElse(1024L * 1024L)));

        Object profileObj = assessment.get("defenseProfile");
        boolean monitorEnabled = false;
        boolean predictEnabled = false;
        int keywordSize = 0;
        if (profileObj instanceof Map<?, ?> profile) {
            monitorEnabled = toBoolean(profile.get("monitorEnabled"));
            predictEnabled = toBoolean(profile.get("predictEnabled"));
            keywordSize = (int) toLong(profile.get("sensitiveKeywordsSize"), 0L);
        }

        double thresholdScore = 30.0 - Math.min(20.0, Math.max(0.0, (avgThreshold - 64 * 1024.0) / (1024 * 1024.0)) * 1.2);
        double score = 25.0
            + Math.min(20.0, enabledRuleCount * 4.0)
            + Math.max(0.0, thresholdScore)
            + (monitorEnabled ? 12.0 : 0.0)
            + (predictEnabled ? 10.0 : 0.0)
            + Math.min(8.0, keywordSize / 3.0);
        return Math.max(0L, Math.min(100L, Math.round(score)));
    }

    private List<Map<String, Object>> buildHardeningActions() {
        return List.of(
            Map.of(
                "code", "tighten_alert_threshold",
                "title", "收紧告警阈值",
                "impact", "high",
                "change", Map.of("thresholdReductionPct", 15),
                "approvalRequired", true
            ),
            Map.of(
                "code", "expand_sensitive_scope",
                "title", "扩展敏感目录与扩展名覆盖",
                "impact", "medium",
                "change", Map.of("paths", List.of("/source-code", "/finance"), "extensions", List.of(".sql", ".pem")),
                "approvalRequired", true
            ),
            Map.of(
                "code", "queue_adversarial_retrain",
                "title", "对抗样本增量训练",
                "impact", "medium",
                "change", Map.of("pipeline", "train/adversarial-feedback"),
                "approvalRequired", true
            )
        );
    }

    private String appendCsvToken(String source, String token) {
        String normalizedToken = token == null ? "" : token.trim();
        if (normalizedToken.isBlank()) {
            return source;
        }
        List<String> current = new ArrayList<>();
        if (source != null && !source.isBlank()) {
            for (String item : source.split(",")) {
                String trimmed = item.trim();
                if (!trimmed.isBlank()) {
                    current.add(trimmed);
                }
            }
        }
        boolean exists = current.stream().anyMatch(item -> item.equalsIgnoreCase(normalizedToken));
        if (!exists) {
            current.add(normalizedToken);
        }
        return String.join(",", current);
    }

    private Map<String, Object> finalizeBattleResult(Map<String, Object> assessment,
                                                     Map<String, Object> engineResult,
                                                     boolean pythonAvailable,
                                                     String engineError) {
        Map<String, Object> merged = new HashMap<>(assessment);
        if (engineResult != null) {
            merged.putAll(engineResult);
        }
        merged.put("assessment", assessment);
        merged.put("pythonAvailable", pythonAvailable);
        if (engineError != null && !engineError.isBlank()) {
            merged.put("degraded", true);
            merged.put("engineError", engineError);
        }

        String analysis = buildEffectivenessAnalysis(assessment, pythonAvailable, engineError);
        List<String> suggestions = buildOptimizationSuggestions(assessment, pythonAvailable);
        merged.put("effectivenessAnalysis", analysis);
        merged.put("optimizationSuggestions", suggestions);
        merged.put("suggestions", suggestions);
        merged.putIfAbsent("policyVersion", assessment.get("policyVersion"));
        merged.putIfAbsent("ok", true);
        if (!(merged.get("battle") instanceof Map<?, ?>)) {
            int rounds = (int) toLong(merged.getOrDefault("rounds", 10), 10L);
            Integer seed = null;
            try {
                seed = Integer.parseInt(String.valueOf(merged.get("seed")));
            } catch (Exception ignored) {
                // keep null
            }
            String scenario = String.valueOf(merged.getOrDefault("scenario", "real-threat-check"));
            String mode = String.valueOf(merged.getOrDefault("mode", pythonAvailable ? "python-engine" : "local-fallback"));
            merged.put("battle", buildSyntheticBattle(assessment, rounds, seed, scenario, mode));
        }
        return merged;
    }

    private Map<String, Object> buildSyntheticBattle(Map<String, Object> assessment,
                                                     int rounds,
                                                     Integer seed,
                                                     String scenario,
                                                     String mode) {
        int safeRounds = Math.max(1, Math.min(100, rounds));
        int safeSeed = seed == null ? ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE) : seed;
        Random rng = new Random(safeSeed);
        long riskScore = toLong(assessment.get("riskScore"), 45L);

        int attackerScore = 0;
        int defenderScore = 0;
        int successCount = 0;
        List<Map<String, Object>> roundList = new ArrayList<>();

        double baseSuccess = 0.25 + Math.min(0.45, riskScore / 220.0);
        for (int i = 1; i <= safeRounds; i++) {
            double effectiveness = Math.max(0.08, Math.min(0.92, baseSuccess + (rng.nextDouble() - 0.5) * 0.22));
            boolean success = rng.nextDouble() < effectiveness;
            if (success) {
                attackerScore += 8 + rng.nextInt(7);
                defenderScore -= 1;
                successCount++;
            } else {
                defenderScore += 7 + rng.nextInt(6);
                attackerScore -= 1;
            }

            Map<String, Object> roundPayload = new HashMap<>();
            roundPayload.put("round_num", i);
            roundPayload.put("attack_strategy", scenario + "_attack");
            roundPayload.put("defense_strategy", "adaptive_defense_" + ((i % 4) + 1));
            roundPayload.put("final_effectiveness", Math.round(effectiveness * 1000.0) / 1000.0);
            roundPayload.put("attack_success", success);
            roundPayload.put("narrative", success ? "攻击方突破本轮防线" : "防御方阻断本轮攻击");
            roundList.add(roundPayload);
        }

        String winner = attackerScore > defenderScore ? "攻击方" : "防御方";
        Map<String, Object> battle = new HashMap<>();
        battle.put("scenario", scenario);
        battle.put("mode", mode);
        battle.put("seed", safeSeed);
        battle.put("total_rounds", safeRounds);
        battle.put("attacker_final_score", attackerScore);
        battle.put("defender_final_score", defenderScore);
        battle.put("winner", winner);
        battle.put("attack_success_rate", safeRounds == 0 ? 0.0 : (double) successCount / (double) safeRounds);
        battle.put("rounds", roundList);
        battle.put("recommendations", List.of(
            "提升高危策略双人复核覆盖率",
            "对影子AI告警联动行为熔断",
            "周期性回放随机攻防样本并校准阈值"
        ));
        battle.put("battle_start", LocalDateTime.now().minusMinutes(10).toString());
        battle.put("battle_end", LocalDateTime.now().toString());
        return battle;
    }

    private String buildEffectivenessAnalysis(Map<String, Object> assessment,
                                              boolean pythonAvailable,
                                              String engineError) {
        long riskScore = toLong(assessment.get("riskScore"), 0L);
        String level = String.valueOf(assessment.getOrDefault("threatLevel", "medium"));
        String base;
        if (riskScore >= 70) {
            base = "当前防御有效性偏弱，关键风险信号仍处于高位，建议立即执行高危策略加固与人工复核。";
        } else if (riskScore >= 40) {
            base = "当前防御有效性中等，核心策略已生效但仍存在可利用窗口，建议针对待处理告警做定向收敛。";
        } else {
            base = "当前防御有效性较好，主要风险处于可控区间，可继续通过周期性对抗演练保持稳定。";
        }
        if (!pythonAvailable) {
            String reason = engineError == null ? "攻防引擎不可达" : engineError;
            return base + " 本次结果来自本地降级评估（原因: " + reason + "），建议恢复推理服务后复跑验证。";
        }
        return base + " 综合威胁等级: " + level + "。";
    }

    private List<String> buildOptimizationSuggestions(Map<String, Object> assessment, boolean pythonAvailable) {
        LinkedHashSet<String> suggestions = new LinkedHashSet<>();
        Object signalsObj = assessment.get("signals");
        long pending = 0L;
        long openRisk = 0L;
        long critical = 0L;
        long highRiskAudit = 0L;
        if (signalsObj instanceof Map<?, ?> signals) {
            pending = toLong(signals.get("securityPending"), 0L);
            openRisk = toLong(signals.get("openRiskEvents"), 0L);
            critical = toLong(signals.get("securityCritical"), 0L);
            highRiskAudit = toLong(signals.get("highRiskAudit24h"), 0L);
        }

        Object profileObj = assessment.get("defenseProfile");
        boolean monitorEnabled = false;
        boolean predictEnabled = false;
        int keywordSize = 0;
        int selectorSize = 0;
        if (profileObj instanceof Map<?, ?> profile) {
            monitorEnabled = toBoolean(profile.get("monitorEnabled"));
            predictEnabled = toBoolean(profile.get("predictEnabled"));
            keywordSize = (int) toLong(profile.get("sensitiveKeywordsSize"), 0L);
            selectorSize = (int) toLong(profile.get("siteSelectorSize"), 0L);
        }

        List<SecurityEvent> recentEvents = extractRecentSecurityEvents(assessment);
        Map<String, Long> eventTypeCounts = countEventDimension(recentEvents, SecurityEvent::getEventType);
        Map<String, Long> severityCounts = countEventDimension(recentEvents, SecurityEvent::getSeverity);
        Map<String, Long> fileSuffixCounts = countFileSuffixDimension(recentEvents);

        String dominantEventType = topDimension(eventTypeCounts);
        String dominantSeverity = topDimension(severityCounts);
        boolean hasSourceCodePath = recentEvents.stream()
            .map(SecurityEvent::getFilePath)
            .filter(path -> path != null && !path.isBlank())
            .anyMatch(path -> path.contains("/source-code") || path.contains(".sql") || path.contains(".pem"));
        boolean hasOutboundTarget = recentEvents.stream()
            .map(SecurityEvent::getTargetAddr)
            .filter(addr -> addr != null && !addr.isBlank())
            .anyMatch(addr -> !addr.contains("127.0.0.1") && !addr.contains("localhost") && !addr.startsWith("10.") && !addr.startsWith("192.168."));

        if (!monitorEnabled) {
            suggestions.add("先开启隐私盾监测开关并确认同步间隔正常，当前告警态势无法形成持续闭环。");
        }
        if (!predictEnabled && selectorSize > 0) {
            suggestions.add("把预测策略纳入上线门禁，优先覆盖现有 " + selectorSize + " 个站点选择器命中的页面行为。");
        }
        if (critical > 0) {
            suggestions.add("对当前 " + critical + " 条 critical/high 告警执行 SECOPS 双人复核，并收紧阻断阈值。");
        }
        if (pending > 5 || highRiskAudit > 8) {
            suggestions.add("将待处置告警与高风险审计留痕联动编排到自动工单，优先处理最近 24 小时的积压链路。");
        }
        if (openRisk > 0 && highRiskAudit > 0) {
            suggestions.add("按员工、主机和文件路径做三向关联，先压缩同一主体在风险事件与审计日志中的连续异常链路。");
        }
        if ("EXFILTRATION".equalsIgnoreCase(dominantEventType) || hasOutboundTarget) {
            suggestions.add("当前外传/外联特征偏强，建议收紧出站白名单、核验目标地址，并对可疑外发通道加二次确认。");
        } else if ("SUSPICIOUS_UPLOAD".equalsIgnoreCase(dominantEventType) || "BATCH_COPY".equalsIgnoreCase(dominantEventType)) {
            suggestions.add("上传与批量拷贝占比偏高，建议把高敏目录的文件类型白名单、速率限制和人工审批一起收紧。");
        } else if ("FILE_STEAL".equalsIgnoreCase(dominantEventType)) {
            suggestions.add("文件窃取信号占优，建议对高敏路径增加实时审计规则，并把关键员工终端纳入重点核查。");
        }
        if (hasSourceCodePath || fileSuffixCounts.containsKey(".sql") || fileSuffixCounts.containsKey(".pem")) {
            suggestions.add("当前事件已命中源码或密钥类扩展名，建议优先扩展 /source-code、/finance 与 .sql/.pem 的敏感规则覆盖。");
        }
        if ("CRITICAL".equalsIgnoreCase(dominantSeverity) || "HIGH".equalsIgnoreCase(dominantSeverity)) {
            suggestions.add("把高危事件优先回灌到 train/adversarial-feedback，先复训后扩轮，避免模型只会给出静态建议。");
        }
        if (pythonAvailable) {
            suggestions.add("将最近 " + Math.min(8, recentEvents.size()) + " 条真实事件样本回灌到对抗反馈训练，更新防御基线后再复测一次。");
        } else {
            suggestions.add("检查 ai-inference 服务连通性与超时配置，恢复后执行一次完整攻防模拟以校准策略效果。");
        }
        if (keywordSize > 0) {
            suggestions.add("继续补充敏感关键词库并校验命中率，当前词库规模为 " + keywordSize + "，建议结合真实告警做增量校准。");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("保持现有策略并按周执行一次攻防演练，持续监控策略版本迭代后的风险变化。");
        }
        return new ArrayList<>(suggestions).stream().limit(5).toList();
    }

    private List<SecurityEvent> extractRecentSecurityEvents(Map<String, Object> assessment) {
        Object value = assessment == null ? null : assessment.get("recentSecurityEvents");
        if (!(value instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<SecurityEvent> events = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof SecurityEvent event) {
                events.add(event);
            }
        }
        return events;
    }

    private Map<String, Long> countEventDimension(List<SecurityEvent> events, java.util.function.Function<SecurityEvent, String> extractor) {
        if (events == null || events.isEmpty()) {
            return Map.of();
        }
        return events.stream()
            .map(extractor)
            .filter(value -> value != null && !value.isBlank())
            .map(value -> value.trim().toUpperCase(Locale.ROOT))
            .collect(Collectors.groupingBy(value -> value, Collectors.counting()));
    }

    private Map<String, Long> countFileSuffixDimension(List<SecurityEvent> events) {
        if (events == null || events.isEmpty()) {
            return Map.of();
        }
        return events.stream()
            .map(SecurityEvent::getFilePath)
            .filter(path -> path != null && !path.isBlank())
            .map(String::trim)
            .map(path -> {
                int dotIndex = path.lastIndexOf('.');
                return dotIndex >= 0 ? path.substring(dotIndex).toLowerCase(Locale.ROOT) : "";
            })
            .filter(suffix -> !suffix.isBlank())
            .collect(Collectors.groupingBy(value -> value, Collectors.counting()));
    }

    private String topDimension(Map<String, Long> counts) {
        if (counts == null || counts.isEmpty()) {
            return "";
        }
        return counts.entrySet().stream()
            .max(Map.Entry.<String, Long>comparingByValue().thenComparing(Map.Entry::getKey))
            .map(Map.Entry::getKey)
            .orElse("");
    }

    private boolean toBoolean(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value == null ? "false" : value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }

    private int toSize(Object value) {
        if (value instanceof List<?> list) {
            return list.size();
        }
        if (value instanceof Map<?, ?> map) {
            return map.size();
        }
        return 0;
    }

    private Object nested(Map<String, Object> root, String parentKey, String childKey) {
        if (root == null) {
            return null;
        }
        Object parent = root.get(parentKey);
        if (parent instanceof Map<?, ?> map) {
            return map.get(childKey);
        }
        return null;
    }

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private long toLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private String normalizeLower(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private void persistLog(ChatReq req, AiModel model, String provider, Map<String, Object> resp, Instant begin, String status) {
        try {
            User currentUser = currentUserService.requireCurrentUser();
            AiCallLog log = new AiCallLog();
            log.setCompanyId(currentUser.getCompanyId());
            log.setUserId(currentUser.getId());
            log.setUsername(currentUser.getUsername());
            log.setModelCode(req.getModel());
            if (model != null) log.setModelId(model.getId());
            log.setProvider(provider);
            String inputPreview = req.getMessages() == null || req.getMessages().isEmpty()
                    ? "" : req.getMessages().get(req.getMessages().size() - 1).getContent();
            if (inputPreview != null && inputPreview.length() > 100) inputPreview = inputPreview.substring(0, 100);
            log.setInputPreview(inputPreview);
            String outputPreview = null;
            Object reply = resp.get("reply") != null ? resp.get("reply") : resp.get("raw");
            if (reply != null) {
                outputPreview = reply.toString();
                if (outputPreview.length() > 100) outputPreview = outputPreview.substring(0, 100);
            }
            log.setOutputPreview(outputPreview);
            log.setStatus(status);
            log.setErrorMsg("fail".equals(status) ? String.valueOf(resp.get("reason")) : null);
            log.setDurationMs(Duration.between(begin, Instant.now()).toMillis());
            log.setDataAssetId(req.getAssetId());
            log.setCreateTime(LocalDateTime.now());
            aiCallAuditService.recordAsync(log);
        } catch (Exception ignored) {
            // 审计失败不影响主流程
        }
    }

    private String mergeMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        return messages.stream()
                .map(Message::getContent)
                .collect(Collectors.joining(" "));
    }

    private interface PayloadBuilder { String build(String model, List<Message> messages) throws Exception; }
    private interface AuthApplier { void apply(HttpRequest.Builder builder, String apiKey); }

    private static class ProviderConfig {
        final String url;
        final String apiKey;
        final PayloadBuilder payloadBuilder;
        final AuthApplier authApplier;

        ProviderConfig(String url, String apiKey, PayloadBuilder payloadBuilder, AuthApplier authApplier) {
            this.url = url;
            this.apiKey = apiKey;
            this.payloadBuilder = payloadBuilder;
            this.authApplier = authApplier;
        }

        static ProviderConfig of(String provider) {
            switch (provider) {
                case "qwen":
                    return new ProviderConfig(
                            "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation",
                            System.getenv("ALI_DASHSCOPE_API_KEY"),
                            (model, msgs) -> MAPPER.writeValueAsString(Map.of(
                                    "model", model,
                                    "input", Map.of("messages", msgs.stream().map(m -> Map.of("role", m.getRole(), "content", m.getContent())).collect(Collectors.toList())))),
                            (b, key) -> b.header("Authorization", "Bearer " + key)
                    );
                case "qianfan":
                case "wenxin":
                case "yiyan":
                    return new ProviderConfig(
                            "https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions",
                            System.getenv("BAIDU_API_KEY"),
                            (model, msgs) -> MAPPER.writeValueAsString(Map.of(
                                    "model", model,
                                    "messages", msgs.stream().map(m -> Map.of("role", m.getRole(), "content", m.getContent())).collect(Collectors.toList()))),
                            (b, key) -> b.uri(URI.create("https://aip.baidubce.com/rpc/2.0/ai_custom/v1/wenxinworkshop/chat/completions?access_token=" + key))
                    );
                case "hunyuan":
                    return new ProviderConfig(
                            "https://api.hunyuan.cloud.tencent.com/v1/chat/completions",
                            System.getenv("TENCENT_HUNYUAN_API_KEY"),
                            (model, msgs) -> MAPPER.writeValueAsString(Map.of(
                                    "model", model,
                                    "messages", msgs.stream().map(m -> Map.of("role", m.getRole(), "content", m.getContent())).collect(Collectors.toList()))),
                            (b, key) -> b.header("Authorization", "Bearer " + key)
                    );
                case "spark":
                case "xinghuo":
                    return new ProviderConfig(
                            "https://spark-api.xf-yun.com/v3.5/chat",
                            System.getenv("IFLYTEK_SPARK_API_KEY"),
                            (model, msgs) -> MAPPER.writeValueAsString(Map.of(
                                    "model", model,
                                    "messages", msgs.stream().map(m -> Map.of("role", m.getRole(), "content", m.getContent())).collect(Collectors.toList()))),
                            (b, key) -> b.header("Authorization", key)
                    );
                case "doubao":
                    return new ProviderConfig(
                            "https://ark.cn-beijing.volces.com/api/v1/chat/completions",
                            System.getenv("BYTE_DOUYIN_API_KEY"),
                            (model, msgs) -> MAPPER.writeValueAsString(Map.of(
                                    "model", model,
                                    "messages", msgs.stream().map(m -> Map.of("role", m.getRole(), "content", m.getContent())).collect(Collectors.toList()))),
                            (b, key) -> b.header("Authorization", "Bearer " + key)
                    );
                default:
                    return null;
            }
        }
    }
}
