package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.entity.AdversarialRecord;
import com.trustai.entity.AuditLog;
import com.trustai.entity.GovernanceEvent;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import com.trustai.service.AdversarialRecordService;
import com.trustai.service.AiGatewayService;
import com.trustai.service.AuditLogService;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.EventHubService;
import com.trustai.service.GovernanceEventService;
import com.trustai.service.UserService;
import com.trustai.utils.R;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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

    private final GovernanceEventService governanceEventService;
    private final AdversarialRecordService adversarialRecordService;
    private final CurrentUserService currentUserService;
    private final CompanyScopeService companyScopeService;
    private final UserService userService;
    private final AiGatewayService aiGatewayService;
    private final EventHubService eventHubService;
    private final AuditLogService auditLogService;
    private final ObjectMapper objectMapper;

    public AlertCenterController(GovernanceEventService governanceEventService,
                                 AdversarialRecordService adversarialRecordService,
                                 CurrentUserService currentUserService,
                                 CompanyScopeService companyScopeService,
                                 UserService userService,
                                 AiGatewayService aiGatewayService,
                                 EventHubService eventHubService,
                                 AuditLogService auditLogService,
                                 ObjectMapper objectMapper) {
        this.governanceEventService = governanceEventService;
        this.adversarialRecordService = adversarialRecordService;
        this.currentUserService = currentUserService;
        this.companyScopeService = companyScopeService;
        this.userService = userService;
        this.aiGatewayService = aiGatewayService;
        this.eventHubService = eventHubService;
        this.auditLogService = auditLogService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','EXECUTIVE','SECOPS','DATA_ADMIN','AI_BUILDER','BUSINESS_OWNER','EMPLOYEE')")
    public R<Map<String, Object>> list(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "20") int pageSize,
        @RequestParam(required = false) String eventType,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false) Long userId
    ) {
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
        Page<GovernanceEvent> result = governanceEventService.page(new Page<>(Math.max(1, page), Math.max(1, pageSize)), qw);

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
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','EXECUTIVE','SECOPS','DATA_ADMIN','AI_BUILDER','BUSINESS_OWNER','EMPLOYEE')")
    public R<Map<String, Object>> stats() {
        return R.ok(statsCore(currentUserService.requireCurrentUser()));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','EXECUTIVE','SECOPS','DATA_ADMIN','AI_BUILDER','BUSINESS_OWNER','EMPLOYEE')")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        GovernanceEvent event = getScopedEvent(id);
        Map<String, Object> payload = parsePayload(event.getPayloadJson());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", event);
        data.put("payload", payload);
        return R.ok(data);
    }

    @GetMapping("/{id}/related")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','EXECUTIVE','SECOPS','DATA_ADMIN','AI_BUILDER','BUSINESS_OWNER','EMPLOYEE')")
    public R<Map<String, Object>> related(@PathVariable Long id,
                                          @RequestParam(defaultValue = "20") int limit) {
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
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','EXECUTIVE','SECOPS','DATA_ADMIN','AI_BUILDER','BUSINESS_OWNER','EMPLOYEE')")
    public R<Map<String, Object>> userHistory(@RequestParam(required = false) Long userId,
                                              @RequestParam(required = false) String username,
                                              @RequestParam(defaultValue = "30") int limit) {
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

    @PostMapping("/dispose")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS')")
    public R<Map<String, Object>> dispose(@RequestBody DisposeReq req) {
        if (req == null || req.getId() == null) {
            throw new BizException(40000, "告警ID不能为空");
        }
        GovernanceEvent event = getScopedEvent(req.getId());
        User operator = currentUserService.requireCurrentUser();

        String nextStatus = StringUtils.hasText(req.getStatus()) ? req.getStatus().toLowerCase(Locale.ROOT) : "reviewing";
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
        }

        saveAudit(operator, event, nextStatus, req.getTriggerSimulation(), validation);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("event", event);
        data.put("validation", validation);
        return R.ok(data);
    }

    private GovernanceEvent getScopedEvent(Long id) {
        User current = currentUserService.requireCurrentUser();
        GovernanceEvent event = governanceEventService.getOne(companyScopeService.withCompany(new QueryWrapper<GovernanceEvent>()).eq("id", id));
        if (event == null) {
            throw new BizException(40400, "告警不存在或不在当前公司");
        }
        if (!currentUserService.hasAnyRole("ADMIN", "SECOPS", "EXECUTIVE")
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
        if (!currentUserService.hasAnyRole("ADMIN", "SECOPS", "EXECUTIVE")) {
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
