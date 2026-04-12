package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.entity.ClientReport;
import com.trustai.entity.GovernanceEvent;
import com.trustai.entity.PrivacyEvent;
import com.trustai.entity.SimulationEvent;
import com.trustai.entity.User;
import com.trustai.service.ClientIngressAuthService;
import com.trustai.service.ClientReportService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.EventHubService;
import com.trustai.service.PrivacyEventService;
import com.trustai.service.SimulationEventService;
import com.trustai.service.UserService;
import com.trustai.utils.R;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/client/simulation")
@RequiredArgsConstructor
public class ClientSimulationController {

    private static final List<String> DEFAULT_SHADOW_TYPES = List.of(
        "OUT_OF_ALLOWLIST_AI_ACCESS",
        "MIDJOURNEY_USAGE_DETECTED",
        "LOCAL_OLLAMA_RUNTIME_DETECTED",
        "DEEPL_TRANSLATION_DOC_FLOW"
    );

    private static final List<String> DEFAULT_ANOMALY_SCENARIOS = List.of(
        "CLIPBOARD_EXFIL",
        "WINDOW_SWITCH_BURST",
        "BULK_ACCESS_WITH_AI_ACTIVE"
    );

    private final ClientIngressAuthService clientIngressAuthService;
    private final UserService userService;
    private final EventHubService eventHubService;
    private final ClientReportService clientReportService;
    private final PrivacyEventService privacyEventService;
    private final SimulationEventService simulationEventService;
    private final CurrentUserService currentUserService;
    private final ObjectMapper objectMapper;

    @PostMapping("/shadow-ai/trigger")
    public R<Map<String, Object>> triggerShadowAi(@RequestHeader(value = "X-Client-Token", required = false) String clientToken,
                                                   @RequestHeader(value = "X-Company-Id", required = false) Long headerCompanyId,
                                                   @RequestBody(required = false) ShadowSimReq req) {
        if (!clientIngressAuthService.isAuthorized(clientToken)) {
            return R.error(40100, "客户端令牌无效");
        }

        ShadowSimReq safeReq = req == null ? new ShadowSimReq() : req;
        User actor = resolveActor(safeReq.getUsername(), headerCompanyId, safeReq.getCompanyId());
        if (actor == null) {
            return R.error(40000, "无法绑定合法用户");
        }

        String eventType = normalizeShadowType(safeReq.getEventType());
        String processName = defaultIfBlank(safeReq.getProcessName(), guessProcessByType(eventType));
        String dstDomain = defaultIfBlank(safeReq.getTargetDomain(), guessDomainByType(eventType));
        String dstIp = defaultIfBlank(safeReq.getTargetIp(), guessIpByType(eventType));
        String hostname = defaultIfBlank(safeReq.getHostname(), "sim-host-" + actor.getUsername());
        String clientId = defaultIfBlank(safeReq.getClientId(), "sim-client-" + actor.getId());

        List<Map<String, Object>> relatedFileOps = normalizeFileOps(safeReq.getRelatedFileOps());
        if (relatedFileOps.isEmpty()) {
            relatedFileOps = List.of(Map.of(
                "op", "read",
                "path", "D:/sensitive/contracts/award-plan.docx",
                "sensitivity", "high"
            ));
        }

        int riskScore = clampRisk(safeReq.getRiskScore(), defaultRiskForShadowType(eventType));
        String riskLevel = scoreToRiskLevel(riskScore);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("simulation", true);
        payload.put("module", "shadow_ai");
        payload.put("eventType", eventType);
        payload.put("processName", processName);
        payload.put("processPath", defaultIfBlank(safeReq.getProcessPath(), "C:/Program Files/Browser/browser.exe"));
        payload.put("commandLine", defaultIfBlank(safeReq.getCommandLine(), processName + " --profile corporate"));
        payload.put("parentProcess", defaultIfBlank(safeReq.getParentProcess(), "explorer.exe"));
        payload.put("dstDomain", dstDomain);
        payload.put("dstIp", dstIp);
        payload.put("dstPort", safeReq.getDstPort() == null ? 443 : safeReq.getDstPort());
        payload.put("protocol", defaultIfBlank(safeReq.getProtocol(), "https"));
        payload.put("relatedFileOps", relatedFileOps);
        payload.put("riskScore", riskScore);
        payload.put("riskLevel", riskLevel);
        payload.put("allowlistMatched", false);
        payload.put("tenantAllowlistVersion", defaultIfBlank(safeReq.getAllowlistVersion(), "allowlist-v1"));
        payload.put("serviceFingerprint", defaultIfBlank(safeReq.getServiceFingerprint(), eventType.toLowerCase(Locale.ROOT) + "-fp"));
        payload.put("evidenceHashes", List.of(UUID.randomUUID().toString().replace("-", "")));
        payload.put("firstSeenAt", nowIso());
        payload.put("lastSeenAt", nowIso());

        ClientReport report = new ClientReport();
        report.setCompanyId(actor.getCompanyId());
        report.setClientId(clientId);
        report.setHostname(hostname);
        report.setIpAddress(defaultIfBlank(safeReq.getClientIp(), "10.10.1.25"));
        report.setOsUsername(actor.getUsername());
        report.setOsType(defaultIfBlank(safeReq.getOsType(), "Windows"));
        report.setClientVersion(defaultIfBlank(safeReq.getClientVersion(), "sim-1.0"));
        report.setDiscoveredServices(toJson(List.of(Map.of(
            "name", serviceNameByType(eventType),
            "domain", dstDomain,
            "category", "ai",
            "source", "simulation",
            "riskLevel", riskLevel,
            "firstSeen", nowIso(),
            "lastSeen", nowIso()
        ))));
        report.setShadowAiCount(1);
        report.setRiskLevel(riskLevel);
        report.setScanTime(LocalDateTime.now());
        report.setCreateTime(LocalDateTime.now());
        report.setUpdateTime(LocalDateTime.now());
        clientReportService.save(report);

        GovernanceEvent governanceEvent = eventHubService.ingestShadowAiEvent(report, actor, payload);
        SimulationEvent simulationEvent = saveSimulationEvent(actor.getCompanyId(), eventType, "shadow-ai-sim", actor.getUsername(), payload, riskLevel);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("simulationEventId", simulationEvent.getId());
        data.put("sourceEventId", report.getId());
        data.put("governanceEventId", governanceEvent == null ? null : governanceEvent.getId());
        data.put("eventType", eventType);
        data.put("riskScore", riskScore);
        data.put("riskLevel", riskLevel);
        data.put("status", "pending");
        data.put("effectProfile", resolveEffectProfile(eventType, riskLevel));
        return R.ok(data);
    }

    @PostMapping("/employee-anomaly/trigger")
    public R<Map<String, Object>> triggerEmployeeAnomaly(@RequestHeader(value = "X-Client-Token", required = false) String clientToken,
                                                          @RequestHeader(value = "X-Company-Id", required = false) Long headerCompanyId,
                                                          @RequestBody(required = false) EmployeeAnomalyReq req) {
        if (!clientIngressAuthService.isAuthorized(clientToken)) {
            return R.error(40100, "客户端令牌无效");
        }

        EmployeeAnomalyReq safeReq = req == null ? new EmployeeAnomalyReq() : req;
        User actor = resolveActor(safeReq.getUsername(), headerCompanyId, safeReq.getCompanyId());
        if (actor == null) {
            return R.error(40000, "无法绑定合法用户");
        }

        String scenarioType = normalizeScenarioType(safeReq.getScenarioType());
        int ruleScore = clampRisk(safeReq.getRuleScore(), defaultRuleScore(scenarioType));
        double baselineDeviation = safeReq.getBaselineDeviation() == null ? defaultDeviation(scenarioType) : Math.max(0D, safeReq.getBaselineDeviation());
        int baselineScore = (int) Math.round(Math.min(100D, baselineDeviation * 100D));

        // V1 规则引擎 + V2 基线偏离融合分
        double alpha = 0.7D;
        int finalRisk = (int) Math.round(alpha * ruleScore + (1D - alpha) * baselineScore);
        finalRisk = Math.max(0, Math.min(100, finalRisk));
        String riskLevel = scoreToRiskLevel(finalRisk);

        List<Map<String, Object>> sequence = normalizeSequence(safeReq.getEventSequence(), scenarioType);

        Map<String, Object> behaviorChain = new LinkedHashMap<>();
        behaviorChain.put("userId", String.valueOf(actor.getId()));
        behaviorChain.put("sessionId", defaultIfBlank(safeReq.getSessionId(), "sim-session-" + UUID.randomUUID()));
        behaviorChain.put("anomalyType", scenarioType);
        behaviorChain.put("eventSequence", sequence);
        behaviorChain.put("riskScore", finalRisk);
        behaviorChain.put("scoreV1Rule", ruleScore);
        behaviorChain.put("scoreV2Baseline", baselineScore);
        behaviorChain.put("baselineDeviation", round4(baselineDeviation));
        behaviorChain.put("ruleHits", defaultRuleHits(scenarioType));
        behaviorChain.put("scoreModel", Map.of("alpha", alpha, "version", "v1_rule_plus_v2_baseline"));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("simulation", true);
        payload.put("module", "employee_anomaly");
        payload.put("scenarioType", scenarioType);
        payload.put("employeeId", actor.getUsername());
        payload.put("companyId", actor.getCompanyId());
        payload.put("behaviorChain", behaviorChain);
        payload.put("riskLevel", riskLevel);
        payload.put("riskScore", finalRisk);

        GovernanceEvent governanceEvent;
        Long sourceEventId = null;
        if ("CLIPBOARD_EXFIL".equals(scenarioType)) {
            PrivacyEvent privacyEvent = new PrivacyEvent();
            privacyEvent.setCompanyId(actor.getCompanyId());
            privacyEvent.setUserId(String.valueOf(actor.getId()));
            privacyEvent.setEventType("CLIPBOARD_TEXT_EXFIL");
            privacyEvent.setContentMasked("[SIMULATED] confidential content masked");
            privacyEvent.setSource("simulation");
            privacyEvent.setAction("paste_to_ai");
            privacyEvent.setSeverity(riskLevel);
            privacyEvent.setDeviceId(defaultIfBlank(safeReq.getDeviceId(), "sim-device-" + actor.getId()));
            privacyEvent.setHostname(defaultIfBlank(safeReq.getHostname(), "sim-host-" + actor.getUsername()));
            privacyEvent.setWindowTitle("[SIM] confidential-file -> ai-chat-window");
            privacyEvent.setMatchedTypes("id_card,bank_card,low_slow_exfil_sequence");
            privacyEvent.setPolicyVersion(eventHubService.resolvePolicyVersion(actor.getCompanyId()));
            privacyEvent.setEventTime(new Date());
            privacyEvent.setCreateTime(new Date());
            privacyEvent.setUpdateTime(new Date());
            privacyEventService.save(privacyEvent);
            sourceEventId = privacyEvent.getId();
            governanceEvent = eventHubService.ingestPrivacyEvent(privacyEvent, actor, payload);
        } else {
            payload.put("description", scenarioDescription(scenarioType));
            payload.put("risk_level", riskLevel);
            payload.put("event_id", "sim-anomaly-" + UUID.randomUUID());
            governanceEvent = eventHubService.ingestAnomalyEvent(actor.getCompanyId(), actor, payload);
            sourceEventId = governanceEvent == null ? null : governanceEvent.getId();
        }

        SimulationEvent simulationEvent = saveSimulationEvent(actor.getCompanyId(), scenarioType, "employee-anomaly-sim", actor.getUsername(), payload, riskLevel);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("simulationEventId", simulationEvent.getId());
        data.put("sourceEventId", sourceEventId);
        data.put("governanceEventId", governanceEvent == null ? null : governanceEvent.getId());
        data.put("scenarioType", scenarioType);
        data.put("riskScore", finalRisk);
        data.put("riskLevel", riskLevel);
        data.put("status", "pending");
        data.put("behaviorChain", behaviorChain);
        data.put("effectProfile", resolveEffectProfile(scenarioType, riskLevel));
        return R.ok(data);
    }

    @PostMapping("/replay")
    public R<Map<String, Object>> replay(@RequestHeader(value = "X-Client-Token", required = false) String clientToken,
                                         @RequestHeader(value = "X-Company-Id", required = false) Long headerCompanyId,
                                         @RequestBody(required = false) ReplayReq req) {
        if (!clientIngressAuthService.isAuthorized(clientToken)) {
            return R.error(40100, "客户端令牌无效");
        }
        ReplayReq safeReq = req == null ? new ReplayReq() : req;
        String module = StringUtils.hasText(safeReq.getModule()) ? safeReq.getModule().trim().toLowerCase(Locale.ROOT) : "shadow_ai";
        int rounds = Math.max(1, Math.min(20, safeReq.getRounds() == null ? 3 : safeReq.getRounds()));

        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < rounds; i++) {
            if ("employee_anomaly".equals(module)) {
                EmployeeAnomalyReq one = new EmployeeAnomalyReq();
                one.setCompanyId(safeReq.getCompanyId());
                one.setUsername(safeReq.getUsername());
                one.setScenarioType(DEFAULT_ANOMALY_SCENARIOS.get(i % DEFAULT_ANOMALY_SCENARIOS.size()));
                R<Map<String, Object>> res = triggerEmployeeAnomaly(clientToken, headerCompanyId, one);
                if (res.getCode() != 20000) {
                    return R.error(res.getCode(), res.getMsg());
                }
                items.add((Map<String, Object>) res.getData());
            } else {
                ShadowSimReq one = new ShadowSimReq();
                one.setCompanyId(safeReq.getCompanyId());
                one.setUsername(safeReq.getUsername());
                one.setEventType(DEFAULT_SHADOW_TYPES.get(i % DEFAULT_SHADOW_TYPES.size()));
                R<Map<String, Object>> res = triggerShadowAi(clientToken, headerCompanyId, one);
                if (res.getCode() != 20000) {
                    return R.error(res.getCode(), res.getMsg());
                }
                items.add((Map<String, Object>) res.getData());
            }
        }

        return R.ok(Map.of("module", module, "rounds", rounds, "items", items));
    }

    @GetMapping("/events")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<Map<String, Object>> events(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "20") int pageSize,
                                         @RequestParam(required = false) String type,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String module) {
        Long companyId = currentUserService.requireCurrentUser().getCompanyId();
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, pageSize));

        QueryWrapper<SimulationEvent> qw = new QueryWrapper<SimulationEvent>()
            .eq("company_id", companyId)
            .orderByDesc("id")
            .last("limit " + (safePage * safeSize));
        if (StringUtils.hasText(type)) {
            qw.eq("event_type", type.trim());
        }
        if (StringUtils.hasText(status)) {
            qw.eq("status", status.trim());
        }
        if (StringUtils.hasText(module)) {
            String marker = module.trim().toLowerCase(Locale.ROOT);
            if ("shadow_ai".equals(marker)) {
                qw.like("source", "shadow-ai-sim");
            } else if ("employee_anomaly".equals(marker)) {
                qw.like("source", "employee-anomaly-sim");
            }
        }

        List<SimulationEvent> all = simulationEventService.list(qw);
        int total = all.size();
        int from = Math.min((safePage - 1) * safeSize, total);
        int to = Math.min(from + safeSize, total);
        List<Map<String, Object>> rows = all.subList(from, to).stream().map(this::toViewRow).toList();

        return R.ok(Map.of(
            "page", safePage,
            "pageSize", safeSize,
            "total", total,
            "list", rows
        ));
    }

    private SimulationEvent saveSimulationEvent(Long companyId,
                                                String eventType,
                                                String source,
                                                String triggerUser,
                                                Map<String, Object> payload,
                                                String severity) {
        SimulationEvent event = new SimulationEvent();
        event.setCompanyId(companyId);
        event.setEventType(eventType);
        event.setTargetKey(String.valueOf(payload.getOrDefault("module", "simulation")));
        event.setSeverity(severity);
        event.setStatus("pending");
        event.setSource(source);
        event.setTriggerUser(triggerUser);
        event.setPayloadJson(toJson(payload));
        event.setCreateTime(new Date());
        event.setUpdateTime(new Date());
        simulationEventService.save(event);
        return event;
    }

    private List<Map<String, Object>> normalizeFileOps(List<Map<String, Object>> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> item : raw) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("op", defaultIfBlank(item.get("op"), "read"));
            row.put("path", defaultIfBlank(item.get("path"), "D:/unknown.dat"));
            row.put("sensitivity", defaultIfBlank(item.get("sensitivity"), "medium"));
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> normalizeSequence(List<Map<String, Object>> incoming, String scenarioType) {
        if (incoming != null && !incoming.isEmpty()) {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Map<String, Object> item : incoming) {
                if (item == null || item.isEmpty()) {
                    continue;
                }
                Map<String, Object> row = new LinkedHashMap<>(item);
                row.putIfAbsent("ts", nowIso());
                rows.add(row);
            }
            if (!rows.isEmpty()) {
                return rows;
            }
        }

        if ("WINDOW_SWITCH_BURST".equals(scenarioType)) {
            return List.of(
                Map.of("ts", nowIso(), "type", "WINDOW_SWITCH", "from", "confidential-pdf", "to", "ai-web"),
                Map.of("ts", nowIso(), "type", "WINDOW_SWITCH", "from", "ai-web", "to", "confidential-pdf")
            );
        }
        if ("BULK_ACCESS_WITH_AI_ACTIVE".equals(scenarioType)) {
            return List.of(
                Map.of("ts", nowIso(), "type", "FILE_READ_BATCH", "count", 126, "windowSec", 300),
                Map.of("ts", nowIso(), "type", "AI_SESSION_ACTIVE", "service", "deepl")
            );
        }
        return List.of(
            Map.of("ts", nowIso(), "type", "FILE_COPY", "file", "D:/finance/q2-plan.xlsx", "sensitivity", "critical"),
            Map.of("ts", nowIso(), "type", "WINDOW_FOCUS", "app", "chatgpt-web"),
            Map.of("ts", nowIso(), "type", "PASTE", "target", "chat-input")
        );
    }

    private Map<String, Object> resolveEffectProfile(String type, String severity) {
        String key = String.valueOf(type == null ? "" : type).toUpperCase(Locale.ROOT);
        if ("OUT_OF_ALLOWLIST_AI_ACCESS".equals(key)) {
            return Map.of("theme", "allowlist_breach", "primaryColor", "#4AA8FF", "durationMs", 820, "intensity", "high", "severity", severity);
        }
        if ("LOCAL_OLLAMA_RUNTIME_DETECTED".equals(key)) {
            return Map.of("theme", "local_runtime_pulse", "primaryColor", "#F9A826", "durationMs", 760, "intensity", "medium", "severity", severity);
        }
        if ("CLIPBOARD_EXFIL".equals(key)) {
            return Map.of("theme", "clipboard_exfil", "primaryColor", "#FF3858", "durationMs", 880, "intensity", "high", "severity", severity);
        }
        if ("WINDOW_SWITCH_BURST".equals(key)) {
            return Map.of("theme", "switch_burst", "primaryColor", "#B35CFF", "durationMs", 700, "intensity", "medium", "severity", severity);
        }
        if ("BULK_ACCESS_WITH_AI_ACTIVE".equals(key)) {
            return Map.of("theme", "bulk_ai_active", "primaryColor", "#FF8B2B", "durationMs", 920, "intensity", "high", "severity", severity);
        }
        return Map.of("theme", "default_alert", "primaryColor", "#FF5F3D", "durationMs", 680, "intensity", "medium", "severity", severity);
    }

    private Map<String, Object> toViewRow(SimulationEvent event) {
        Map<String, Object> payload = fromJson(event.getPayloadJson());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", event.getId());
        row.put("eventType", event.getEventType());
        row.put("severity", event.getSeverity());
        row.put("status", event.getStatus());
        row.put("source", event.getSource());
        row.put("triggerUser", event.getTriggerUser());
        row.put("payload", payload);
        row.put("effectProfile", resolveEffectProfile(event.getEventType(), String.valueOf(event.getSeverity())));
        row.put("createTime", event.getCreateTime());
        return row;
    }

    private User resolveActor(String username, Long headerCompanyId, Long bodyCompanyId) {
        Long companyId = headerCompanyId != null && headerCompanyId > 0 ? headerCompanyId : bodyCompanyId;
        if (StringUtils.hasText(username)) {
            List<User> users = userService.lambdaQuery()
                .eq(User::getUsername, username.trim())
                .eq(companyId != null && companyId > 0, User::getCompanyId, companyId)
                .list();
            if (users != null && !users.isEmpty()) {
                return users.get(0);
            }
        }
        if (companyId != null && companyId > 0) {
            return userService.lambdaQuery()
                .eq(User::getCompanyId, companyId)
                .eq(User::getAccountStatus, "active")
                .last("limit 1")
                .one();
        }
        return null;
    }

    private String normalizeShadowType(String eventType) {
        String normalized = String.valueOf(eventType == null ? "" : eventType).trim().toUpperCase(Locale.ROOT);
        if (DEFAULT_SHADOW_TYPES.contains(normalized)) {
            return normalized;
        }
        return "OUT_OF_ALLOWLIST_AI_ACCESS";
    }

    private String normalizeScenarioType(String scenarioType) {
        String normalized = String.valueOf(scenarioType == null ? "" : scenarioType).trim().toUpperCase(Locale.ROOT);
        if (DEFAULT_ANOMALY_SCENARIOS.contains(normalized)) {
            return normalized;
        }
        return "CLIPBOARD_EXFIL";
    }

    private int defaultRuleScore(String scenarioType) {
        return switch (normalizeScenarioType(scenarioType)) {
            case "WINDOW_SWITCH_BURST" -> 74;
            case "BULK_ACCESS_WITH_AI_ACTIVE" -> 82;
            default -> 90;
        };
    }

    private double defaultDeviation(String scenarioType) {
        return switch (normalizeScenarioType(scenarioType)) {
            case "WINDOW_SWITCH_BURST" -> 0.71;
            case "BULK_ACCESS_WITH_AI_ACTIVE" -> 0.79;
            default -> 0.86;
        };
    }

    private List<String> defaultRuleHits(String scenarioType) {
        return switch (normalizeScenarioType(scenarioType)) {
            case "WINDOW_SWITCH_BURST" -> List.of("R002_WINDOW_SWITCH_FREQUENCY");
            case "BULK_ACCESS_WITH_AI_ACTIVE" -> List.of("R003_BULK_FILE_ACCESS_AI_ACTIVITY");
            default -> List.of("R001_CLIPBOARD_SENSITIVE_EXFIL");
        };
    }

    private String scenarioDescription(String scenarioType) {
        return switch (normalizeScenarioType(scenarioType)) {
            case "WINDOW_SWITCH_BURST" -> "机密文件与AI应用窗口高频切换";
            case "BULK_ACCESS_WITH_AI_ACTIVE" -> "短时批量访问机密文件且AI会话活跃";
            default -> "机密剪贴板内容向AI应用外泄";
        };
    }

    private int defaultRiskForShadowType(String eventType) {
        return switch (normalizeShadowType(eventType)) {
            case "MIDJOURNEY_USAGE_DETECTED" -> 73;
            case "LOCAL_OLLAMA_RUNTIME_DETECTED" -> 72;
            case "DEEPL_TRANSLATION_DOC_FLOW" -> 79;
            default -> 86;
        };
    }

    private String serviceNameByType(String eventType) {
        return switch (normalizeShadowType(eventType)) {
            case "MIDJOURNEY_USAGE_DETECTED" -> "Midjourney";
            case "LOCAL_OLLAMA_RUNTIME_DETECTED" -> "Ollama";
            case "DEEPL_TRANSLATION_DOC_FLOW" -> "DeepL";
            default -> "External AI Service";
        };
    }

    private String guessProcessByType(String eventType) {
        return switch (normalizeShadowType(eventType)) {
            case "MIDJOURNEY_USAGE_DETECTED" -> "discord.exe";
            case "LOCAL_OLLAMA_RUNTIME_DETECTED" -> "ollama.exe";
            case "DEEPL_TRANSLATION_DOC_FLOW" -> "deepl.exe";
            default -> "chrome.exe";
        };
    }

    private String guessDomainByType(String eventType) {
        return switch (normalizeShadowType(eventType)) {
            case "MIDJOURNEY_USAGE_DETECTED" -> "discord.com";
            case "LOCAL_OLLAMA_RUNTIME_DETECTED" -> "localhost";
            case "DEEPL_TRANSLATION_DOC_FLOW" -> "www.deepl.com";
            default -> "chat.openai.com";
        };
    }

    private String guessIpByType(String eventType) {
        return switch (normalizeShadowType(eventType)) {
            case "MIDJOURNEY_USAGE_DETECTED" -> "162.159.128.233";
            case "LOCAL_OLLAMA_RUNTIME_DETECTED" -> "127.0.0.1";
            case "DEEPL_TRANSLATION_DOC_FLOW" -> "104.18.34.77";
            default -> "104.18.33.45";
        };
    }

    private String scoreToRiskLevel(int score) {
        if (score >= 85) {
            return "critical";
        }
        if (score >= 70) {
            return "high";
        }
        if (score >= 45) {
            return "medium";
        }
        return "low";
    }

    private int clampRisk(Integer score, int fallback) {
        int value = score == null ? fallback : score;
        return Math.max(0, Math.min(100, value));
    }

    private String defaultIfBlank(Object value, String fallback) {
        String text = value == null ? "" : String.valueOf(value).trim();
        return text.isBlank() ? fallback : text;
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        try {
            Object parsed = objectMapper.readValue(raw, Map.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> out = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    if (entry.getKey() != null) {
                        out.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                return out;
            }
            return Map.of();
        } catch (Exception ex) {
            return Map.of("raw", raw);
        }
    }

    private String nowIso() {
        return new Timestamp(System.currentTimeMillis()).toInstant().toString();
    }

    private double round4(double value) {
        return Math.round(value * 10000D) / 10000D;
    }

    public static class ShadowSimReq {
        private Long companyId;
        private String username;
        private String eventType;
        private String clientId;
        private String hostname;
        private String clientIp;
        private String osType;
        private String clientVersion;
        private String processName;
        private String processPath;
        private String commandLine;
        private String parentProcess;
        private String targetDomain;
        private String targetIp;
        private Integer dstPort;
        private String protocol;
        private String allowlistVersion;
        private String serviceFingerprint;
        private Integer riskScore;
        private List<Map<String, Object>> relatedFileOps;

        public Long getCompanyId() { return companyId; }
        public void setCompanyId(Long companyId) { this.companyId = companyId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
        public String getHostname() { return hostname; }
        public void setHostname(String hostname) { this.hostname = hostname; }
        public String getClientIp() { return clientIp; }
        public void setClientIp(String clientIp) { this.clientIp = clientIp; }
        public String getOsType() { return osType; }
        public void setOsType(String osType) { this.osType = osType; }
        public String getClientVersion() { return clientVersion; }
        public void setClientVersion(String clientVersion) { this.clientVersion = clientVersion; }
        public String getProcessName() { return processName; }
        public void setProcessName(String processName) { this.processName = processName; }
        public String getProcessPath() { return processPath; }
        public void setProcessPath(String processPath) { this.processPath = processPath; }
        public String getCommandLine() { return commandLine; }
        public void setCommandLine(String commandLine) { this.commandLine = commandLine; }
        public String getParentProcess() { return parentProcess; }
        public void setParentProcess(String parentProcess) { this.parentProcess = parentProcess; }
        public String getTargetDomain() { return targetDomain; }
        public void setTargetDomain(String targetDomain) { this.targetDomain = targetDomain; }
        public String getTargetIp() { return targetIp; }
        public void setTargetIp(String targetIp) { this.targetIp = targetIp; }
        public Integer getDstPort() { return dstPort; }
        public void setDstPort(Integer dstPort) { this.dstPort = dstPort; }
        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }
        public String getAllowlistVersion() { return allowlistVersion; }
        public void setAllowlistVersion(String allowlistVersion) { this.allowlistVersion = allowlistVersion; }
        public String getServiceFingerprint() { return serviceFingerprint; }
        public void setServiceFingerprint(String serviceFingerprint) { this.serviceFingerprint = serviceFingerprint; }
        public Integer getRiskScore() { return riskScore; }
        public void setRiskScore(Integer riskScore) { this.riskScore = riskScore; }
        public List<Map<String, Object>> getRelatedFileOps() { return relatedFileOps; }
        public void setRelatedFileOps(List<Map<String, Object>> relatedFileOps) { this.relatedFileOps = relatedFileOps; }
    }

    public static class EmployeeAnomalyReq {
        private Long companyId;
        private String username;
        private String scenarioType;
        private String sessionId;
        private String deviceId;
        private String hostname;
        private Integer ruleScore;
        private Double baselineDeviation;
        private List<Map<String, Object>> eventSequence;

        public Long getCompanyId() { return companyId; }
        public void setCompanyId(Long companyId) { this.companyId = companyId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getScenarioType() { return scenarioType; }
        public void setScenarioType(String scenarioType) { this.scenarioType = scenarioType; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
        public String getDeviceId() { return deviceId; }
        public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
        public String getHostname() { return hostname; }
        public void setHostname(String hostname) { this.hostname = hostname; }
        public Integer getRuleScore() { return ruleScore; }
        public void setRuleScore(Integer ruleScore) { this.ruleScore = ruleScore; }
        public Double getBaselineDeviation() { return baselineDeviation; }
        public void setBaselineDeviation(Double baselineDeviation) { this.baselineDeviation = baselineDeviation; }
        public List<Map<String, Object>> getEventSequence() { return eventSequence; }
        public void setEventSequence(List<Map<String, Object>> eventSequence) { this.eventSequence = eventSequence; }
    }

    public static class ReplayReq {
        private Long companyId;
        private String username;
        private String module;
        private Integer rounds;

        public Long getCompanyId() { return companyId; }
        public void setCompanyId(Long companyId) { this.companyId = companyId; }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getModule() { return module; }
        public void setModule(String module) { this.module = module; }
        public Integer getRounds() { return rounds; }
        public void setRounds(Integer rounds) { this.rounds = rounds; }
    }
}
