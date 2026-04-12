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

        Map<String, Object> stats = statsCore(current);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", result.getTotal());
        data.put("pages", result.getPages());
        data.put("current", result.getCurrent());
        data.put("list", result.getRecords());
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
        QueryWrapper<GovernanceEvent> qw = companyScopeService.withCompany(new QueryWrapper<GovernanceEvent>())
            .eq("user_id", event.getUserId())
            .ne("id", event.getId())
            .orderByDesc("event_time")
            .last("limit " + Math.max(1, Math.min(200, limit)));
        List<GovernanceEvent> related = governanceEventService.list(qw);

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

        Map<String, Object> validation = Map.of();
        if (Boolean.TRUE.equals(req.getTriggerSimulation())) {
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

        Map<String, Object> governanceChangeDraft = autoCreateBlacklistGovernanceDraft(event, operator, nextStatus, req.getNote());

        saveAudit(operator, event, nextStatus, req.getTriggerSimulation(), validation);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", event);
        data.put("validation", validation);
        data.put("governanceChangeDraft", governanceChangeDraft);
        return R.ok(data);
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
