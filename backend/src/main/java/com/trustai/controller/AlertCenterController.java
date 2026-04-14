package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.entity.AdversarialRecord;
import com.trustai.entity.AuditLog;
import com.trustai.entity.GovernanceChangeRequest;
import com.trustai.entity.GovernanceEvent;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import com.trustai.service.AdversarialRecordService;
import com.trustai.service.AiGatewayService;
import com.trustai.service.AuditLogService;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.EventHubService;
import com.trustai.service.GovernanceChangeRequestService;
import com.trustai.service.GovernanceEventService;
import com.trustai.service.UserService;
import com.trustai.utils.R;
import java.util.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alert-center")
public class AlertCenterController {

    private static final Set<String> THREAT_EVENT_TYPES = Set.of(
        "PRIVACY_ALERT", "ANOMALY_ALERT", "SHADOW_AI_ALERT", "SECURITY_ALERT"
    );

    private final GovernanceEventService governanceEventService;
    private final AdversarialRecordService adversarialRecordService;
    private final CurrentUserService currentUserService;
    private final CompanyScopeService companyScopeService;
    private final UserService userService;
    private final AiGatewayService aiGatewayService;
    private final EventHubService eventHubService;
    private final GovernanceChangeRequestService governanceChangeRequestService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AlertCenterController(GovernanceEventService governanceEventService,
                                 AdversarialRecordService adversarialRecordService,
                                 CurrentUserService currentUserService,
                                 CompanyScopeService companyScopeService,
                                 UserService userService,
                                 AiGatewayService aiGatewayService,
                                 EventHubService eventHubService,
                                 GovernanceChangeRequestService governanceChangeRequestService,
                                 AuditLogService auditLogService,
                                 ObjectMapper objectMapper) {
        this.governanceEventService = governanceEventService;
        this.adversarialRecordService = adversarialRecordService;
        this.currentUserService = currentUserService;
        this.companyScopeService = companyScopeService;
        this.userService = userService;
        this.aiGatewayService = aiGatewayService;
        this.eventHubService = eventHubService;
        this.governanceChangeRequestService = governanceChangeRequestService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<Map<String, Object>> list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long userId
    ) {
        enforceExecutiveDuty("list");
        User current = currentUserService.requireCurrentUser();
        QueryWrapper<GovernanceEvent> qw = companyScopeService.withCompany(new QueryWrapper<>());
        if (StringUtils.hasText(eventType)) {
            qw.eq("event_type", eventType.trim().toUpperCase(Locale.ROOT));
        }
        if (StringUtils.hasText(status)) {
            qw.eq("status", status.trim().toLowerCase(Locale.ROOT));
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like("title", keyword)
                .or().like("description", keyword)
                .or().like("username", keyword)
                .or().like("source_module", keyword));
        }

        if (currentUserService.hasAnyRole("ADMIN", "SECOPS")) {
            if (userId != null) {
                qw.eq("user_id", userId);
            }
        } else {
            qw.eq("user_id", current.getId());
        }

        qw.orderByDesc("event_time");
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        Page<GovernanceEvent> result = governanceEventService.page(new Page<>(safePage, safePageSize), qw);
        List<GovernanceEvent> normalizedRecords = result.getRecords().stream()
            .map(this::normalizeEventDisplay)
            .toList();
        List<GovernanceEvent> mixedRecords = mixByUsername(normalizedRecords);

        Map<String, Object> stats = statsCore(current);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", result.getTotal());
        data.put("pages", result.getPages());
        data.put("current", result.getCurrent());
        data.put("list", mixedRecords);
        data.put("stats", stats);
        return R.ok(data);
    }

    @GetMapping("/stats")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<Map<String, Object>> stats() {
        enforceExecutiveDuty("stats");
        return R.ok(statsCore(currentUserService.requireCurrentUser()));
    }

    @GetMapping("/threat-overview")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<Map<String, Object>> threatOverview(@RequestParam(defaultValue = "72") int windowHours) {
        enforceExecutiveDuty("stats");
        User current = currentUserService.requireCurrentUser();
        int safeWindowHours = Math.max(1, Math.min(720, windowHours));
        Date since = Date.from(LocalDateTime.now().minusHours(safeWindowHours).atZone(ZoneId.systemDefault()).toInstant());

        QueryWrapper<GovernanceEvent> query = companyScopeService.withCompany(new QueryWrapper<GovernanceEvent>())
            .ge("create_time", since)
            .orderByDesc("event_time")
            .last("LIMIT 8000");
        if (!currentUserService.hasAnyRole("ADMIN", "SECOPS", "ADMIN_REVIEWER")) {
            query.eq("user_id", current.getId());
        }

        List<GovernanceEvent> sourceEvents = governanceEventService.list(query).stream()
            .filter(item -> THREAT_EVENT_TYPES.contains(String.valueOf(item.getEventType()).toUpperCase(Locale.ROOT)))
            .toList();

        Map<String, GovernanceEvent> deduped = new LinkedHashMap<>();
        for (GovernanceEvent item : sourceEvents) {
            String key = threatChainKey(item);
            GovernanceEvent currentValue = deduped.get(key);
            if (currentValue == null || isNewerEvent(item, currentValue)) {
                deduped.put(key, item);
            }
        }

        long pending = 0L;
        long blocked = 0L;
        long critical = 0L;
        long high = 0L;
        long sourceLinked = 0L;
        Map<String, Long> byType = new LinkedHashMap<>();
        Map<String, Long> bySource = new LinkedHashMap<>();

        byType.put("privacy", 0L);
        byType.put("anomaly", 0L);
        byType.put("shadowAi", 0L);
        byType.put("security", 0L);

        for (GovernanceEvent item : deduped.values()) {
            String status = normalize(String.valueOf(item.getStatus()));
            String severity = normalize(String.valueOf(item.getSeverity()));
            String type = String.valueOf(item.getEventType() == null ? "" : item.getEventType()).toUpperCase(Locale.ROOT);
            String source = normalize(String.valueOf(item.getSourceModule()));

            if ("pending".equals(status) || "reviewing".equals(status)) {
                pending++;
            }
            if ("blocked".equals(status)) {
                blocked++;
            }
            if ("critical".equals(severity)) {
                critical++;
            }
            if ("high".equals(severity)) {
                high++;
            }

            if (StringUtils.hasText(item.getSourceEventId())) {
                sourceLinked++;
            }

            if ("PRIVACY_ALERT".equals(type)) {
                byType.put("privacy", byType.get("privacy") + 1);
            } else if ("ANOMALY_ALERT".equals(type)) {
                byType.put("anomaly", byType.get("anomaly") + 1);
            } else if ("SHADOW_AI_ALERT".equals(type)) {
                byType.put("shadowAi", byType.get("shadowAi") + 1);
            } else if ("SECURITY_ALERT".equals(type)) {
                byType.put("security", byType.get("security") + 1);
            }

            String sourceKey = source.isBlank() ? "unknown" : source;
            bySource.put(sourceKey, bySource.getOrDefault(sourceKey, 0L) + 1);
        }

        long uniqueTotal = deduped.size();
        long rawTotal = sourceEvents.size();
        long collapsed = Math.max(0L, rawTotal - uniqueTotal);
        double linkRate = uniqueTotal <= 0 ? 0D : (double) sourceLinked / uniqueTotal;

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", uniqueTotal);
        summary.put("pending", pending);
        summary.put("blocked", blocked);
        summary.put("critical", critical);
        summary.put("high", high);

        Map<String, Object> dedupe = new LinkedHashMap<>();
        dedupe.put("rawTotal", rawTotal);
        dedupe.put("uniqueTotal", uniqueTotal);
        dedupe.put("collapsed", collapsed);
        dedupe.put("windowHours", safeWindowHours);
        dedupe.put("caliber", "governance_event_dedup_chain_v1");

        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("sourceLinked", sourceLinked);
        trace.put("sourceLinkRate", Math.round(linkRate * 1000D) / 1000D);
        trace.put("rule", "source_module + source_event_id，缺失时回退 event_type + user_id + title + minute");

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("summary", summary);
        data.put("byType", byType);
        data.put("bySource", bySource);
        data.put("dedupe", dedupe);
        data.put("trace", trace);
        data.put("generatedAt", new Date());
        return R.ok(data);
    }

    @GetMapping("/{id}")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        enforceExecutiveDuty("detail");
        GovernanceEvent event = getScopedEvent(id);
        Map<String, Object> payload = parsePayload(event.getPayloadJson());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", event);
        data.put("payload", payload);
        return R.ok(data);
    }

    @GetMapping("/{id}/related")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<Map<String, Object>> related(@PathVariable Long id,
                                          @RequestParam(defaultValue = "20") int limit) {
        enforceExecutiveDuty("related");
        GovernanceEvent event = getScopedEvent(id);
        int safeLimit = Math.max(1, Math.min(200, limit));
        QueryWrapper<GovernanceEvent> qw = companyScopeService.withCompany(new QueryWrapper<GovernanceEvent>())
            .eq("user_id", event.getUserId())
            .ne("id", event.getId())
            .and(w -> w
                .eq(StringUtils.hasText(event.getSourceModule()), "source_module", event.getSourceModule())
                .or(StringUtils.hasText(event.getEventType()))
                .eq(StringUtils.hasText(event.getEventType()), "event_type", event.getEventType())
                .or(StringUtils.hasText(event.getAttackType()))
                .eq(StringUtils.hasText(event.getAttackType()), "attack_type", event.getAttackType())
            )
            .orderByDesc("event_time")
            .last("limit " + safeLimit);
        List<GovernanceEvent> related = governanceEventService.list(qw).stream()
            .map(this::normalizeEventDisplay)
            .toList();

        if (related.isEmpty()) {
            QueryWrapper<GovernanceEvent> fallback = companyScopeService.withCompany(new QueryWrapper<GovernanceEvent>())
                .eq("user_id", event.getUserId())
                .ne("id", event.getId())
                .orderByDesc("event_time")
                .last("limit " + safeLimit);
            related = governanceEventService.list(fallback).stream()
                .map(this::normalizeEventDisplay)
                .toList();
        }

        Map<String, Long> typeCount = new LinkedHashMap<>();
        for (GovernanceEvent item : related) {
            String key = item.getEventType() == null ? "UNKNOWN" : item.getEventType();
            typeCount.put(key, typeCount.getOrDefault(key, 0L) + 1L);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("current", event);
        data.put("related", related);
        data.put("typeCount", typeCount);
        return R.ok(data);
    }

    @GetMapping("/user-history")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<Map<String, Object>> userHistory(@RequestParam(required = false) Long userId,
                                              @RequestParam(required = false) String username,
                                              @RequestParam(defaultValue = "30") int limit) {
        enforceExecutiveDuty("user-history");
        User current = currentUserService.requireCurrentUser();
        Long targetUserId = current.getId();
        String targetUsername = current.getUsername();

        if (currentUserService.hasAnyRole("ADMIN", "SECOPS")) {
            if (userId != null) {
                User u = userService.getById(userId);
                if (u != null && java.util.Objects.equals(u.getCompanyId(), current.getCompanyId())) {
                    targetUserId = u.getId();
                    targetUsername = u.getUsername();
                }
            } else if (StringUtils.hasText(username)) {
                User u = userService.lambdaQuery().eq(User::getCompanyId, current.getCompanyId()).eq(User::getUsername, username).one();
                if (u != null) {
                    targetUserId = u.getId();
                    targetUsername = u.getUsername();
                }
            }
        }

        List<GovernanceEvent> events = governanceEventService.list(companyScopeService.withCompany(new QueryWrapper<GovernanceEvent>())
            .eq("user_id", targetUserId)
            .orderByDesc("event_time")
            .last("limit " + Math.max(1, Math.min(200, limit))));

        List<AdversarialRecord> battles = adversarialRecordService.list(companyScopeService.withCompany(new QueryWrapper<AdversarialRecord>())
            .eq("user_id", targetUserId)
            .orderByDesc("create_time")
            .last("limit " + Math.max(1, Math.min(200, limit))));

        Map<String, Long> counters = new LinkedHashMap<>();
        counters.put("privacy", 0L);
        counters.put("anomaly", 0L);
        counters.put("shadowAi", 0L);
        counters.put("security", 0L);
        for (GovernanceEvent item : events) {
            String key = item.getEventType();
            if ("PRIVACY_ALERT".equalsIgnoreCase(key)) counters.put("privacy", counters.get("privacy") + 1);
            else if ("ANOMALY_ALERT".equalsIgnoreCase(key)) counters.put("anomaly", counters.get("anomaly") + 1);
            else if ("SHADOW_AI_ALERT".equalsIgnoreCase(key)) counters.put("shadowAi", counters.get("shadowAi") + 1);
            else if ("SECURITY_ALERT".equalsIgnoreCase(key)) counters.put("security", counters.get("security") + 1);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("userId", targetUserId);
        data.put("username", targetUsername);
        data.put("counters", counters);
        data.put("events", events);
        data.put("adversarialRecords", battles);
        return R.ok(data);
    }

    private void enforceExecutiveDuty(String action) {
        currentUserService.requireCurrentUser();
        // Canonical role model no longer differentiates executive roles.
    }

    @PostMapping("/dispose")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS')")
    public R<Map<String, Object>> dispose(@RequestBody DisposeReq req) {
        if (req == null || req.getId() == null) {
            throw new BizException(40000, "告警ID不能为空");
        }
        GovernanceEvent event = getScopedEvent(req.getId());
        User operator = currentUserService.requireCurrentUser();
        boolean isAdmin = currentUserService.hasRole("ADMIN");
        boolean isSecops = currentUserService.hasRole("SECOPS");
        boolean isShadow = "SHADOW_AI_ALERT".equalsIgnoreCase(event.getEventType());

        String nextStatus = StringUtils.hasText(req.getStatus()) ? req.getStatus().toLowerCase(Locale.ROOT) : "reviewing";
        enforceSecopsDuty(operator, nextStatus);
        if (isShadow) {
            if ("blocked".equals(nextStatus) && !isAdmin) {
                throw new BizException(40300, "仅治理管理员可执行影子AI拉黑处置");
            }
            if ("ignored".equals(nextStatus) && !isSecops) {
                throw new BizException(40300, "仅安全运维可标记影子AI误报");
            }
        } else if (!isSecops) {
            throw new BizException(40300, "仅安全运维可处置非影子AI告警");
        }
        event.setStatus(nextStatus);
        event.setDisposeNote(req.getNote());
        event.setHandlerId(operator.getId());
        event.setDisposedAt(new Date());
        event.setUpdateTime(new Date());
        governanceEventService.updateById(event);

        if ("security".equalsIgnoreCase(event.getSourceModule()) && StringUtils.hasText(event.getSourceEventId())) {
            try {
                eventHubService.syncGovernanceStatus("security", Long.parseLong(event.getSourceEventId()), nextStatus, operator.getId(), req.getNote());
            } catch (Exception ignored) {
                // keep governance update as source of truth
            }
        }

        boolean falsePositiveMarked = "ignored".equalsIgnoreCase(nextStatus);
        boolean shouldTriggerSimulation = Boolean.TRUE.equals(req.getTriggerSimulation())
            || (falsePositiveMarked && req.getTriggerSimulation() == null);

        Map<String, Object> validation = Map.of();
        if (shouldTriggerSimulation) {
            try {
                AiGatewayController.BattleReq battleReq = new AiGatewayController.BattleReq();
                battleReq.setScenario(mapScenario(event));
                battleReq.setRounds(req.getRounds() == null ? 12 : Math.max(1, Math.min(100, req.getRounds())));
                validation = aiGatewayService.adversarialRun(battleReq);

                AdversarialRecord record = new AdversarialRecord();
                record.setCompanyId(event.getCompanyId());
                record.setUserId(event.getUserId());
                record.setUsername(event.getUsername());
                record.setGovernanceEventId(event.getId());
                record.setScenario(battleReq.getScenario());
                record.setPolicyVersion(event.getPolicyVersion());
                record.setResultJson(writeJson(validation.getOrDefault("battle", validation)));
                String analysisText = String.valueOf(validation.getOrDefault(
                    "effectivenessAnalysis",
                    validation.getOrDefault("analysis", validation.getOrDefault("defensePolicyEffectiveness", ""))
                ));
                record.setEffectivenessAnalysis(analysisText);
                Object suggestionPayload = validation.get("optimizationSuggestions");
                if (suggestionPayload == null) {
                    suggestionPayload = validation.getOrDefault("suggestions", validation.getOrDefault("strategyOptimizationSuggestions", List.of()));
                }
                record.setSuggestionsJson(writeJson(suggestionPayload));
                record.setCreateTime(new Date());
                record.setUpdateTime(new Date());
                adversarialRecordService.save(record);
            } catch (Exception ex) {
                validation = Map.of(
                    "available", false,
                    "message", "攻防验证引擎暂不可用，处置状态已更新",
                    "engineError", ex.getMessage() == null ? "unknown" : ex.getMessage()
                );
            }
        }

        Map<String, Object> validationReport = buildValidationReport(event, nextStatus, shouldTriggerSimulation, validation, req.getNote());

        Map<String, Object> governanceChangeDraft = autoCreateBlacklistGovernanceDraft(event, operator, nextStatus, req.getNote());

        saveAudit(operator, event, nextStatus, shouldTriggerSimulation, validationReport);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", event);
        data.put("validation", validationReport);
        data.put("validationRaw", validation);
        data.put("governanceChangeDraft", governanceChangeDraft);
        return R.ok(data);
    }

    private Map<String, Object> buildValidationReport(GovernanceEvent event,
                                                      String nextStatus,
                                                      boolean verificationTriggered,
                                                      Map<String, Object> validation,
                                                      String disposeNote) {
        Map<String, Object> safeValidation = validation == null ? Map.of() : validation;
        Map<String, Object> battle = asMap(safeValidation.get("battle"));

        double breachRate = firstMetric(safeValidation, battle, "breachRate", "breach_rate", "compromiseRate", "attackSuccessRate");
        double blockRate = firstMetric(safeValidation, battle, "blockRate", "block_rate", "defenseSuccessRate", "successRate");
        if (blockRate < 0 && breachRate >= 0) {
            blockRate = Math.max(0.0, Math.min(1.0, 1.0 - breachRate));
        }

        boolean falsePositiveMarked = "ignored".equalsIgnoreCase(nextStatus);
        boolean engineAvailable = !verificationTriggered || !Boolean.FALSE.equals(safeValidation.get("available"));
        boolean defenseEffective = verificationTriggered
            ? (engineAvailable && (breachRate < 0 || breachRate <= 0.20) && (blockRate < 0 || blockRate >= 0.80))
            : falsePositiveMarked;

        String verdict;
        if (!verificationTriggered) {
            verdict = falsePositiveMarked ? "manual_false_positive_pending_verification" : "status_updated_without_verification";
        } else if (!engineAvailable) {
            verdict = "verification_engine_unavailable";
        } else if (defenseEffective) {
            verdict = "false_positive_confirmed";
        } else {
            verdict = "policy_gap_detected";
        }

        List<String> recommendations = new ArrayList<>();
        recommendations.addAll(extractSuggestions(safeValidation.get("optimizationSuggestions")));
        recommendations.addAll(extractSuggestions(safeValidation.get("suggestions")));
        if (recommendations.isEmpty()) {
            recommendations.addAll(defaultHardeningSuggestions(event, defenseEffective));
        }

        String analysis = String.valueOf(safeValidation.getOrDefault(
            "effectivenessAnalysis",
            safeValidation.getOrDefault("analysis", "")
        )).trim();
        if (analysis.isBlank()) {
            analysis = buildDefaultAnalysis(defenseEffective, verificationTriggered, engineAvailable, breachRate, blockRate, disposeNote);
        }

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("falsePositiveMarked", falsePositiveMarked);
        report.put("verificationTriggered", verificationTriggered);
        report.put("engineAvailable", engineAvailable);
        report.put("verificationVerdict", verdict);
        report.put("defenseEffective", defenseEffective);
        report.put("canCloseAlert", falsePositiveMarked && (!verificationTriggered || defenseEffective));
        report.put("nextAction", defenseEffective
            ? "可确认误报并关闭告警；如需长期稳定建议补齐白名单。"
            : "建议先修复策略再关闭告警（白名单、阈值、规则优先级需调整）。");
        report.put("effectivenessAnalysis", analysis);
        report.put("optimizationSuggestions", recommendations);
        if (!battle.isEmpty()) {
            report.put("battle", battle);
        }
        if (breachRate >= 0) {
            report.put("breachRate", breachRate);
        }
        if (blockRate >= 0) {
            report.put("blockRate", blockRate);
        }
        return report;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private double firstMetric(Map<String, Object> root, Map<String, Object> battle, String... keys) {
        for (String key : keys) {
            double fromRoot = toDouble(root.get(key));
            if (fromRoot >= 0) {
                return fromRoot;
            }
            double fromBattle = toDouble(battle.get(key));
            if (fromBattle >= 0) {
                return fromBattle;
            }
        }
        return -1;
    }

    private double toDouble(Object value) {
        if (value == null) {
            return -1;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isBlank()) {
            return -1;
        }
        try {
            return Double.parseDouble(text);
        } catch (Exception ex) {
            return -1;
        }
    }

    private List<String> extractSuggestions(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String text = String.valueOf(item == null ? "" : item).trim();
            if (!text.isBlank() && !result.contains(text)) {
                result.add(text);
            }
        }
        return result;
    }

    private List<String> defaultHardeningSuggestions(GovernanceEvent event, boolean defenseEffective) {
        List<String> tips = new ArrayList<>();
        String eventType = String.valueOf(event == null ? "" : event.getEventType()).toUpperCase(Locale.ROOT);
        if ("SHADOW_AI_ALERT".equals(eventType)) {
            tips.add("将业务必需的AI域名/工具加入白名单并绑定审批责任人。");
            tips.add("为同类影子AI流量设置行为阈值，区分业务使用与异常扩散。");
        } else if ("ANOMALY_ALERT".equals(eventType)) {
            tips.add("收紧异常行为阈值并增加角色/时段维度基线。");
            tips.add("补充高风险行为特征，降低重复误报。");
        } else {
            tips.add("按处置结果校准策略阈值并补充白名单/黑名单规则。");
        }
        if (!defenseEffective) {
            tips.add("优先执行策略加固后再关闭告警，避免同类攻击绕过。");
        }
        return tips;
    }

    private String buildDefaultAnalysis(boolean defenseEffective,
                                        boolean verificationTriggered,
                                        boolean engineAvailable,
                                        double breachRate,
                                        double blockRate,
                                        String disposeNote) {
        StringBuilder text = new StringBuilder();
        if (!verificationTriggered) {
            text.append("未触发攻防验证，当前仅完成处置状态更新。");
        } else if (!engineAvailable) {
            text.append("攻防验证未成功执行，请稍后重试并复核策略有效性。");
        } else if (defenseEffective) {
            text.append("攻防验证显示当前防御策略有效，可作为误报闭环依据。");
        } else {
            text.append("攻防验证发现策略存在漏洞，需先加固后再关闭告警。");
        }
        if (breachRate >= 0 || blockRate >= 0) {
            text.append(" 指标：");
            if (breachRate >= 0) {
                text.append("突破率=").append(String.format(Locale.ROOT, "%.2f", breachRate)).append("; ");
            }
            if (blockRate >= 0) {
                text.append("拦截率=").append(String.format(Locale.ROOT, "%.2f", blockRate)).append("; ");
            }
        }
        if (StringUtils.hasText(disposeNote)) {
            text.append(" 处置备注：").append(disposeNote.trim());
        }
        return text.toString().trim();
    }

    private Map<String, Object> autoCreateBlacklistGovernanceDraft(GovernanceEvent event,
                                                                   User operator,
                                                                   String nextStatus,
                                                                   String disposeNote) {
        if (event == null || operator == null || !"blocked".equalsIgnoreCase(nextStatus)) {
            return Map.of("created", false, "reason", "status_not_blocked");
        }

        Long companyId = event.getCompanyId();
        if (companyId == null || companyId <= 0) {
            return Map.of("created", false, "reason", "company_missing");
        }

        long pendingExists = governanceChangeRequestService.count(new QueryWrapper<GovernanceChangeRequest>()
            .eq("company_id", companyId)
            .eq("module", "POLICY")
            .eq("action", "UPDATE")
            .eq("status", "pending")
            .eq("target_id", event.getId()));
        if (pendingExists > 0) {
            return Map.of("created", false, "reason", "pending_draft_exists", "governanceEventId", event.getId());
        }

        Map<String, Object> payload = parsePayload(event.getPayloadJson());
        Map<String, Object> blacklistCandidate = new LinkedHashMap<>();
        blacklistCandidate.put("sourceModule", event.getSourceModule());
        blacklistCandidate.put("eventType", event.getEventType());
        blacklistCandidate.put("attackType", event.getAttackType());
        blacklistCandidate.put("processName", firstText(
            payload.get("processName"),
            payload.get("process_name"),
            payload.get("employeeProcess"),
            payload.get("app")
        ));
        blacklistCandidate.put("targetDomain", firstText(
            payload.get("dstDomain"),
            payload.get("targetDomain"),
            payload.get("domain")
        ));
        blacklistCandidate.put("targetIp", firstText(
            payload.get("dstIp"),
            payload.get("targetIp"),
            payload.get("ip")
        ));
        blacklistCandidate.put("serviceName", firstText(
            payload.get("serviceName"),
            payload.get("service"),
            payload.get("aiService")
        ));
        blacklistCandidate.put("userId", event.getUserId());
        blacklistCandidate.put("username", event.getUsername());
        blacklistCandidate.put("riskLevel", event.getSeverity());
        blacklistCandidate.put("riskScore", payload.get("riskScore"));

        Map<String, Object> draftPayload = new LinkedHashMap<>();
        draftPayload.put("title", "自动生成黑名单治理草案");
        draftPayload.put("reason", "告警处置状态为 blocked，系统自动生成治理变更草案");
        draftPayload.put("governanceEventId", event.getId());
        draftPayload.put("sourceEventId", event.getSourceEventId());
        draftPayload.put("sourceModule", event.getSourceModule());
        draftPayload.put("blacklistCandidate", blacklistCandidate);
        draftPayload.put("disposeNote", disposeNote == null ? "" : disposeNote);
        draftPayload.put("simulation", Boolean.TRUE.equals(payload.get("simulation")));
        draftPayload.put("triggerAt", new Date());

        GovernanceChangeRequest draft = new GovernanceChangeRequest();
        draft.setCompanyId(companyId);
        draft.setModule("POLICY");
        draft.setAction("UPDATE");
        draft.setTargetId(event.getId());
        draft.setPayloadJson(writeJson(draftPayload));
        draft.setStatus("pending");
        draft.setRiskLevel("HIGH");
        draft.setRequesterId(operator.getId());
        draft.setRequesterRoleCode(resolveRoleCode(operator));
        draft.setCreateTime(new Date());
        draft.setUpdateTime(new Date());
        governanceChangeRequestService.save(draft);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("created", true);
        result.put("requestId", draft.getId());
        result.put("module", draft.getModule());
        result.put("action", draft.getAction());
        result.put("status", draft.getStatus());
        result.put("governanceEventId", event.getId());
        result.put("blacklistCandidate", blacklistCandidate);
        return result;
    }

    private String resolveRoleCode(User user) {
        if (user == null) {
            return "UNKNOWN";
        }
        if (currentUserService.hasRole("ADMIN")) {
            return "ADMIN";
        }
        if (currentUserService.hasRole("SECOPS")) {
            return "SECOPS";
        }
        if (currentUserService.hasRole("ADMIN_REVIEWER")) {
            return "ADMIN_REVIEWER";
        }
        return "UNKNOWN";
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            if (value == null) {
                continue;
            }
            String text = String.valueOf(value).trim();
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private void enforceSecopsDuty(User operator, String targetStatus) {
        if (operator == null || !currentUserService.hasRole("SECOPS")) {
            return;
        }
        // Canonical role model no longer differentiates secops sub-roles.
    }

    private GovernanceEvent getScopedEvent(Long id) {
        User current = currentUserService.requireCurrentUser();
        GovernanceEvent event = governanceEventService.getOne(companyScopeService.withCompany(new QueryWrapper<GovernanceEvent>()).eq("id", id));
        if (event == null) {
            throw new BizException(40400, "告警不存在或不在当前公司");
        }
        if (!currentUserService.hasAnyRole("ADMIN", "SECOPS", "ADMIN_REVIEWER")
            && (event.getUserId() == null || !java.util.Objects.equals(event.getUserId(), current.getId()))) {
            throw new BizException(40300, "无权访问该告警");
        }
        return event;
    }

    private String mapScenario(GovernanceEvent event) {
        if (StringUtils.hasText(event.getAttackType())) {
            return event.getAttackType();
        }
        String type = String.valueOf(event.getEventType() == null ? "" : event.getEventType()).toUpperCase(Locale.ROOT);
        return switch (type) {
            case "PRIVACY_ALERT" -> "data_exfil_steg";
            case "ANOMALY_ALERT" -> "decision_drift";
            case "SHADOW_AI_ALERT" -> "shadow_deployment";
            default -> "prompt_injection";
        };
    }

    private Map<String, Object> statsCore(User current) {
        QueryWrapper<GovernanceEvent> base = companyScopeService.withCompany(new QueryWrapper<>());
        if (!currentUserService.hasAnyRole("ADMIN", "SECOPS", "ADMIN_REVIEWER")) {
            base.eq("user_id", current.getId());
        }

        long total = governanceEventService.count(base);
        long pending = governanceEventService.count(base.clone().eq("status", "pending"));
        long blocked = governanceEventService.count(base.clone().eq("status", "blocked"));
        long critical = governanceEventService.count(base.clone().eq("severity", "critical"));
        long high = governanceEventService.count(base.clone().eq("severity", "high"));
        long privacy = governanceEventService.count(base.clone().eq("event_type", "PRIVACY_ALERT"));
        long anomaly = governanceEventService.count(base.clone().eq("event_type", "ANOMALY_ALERT"));
        long shadowAi = governanceEventService.count(base.clone().eq("event_type", "SHADOW_AI_ALERT"));

        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", total);
        stats.put("pending", pending);
        stats.put("blocked", blocked);
        stats.put("critical", critical);
        stats.put("high", high);
        stats.put("privacy", privacy);
        stats.put("anomaly", anomaly);
        stats.put("shadowAi", shadowAi);
        return stats;
    }

    private String threatChainKey(GovernanceEvent item) {
        String sourceModule = normalize(item.getSourceModule());
        String sourceEventId = item.getSourceEventId() == null ? "" : item.getSourceEventId().trim();
        if (!sourceModule.isBlank() && !sourceEventId.isBlank()) {
            return sourceModule + "::" + sourceEventId;
        }
        LocalDateTime time = resolveAnchor(item).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        return String.join("::",
            normalize(item.getEventType()),
            String.valueOf(item.getUserId() == null ? 0L : item.getUserId()),
            normalize(item.getTitle()),
            String.valueOf(time.getYear()),
            String.valueOf(time.getMonthValue()),
            String.valueOf(time.getDayOfMonth()),
            String.valueOf(time.getHour()),
            String.valueOf(time.getMinute())
        );
    }

    private boolean isNewerEvent(GovernanceEvent left, GovernanceEvent right) {
        Date leftTime = resolveAnchor(left);
        Date rightTime = resolveAnchor(right);
        if (leftTime.after(rightTime)) {
            return true;
        }
        if (leftTime.before(rightTime)) {
            return false;
        }
        long leftId = left.getId() == null ? 0L : left.getId();
        long rightId = right.getId() == null ? 0L : right.getId();
        return leftId > rightId;
    }

    private Date resolveAnchor(GovernanceEvent event) {
        if (event.getEventTime() != null) {
            return event.getEventTime();
        }
        if (event.getCreateTime() != null) {
            return event.getCreateTime();
        }
        return new Date(0L);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private GovernanceEvent normalizeEventDisplay(GovernanceEvent source) {
        if (source == null) {
            return null;
        }
        source.setTitle(cleanGarbledText(source.getTitle(), "SLOW_QUERY_ALERT"));
        source.setDescription(cleanGarbledText(source.getDescription(), "DETAIL_PENDING"));
        source.setAttackType(normalizeAttackTypeText(source.getAttackType()));
        return source;
    }

    private String cleanGarbledText(String value, String fallback) {
        String text = value == null ? "" : value.trim();
        if (text.isBlank()) {
            return fallback;
        }
        if (text.matches("^\\?{2,}$")) {
            return fallback;
        }
        return text.replaceAll("\\?{2,}", fallback);
    }

    private String normalizeAttackTypeText(String attackType) {
        if (!StringUtils.hasText(attackType)) {
            return "GENERIC_ATTACK";
        }
        String raw = attackType.trim().toUpperCase(Locale.ROOT);
        return switch (raw) {
            case "QUERY_REGRESSION" -> "QUERY_REGRESSION";
            case "PROMPT_INJECTION" -> "PROMPT_INJECTION";
            case "POLICY_BYPASS" -> "POLICY_BYPASS";
            case "ABNORMAL_ACCESS" -> "ABNORMAL_ACCESS";
            case "PRIVACY_POLICY_HIT" -> "PRIVACY_POLICY_HIT";
            case "DECISION_DRIFT" -> "DECISION_DRIFT";
            case "SHADOW_DEPLOYMENT" -> "SHADOW_DEPLOYMENT";
            case "RESILIENCE_CHAOS" -> "RESILIENCE_CHAOS";
            case "DATA_EXFIL_PLAIN" -> "DATA_EXFIL_PLAIN";
            case "DATA_EXFIL_STEG" -> "DATA_EXFIL_STEG";
            case "DATA_EXFILTRATION" -> "DATA_EXFILTRATION";
            case "SENSITIVE_DATA_EXFILTRATION" -> "SENSITIVE_DATA_EXFILTRATION";
            default -> raw;
        };
    }

    private List<GovernanceEvent> mixByUsername(List<GovernanceEvent> records) {
        List<GovernanceEvent> source = records == null ? List.of() : records;
        if (source.size() <= 2) {
            return source;
        }

        Map<String, List<GovernanceEvent>> buckets = new LinkedHashMap<>();
        for (GovernanceEvent item : source) {
            String key = item == null || !StringUtils.hasText(item.getUsername())
                ? "unknown"
                : item.getUsername().trim().toLowerCase(Locale.ROOT);
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }

        List<String> keys = new ArrayList<>(buckets.keySet());
        Collections.shuffle(keys);
        List<GovernanceEvent> mixed = new ArrayList<>(source.size());

        boolean progressed = true;
        while (mixed.size() < source.size() && progressed) {
            progressed = false;
            for (String key : keys) {
                List<GovernanceEvent> bucket = buckets.get(key);
                if (bucket != null && !bucket.isEmpty()) {
                    mixed.add(bucket.remove(0));
                    progressed = true;
                }
            }
        }
        return mixed;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(raw, Map.class);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Map.of() : value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private void saveAudit(User operator,
                           GovernanceEvent event,
                           String status,
                           Boolean triggerSimulation,
                           Map<String, Object> validation) {
        AuditLog log = new AuditLog();
        log.setUserId(operator.getId());
        log.setOperation("alert_dispose_" + status);
        log.setOperationTime(new Date());
        log.setInputOverview("eventId=" + event.getId() + ", type=" + event.getEventType() + ", triggerSim=" + Boolean.TRUE.equals(triggerSimulation));
        log.setOutputOverview("validation=" + (validation == null ? "{}" : validation.keySet()));
        log.setResult("success");
        log.setRiskLevel("MEDIUM");
        log.setHash(String.valueOf(System.currentTimeMillis()));
        log.setCreateTime(new Date());
        auditLogService.saveAudit(log);
    }

    public static class DisposeReq {
        private Long id;
        private String status;
        private String note;
        private Boolean triggerSimulation;
        private Integer rounds;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
        public Boolean getTriggerSimulation() { return triggerSimulation; }
        public void setTriggerSimulation(Boolean triggerSimulation) { this.triggerSimulation = triggerSimulation; }
        public Integer getRounds() { return rounds; }
        public void setRounds(Integer rounds) { this.rounds = rounds; }
    }
}
