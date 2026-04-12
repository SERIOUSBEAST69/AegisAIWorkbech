package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.config.jwt.JwtUtil;
import com.trustai.controller.AiGatewayController.ChatReq;
import com.trustai.controller.AiGatewayController.Message;
import com.trustai.entity.AiCallLog;
import com.trustai.entity.AuditLog;
import com.trustai.entity.DataAsset;
import com.trustai.entity.GovernanceEvent;
import com.trustai.entity.User;
import com.trustai.service.AiGatewayService;
import com.trustai.service.AiCallAuditService;
import com.trustai.service.AuditLogService;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.DataAssetService;
import com.trustai.service.GovernanceEventService;
import com.trustai.service.UserService;
import com.trustai.utils.R;
import io.jsonwebtoken.Claims;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Comparator;
import java.util.Set;
import java.util.LinkedHashSet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.regex.Pattern;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/dashboard")
public class HomeAiHubController {

    private static final Set<String> PRIVILEGED_ROLES = Set.of(
        "ADMIN", "ADMIN_REVIEWER", "SECOPS", "BUSINESS_OWNER", "AUDIT"
    );
    private static final Set<String> THREAT_TYPES = Set.of(
        "PRIVACY_ALERT", "ANOMALY_ALERT", "SHADOW_AI_ALERT", "SECURITY_ALERT"
    );
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern EMAIL_PATTERN = Pattern.compile("([A-Za-z0-9._%+\\-]{1,3})[A-Za-z0-9._%+\\-]*@([A-Za-z0-9.\\-]+\\.[A-Za-z]{2,})");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)");
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(?<!\\d)([1-9]\\d{5})\\d{8}(\\d{4}|\\d{3}[Xx])(?!\\d)");
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("(?<!\\d)(\\d{4})\\d{8,11}(\\d{4})(?!\\d)");
    private static final Set<String> PROMPT_SENSITIVE_KEYS = Set.of(
        "username", "user", "userid", "user_id", "approver", "applicant",
        "phone", "mobile", "email", "idcard", "id_card", "bankcard", "bank_card",
        "ip", "ipaddress", "clientid", "client_id", "hostname", "devicefingerprint",
        "token", "authorization", "raw", "payload", "content"
    );

    private final CurrentUserService currentUserService;
    private final CompanyScopeService companyScopeService;
    private final UserService userService;
    private final DataAssetService dataAssetService;
    private final GovernanceEventService governanceEventService;
    private final AiCallAuditService aiCallAuditService;
    private final AuditLogService auditLogService;
    private final AiGatewayService aiGatewayService;
    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbcTemplate;

    private final ScheduledExecutorService streamScheduler = Executors.newScheduledThreadPool(2);

    public HomeAiHubController(CurrentUserService currentUserService,
                               CompanyScopeService companyScopeService,
                               UserService userService,
                               DataAssetService dataAssetService,
                               GovernanceEventService governanceEventService,
                               AiCallAuditService aiCallAuditService,
                               AuditLogService auditLogService,
                               AiGatewayService aiGatewayService,
                               JwtUtil jwtUtil,
                               JdbcTemplate jdbcTemplate) {
        this.currentUserService = currentUserService;
        this.companyScopeService = companyScopeService;
        this.userService = userService;
        this.dataAssetService = dataAssetService;
        this.governanceEventService = governanceEventService;
        this.aiCallAuditService = aiCallAuditService;
        this.auditLogService = auditLogService;
        this.aiGatewayService = aiGatewayService;
        this.jwtUtil = jwtUtil;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/ai-hub")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> hubData(@RequestParam(defaultValue = "company") String scopeLevel,
                                          @RequestParam(required = false) String department,
                                          @RequestParam(required = false) String username) {
        User viewer = currentUserService.requireCurrentUser();
        ScopeSelection selection = resolveScope(viewer, scopeLevel, department, username);
        return R.ok(buildHubPayload(selection));
    }

    @GetMapping("/ai-hub/deepseek-analysis")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> deepseekAnalysis(@RequestParam(defaultValue = "company") String scopeLevel,
                                                   @RequestParam(required = false) String department,
                                                   @RequestParam(required = false) String username) {
        User viewer = currentUserService.requireCurrentUser();
        ScopeSelection selection = resolveScope(viewer, scopeLevel, department, username);
        Map<String, Object> hub = buildHubPayload(selection);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("generatedAt", new Date());
        result.put("scope", hub.get("scope"));

        String content = buildAnalysisPrompt(hub);
        try {
            String analysis = runDeepseekAnalysis(content);
            result.put("analysis", analysis);
            result.put("source", "deepseek-chat");
        } catch (Exception ex) {
            result.put("analysis", buildLocalFallbackAnalysis(hub, ex.getMessage()));
            result.put("source", "deepseek-fallback");
            result.put("warning", "DeepSeek调用失败，已返回本地聚合解读");
        }
        result.put("trace", Map.of(
            "companyId", selection.companyId,
            "scopeLevel", selection.scopeLevel,
            "scopeUserCount", selection.userIds.size(),
            "dataKpiCount", ((List<?>) hub.getOrDefault("kpis", List.of())).size()
        ));
        return R.ok(result);
    }

    @GetMapping("/ai-hub/scope-options")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> hubScopeOptions() {
        User viewer = currentUserService.requireCurrentUser();
        Long companyId = companyScopeService.requireCompanyId();
        String roleCode = currentUserService.currentRoleCode();
        boolean privileged = canUseCompanyDepartmentScope(viewer);

        List<User> companyUsers = loadCompanyUsers(companyId);
        if (!privileged) {
            companyUsers = companyUsers.stream()
                .filter(item -> Objects.equals(item.getId(), viewer.getId()))
                .collect(Collectors.toList());
        }

        List<Long> targetUserIds = companyUsers.stream()
            .map(User::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());

        Map<String, Long> threatCountByUser = governanceEventService.list(
            new QueryWrapper<GovernanceEvent>()
                .eq("company_id", companyId)
                .in("event_type", THREAT_TYPES)
                .in(!targetUserIds.isEmpty(), "user_id", targetUserIds)
                .select("user_id")
        ).stream().collect(Collectors.groupingBy(
            item -> String.valueOf(item.getUserId()),
            Collectors.counting()
        ));

        Map<String, List<User>> departmentUsers = companyUsers.stream()
            .collect(Collectors.groupingBy(item -> String.valueOf(item.getDepartment() == null ? "未分配部门" : item.getDepartment()).trim()));

        List<Map<String, Object>> departments = departmentUsers.entrySet().stream()
            .map(entry -> {
                String dept = entry.getKey();
                List<User> users = entry.getValue();
                long members = users.size();
                long riskUsers = users.stream()
                    .filter(user -> threatCountByUser.getOrDefault(String.valueOf(user.getId()), 0L) > 0)
                    .count();
                long riskEvents = users.stream()
                    .map(User::getId)
                    .mapToLong(uid -> threatCountByUser.getOrDefault(String.valueOf(uid), 0L))
                    .sum();

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("value", dept);
                row.put("label", dept);
                row.put("memberCount", members);
                row.put("riskUserCount", riskUsers);
                row.put("riskEventCount", riskEvents);
                return row;
            })
            .sorted(Comparator.comparing(item -> String.valueOf(item.get("label"))))
            .collect(Collectors.toList());

        List<Map<String, Object>> users = companyUsers.stream()
            .map(item -> {
                String dept = String.valueOf(item.getDepartment() == null ? "未分配部门" : item.getDepartment()).trim();
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("value", item.getUsername());
                row.put("label", item.getUsername());
                row.put("department", dept);
                row.put("riskEventCount", threatCountByUser.getOrDefault(String.valueOf(item.getId()), 0L));
                return row;
            })
            .sorted(Comparator.comparing(item -> String.valueOf(item.get("label"))))
            .collect(Collectors.toList());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("companyId", companyId);
        data.put("userCount", companyUsers.size());
        data.put("allowedLevels", privileged ? List.of("company", "department", "user") : List.of("user"));
        data.put("departments", departments);
        data.put("users", users);
        data.put("viewer", Map.of(
            "username", viewer.getUsername(),
            "department", String.valueOf(viewer.getDepartment() == null ? "" : viewer.getDepartment()).trim(),
            "roleCode", roleCode
        ));
        return R.ok(data);
    }

    @GetMapping("/ai-hub/detail")
    @PreAuthorize("isAuthenticated()")
    public R<Map<String, Object>> hubDetail(@RequestParam String kind,
                                            @RequestParam String key,
                                            @RequestParam(defaultValue = "company") String scopeLevel,
                                            @RequestParam(required = false) String department,
                                            @RequestParam(required = false) String username,
                                            @RequestParam(defaultValue = "20") int limit,
                                            @RequestParam(required = false) String source,
                                            @RequestParam(required = false) String target) {
        User viewer = currentUserService.requireCurrentUser();
        ScopeSelection selection = resolveScope(viewer, scopeLevel, department, username);
        int safeLimit = Math.max(5, Math.min(120, limit));
        String safeKind = String.valueOf(kind == null ? "" : kind).trim().toLowerCase(Locale.ROOT);
        String safeKey = String.valueOf(key == null ? "" : key).trim();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("kind", safeKind);
        data.put("key", safeKey);
        data.put("title", "中枢明细");
        data.put("description", "");
        data.put("records", List.of());

        if ("graph-node".equals(safeKind)) {
            fillGraphNodeDetail(data, selection, safeKey, safeLimit);
        } else if ("graph-edge".equals(safeKind)) {
            fillGraphEdgeDetail(data, selection, safeKey, safeLimit, source, target);
        } else if ("radar-dimension".equals(safeKind)) {
            fillRadarDimensionDetail(data, selection, safeKey, safeLimit);
        } else if ("pulse-node".equals(safeKind)) {
            fillPulseNodeDetail(data, selection, safeKey, safeLimit);
        }

        return R.ok(data);
    }

    @GetMapping(path = "/ai-hub/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter hubStream(@RequestParam("token") String token,
                                @RequestParam(defaultValue = "company") String scopeLevel,
                                @RequestParam(required = false) String department,
                                @RequestParam(required = false) String username,
                                @RequestParam(defaultValue = "0") Long cursor,
                                @RequestParam(defaultValue = "25") int limit) {
        ScopeSelection selection = scopeFromToken(token, scopeLevel, department, username);
        long safeCursor = cursor == null ? 0L : Math.max(0L, cursor);
        int safeLimit = Math.max(5, Math.min(80, limit));

        SseEmitter emitter = new SseEmitter(0L);
        final long[] currentCursor = {safeCursor};

        Runnable task = () -> {
            try {
                List<Map<String, Object>> updates = loadTimelineDelta(selection, currentCursor[0], safeLimit);
                if (updates.isEmpty()) {
                    emitter.send(SseEmitter.event().name("ping").data(Map.of("ts", System.currentTimeMillis())));
                    return;
                }

                long maxId = updates.stream()
                    .map(item -> Long.valueOf(String.valueOf(item.get("id"))))
                    .max(Long::compareTo)
                    .orElse(currentCursor[0]);
                currentCursor[0] = Math.max(currentCursor[0], maxId);

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("cursor", currentCursor[0]);
                payload.put("timelineDelta", updates);
                payload.put("kpis", buildKpis(selection));
                payload.put("alertBoard", buildAlertBoard(selection));
                payload.put("scopePersona", buildScopePersona(selection));
                payload.put("pulseWall", buildPulseWall(selection));
                payload.put("generatedAt", new Date());
                emitter.send(SseEmitter.event().name("delta").data(payload));
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        };

        try {
            Map<String, Object> snapshot = buildHubPayload(selection);
            snapshot.put("event", "snapshot");
            emitter.send(SseEmitter.event().name("snapshot").data(snapshot));
        } catch (Exception ex) {
            emitter.completeWithError(ex);
            return emitter;
        }

        var future = streamScheduler.scheduleAtFixedRate(task, 5, 5, TimeUnit.SECONDS);
        emitter.onCompletion(() -> future.cancel(true));
        emitter.onTimeout(() -> {
            future.cancel(true);
            emitter.complete();
        });
        emitter.onError(ex -> future.cancel(true));
        return emitter;
    }

    private Map<String, Object> buildHubPayload(ScopeSelection selection) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scope", Map.of(
            "level", selection.scopeLevel,
            "department", selection.department,
            "username", selection.username,
            "companyId", selection.companyId
        ));
        payload.put("kpis", buildKpis(selection));
        payload.put("alertBoard", buildAlertBoard(selection));
        payload.put("scopePersona", buildScopePersona(selection));
        payload.put("pulseWall", buildPulseWall(selection));
        payload.put("graph", buildGraph(selection));
        payload.put("radar", Map.of("dimensions", buildRadar(selection)));
        payload.put("cursor", latestTimelineCursor(selection));
        payload.put("generatedAt", new Date());
        return payload;
    }

    private List<Map<String, Object>> buildKpis(ScopeSelection selection) {
        long assetCount = dataAssetService.count(scopeAssetQuery(selection));
        long riskCount = governanceEventService.count(scopeThreatQuery(selection));
        long aiCallCount = aiCallAuditService.count(scopeAiCallQuery(selection));
        long auditCount = auditLogService.count(scopeAuditQuery(selection));
        long pendingCount = governanceEventService.count(
            scopeThreatQuery(selection).in("status", List.of("pending", "reviewing"))
        );

        int governanceScore = Math.max(0, Math.min(100,
            (int) Math.round(100 - Math.min(80, pendingCount * 2 + Math.max(0, riskCount - 20)))
        ));

        List<Map<String, Object>> kpis = new ArrayList<>();
        kpis.add(kpi("assetTotal", "资产库对象", assetCount, "Data Asset Snapshot"));
        kpis.add(kpi("riskEvents", "风险事件总量", riskCount, "Governance Event Chain"));
        kpis.add(kpi("aiCalls", "模型调用总量", aiCallCount, "AI Call Audit"));
        kpis.add(kpi("auditLogs", "审计留痕记录", auditCount, "Audit Evidence"));
        kpis.add(kpi("governanceScore", "治理脉冲分", governanceScore, "Scope: " + selection.scopeLevel));
        return kpis;
    }

    private Map<String, Object> buildAlertBoard(ScopeSelection selection) {
        List<GovernanceEvent> events = governanceEventService.list(
            scopeThreatQuery(selection)
                .in("status", List.of("pending", "reviewing", "blocked"))
                .select("id", "event_type", "severity", "status", "title", "username", "source_module", "event_time")
                .orderByDesc("event_time")
                .last("LIMIT 10")
        );

        List<Map<String, Object>> items = events.stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("title", StringUtils.hasText(item.getTitle()) ? item.getTitle() : String.valueOf(item.getEventType()));
            row.put("eventType", item.getEventType());
            row.put("severity", item.getSeverity());
            row.put("status", item.getStatus());
            row.put("username", item.getUsername());
            row.put("sourceModule", item.getSourceModule());
            row.put("eventTime", formatTime(item.getEventTime()));
            return row;
        }).collect(Collectors.toList());

        long pendingCount = events.stream()
            .filter(item -> List.of("pending", "reviewing").contains(String.valueOf(item.getStatus()).toLowerCase(Locale.ROOT)))
            .count();
        long highSeverityCount = events.stream()
            .filter(item -> {
                String severity = String.valueOf(item.getSeverity() == null ? "" : item.getSeverity()).toLowerCase(Locale.ROOT);
                return "high".equals(severity) || "critical".equals(severity);
            })
            .count();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("items", items);
        data.put("pendingCount", pendingCount);
        data.put("highSeverityCount", highSeverityCount);
        return data;
    }

    private Map<String, Object> buildScopePersona(ScopeSelection selection) {
        Map<String, Object> persona = new LinkedHashMap<>();
        persona.put("scopeLevel", selection.scopeLevel);

        if ("company".equals(selection.scopeLevel)) {
            long userCount = selection.userMap.size();
            long departmentCount = selection.userMap.values().stream()
                .map(item -> String.valueOf(item.getDepartment() == null ? "未分配部门" : item.getDepartment()).trim())
                .distinct()
                .count();
            long riskyUsers = governanceEventService.list(
                scopeThreatQuery(selection).select("user_id")
            ).stream().map(GovernanceEvent::getUserId).filter(Objects::nonNull).distinct().count();

            persona.put("title", "公司画像");
            persona.put("summary", "覆盖全公司治理态势，用于管理层总览与风险优先级判断。");
            persona.put("stats", List.of(
                Map.of("label", "覆盖部门", "value", departmentCount),
                Map.of("label", "覆盖成员", "value", userCount),
                Map.of("label", "风险成员", "value", riskyUsers)
            ));
            return persona;
        }

        if ("department".equals(selection.scopeLevel)) {
            String dept = StringUtils.hasText(selection.department) ? selection.department : "未分配部门";
            long memberCount = selection.userIds.size();
            long riskCount = governanceEventService.count(scopeThreatQuery(selection));
            long pendingCount = governanceEventService.count(
                scopeThreatQuery(selection).in("status", List.of("pending", "reviewing"))
            );

            persona.put("title", "部门画像");
            persona.put("summary", "当前部门风险密度与处置效率，可用于部门负责人跟踪治理质量。");
            persona.put("department", dept);
            persona.put("stats", List.of(
                Map.of("label", "部门成员", "value", memberCount),
                Map.of("label", "风险事件", "value", riskCount),
                Map.of("label", "待处置", "value", pendingCount)
            ));
            return persona;
        }

        User current = selection.userMap.values().stream()
            .filter(item -> String.valueOf(item.getUsername()).equalsIgnoreCase(selection.username))
            .findFirst()
            .orElseGet(() -> selection.userMap.get(selection.userIds.isEmpty() ? null : selection.userIds.get(0)));
        Long userId = current == null ? (selection.userIds.isEmpty() ? null : selection.userIds.get(0)) : current.getId();

        long riskCount = governanceEventService.count(scopeThreatQuery(selection));
        long pendingCount = governanceEventService.count(
            scopeThreatQuery(selection).in("status", List.of("pending", "reviewing"))
        );
        long aiCall7d = aiCallAuditService.count(
            scopeAiCallQuery(selection)
                .ge("create_time", LocalDateTime.now().minusDays(7))
        );
        long highRiskAudit = auditLogService.count(
            scopeAuditQuery(selection).in("risk_level", List.of("high", "critical"))
        );

        List<AiCallLog> recentCalls = aiCallAuditService.list(
            scopeAiCallQuery(selection)
                .select("duration_ms")
                .orderByDesc("create_time")
                .last("LIMIT 100")
        );
        long avgLatency = recentCalls.isEmpty()
            ? 0
            : Math.round(recentCalls.stream().mapToLong(item -> item.getDurationMs() == null ? 0L : item.getDurationMs()).average().orElse(0D));

        String username = current != null && StringUtils.hasText(current.getUsername()) ? current.getUsername() : selection.username;
        String department = current != null && StringUtils.hasText(current.getDepartment())
            ? current.getDepartment()
            : (StringUtils.hasText(selection.department) ? selection.department : "未分配部门");
        String roleCode = current == null ? "AUDIT" : roleCodeOf(current);

        persona.put("title", "个人画像");
        persona.put("summary", "基于用户粒度的风险、调用与审计行为分析，支持精准治理闭环。");
        persona.put("username", username);
        persona.put("department", department);
        persona.put("roleCode", roleCode);
        persona.put("stats", List.of(
            Map.of("label", "风险事件", "value", riskCount),
            Map.of("label", "待处置", "value", pendingCount),
            Map.of("label", "7天模型调用", "value", aiCall7d),
            Map.of("label", "平均时延(ms)", "value", avgLatency),
            Map.of("label", "高风险审计", "value", highRiskAudit)
        ));
        return persona;
    }

    private Map<String, Object> buildPulseWall(ScopeSelection selection) {
        List<Map<String, Object>> nodes = new ArrayList<>();

        List<DataAsset> topAssets = dataAssetService.list(
            scopeAssetQuery(selection)
                .select("id", "name", "sensitivity_level", "owner_id", "create_time")
                .orderByDesc("create_time")
                .last("LIMIT 8")
        );
        for (DataAsset item : topAssets) {
            String sensitivity = String.valueOf(item.getSensitivityLevel() == null ? "L2" : item.getSensitivityLevel()).toUpperCase(Locale.ROOT);
            String risk = ("L4".equals(sensitivity) || "L5".equals(sensitivity)) ? "high" : ("L3".equals(sensitivity) ? "medium" : "low");
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", "asset-" + item.getId());
            node.put("kind", "asset");
            node.put("title", StringUtils.hasText(item.getName()) ? item.getName() : "未命名资产");
            node.put("subtitle", "敏感等级 " + sensitivity);
            node.put("riskLevel", risk);
            node.put("activity", "medium");
            node.put("eventTime", formatTime(item.getCreateTime()));
            node.put("detailKind", "pulse-node");
            node.put("detailKey", "asset:" + item.getId());
            nodes.add(node);
        }

        List<GovernanceEvent> topEvents = governanceEventService.list(
            scopeThreatQuery(selection)
                .select("id", "event_type", "severity", "status", "title", "username", "event_time")
                .orderByDesc("event_time")
                .last("LIMIT 8")
        );
        for (GovernanceEvent item : topEvents) {
            String severity = String.valueOf(item.getSeverity() == null ? "medium" : item.getSeverity()).toLowerCase(Locale.ROOT);
            String risk = "critical".equals(severity) || "high".equals(severity) ? "high" : ("medium".equals(severity) ? "medium" : "low");
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", "event-" + item.getId());
            node.put("kind", "event");
            node.put("title", StringUtils.hasText(item.getTitle()) ? item.getTitle() : String.valueOf(item.getEventType()));
            node.put("subtitle", String.valueOf(item.getEventType()));
            node.put("riskLevel", risk);
            node.put("activity", List.of("pending", "reviewing").contains(String.valueOf(item.getStatus()).toLowerCase(Locale.ROOT)) ? "high" : "medium");
            node.put("eventTime", formatTime(item.getEventTime()));
            node.put("detailKind", "pulse-node");
            node.put("detailKey", "event:" + item.getId());
            nodes.add(node);
        }

        List<AiCallLog> topCalls = aiCallAuditService.list(
            scopeAiCallQuery(selection)
                .select("id", "username", "model_code", "provider", "status", "duration_ms", "create_time")
                .orderByDesc("create_time")
                .last("LIMIT 8")
        );
        for (AiCallLog item : topCalls) {
            long duration = item.getDurationMs() == null ? 0L : item.getDurationMs();
            String status = String.valueOf(item.getStatus() == null ? "unknown" : item.getStatus()).toLowerCase(Locale.ROOT);
            String risk = "success".equals(status) && duration < 2000 ? "low" : ("success".equals(status) ? "medium" : "high");
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", "call-" + item.getId());
            node.put("kind", "ai_call");
            node.put("title", StringUtils.hasText(item.getModelCode()) ? item.getModelCode() : "模型调用");
            node.put("subtitle", String.valueOf(item.getProvider() == null ? "unknown" : item.getProvider()) + " / " + status);
            node.put("riskLevel", risk);
            node.put("activity", duration >= 2500 ? "high" : (duration >= 1200 ? "medium" : "low"));
            node.put("eventTime", formatTime(item.getCreateTime()));
            node.put("detailKind", "pulse-node");
            node.put("detailKey", "ai:" + item.getId());
            nodes.add(node);
        }

        nodes = nodes.stream()
            .sorted(Comparator
                .comparing((Map<String, Object> node) -> severityRank(String.valueOf(node.get("riskLevel")))).reversed()
                .thenComparing(node -> String.valueOf(node.get("eventTime")), Comparator.reverseOrder()))
            .limit(24)
            .collect(Collectors.toList());

        if (nodes.isEmpty()) {
            nodes = buildAggregatePulseFallback(selection);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nodes", nodes);
        data.put("total", nodes.size());
        data.put("generatedAt", new Date());
        return data;
    }

    private Map<String, Object> buildGraph(ScopeSelection selection) {
        long assets = dataAssetService.count(scopeAssetQuery(selection));
        long threats = governanceEventService.count(scopeThreatQuery(selection));
        long calls = aiCallAuditService.count(scopeAiCallQuery(selection));
        long audits = auditLogService.count(scopeAuditQuery(selection));

        long highSensitivityAssets = dataAssetService.count(
            scopeAssetQuery(selection).in("sensitivity_level", List.of("L4", "L5"))
        );
        long recentAssets = dataAssetService.count(
            scopeAssetQuery(selection).ge("create_time", LocalDateTime.now().minusDays(30))
        );

        long pendingThreats = governanceEventService.count(
            scopeThreatQuery(selection).in("status", List.of("pending", "reviewing"))
        );
        long blockedThreats = governanceEventService.count(
            scopeThreatQuery(selection).eq("status", "blocked")
        );
        long highSeverityThreats = governanceEventService.count(
            scopeThreatQuery(selection).in("severity", List.of("high", "critical"))
        );

        long highRiskAudits = auditLogService.count(
            scopeAuditQuery(selection).in("risk_level", List.of("high", "critical"))
        );
        long recentAudits = auditLogService.count(
            scopeAuditQuery(selection).ge("operation_time", LocalDateTime.now().minusDays(7))
        );

        List<AiCallLog> callSamples = aiCallAuditService.list(
            scopeAiCallQuery(selection)
                .select("status", "duration_ms")
                .orderByDesc("create_time")
                .last("LIMIT 1200")
        );
        long callFailures = callSamples.stream().filter(item -> isFailureStatus(item.getStatus())).count();
        long slowCalls = callSamples.stream()
            .filter(item -> (item.getDurationMs() == null ? 0L : item.getDurationMs()) >= 2500)
            .count();

        long assetPressure = Math.max(2L, Math.round(assets * 0.55 + highSensitivityAssets * 1.9 + recentAssets * 0.45));
        long riskPressure = Math.max(2L, Math.round(threats * 0.4 + pendingThreats * 1.5 + blockedThreats * 1.2 + highSeverityThreats * 1.8));
        long auditPressure = Math.max(2L, Math.round(audits * 0.28 + highRiskAudits * 1.7 + recentAudits * 0.55));
        long aiPressure = Math.max(2L, Math.round(calls * 0.32 + callFailures * 1.6 + slowCalls * 1.05));

        int companyPulse = clamp((int) Math.round(22
            + percent(highSensitivityAssets, Math.max(1L, assets)) * 0.22
            + percent(highSeverityThreats, Math.max(1L, threats)) * 0.26
            + percent(callFailures, Math.max(1L, callSamples.size())) * 0.18));
        long companyValue = Math.max(5L, Math.round(companyPulse / 8.0));

        List<Map<String, Object>> nodes = List.of(
            node("company", "公司#" + selection.companyId, companyValue, "#38bdf8"),
            node("asset", "资产库", assetPressure, "#22d3ee"),
            node("risk", "风险事件", riskPressure, "#f97316"),
            node("audit", "审计日志", auditPressure, "#a78bfa"),
            node("ai", "模型调用", aiPressure, "#34d399")
        );

        List<Map<String, Object>> edges = List.of(
            edge("company", "asset", edgeWeight(assetPressure * 0.12 + highSensitivityAssets * 0.3), "rgba(34,211,238,0.75)"),
            edge("company", "risk", edgeWeight(riskPressure * 0.14 + highSeverityThreats * 0.35), "rgba(249,115,22,0.8)"),
            edge("company", "audit", edgeWeight(auditPressure * 0.13 + highRiskAudits * 0.35), "rgba(167,139,250,0.78)"),
            edge("asset", "ai", edgeWeight((assetPressure + aiPressure) * 0.08 + highSensitivityAssets * 0.2), "rgba(52,211,153,0.72)"),
            edge("ai", "risk", edgeWeight(callFailures * 0.6 + slowCalls * 0.35 + highSeverityThreats * 0.22), "rgba(251,146,60,0.76)"),
            edge("risk", "audit", edgeWeight((pendingThreats + blockedThreats) * 0.55 + highRiskAudits * 0.25), "rgba(147,197,253,0.70)")
        );

        return Map.of("nodes", nodes, "edges", edges);
    }

    private List<Map<String, Object>> buildRadar(ScopeSelection selection) {
        List<GovernanceEvent> events = governanceEventService.list(
            scopeThreatQuery(selection)
                .select("event_type", "severity", "status", "event_time")
                .orderByDesc("event_time")
                .last("LIMIT 2500")
        );

        long totalThreats = events.size();
        long privacyAlerts = events.stream().filter(item -> "PRIVACY_ALERT".equalsIgnoreCase(String.valueOf(item.getEventType()))).count();
        long anomalyAlerts = events.stream().filter(item -> "ANOMALY_ALERT".equalsIgnoreCase(String.valueOf(item.getEventType()))).count();
        long shadowAlerts = events.stream().filter(item -> "SHADOW_AI_ALERT".equalsIgnoreCase(String.valueOf(item.getEventType()))).count();

        LocalDateTime since14d = LocalDateTime.now().minusDays(14);
        long privacyHigh = events.stream()
            .filter(item -> "PRIVACY_ALERT".equalsIgnoreCase(String.valueOf(item.getEventType())))
            .filter(item -> isHighSeverity(item.getSeverity()))
            .count();
        long anomalyHigh = events.stream()
            .filter(item -> "ANOMALY_ALERT".equalsIgnoreCase(String.valueOf(item.getEventType())))
            .filter(item -> isHighSeverity(item.getSeverity()))
            .count();
        long shadowHigh = events.stream()
            .filter(item -> "SHADOW_AI_ALERT".equalsIgnoreCase(String.valueOf(item.getEventType())))
            .filter(item -> isHighSeverity(item.getSeverity()))
            .count();

        long privacyRecent = events.stream()
            .filter(item -> "PRIVACY_ALERT".equalsIgnoreCase(String.valueOf(item.getEventType())))
            .filter(item -> toLocalDateTime(item.getEventTime()).isAfter(since14d))
            .count();
        long anomalyRecent = events.stream()
            .filter(item -> "ANOMALY_ALERT".equalsIgnoreCase(String.valueOf(item.getEventType())))
            .filter(item -> toLocalDateTime(item.getEventTime()).isAfter(since14d))
            .count();
        long shadowRecent = events.stream()
            .filter(item -> "SHADOW_AI_ALERT".equalsIgnoreCase(String.valueOf(item.getEventType())))
            .filter(item -> toLocalDateTime(item.getEventTime()).isAfter(since14d))
            .count();

        long pendingAlerts = events.stream().filter(item -> isPendingOrReviewing(item.getStatus())).count();
        long blockedAlerts = events.stream().filter(item -> "blocked".equalsIgnoreCase(String.valueOf(item.getStatus()))).count();
        long criticalPending = events.stream()
            .filter(item -> isPendingOrReviewing(item.getStatus()))
            .filter(item -> isHighSeverity(item.getSeverity()))
            .count();

        List<AiCallLog> calls = aiCallAuditService.list(
            scopeAiCallQuery(selection)
                .select("id", "status", "duration_ms")
                .orderByDesc("create_time")
                .last("LIMIT 2000")
        );
        long callTotal = calls.size();
        long callFailure = calls.stream().filter(item -> isFailureStatus(item.getStatus())).count();
        long slowCalls = calls.stream().filter(item -> (item.getDurationMs() == null ? 0L : item.getDurationMs()) >= 2500).count();
        long timeoutCalls = calls.stream()
            .filter(item -> String.valueOf(item.getStatus() == null ? "" : item.getStatus()).toLowerCase(Locale.ROOT).contains("timeout"))
            .count();
        List<Long> latencies = calls.stream()
            .map(item -> item.getDurationMs() == null ? 0L : item.getDurationMs())
            .sorted()
            .collect(Collectors.toList());
        long avgLatency = callTotal <= 0 ? 0 : Math.round(latencies.stream().mapToLong(Long::longValue).average().orElse(0D));
        long p95Latency = percentile(latencies, 95);

        long highRiskAudits = auditLogService.count(
            scopeAuditQuery(selection).in("risk_level", List.of("high", "critical"))
        );

        int privacyRisk = clamp((int) Math.round(
            privacyAlerts * 6.0
                + privacyHigh * 11.0
                + privacyRecent * 7.0
                + percent(privacyAlerts, Math.max(1L, totalThreats)) * 0.18
                + percent(criticalPending, Math.max(1L, pendingAlerts + blockedAlerts)) * 0.08
        ));

        int behaviorRisk = clamp((int) Math.round(
            anomalyAlerts * 5.0
                + anomalyHigh * 10.0
                + anomalyRecent * 8.0
                + percent(callFailure, Math.max(1L, callTotal)) * 0.32
                + (p95Latency >= 3200 ? 18 : (p95Latency >= 2200 ? 10 : (p95Latency >= 1400 ? 5 : 0)))
        ));

        int shadowRisk = clamp((int) Math.round(
            shadowAlerts * 8.0
                + shadowHigh * 13.0
                + shadowRecent * 10.0
                + percent(shadowAlerts, Math.max(1L, totalThreats)) * 0.3
                + (shadowRecent >= 5 ? 6 : 0)
        ));

        int complianceRisk = clamp((int) Math.round(
            pendingAlerts * 8.0
                + blockedAlerts * 12.0
                + criticalPending * 13.0
                + Math.min(24, highRiskAudits * 0.7)
        ));

        int reliabilityRisk = clamp((int) Math.round(
            percent(callFailure, Math.max(1L, callTotal)) * 0.55
                + percent(slowCalls, Math.max(1L, callTotal)) * 0.35
                + percent(timeoutCalls, Math.max(1L, callTotal)) * 0.3
                + (avgLatency >= 3000 ? 24 : (avgLatency >= 2000 ? 15 : (avgLatency >= 1200 ? 8 : 3)))
                + (p95Latency >= 3800 ? 20 : (p95Latency >= 2800 ? 12 : (p95Latency >= 1800 ? 6 : 0)))
        ));

        List<Map<String, Object>> dimensions = new ArrayList<>();
        dimensions.add(radar("privacyDiscipline", "隐私纪律", privacyRisk));
        dimensions.add(radar("behaviorStability", "行为稳定性", behaviorRisk));
        dimensions.add(radar("shadowAiExposure", "影子AI暴露", shadowRisk));
        dimensions.add(radar("securityCompliance", "安全处置合规", complianceRisk));
        dimensions.add(radar("modelReliability", "模型调用可靠性", reliabilityRisk));
        return dimensions;
    }

    private List<Map<String, Object>> loadTimeline(ScopeSelection selection, int limit) {
        int safeLimit = Math.max(5, Math.min(60, limit));
        List<GovernanceEvent> events = governanceEventService.list(
            scopeThreatQuery(selection)
                .select("id", "event_type", "title", "description", "event_time")
                .orderByDesc("event_time")
                .last("LIMIT " + safeLimit)
        );
        return events.stream().map(this::timelineRow).collect(Collectors.toList());
    }

    private List<Map<String, Object>> loadTimelineDelta(ScopeSelection selection, long afterIdExclusive, int limit) {
        List<GovernanceEvent> events = governanceEventService.list(
            scopeThreatQuery(selection)
                .gt("id", afterIdExclusive)
                .select("id", "event_type", "title", "description", "event_time")
                .orderByAsc("id")
                .last("LIMIT " + limit)
        );
        return events.stream().map(this::timelineRow).collect(Collectors.toList());
    }

    private long latestTimelineCursor(ScopeSelection selection) {
        GovernanceEvent latest = governanceEventService.getOne(
            scopeThreatQuery(selection)
                .select("id")
                .orderByDesc("id")
                .last("LIMIT 1")
        );
        return latest == null || latest.getId() == null ? 0L : latest.getId();
    }

    private List<Map<String, Object>> buildRecommendations(ScopeSelection selection) {
        List<Map<String, Object>> dims = buildRadar(selection);
        int privacy = intOf(findRadarValue(dims, "privacyDiscipline"));
        int behavior = intOf(findRadarValue(dims, "behaviorStability"));
        int shadow = intOf(findRadarValue(dims, "shadowAiExposure"));
        int compliance = intOf(findRadarValue(dims, "securityCompliance"));

        List<Map<String, Object>> list = new ArrayList<>();
        list.add(recommend("隐私泄露防控加固", privacy >= 60 ? "P0" : "P1", "优先核查隐私告警并加固策略规则", "安全指挥台", "/operations-command", Map.of("status", "pending")));
        list.add(recommend("异常行为轨迹复核", behavior >= 60 ? "P0" : "P1", "复核异常行为样本并绑定审计动作", "AI使用合规监控", "/ai/anomaly", Map.of("tab", "profile")));
        list.add(recommend("影子AI治理闭环", shadow >= 60 ? "P0" : "P1", "核查白名单外服务并执行审批", "影子AI发现", "/shadow-ai", Map.of("tab", "risk")));
        list.add(recommend("待处置告警收敛", compliance >= 55 ? "P0" : "P2", "对待处置事件执行阻断并触发验证", "AI攻击实时防御", "/threat-monitor", Map.of("tab", "alertCenter")));
        return list;
    }

    private ScopeSelection resolveScope(User viewer,
                                        String scopeLevel,
                                        String department,
                                        String username) {
        Long companyId = companyScopeService.requireCompanyId();
        boolean privileged = canUseCompanyDepartmentScope(viewer);

        String safeScope = normalizeScope(scopeLevel);
        if (!privileged) {
            safeScope = "user";
        }

        List<User> companyUsers = loadCompanyUsers(companyId);
        Map<Long, User> userMap = companyUsers.stream()
            .filter(item -> item.getId() != null)
            .collect(Collectors.toMap(User::getId, item -> item, (a, b) -> a));

        List<Long> targetUserIds = new ArrayList<>();
        String targetDepartment = "";
        String targetUsername = viewer.getUsername();

        if ("department".equals(safeScope)) {
            targetDepartment = StringUtils.hasText(department) ? department.trim() : "";
            final String deptFilter = targetDepartment;
            targetUserIds = companyUsers.stream()
                .filter(item -> deptFilter.equals(String.valueOf(item.getDepartment() == null ? "" : item.getDepartment()).trim()))
                .map(User::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
            if (targetUserIds.isEmpty()) {
                targetUserIds.add(viewer.getId());
            }
        } else if ("user".equals(safeScope)) {
            targetUsername = StringUtils.hasText(username) && privileged ? username.trim() : viewer.getUsername();
            final String userFilter = targetUsername;
            User targetUser = companyUsers.stream()
                .filter(item -> userFilter.equalsIgnoreCase(String.valueOf(item.getUsername())))
                .findFirst()
                .orElse(viewer);
            targetUserIds.add(targetUser.getId());
            targetUsername = targetUser.getUsername();
        } else {
            targetUserIds = companyUsers.stream()
                .map(User::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        }

        if (targetUserIds.isEmpty()) {
            targetUserIds.add(viewer.getId());
        }

        return new ScopeSelection(companyId, safeScope, targetDepartment, targetUsername, targetUserIds, userMap);
    }

    private List<User> loadCompanyUsers(Long companyId) {
        return userService.lambdaQuery().eq(User::getCompanyId, companyId).list();
    }

    private ScopeSelection scopeFromToken(String rawToken,
                                          String scopeLevel,
                                          String department,
                                          String username) {
        String token = String.valueOf(rawToken == null ? "" : rawToken).trim();
        if (token.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            token = token.substring(7).trim();
        }
        Claims claims = jwtUtil.parse(token);
        Long uid = claims.get("uid", Long.class);
        Long cid = claims.get("cid", Long.class);
        if (uid == null || cid == null) {
            throw new IllegalArgumentException("token 缺少 uid/cid");
        }
        User viewer = userService.getById(uid);
        if (viewer == null || !Objects.equals(viewer.getCompanyId(), cid)) {
            throw new IllegalArgumentException("token 用户无效");
        }
        return resolveScope(viewer, scopeLevel, department, username);
    }

    private void fillGraphNodeDetail(Map<String, Object> data,
                                     ScopeSelection selection,
                                     String key,
                                     int limit) {
        String normalized = String.valueOf(key == null ? "" : key).toLowerCase(Locale.ROOT).trim();
        if ("asset".equals(normalized)) {
            List<DataAsset> assets = dataAssetService.list(
                scopeAssetQuery(selection)
                    .select("id", "name", "type", "sensitivity_level", "owner_id", "create_time")
                    .orderByDesc("create_time")
                    .last("LIMIT " + limit)
            );
            data.put("title", "资产库明细");
            data.put("description", "最近资产数据（按当前视角范围）");
            data.put("records", assets.stream().map(item -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.getId());
                row.put("name", item.getName());
                row.put("type", item.getType());
                row.put("sensitivityLevel", item.getSensitivityLevel());
                row.put("ownerId", item.getOwnerId());
                row.put("createTime", formatTime(item.getCreateTime()));
                return row;
            }).collect(Collectors.toList()));
            return;
        }
        if ("risk".equals(normalized)) {
            List<GovernanceEvent> events = governanceEventService.list(
                scopeThreatQuery(selection)
                    .select("id", "event_type", "severity", "status", "title", "username", "event_time")
                    .orderByDesc("event_time")
                    .last("LIMIT " + limit)
            );
            data.put("title", "风险事件明细");
            data.put("description", "治理事件链路中的风险相关告警");
            data.put("records", events.stream().map(item -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.getId());
                row.put("eventType", item.getEventType());
                row.put("severity", item.getSeverity());
                row.put("status", item.getStatus());
                row.put("title", item.getTitle());
                row.put("username", item.getUsername());
                row.put("eventTime", formatTime(item.getEventTime()));
                return row;
            }).collect(Collectors.toList()));
            return;
        }
        if ("audit".equals(normalized)) {
            List<AuditLog> logs = auditLogService.list(
                scopeAuditQuery(selection)
                    .select("id", "user_id", "operation", "risk_level", "operation_time")
                    .orderByDesc("operation_time")
                    .last("LIMIT " + limit)
            );
            data.put("title", "审计日志明细");
            data.put("description", "审计留痕链路关键记录");
            data.put("records", logs.stream().map(item -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.getId());
                row.put("userId", item.getUserId());
                row.put("operation", item.getOperation());
                row.put("riskLevel", item.getRiskLevel());
                row.put("operationTime", formatTime(item.getOperationTime()));
                return row;
            }).collect(Collectors.toList()));
            return;
        }
        if ("ai".equals(normalized)) {
            List<AiCallLog> logs = aiCallAuditService.list(
                scopeAiCallQuery(selection)
                    .select("id", "user_id", "username", "model_code", "provider", "status", "duration_ms", "create_time")
                    .orderByDesc("create_time")
                    .last("LIMIT " + limit)
            );
            data.put("title", "模型调用明细");
            data.put("description", "AI调用审计记录");
            data.put("records", logs.stream().map(item -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.getId());
                row.put("userId", item.getUserId());
                row.put("username", item.getUsername());
                row.put("modelCode", item.getModelCode());
                row.put("provider", item.getProvider());
                row.put("status", item.getStatus());
                row.put("durationMs", item.getDurationMs());
                row.put("createTime", formatTime(item.getCreateTime()));
                return row;
            }).collect(Collectors.toList()));
            return;
        }

        data.put("title", "公司视角总览");
        data.put("description", "当前视角聚合指标快照");
        data.put("records", buildKpis(selection));
    }

    private void fillGraphEdgeDetail(Map<String, Object> data,
                                     ScopeSelection selection,
                                     String key,
                                     int limit,
                                     String source,
                                     String target) {
        String safeSource = StringUtils.hasText(source) ? source.trim() : "";
        String safeTarget = StringUtils.hasText(target) ? target.trim() : "";
        data.put("title", "链路边明细");
        data.put("description", StringUtils.hasText(key) ? key : (safeSource + " -> " + safeTarget));

        QueryWrapper<GovernanceEvent> query = scopeThreatQuery(selection)
            .select("id", "event_type", "severity", "status", "title", "username", "source_module", "event_time")
            .orderByDesc("event_time")
            .last("LIMIT " + limit);
        if ("asset".equalsIgnoreCase(safeSource) && "ai".equalsIgnoreCase(safeTarget)) {
            query.in("event_type", List.of("ANOMALY_ALERT", "SHADOW_AI_ALERT"));
        }
        if ("ai".equalsIgnoreCase(safeSource) && "risk".equalsIgnoreCase(safeTarget)) {
            query.in("event_type", List.of("ANOMALY_ALERT", "PRIVACY_ALERT"));
        }
        if ("risk".equalsIgnoreCase(safeSource) && "audit".equalsIgnoreCase(safeTarget)) {
            query.in("status", List.of("blocked", "ignored", "reviewing", "pending"));
        }
        List<GovernanceEvent> rows = governanceEventService.list(query);
        data.put("records", rows.stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("eventType", item.getEventType());
            row.put("severity", item.getSeverity());
            row.put("status", item.getStatus());
            row.put("title", item.getTitle());
            row.put("username", item.getUsername());
            row.put("sourceModule", item.getSourceModule());
            row.put("eventTime", formatTime(item.getEventTime()));
            return row;
        }).collect(Collectors.toList()));
    }

    private void fillRadarDimensionDetail(Map<String, Object> data,
                                          ScopeSelection selection,
                                          String key,
                                          int limit) {
        String dimension = String.valueOf(key == null ? "" : key).trim();
        if ("privacyDiscipline".equals(dimension)) {
            List<GovernanceEvent> rows = governanceEventService.list(
                scopeThreatQuery(selection)
                    .eq("event_type", "PRIVACY_ALERT")
                    .select("id", "severity", "status", "title", "username", "event_time")
                    .orderByDesc("event_time")
                    .last("LIMIT " + limit)
            );
            data.put("title", "隐私纪律风险证据");
            data.put("description", "近30天隐私相关治理事件");
            data.put("records", rows.stream().map(item -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.getId());
                row.put("severity", item.getSeverity());
                row.put("status", item.getStatus());
                row.put("title", item.getTitle());
                row.put("username", item.getUsername());
                row.put("eventTime", formatTime(item.getEventTime()));
                return row;
            }).collect(Collectors.toList()));
            return;
        }
        if ("behaviorStability".equals(dimension)) {
            List<GovernanceEvent> rows = governanceEventService.list(
                scopeThreatQuery(selection)
                    .eq("event_type", "ANOMALY_ALERT")
                    .select("id", "severity", "status", "title", "username", "event_time")
                    .orderByDesc("event_time")
                    .last("LIMIT " + limit)
            );
            data.put("title", "行为稳定性风险证据");
            data.put("description", "异常行为治理事件样本");
            data.put("records", rows.stream().map(item -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.getId());
                row.put("severity", item.getSeverity());
                row.put("status", item.getStatus());
                row.put("title", item.getTitle());
                row.put("username", item.getUsername());
                row.put("eventTime", formatTime(item.getEventTime()));
                return row;
            }).collect(Collectors.toList()));
            return;
        }
        if ("shadowAiExposure".equals(dimension)) {
            List<GovernanceEvent> rows = governanceEventService.list(
                scopeThreatQuery(selection)
                    .eq("event_type", "SHADOW_AI_ALERT")
                    .select("id", "severity", "status", "title", "username", "event_time")
                    .orderByDesc("event_time")
                    .last("LIMIT " + limit)
            );
            data.put("title", "影子AI暴露风险证据");
            data.put("description", "白名单外服务触发治理事件");
            data.put("records", rows.stream().map(item -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.getId());
                row.put("severity", item.getSeverity());
                row.put("status", item.getStatus());
                row.put("title", item.getTitle());
                row.put("username", item.getUsername());
                row.put("eventTime", formatTime(item.getEventTime()));
                return row;
            }).collect(Collectors.toList()));
            return;
        }
        if ("securityCompliance".equals(dimension)) {
            List<GovernanceEvent> rows = governanceEventService.list(
                scopeThreatQuery(selection)
                    .in("status", List.of("pending", "reviewing"))
                    .select("id", "event_type", "severity", "status", "title", "username", "event_time")
                    .orderByDesc("event_time")
                    .last("LIMIT " + limit)
            );
            data.put("title", "安全处置合规风险证据");
            data.put("description", "待处理/审查中的治理告警");
            data.put("records", rows.stream().map(item -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.getId());
                row.put("eventType", item.getEventType());
                row.put("severity", item.getSeverity());
                row.put("status", item.getStatus());
                row.put("title", item.getTitle());
                row.put("username", item.getUsername());
                row.put("eventTime", formatTime(item.getEventTime()));
                return row;
            }).collect(Collectors.toList()));
            return;
        }

        List<AiCallLog> rows = aiCallAuditService.list(
            scopeAiCallQuery(selection)
                .select("id", "username", "model_code", "status", "duration_ms", "create_time")
                .orderByDesc("duration_ms")
                .last("LIMIT " + limit)
        );
        data.put("title", "模型调用可靠性风险证据");
        data.put("description", "高延时与失败调用样本");
        data.put("records", rows.stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("username", item.getUsername());
            row.put("modelCode", item.getModelCode());
            row.put("status", item.getStatus());
            row.put("durationMs", item.getDurationMs());
            row.put("createTime", formatTime(item.getCreateTime()));
            return row;
        }).collect(Collectors.toList()));
    }

    private void fillPulseNodeDetail(Map<String, Object> data,
                                     ScopeSelection selection,
                                     String key,
                                     int limit) {
        String safeKey = String.valueOf(key == null ? "" : key).trim();
        String[] parts = safeKey.split(":", 2);
        if (parts.length != 2) {
            data.put("title", "脉冲节点明细");
            data.put("description", "节点标识无效");
            return;
        }

        String kind = parts[0].toLowerCase(Locale.ROOT);
        String rawId = parts[1];
        if ("asset".equals(kind)) {
            DataAsset item = dataAssetService.getOne(
                scopeAssetQuery(selection)
                    .eq("id", rawId)
                    .last("LIMIT 1")
            );
            if (item == null) {
                data.put("title", "资产节点明细");
                data.put("description", "目标资产不存在或超出权限范围");
                return;
            }
            data.put("title", "资产节点明细");
            data.put("description", "资产节点关联的近期待处置风险事件");
            List<GovernanceEvent> rows = governanceEventService.list(
                scopeThreatQuery(selection)
                    .eq("asset_id", item.getId())
                    .select("id", "event_type", "severity", "status", "title", "username", "event_time")
                    .orderByDesc("event_time")
                    .last("LIMIT " + limit)
            );
            data.put("records", rows.stream().map(event -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", event.getId());
                row.put("eventType", event.getEventType());
                row.put("severity", event.getSeverity());
                row.put("status", event.getStatus());
                row.put("title", event.getTitle());
                row.put("username", event.getUsername());
                row.put("eventTime", formatTime(event.getEventTime()));
                return row;
            }).collect(Collectors.toList()));
            return;
        }

        if ("event".equals(kind)) {
            GovernanceEvent item = governanceEventService.getOne(
                scopeThreatQuery(selection)
                    .eq("id", rawId)
                    .last("LIMIT 1")
            );
            data.put("title", "风险事件节点明细");
            data.put("description", "单事件完整视图");
            if (item == null) {
                data.put("records", List.of());
                return;
            }
            data.put("records", List.of(Map.of(
                "id", item.getId(),
                "eventType", String.valueOf(item.getEventType()),
                "severity", String.valueOf(item.getSeverity()),
                "status", String.valueOf(item.getStatus()),
                "title", String.valueOf(item.getTitle()),
                "username", String.valueOf(item.getUsername()),
                "eventTime", formatTime(item.getEventTime())
            )));
            return;
        }

        if ("ai".equals(kind)) {
            AiCallLog item = aiCallAuditService.getOne(
                scopeAiCallQuery(selection)
                    .eq("id", rawId)
                    .last("LIMIT 1")
            );
            data.put("title", "模型调用节点明细");
            data.put("description", "模型调用审计详情");
            if (item == null) {
                data.put("records", List.of());
                return;
            }
            data.put("records", List.of(Map.of(
                "id", item.getId(),
                "username", String.valueOf(item.getUsername()),
                "modelCode", String.valueOf(item.getModelCode()),
                "provider", String.valueOf(item.getProvider()),
                "status", String.valueOf(item.getStatus()),
                "durationMs", item.getDurationMs() == null ? 0L : item.getDurationMs(),
                "createTime", formatTime(item.getCreateTime())
            )));
            return;
        }

        if ("aggregate".equals(kind)) {
            String normalized = String.valueOf(rawId == null ? "" : rawId).toLowerCase(Locale.ROOT);
            if ("asset".equals(normalized) || "risk".equals(normalized) || "audit".equals(normalized) || "ai".equals(normalized)) {
                fillGraphNodeDetail(data, selection, normalized, limit);
                return;
            }
        }

        data.put("title", "脉冲节点明细");
        data.put("description", "不支持的节点类型");
    }

    private List<Map<String, Object>> buildAggregatePulseFallback(ScopeSelection selection) {
        List<Map<String, Object>> nodes = new ArrayList<>();
        long assetCount = dataAssetService.count(scopeAssetQuery(selection));
        long riskCount = governanceEventService.count(scopeThreatQuery(selection));
        long aiCount = aiCallAuditService.count(scopeAiCallQuery(selection));
        long auditCount = auditLogService.count(scopeAuditQuery(selection));

        nodes.add(aggregatePulseNode("asset", "资产聚合节点", "当前视角资产总量 " + assetCount, assetCount >= 50 ? "medium" : "low"));
        nodes.add(aggregatePulseNode("risk", "风险聚合节点", "当前视角风险总量 " + riskCount, riskCount >= 20 ? "high" : (riskCount >= 5 ? "medium" : "low")));
        nodes.add(aggregatePulseNode("ai", "调用聚合节点", "当前视角模型调用总量 " + aiCount, aiCount >= 100 ? "medium" : "low"));
        nodes.add(aggregatePulseNode("audit", "审计聚合节点", "当前视角审计总量 " + auditCount, auditCount >= 100 ? "medium" : "low"));
        return nodes;
    }

    private Map<String, Object> aggregatePulseNode(String code, String title, String subtitle, String riskLevel) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("id", "agg-" + code);
        node.put("kind", "aggregate");
        node.put("title", title);
        node.put("subtitle", subtitle);
        node.put("riskLevel", riskLevel);
        node.put("activity", "medium");
        node.put("eventTime", formatTime(new Date()));
        node.put("detailKind", "pulse-node");
        node.put("detailKey", "aggregate:" + code);
        return node;
    }

    private QueryWrapper<DataAsset> scopeAssetQuery(ScopeSelection selection) {
        QueryWrapper<DataAsset> query = new QueryWrapper<DataAsset>().eq("company_id", selection.companyId);
        if (!"company".equals(selection.scopeLevel)) {
            query.in("owner_id", selection.userIds);
        }
        return query;
    }

    private QueryWrapper<GovernanceEvent> scopeThreatQuery(ScopeSelection selection) {
        QueryWrapper<GovernanceEvent> query = new QueryWrapper<GovernanceEvent>()
            .eq("company_id", selection.companyId)
            .in("event_type", THREAT_TYPES);
        if (!"company".equals(selection.scopeLevel)) {
            query.in("user_id", selection.userIds);
        }
        return query;
    }

    private QueryWrapper<AiCallLog> scopeAiCallQuery(ScopeSelection selection) {
        QueryWrapper<AiCallLog> query = new QueryWrapper<AiCallLog>().eq("company_id", selection.companyId);
        if (!"company".equals(selection.scopeLevel)) {
            query.in("user_id", selection.userIds);
        }
        return query;
    }

    private QueryWrapper<AuditLog> scopeAuditQuery(ScopeSelection selection) {
        QueryWrapper<AuditLog> query = new QueryWrapper<>();
        if ("company".equals(selection.scopeLevel)) {
            query.in("user_id", selection.userMap.keySet());
        } else {
            query.in("user_id", selection.userIds);
        }
        return query;
    }

    private Map<String, Object> timelineRow(GovernanceEvent event) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", event.getId());
        row.put("title", StringUtils.hasText(event.getTitle()) ? event.getTitle() : String.valueOf(event.getEventType()));
        row.put("summary", StringUtils.hasText(event.getDescription()) ? event.getDescription() : "治理事件已入库");
        row.put("time", formatTime(event.getEventTime()));
        return row;
    }

    private String formatTime(Date value) {
        if (value == null) {
            return "-";
        }
        return value.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().format(TIME_FORMATTER);
    }

    private String formatTime(LocalDateTime value) {
        if (value == null) {
            return "-";
        }
        return value.format(TIME_FORMATTER);
    }

    private String normalizeScope(String input) {
        String value = String.valueOf(input == null ? "company" : input).toLowerCase(Locale.ROOT).trim();
        if ("department".equals(value) || "user".equals(value)) {
            return value;
        }
        return "company";
    }

    private String roleCodeOf(User user) {
        if (user == null || user.getRoleId() == null) {
            return "AUDIT";
        }
        String code = jdbcTemplate.query(
            "SELECT code FROM role WHERE id = ? LIMIT 1",
            (rs, rowNum) -> rs.getString(1),
            user.getRoleId()
        ).stream().findFirst().orElse("AUDIT");
        return String.valueOf(code == null ? "AUDIT" : code).toUpperCase(Locale.ROOT);
    }

    private boolean canUseCompanyDepartmentScope(User viewer) {
        if (viewer == null) {
            return false;
        }
        if (!Objects.equals(viewer.getCompanyId(), 1L)) {
            return false;
        }
        String role = roleCodeOf(viewer);
        return "ADMIN".equals(role) || currentUserService.hasAnyRole("ADMIN");
    }

    private String buildAnalysisPrompt(Map<String, Object> hub) {
        try {
            Map<String, Object> sanitizedHub = sanitizeForPrompt(hub);
            String hubJson = JSON.writerWithDefaultPrettyPrinter().writeValueAsString(sanitizedHub);
            return "你是AegisAI治理中枢分析助手。以下数据已做脱敏处理。请基于真实聚合数据输出简洁结论："
                + "1) 三条核心风险判断；2) 两条优先处置建议；3) 一条可量化追踪指标。"
                + "要求：严禁编造不存在字段，直接引用数据中的指标或事件。\\n\\n"
                + hubJson;
        } catch (Exception ex) {
            return "请基于当前治理中枢数据输出风险判断与处置建议。";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> sanitizeForPrompt(Map<String, Object> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Object cleaned = sanitizeValue(source, "root", 0);
        if (cleaned instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private Object sanitizeValue(Object value, String key, int depth) {
        if (value == null) {
            return null;
        }
        if (depth > 8) {
            return "[TRUNCATED]";
        }

        if (value instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String childKey = String.valueOf(entry.getKey() == null ? "" : entry.getKey());
                out.put(childKey, sanitizeValue(entry.getValue(), childKey, depth + 1));
            }
            return out;
        }

        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(sanitizeValue(item, key, depth + 1));
            }
            return out;
        }

        if (value instanceof String text) {
            String normalizedKey = String.valueOf(key == null ? "" : key).toLowerCase(Locale.ROOT);
            if (PROMPT_SENSITIVE_KEYS.contains(normalizedKey)) {
                return maskSensitiveScalar(text);
            }
            return redactInline(text);
        }

        if (value instanceof Number || value instanceof Boolean) {
            String normalizedKey = String.valueOf(key == null ? "" : key).toLowerCase(Locale.ROOT);
            if (PROMPT_SENSITIVE_KEYS.contains(normalizedKey)) {
                return "[REDACTED]";
            }
            return value;
        }

        return "[REDACTED]";
    }

    private String maskSensitiveScalar(String text) {
        if (!StringUtils.hasText(text)) {
            return "[REDACTED]";
        }
        String trimmed = text.trim();
        if (trimmed.length() <= 2) {
            return "**";
        }
        return trimmed.substring(0, 1) + "***" + trimmed.substring(trimmed.length() - 1);
    }

    private String redactInline(String text) {
        if (!StringUtils.hasText(text)) {
            return text;
        }
        String result = text;
        result = EMAIL_PATTERN.matcher(result).replaceAll("$1***@$2");
        result = PHONE_PATTERN.matcher(result).replaceAll("$1****$2");
        result = ID_CARD_PATTERN.matcher(result).replaceAll("$1********$2");
        result = BANK_CARD_PATTERN.matcher(result).replaceAll("$1********$2");
        return result;
    }

    private String runDeepseekAnalysis(String prompt) {
        try {
            ChatReq req = new ChatReq();
            req.setProvider("deepseek");
            req.setModel("deepseek-chat");
            Message userPrompt = new Message();
            userPrompt.setRole("user");
            userPrompt.setContent(prompt);
            req.setMessages(List.of(userPrompt));
            req.setAccessReason("home_ai_hub_analysis");

            Map<String, Object> response = aiGatewayService.chat(req);
            return extractAiRawText(response);
        } catch (AccessDeniedException denied) {
            throw denied;
        } catch (Exception ex) {
            throw new IllegalStateException("DeepSeek 分析调用失败: " + ex.getMessage(), ex);
        }
    }

    private String extractAiRawText(Map<String, Object> response) {
        String raw = String.valueOf(response == null ? "" : response.getOrDefault("raw", ""));
        if (!StringUtils.hasText(raw)) {
            return "模型未返回可解析内容";
        }
        try {
            JsonNode root = JSON.readTree(raw);
            JsonNode text = root.path("choices").path(0).path("message").path("content");
            if (text.isTextual() && StringUtils.hasText(text.asText())) {
                return text.asText();
            }
        } catch (Exception ignore) {
        }
        return raw;
    }

    private String buildLocalFallbackAnalysis(Map<String, Object> hub, String cause) {
        List<Map<String, Object>> kpis = castListMap(hub.get("kpis"));
        String risk = kpiValue(kpis, "riskEvents");
        String pending = String.valueOf(castMap(hub.get("alertBoard")).getOrDefault("pendingCount", 0));
        String score = kpiValue(kpis, "governanceScore");
        return "核心判断：当前风险事件总量 " + risk + "，待处置告警 " + pending + "，治理脉冲分 " + score + "。"
            + "建议优先收敛待处置与高风险链路，并在下一周期对比脉冲分变化。"
            + (StringUtils.hasText(cause) ? (" 失败原因: " + cause) : "");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castListMap(Object value) {
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return Map.of();
    }

    private String kpiValue(List<Map<String, Object>> kpis, String key) {
        return kpis.stream()
            .filter(item -> key.equals(String.valueOf(item.get("key"))))
            .map(item -> String.valueOf(item.getOrDefault("value", 0)))
            .findFirst()
            .orElse("0");
    }

    private boolean isPrivilegedViewer() {
        return currentUserService.hasAnyRole(PRIVILEGED_ROLES.toArray(new String[0]));
    }

    private int severityRank(String riskLevel) {
        String normalized = String.valueOf(riskLevel == null ? "" : riskLevel).toLowerCase(Locale.ROOT);
        if ("critical".equals(normalized) || "high".equals(normalized)) {
            return 3;
        }
        if ("medium".equals(normalized)) {
            return 2;
        }
        return 1;
    }

    private Map<String, Object> kpi(String key, String label, Object value, String note) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("key", key);
        row.put("label", label);
        row.put("value", value);
        row.put("note", note);
        return row;
    }

    private Map<String, Object> node(String id, String label, Object value, String color) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("label", label);
        row.put("value", value);
        row.put("color", color);
        return row;
    }

    private Map<String, Object> edge(String source, String target, Object value, String color) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("source", source);
        row.put("target", target);
        row.put("value", value);
        row.put("color", color);
        return row;
    }

    private Map<String, Object> radar(String code, String label, int value) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("code", code);
        row.put("label", label);
        row.put("value", value);
        return row;
    }

    private Object findRadarValue(List<Map<String, Object>> dimensions, String code) {
        return dimensions.stream()
            .filter(item -> code.equals(String.valueOf(item.get("code"))))
            .map(item -> item.get("value"))
            .findFirst()
            .orElse(0);
    }

    private int intOf(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ex) {
            return 0;
        }
    }

    private int clamp(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private int edgeWeight(double value) {
        return Math.max(1, Math.min(9, (int) Math.round(value)));
    }

    private double percent(long part, long total) {
        if (total <= 0) {
            return 0D;
        }
        return part * 100D / total;
    }

    private long percentile(List<Long> sorted, int p) {
        if (sorted == null || sorted.isEmpty()) {
            return 0L;
        }
        int safeP = Math.max(1, Math.min(100, p));
        int index = (int) Math.ceil(safeP / 100D * sorted.size()) - 1;
        index = Math.max(0, Math.min(sorted.size() - 1, index));
        return sorted.get(index);
    }

    private boolean isPendingOrReviewing(Object status) {
        String normalized = String.valueOf(status == null ? "" : status).toLowerCase(Locale.ROOT);
        return "pending".equals(normalized) || "reviewing".equals(normalized);
    }

    private boolean isHighSeverity(Object severity) {
        String normalized = String.valueOf(severity == null ? "" : severity).toLowerCase(Locale.ROOT);
        return "high".equals(normalized) || "critical".equals(normalized);
    }

    private boolean isFailureStatus(Object status) {
        String normalized = String.valueOf(status == null ? "" : status).toLowerCase(Locale.ROOT);
        return !("success".equals(normalized) || "ok".equals(normalized));
    }

    private LocalDateTime toLocalDateTime(Date date) {
        if (date == null) {
            return LocalDateTime.MIN;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    private Map<String, Object> recommend(String title,
                                          String priority,
                                          String action,
                                          String targetLabel,
                                          String route,
                                          Map<String, Object> query) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("title", title);
        row.put("priority", priority);
        row.put("action", action);
        row.put("targetLabel", targetLabel);
        row.put("route", route);
        row.put("query", query);
        return row;
    }

    private static final class ScopeSelection {
        private final Long companyId;
        private final String scopeLevel;
        private final String department;
        private final String username;
        private final List<Long> userIds;
        private final Map<Long, User> userMap;

        private ScopeSelection(Long companyId,
                               String scopeLevel,
                               String department,
                               String username,
                               List<Long> userIds,
                               Map<Long, User> userMap) {
            this.companyId = companyId;
            this.scopeLevel = scopeLevel;
            this.department = department;
            this.username = username;
            this.userIds = userIds;
            this.userMap = userMap;
        }
    }
}
