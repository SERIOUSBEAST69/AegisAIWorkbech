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
import com.trustai.entity.SecurityEvent;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Service
public class AiGatewayService {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    private final AiCallAuditService aiCallAuditService;
    private final AiModelService aiModelService;
    private final AiModelAccessGuardService aiModelAccessGuardService;
    private final AiInferenceClient aiInferenceClient;
    private final SecurityEventService securityEventService;
    private final RiskEventService riskEventService;
    private final AuditLogService auditLogService;
    private final CompanyScopeService companyScopeService;
    private final PrivacyShieldConfigService privacyShieldConfigService;

    public AiGatewayService(AiCallAuditService aiCallAuditService,
                            AiModelService aiModelService,
                            AiModelAccessGuardService aiModelAccessGuardService,
                            AiInferenceClient aiInferenceClient,
                            SecurityEventService securityEventService,
                            RiskEventService riskEventService,
                            AuditLogService auditLogService,
                            CompanyScopeService companyScopeService,
                            PrivacyShieldConfigService privacyShieldConfigService) {
        this.aiCallAuditService = aiCallAuditService;
        this.aiModelService = aiModelService;
        this.aiModelAccessGuardService = aiModelAccessGuardService;
        this.aiInferenceClient = aiInferenceClient;
        this.securityEventService = securityEventService;
        this.riskEventService = riskEventService;
        this.auditLogService = auditLogService;
        this.companyScopeService = companyScopeService;
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

    public Map<String, Object> chat(ChatReq req) {
        Instant begin = Instant.now();
        AiModel model = aiModelService.lambdaQuery().eq(AiModel::getModelCode, req.getModel()).one();
        aiModelAccessGuardService.validate(model, req.getAssetId(), req.getAccessReason(), mergeMessages(req.getMessages()));

        String provider = model.getProvider() != null ? model.getProvider().toLowerCase(Locale.ROOT) : req.getProvider().toLowerCase(Locale.ROOT);
        ProviderConfig cfg = ProviderConfig.of(provider);
        String statusFlag = "success";
        if (cfg == null) {
            Map<String, Object> mock = mock("unsupported provider: " + provider, req.getMessages());
            persistLog(req, model, provider, mock, begin, "fail");
            return mock;
        }
        if (cfg.apiKey == null || cfg.apiKey.isEmpty()) {
            Map<String, Object> mock = mock("apiKey missing for " + provider + " (返回模拟响应)", req.getMessages());
            persistLog(req, model, provider, mock, begin, "fail");
            return mock;
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
            Map<String, Object> mock = mock("invoke failed: " + e.getMessage(), req.getMessages());
            persistLog(req, model, provider, mock, begin, "fail");
            return mock;
        }
    }

    public List<Map<String, Object>> modelCatalog() {
        return aiModelService.lambdaQuery()
            .eq(AiModel::getStatus, "enabled")
            .list()
            .stream()
            .map(item -> {
                Map<String, Object> model = new HashMap<>();
                model.put("id", item.getId());
                model.put("modelCode", item.getModelCode());
                model.put("modelName", item.getModelName());
                model.put("provider", item.getProvider());
                model.put("riskLevel", item.getRiskLevel());
                model.put("status", item.getStatus());
                return model;
            })
            .collect(Collectors.toList());
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
        if ("real-threat-check".equalsIgnoreCase(scenario)) {
            Map<String, Object> quick = new HashMap<>();
            quick.put("scenario", "real-threat-check");
            quick.put("mode", "real-threat-assessment");
            quick.put("rounds", rounds);
            quick.put("seed", effectiveSeed);
            quick.put("battle", buildSyntheticBattle(assessment, rounds, effectiveSeed, "real-threat-check", "real-threat-assessment"));
            return finalizeBattleResult(assessment, quick, true, null);
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("scenario", scenario);
            payload.put("rounds", rounds);
            payload.put("seed", effectiveSeed);
            Map<String, Object> remote = aiInferenceClient.adversarialRun(payload);
            return finalizeBattleResult(assessment, remote, true, null);
        } catch (Exception ex) {
            Map<String, Object> fallback = buildLocalAdversarialFallback(scenario, rounds, effectiveSeed, assessment, ex);
            return finalizeBattleResult(assessment, fallback, false, ex.getMessage());
        }
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

    private Map<String, Object> buildLocalAdversarialFallback(String scenario,
                                                               int rounds,
                                                               Integer seed,
                                                               Map<String, Object> assessment,
                                                               Exception ex) {
        long riskScore = toLong(assessment.get("riskScore"), 0L);
        String threatLevel = String.valueOf(assessment.getOrDefault("threatLevel", "medium"));
        Random rng = new Random(seed == null ? System.nanoTime() : seed.longValue());
        String defender = riskScore >= 60 && rng.nextDouble() > 0.4 ? "attacker" : "defender";
        String fallbackMode = ex != null && String.valueOf(ex.getMessage()).toLowerCase(Locale.ROOT).contains("timed out")
            ? "timeout-fallback"
            : "local-fallback";

        Map<String, Object> battle = buildSyntheticBattle(assessment, rounds, seed, scenario, fallbackMode);

        Map<String, Object> result = new HashMap<>();
        result.put("scenario", scenario);
        result.put("rounds", rounds);
        result.put("seed", seed);
        result.put("winner", defender);
        result.put("threatLevel", threatLevel);
        result.put("score", Math.max(1L, 100L - riskScore));
        result.put("mode", fallbackMode);
        result.put("engine", "local-evaluator");
        result.put("summary", "Python攻防引擎当前不可达，已自动切换到本地策略评估并给出处置建议");
        result.put("battle", battle);
        result.put("ok", true);
        return result;
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

        String winner = attackerScore > defenderScore ? "攻击方 (OpenClaw-v2)" : "防御方 (AegisAI-Guard)";
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
        List<String> suggestions = new ArrayList<>();
        Object signalsObj = assessment.get("signals");
        long pending = 0L;
        long openRisk = 0L;
        long critical = 0L;
        if (signalsObj instanceof Map<?, ?> signals) {
            pending = toLong(signals.get("securityPending"), 0L);
            openRisk = toLong(signals.get("openRiskEvents"), 0L);
            critical = toLong(signals.get("securityCritical"), 0L);
        }

        if (critical > 0) {
            suggestions.add("提升高危告警阻断阈值并启用SECOPS双人复核，优先清理critical/high待处理事件。");
        }
        if (pending > 5) {
            suggestions.add("为待处置告警配置自动化工单与SLA升级规则，避免pending事件累积。");
        }
        if (openRisk > 0) {
            suggestions.add("将行为异常事件与影子AI发现结果进行关联分析，先处置同一员工的连续异常链路。");
        }
        if (!pythonAvailable) {
            suggestions.add("检查 ai-inference 服务连通性与超时配置，恢复后执行一次完整攻防模拟以校准策略效果。");
        }
        if (suggestions.isEmpty()) {
            suggestions.add("保持现有策略并按周执行一次攻防演练，持续监控策略版本迭代后的风险变化。");
        }
        return suggestions;
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
        return 0;
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

    private Map<String, Object> mock(String reason, List<Message> messages) {
        Map<String, Object> map = new HashMap<>();
        map.put("provider", "mock");
        map.put("reply", "【模拟响应】" + (messages.isEmpty() ? "" : messages.get(messages.size()-1).getContent()));
        map.put("reason", reason);
        return map;
    }

    private void persistLog(ChatReq req, AiModel model, String provider, Map<String, Object> resp, Instant begin, String status) {
        try {
            AiCallLog log = new AiCallLog();
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
