package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.dto.dashboard.GovernanceInsightDTO;
import com.trustai.dto.dashboard.TrustPulseDTO;
import com.trustai.dto.dashboard.WorkbenchOverviewDTO;
import com.trustai.entity.AiModel;
import com.trustai.entity.AuditLog;
import com.trustai.entity.ApprovalRequest;
import com.trustai.entity.DataAsset;
import com.trustai.entity.GovernanceEvent;
import com.trustai.entity.ModelCallStat;
import com.trustai.entity.Role;
import com.trustai.entity.RiskEvent;
import com.trustai.entity.SubjectRequest;
import com.trustai.entity.User;
import com.trustai.service.*;
import com.trustai.utils.R;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("isAuthenticated()")
public class DashboardController {
    @Autowired private DataAssetService dataAssetService;
    @Autowired private AiModelService aiModelService;
    @Autowired private UserService userService;
    @Autowired private RiskEventService riskEventService;
    @Autowired private SubjectRequestService subjectRequestService;
    @Autowired private AuditLogService auditLogService;
    @Autowired private ModelCallStatService modelCallStatService;
    @Autowired private ApprovalRequestService approvalRequestService;
    @Autowired private CurrentUserService currentUserService;
    @Autowired private CompanyScopeService companyScopeService;
    @Autowired private RiskForecastScheduler riskForecastScheduler;
    @Autowired private AiCallAuditService aiCallAuditService;
    @Autowired private GovernanceEventService governanceEventService;

    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        currentUserService.requireCurrentUser();
        Long companyId = companyScopeService.requireCompanyId();
        Map<String, Object> map = new HashMap<>();
        map.put("dataAsset", dataAssetService.count(new QueryWrapper<DataAsset>().eq("company_id", companyId)));
        map.put("aiModel", aiModelService.count(new QueryWrapper<AiModel>().eq(companyId != null, "company_id", companyId)));
        map.put("user", userService.count(new QueryWrapper<User>().eq("company_id", companyId)));
        map.put("riskEvent", riskEventService.count(new QueryWrapper<RiskEvent>().eq("company_id", companyId)));
        return R.ok(map);
    }

    @GetMapping("/risk-forecast")
    public R<List<Double>> riskForecast() {
        return R.ok(riskForecastScheduler.getLatest().forecast);
    }

    @GetMapping("/workbench")
    public R<WorkbenchOverviewDTO> workbench(@RequestParam(defaultValue = "7") int days) {
        User currentUser = currentUserService.requireCurrentUser();
        Role currentRole = currentUserService.getCurrentRole(currentUser);
        Long companyId = companyScopeService.requireCompanyId();
        List<Long> companyUserIds = companyScopeService.companyUserIds();
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate today = LocalDate.now();
        int safeDays = Math.max(1, Math.min(30, days));
        LocalDate last7Start = today.minusDays(safeDays - 1L);
        LocalDate previous7Start = today.minusDays((safeDays * 2L) - 1L);

        Date last7Date = Date.from(last7Start.atStartOfDay(zoneId).toInstant());
        Date previous7Date = Date.from(previous7Start.atStartOfDay(zoneId).toInstant());
        Date todayDate = Date.from(today.atStartOfDay(zoneId).toInstant());
        Date yesterdayDate = Date.from(today.minusDays(1).atStartOfDay(zoneId).toInstant());

        List<RiskEvent> recentRiskEvents = riskEventService.list(
            new QueryWrapper<RiskEvent>()
                .select("id", "type", "level", "status", "process_log", "create_time")
                .eq("company_id", companyId)
                .ge("create_time", previous7Date)
                .orderByDesc("create_time")
                .last("LIMIT 5000")
        );
            List<GovernanceEvent> recentGovernanceEvents = loadRecentGovernanceEvents(companyId, previous7Date);
            Map<String, Object> monitorCaliber = buildGovernanceMonitorCaliber(recentGovernanceEvents, last7Start, previous7Start);
        QueryWrapper<AuditLog> auditQuery = new QueryWrapper<AuditLog>()
            .select("id", "user_id", "operation_time")
            .ge("operation_time", previous7Date)
            .last("LIMIT 10000");
        if (!companyUserIds.isEmpty()) {
            auditQuery.in("user_id", companyUserIds);
        }
        List<AuditLog> recentAuditLogs = auditLogService.list(auditQuery);
        QueryWrapper<ModelCallStat> modelStatQuery = new QueryWrapper<ModelCallStat>()
            .select("id", "user_id", "date", "call_count", "cost_cents")
            .ge("date", previous7Date)
            .last("LIMIT 10000");
        if (!companyUserIds.isEmpty()) {
            modelStatQuery.in("user_id", companyUserIds);
        }
        List<ModelCallStat> recentModelStats = modelCallStatService.list(modelStatQuery);
        QueryWrapper<SubjectRequest> pendingSubjectQuery = new QueryWrapper<SubjectRequest>()
            .in("status", Arrays.asList("pending", "processing"));
        if (!companyUserIds.isEmpty()) {
            pendingSubjectQuery.in("user_id", companyUserIds);
        }
        long pendingSubjectRequests = subjectRequestService.count(pendingSubjectQuery);
        QueryWrapper<SubjectRequest> subjectFeedQuery = new QueryWrapper<SubjectRequest>()
            .select("id", "type", "status", "comment", "create_time")
            .orderByDesc("create_time")
            .last("LIMIT 500");
        if (!companyUserIds.isEmpty()) {
            subjectFeedQuery.in("user_id", companyUserIds);
        }
        List<SubjectRequest> recentSubjectRequests = subjectRequestService.list(subjectFeedQuery);

        long highSensitivityAssets = dataAssetService.count(
            new QueryWrapper<DataAsset>()
                .eq("company_id", companyId)
                .in("sensitivity_level", Arrays.asList("high", "critical", "HIGH", "CRITICAL", "高", "高敏"))
        );
        long newHighSensitivityAssets = dataAssetService.count(
            new QueryWrapper<DataAsset>()
                .eq("company_id", companyId)
                .in("sensitivity_level", Arrays.asList("high", "critical", "HIGH", "CRITICAL", "高", "高敏"))
                .ge("create_time", last7Date)
        );
        long previousHighSensitivityAssets = dataAssetService.count(
            new QueryWrapper<DataAsset>()
                .eq("company_id", companyId)
                .in("sensitivity_level", Arrays.asList("high", "critical", "HIGH", "CRITICAL", "高", "高敏"))
                .between("create_time", previous7Date, last7Date)
        );
        long openAlerts = asLong(monitorCaliber.get("pendingTotal"));
        long recentOpenAlerts = asLong(monitorCaliber.get("pendingLast7"));
        long previousOpenAlerts = asLong(monitorCaliber.get("pendingPrevious7"));
        long aiCallsLast7 = recentModelStats.stream()
            .filter(item -> !toLocalDate(item.getDate()).isBefore(last7Start))
            .mapToLong(item -> item.getCallCount() == null ? 0L : item.getCallCount())
            .sum();
        long aiCallsPrevious7 = recentModelStats.stream()
            .filter(item -> {
                LocalDate date = toLocalDate(item.getDate());
                return !date.isBefore(previous7Start) && date.isBefore(last7Start);
            })
            .mapToLong(item -> item.getCallCount() == null ? 0L : item.getCallCount())
            .sum();
        long todayAuditCount = recentAuditLogs.stream()
            .filter(item -> item.getOperationTime() != null && !item.getOperationTime().before(todayDate))
            .count();
        long yesterdayAuditCount = recentAuditLogs.stream()
            .filter(item -> item.getOperationTime() != null && !item.getOperationTime().before(yesterdayDate) && item.getOperationTime().before(todayDate))
            .count();
        long highRiskEvents = recentRiskEvents.stream()
            .filter(item -> Arrays.asList("high", "critical", "高").contains(normalizeLower(item.getLevel())))
            .count();
        long enabledModels = aiModelService.count(new QueryWrapper<AiModel>()
            .eq(companyId != null, "company_id", companyId)
            .eq("status", "enabled"));

        WorkbenchOverviewDTO dto = new WorkbenchOverviewDTO();
        dto.setOperator(new WorkbenchOverviewDTO.Operator(
            safeUserName(currentUser),
            currentRole == null ? "未分配身份" : currentRole.getName(),
            currentUser.getDepartment() == null ? "可信AI治理中心" : currentUser.getDepartment(),
            currentUser.getAvatar()
        ));
        dto.setHeadline("可信AI数据治理与隐私合规工作台");
        dto.setSubheadline(String.format(
            "%s视角下，平台正在对 %d 项高敏资产、%d 个启用模型、%d 条待闭环风险与 %d 个主体工单进行统一编排。",
            currentRole == null ? "运营" : currentRole.getName(),
            highSensitivityAssets,
            enabledModels,
            highRiskEvents,
            pendingSubjectRequests
        ));
        dto.setSceneTags(Arrays.asList("审计证据链", "高敏资产纳管", "模型分级准入", "主体权利履约"));
        dto.setMetrics(buildMetrics(highSensitivityAssets, newHighSensitivityAssets, previousHighSensitivityAssets, openAlerts, recentOpenAlerts, previousOpenAlerts, aiCallsLast7, aiCallsPrevious7, todayAuditCount, yesterdayAuditCount, safeDays));
        dto.setTrend(buildTrend(last7Start, recentRiskEvents, recentAuditLogs, recentModelStats, safeDays));
        dto.setRiskDistribution(buildRiskDistribution(recentRiskEvents));
        dto.setTodos(buildTodos(highRiskEvents, openAlerts, pendingSubjectRequests, enabledModels));
        dto.setFeeds(buildFeeds(recentRiskEvents, recentSubjectRequests));
        return R.ok(dto);
    }

    @GetMapping("/home-bundle")
    public R<Map<String, Object>> homeBundle() {
        User currentUser = currentUserService.requireCurrentUser();
        Long companyId = companyScopeService.requireCompanyId();
        List<Long> companyUserIds = companyScopeService.companyUserIds();
        LocalDate today = LocalDate.now();
        LocalDate last7Start = today.minusDays(6);
        LocalDate previous7Start = today.minusDays(13);
        Date previous7Date = Date.from(previous7Start.atStartOfDay(ZoneId.systemDefault()).toInstant());
        Map<String, Object> monitorCaliber = buildGovernanceMonitorCaliber(
            loadRecentGovernanceEvents(companyId, previous7Date),
            last7Start,
            previous7Start
        );
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("workbench", workbench(7).getData());
        bundle.put("insights", insights().getData());
        bundle.put("trustPulse", trustPulse().getData());
        bundle.put("forecast", riskForecastScheduler.getLatest());
        Map<String, Object> traceContext = new LinkedHashMap<>();
        traceContext.put("companyId", companyId);
        traceContext.put("companyUserCount", companyUserIds.size());
        traceContext.put("currentUserId", currentUser.getId());
        traceContext.put("currentUsername", currentUser.getUsername());
        traceContext.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        traceContext.put("windowDays", 7);
        traceContext.put("traceabilityStatement", "首页指标按公司与账号范围聚合，支持下钻到账号与上传数据记录");
        traceContext.put("monitorCaliber", monitorCaliber.get("caliber"));
        traceContext.put("monitorAnomaly", asLong(monitorCaliber.get("anomaly")));
        traceContext.put("monitorPrivacy", asLong(monitorCaliber.get("privacy")));
        traceContext.put("monitorPending", asLong(monitorCaliber.get("pendingTotal")));
        traceContext.put("monitorDuplicateCollapsed", asLong(monitorCaliber.get("duplicateCollapsed")));
        traceContext.put("monitorCaliberNote",
            String.format("统一口径：治理事件去重后统计（异常 %d、隐私 %d、待处置 %d，去重压缩 %d）。与员工AI行为监控页口径一致。",
                asLong(monitorCaliber.get("anomaly")),
                asLong(monitorCaliber.get("privacy")),
                asLong(monitorCaliber.get("pendingTotal")),
                asLong(monitorCaliber.get("duplicateCollapsed"))
            )
        );
        bundle.put("traceContext", traceContext);
        bundle.put("traceModules", buildTraceModules(companyId, companyUserIds));
        return R.ok(bundle);
    }

    @GetMapping("/trace/drilldown")
    public R<Map<String, Object>> traceDrilldown(@RequestParam(defaultValue = "risk-events") String module,
                                                 @RequestParam(defaultValue = "20") int limit) {
        currentUserService.requireCurrentUser();
        Long companyId = companyScopeService.requireCompanyId();
        List<Long> companyUserIds = companyScopeService.companyUserIds();
        int safeLimit = Math.max(1, Math.min(100, limit));
        Map<Long, String> usernameMap = loadUsernameMap(companyUserIds);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("module", module);
        payload.put("companyId", companyId);
        payload.put("limit", safeLimit);

        if ("ai-audit".equalsIgnoreCase(module)) {
            QueryWrapper<com.trustai.entity.AiCallLog> query = new QueryWrapper<com.trustai.entity.AiCallLog>()
                .eq("company_id", companyId)
                .orderByDesc("create_time")
                .last("LIMIT " + safeLimit);
            List<com.trustai.entity.AiCallLog> logs = aiCallAuditService.list(query);
            List<Map<String, Object>> records = logs.stream().map(item -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.getId());
                row.put("userId", item.getUserId());
                row.put("username", item.getUsername());
                row.put("companyId", item.getCompanyId());
                row.put("dataAssetId", item.getDataAssetId());
                row.put("modelCode", item.getModelCode());
                row.put("provider", item.getProvider());
                row.put("status", item.getStatus());
                row.put("durationMs", item.getDurationMs());
                row.put("tokenUsage", item.getTokenUsage());
                row.put("createTime", item.getCreateTime());
                return row;
            }).collect(Collectors.toList());
            payload.put("records", records);
            payload.put("traceRule", "按 company_id + user_id + data_asset_id 回溯 AI 调用");
            return R.ok(payload);
        }

        if ("uploads".equalsIgnoreCase(module)) {
            List<DataAsset> assets = dataAssetService.list(new QueryWrapper<DataAsset>()
                .eq("company_id", companyId)
                .orderByDesc("create_time")
                .last("LIMIT " + safeLimit));
            List<Map<String, Object>> records = assets.stream().map(item -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.getId());
                row.put("name", item.getName());
                row.put("type", item.getType());
                row.put("sensitivityLevel", item.getSensitivityLevel());
                row.put("ownerId", item.getOwnerId());
                row.put("ownerName", usernameMap.getOrDefault(item.getOwnerId(), "-"));
                row.put("createTime", item.getCreateTime());
                return row;
            }).collect(Collectors.toList());
            payload.put("records", records);
            payload.put("traceRule", "按 company_id + owner_id 回溯上传数据资产");
            return R.ok(payload);
        }

        if ("approvals".equalsIgnoreCase(module)) {
            QueryWrapper<ApprovalRequest> query = new QueryWrapper<ApprovalRequest>()
                .eq("company_id", companyId)
                .orderByDesc("create_time")
                .last("LIMIT " + safeLimit);
            List<ApprovalRequest> approvals = approvalRequestService.list(query);
            List<Map<String, Object>> records = approvals.stream().map(item -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", item.getId());
                row.put("applicantId", item.getApplicantId());
                row.put("applicantName", usernameMap.getOrDefault(item.getApplicantId(), "-"));
                row.put("assetId", item.getAssetId());
                row.put("status", item.getStatus());
                row.put("reason", item.getReason());
                row.put("createTime", item.getCreateTime());
                return row;
            }).collect(Collectors.toList());
            payload.put("records", records);
            payload.put("traceRule", "按 company_id + applicant_id + asset_id 回溯审批链路");
            return R.ok(payload);
        }

        QueryWrapper<RiskEvent> query = new QueryWrapper<RiskEvent>()
            .eq("company_id", companyId)
            .orderByDesc("create_time")
            .last("LIMIT " + safeLimit);
        List<RiskEvent> events = riskEventService.list(query);
        List<Map<String, Object>> records = events.stream().map(item -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", item.getId());
            row.put("type", item.getType());
            row.put("level", item.getLevel());
            row.put("status", item.getStatus());
            row.put("relatedLogId", item.getRelatedLogId());
            row.put("handlerId", item.getHandlerId());
            row.put("handlerName", usernameMap.getOrDefault(item.getHandlerId(), "-"));
            row.put("createTime", item.getCreateTime());
            return row;
        }).collect(Collectors.toList());
        payload.put("records", records);
        payload.put("traceRule", "按 company_id + handler_id + related_log_id 回溯风险闭环");
        return R.ok(payload);
    }

    private List<GovernanceEvent> loadRecentGovernanceEvents(Long companyId, Date sinceDate) {
        return governanceEventService.list(
            new QueryWrapper<GovernanceEvent>()
                .select("id", "event_type", "source_module", "source_event_id", "status", "severity", "user_id", "title", "event_time", "create_time")
                .eq("company_id", companyId)
                .ge("create_time", sinceDate)
                .orderByDesc("event_time")
                .last("LIMIT 10000")
        );
    }

    private Map<String, Object> buildGovernanceMonitorCaliber(List<GovernanceEvent> events,
                                                               LocalDate last7Start,
                                                               LocalDate previous7Start) {
        List<GovernanceEvent> alertEvents = events.stream()
            .filter(this::isAlertEvent)
            .collect(Collectors.toList());

        Map<String, GovernanceEvent> deduped = new LinkedHashMap<>();
        for (GovernanceEvent item : alertEvents) {
            String key = governanceChainKey(item);
            GovernanceEvent current = deduped.get(key);
            if (current == null || isGovernanceEventNewer(item, current)) {
                deduped.put(key, item);
            }
        }

        long pendingTotal = 0L;
        long pendingLast7 = 0L;
        long pendingPrevious7 = 0L;
        long anomaly = 0L;
        long privacy = 0L;

        for (GovernanceEvent item : deduped.values()) {
            String eventType = normalizeLower(item.getEventType());
            if ("anomaly_alert".equals(eventType)) {
                anomaly++;
            } else if ("privacy_alert".equals(eventType)) {
                privacy++;
            }

            String status = normalizeLower(item.getStatus());
            if ("pending".equals(status) || "reviewing".equals(status)) {
                pendingTotal++;
                LocalDate date = toLocalDate(resolveGovernanceAnchor(item));
                if (!date.isBefore(last7Start)) {
                    pendingLast7++;
                } else if (!date.isBefore(previous7Start) && date.isBefore(last7Start)) {
                    pendingPrevious7++;
                }
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("caliber", "governance_event_dedup_chain_v1");
        result.put("anomaly", anomaly);
        result.put("privacy", privacy);
        result.put("pendingTotal", pendingTotal);
        result.put("pendingLast7", pendingLast7);
        result.put("pendingPrevious7", pendingPrevious7);
        result.put("rawTotal", (long) alertEvents.size());
        result.put("uniqueTotal", (long) deduped.size());
        result.put("duplicateCollapsed", Math.max(0L, (long) alertEvents.size() - deduped.size()));
        return result;
    }

    private boolean isAlertEvent(GovernanceEvent event) {
        String type = normalizeLower(event.getEventType());
        return "privacy_alert".equals(type)
            || "anomaly_alert".equals(type)
            || "shadow_ai_alert".equals(type)
            || "security_alert".equals(type);
    }

    private String governanceChainKey(GovernanceEvent event) {
        String sourceModule = normalizeLower(event.getSourceModule());
        String sourceEventId = event.getSourceEventId() == null ? "" : event.getSourceEventId().trim();
        if (!sourceModule.isBlank() && !sourceEventId.isBlank()) {
            return sourceModule + "::" + sourceEventId;
        }
        LocalDateTime time = resolveGovernanceAnchor(event).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        return String.join("::",
            normalizeLower(event.getEventType()),
            String.valueOf(event.getUserId() == null ? 0L : event.getUserId()),
            defaultText(event.getTitle(), "-"),
            String.valueOf(time.getYear()),
            String.valueOf(time.getMonthValue()),
            String.valueOf(time.getDayOfMonth()),
            String.valueOf(time.getHour()),
            String.valueOf(time.getMinute())
        );
    }

    private boolean isGovernanceEventNewer(GovernanceEvent left, GovernanceEvent right) {
        Date l = resolveGovernanceAnchor(left);
        Date r = resolveGovernanceAnchor(right);
        if (l.after(r)) {
            return true;
        }
        if (l.before(r)) {
            return false;
        }
        long leftId = left.getId() == null ? 0L : left.getId();
        long rightId = right.getId() == null ? 0L : right.getId();
        return leftId > rightId;
    }

    private Date resolveGovernanceAnchor(GovernanceEvent item) {
        if (item.getEventTime() != null) {
            return item.getEventTime();
        }
        if (item.getCreateTime() != null) {
            return item.getCreateTime();
        }
        return new Date(0L);
    }

    private long asLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private Map<String, Object> buildTraceModules(Long companyId, List<Long> companyUserIds) {
        Map<String, Object> modules = new LinkedHashMap<>();
        modules.put("risk-events", Map.of(
            "label", "风险事件",
            "traceRule", "company_id + handler_id + related_log_id",
            "count", riskEventService.count(new QueryWrapper<RiskEvent>().eq("company_id", companyId))
        ));
        modules.put("uploads", Map.of(
            "label", "上传数据资产",
            "traceRule", "company_id + owner_id",
            "count", dataAssetService.count(new QueryWrapper<DataAsset>().eq("company_id", companyId))
        ));
        modules.put("approvals", Map.of(
            "label", "审批流转",
            "traceRule", "company_id + applicant_id + asset_id",
            "count", approvalRequestService.count(new QueryWrapper<ApprovalRequest>().eq("company_id", companyId))
        ));
        modules.put("ai-audit", Map.of(
            "label", "AI 调用审计",
            "traceRule", "company_id + user_id + data_asset_id",
            "count", aiCallAuditService.count(new QueryWrapper<com.trustai.entity.AiCallLog>().eq("company_id", companyId))
        ));
        modules.put("userScope", companyUserIds.size());
        return modules;
    }

    private Map<Long, String> loadUsernameMap(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        Set<Long> unique = new HashSet<>(userIds);
        List<User> users = userService.list(new QueryWrapper<User>().in("id", unique));
        return users.stream().collect(Collectors.toMap(User::getId, this::safeUserName, (a, b) -> a));
    }

        @GetMapping("/insights")
        public R<GovernanceInsightDTO> insights() {
        currentUserService.requireCurrentUser();
        long highSensitivityAssets = dataAssetService.count(
            new QueryWrapper<DataAsset>()
                .eq("company_id", companyScopeService.requireCompanyId())
                .in("sensitivity_level", Arrays.asList("high", "critical", "HIGH", "CRITICAL", "高", "高敏"))
        );
        List<Long> companyUserIds = companyScopeService.companyUserIds();
        long openRiskEvents = riskEventService.count(
            new QueryWrapper<RiskEvent>().eq("company_id", companyScopeService.requireCompanyId()).eq("status", "open")
        );
        long highRiskEvents = riskEventService.count(
            new QueryWrapper<RiskEvent>().eq("company_id", companyScopeService.requireCompanyId()).eq("status", "open").in("level", Arrays.asList("high", "HIGH", "critical", "CRITICAL", "高"))
        );
        long highRiskModels = aiModelService.count(
            new QueryWrapper<AiModel>()
                .eq("company_id", companyScopeService.requireCompanyId())
                .eq("status", "enabled")
                .in("risk_level", Arrays.asList("high", "HIGH", "高"))
        );
        long pendingSubjectRequests = subjectRequestService.count(
            new QueryWrapper<SubjectRequest>().in("status", Arrays.asList("pending", "processing")).in(!companyUserIds.isEmpty(), "user_id", companyUserIds)
        );

        LocalDate today = LocalDate.now();
        long todayAuditCount = auditLogService.count(
            new QueryWrapper<AuditLog>()
                .ge("operation_time", java.util.Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant()))
                .in(!companyUserIds.isEmpty(), "user_id", companyUserIds)
        );

        Date last30DaysDate = Date.from(today.minusDays(29).atStartOfDay(ZoneId.systemDefault()).toInstant());
        List<ModelCallStat> modelStats = modelCallStatService.list(
            new QueryWrapper<ModelCallStat>()
                .select("id", "user_id", "date", "call_count", "cost_cents")
                .ge("date", last30DaysDate)
                .in(!companyUserIds.isEmpty(), "user_id", companyUserIds)
                .last("LIMIT 20000")
        );
        long totalAiCalls = modelStats.stream().mapToLong(item -> item.getCallCount() == null ? 0L : item.getCallCount()).sum();
        long totalCostCents = modelStats.stream().mapToLong(item -> item.getCostCents() == null ? 0L : item.getCostCents()).sum();

        GovernanceInsightDTO dto = new GovernanceInsightDTO();
        dto.setPostureScore(calculatePostureScore(highSensitivityAssets, openRiskEvents, highRiskEvents, pendingSubjectRequests, todayAuditCount));
        dto.setSummary(new GovernanceInsightDTO.Summary(
            highSensitivityAssets,
            openRiskEvents,
            highRiskEvents,
            highRiskModels,
            pendingSubjectRequests,
            todayAuditCount,
            totalAiCalls,
            totalCostCents
        ));
        dto.setHighlights(buildHighlights(highSensitivityAssets, highRiskModels, todayAuditCount, totalAiCalls, totalCostCents));
        dto.setRecommendations(buildRecommendations(highSensitivityAssets, openRiskEvents, highRiskEvents, pendingSubjectRequests, todayAuditCount, highRiskModels, totalCostCents));
        return R.ok(dto);
        }

        @GetMapping("/trust-pulse")
        public R<TrustPulseDTO> trustPulse() {
        User currentUser = currentUserService.requireCurrentUser();
        Role currentRole = currentUserService.getCurrentRole(currentUser);
            Long companyId = companyScopeService.requireCompanyId();
            List<Long> companyUserIds = companyScopeService.companyUserIds();
        long highSensitivityAssets = dataAssetService.count(
                new QueryWrapper<DataAsset>().eq("company_id", companyId).in("sensitivity_level", Arrays.asList("high", "critical", "HIGH", "CRITICAL", "高", "高敏"))
        );
            long openRiskEvents = riskEventService.count(new QueryWrapper<RiskEvent>().eq("company_id", companyId).eq("status", "open"));
        long highRiskEvents = riskEventService.count(
                new QueryWrapper<RiskEvent>().eq("company_id", companyId).eq("status", "open").in("level", Arrays.asList("high", "HIGH", "critical", "CRITICAL", "高"))
        );
        long enabledModels = aiModelService.count(new QueryWrapper<AiModel>()
            .eq("company_id", companyId)
            .eq("status", "enabled"));
        long highRiskModels = aiModelService.count(
            new QueryWrapper<AiModel>()
                .eq("company_id", companyId)
                .eq("status", "enabled")
                .in("risk_level", Arrays.asList("high", "HIGH", "高"))
        );
        long pendingSubjectRequests = subjectRequestService.count(
            new QueryWrapper<SubjectRequest>().in("status", Arrays.asList("pending", "processing")).in(!companyUserIds.isEmpty(), "user_id", companyUserIds)
        );
        long pendingApprovals = countPendingApprovals(companyUserIds);
        LocalDate today = LocalDate.now();
        long todayAuditCount = auditLogService.count(
            new QueryWrapper<AuditLog>()
            .ge("operation_time", java.util.Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant()))
                .in(!companyUserIds.isEmpty(), "user_id", companyUserIds)
        );
        TrustPulseDTO dto = new TrustPulseDTO();
        int dataScore = clampScore(100 - (int) Math.min(48, highSensitivityAssets * 5 + openRiskEvents * 3));
        int modelScore = clampScore(100 - (int) Math.min(52, highRiskModels * 10 + Math.max(0, enabledModels - 10) * 2));
        int processScore = clampScore(100 - (int) Math.min(46, pendingSubjectRequests * 8 + pendingApprovals * 6));
        int auditScore = clampScore(68 + (int) Math.min(24, todayAuditCount));
        int totalScore = Math.round((dataScore + modelScore + processScore + auditScore) / 4f);

        dto.setScore(totalScore);
        dto.setPulseLevel(resolvePulseLevel(totalScore));
        dto.setMission(buildPulseMission(currentRole == null ? null : currentRole.getCode(), totalScore, highRiskEvents, pendingSubjectRequests));
        dto.setInnovationLabel("治理脉冲引擎 · Governance Pulse Engine");
        dto.setDimensions(Arrays.asList(
            new TrustPulseDTO.Dimension("data", "数据边界", dataScore, highSensitivityAssets + " 项高敏资产正在纳管，持续映射共享与脱敏压力。"),
            new TrustPulseDTO.Dimension("model", "模型可信", modelScore, highRiskModels + " 个高风险模型处于受控接入，跟踪额度与状态门禁。"),
            new TrustPulseDTO.Dimension("process", "流程闭环", processScore, pendingApprovals + " 个审批节点与 " + pendingSubjectRequests + " 个主体工单共同决定履约效率。"),
            new TrustPulseDTO.Dimension("audit", "审计准备度", auditScore, todayAuditCount + " 条当日留痕决定监管抽查时的可追溯性。")
        ));
        dto.setSignals(Arrays.asList(
            new TrustPulseDTO.Signal("高危风险", String.valueOf(highRiskEvents), highRiskEvents > 0 ? "danger" : "safe", "优先压降高危事件"),
            new TrustPulseDTO.Signal("模型接入", enabledModels + " / " + highRiskModels, highRiskModels > 0 ? "warning" : "safe", "检查模型额度与准入条件"),
            new TrustPulseDTO.Signal("流程积压", String.valueOf(pendingApprovals + pendingSubjectRequests), pendingApprovals + pendingSubjectRequests > 0 ? "warning" : "safe", "缩短共享审批与主体履约时延")
        ));
        return R.ok(dto);
        }

        private int calculatePostureScore(long highSensitivityAssets,
                          long openRiskEvents,
                          long highRiskEvents,
                          long pendingSubjectRequests,
                          long todayAuditCount) {
        int score = 100;
        score -= Math.min(28, (int) highSensitivityAssets * 6);
        score -= Math.min(30, (int) highRiskEvents * 8 + Math.max(0, (int) (openRiskEvents - highRiskEvents)) * 3);
        score -= Math.min(16, (int) pendingSubjectRequests * 4);
        if (todayAuditCount == 0) {
            score -= 10;
        }
        return Math.max(18, score);
        }

        private int clampScore(int score) {
        return Math.max(18, Math.min(100, score));
        }

        private long countPendingApprovals(List<Long> companyUserIds) {
        return approvalRequestService.count(
            new QueryWrapper<ApprovalRequest>()
                .in("status", Arrays.asList("待审批", "pending", "processing"))
                .in(!companyUserIds.isEmpty(), "applicant_id", companyUserIds)
        );
        }

        private String resolvePulseLevel(int score) {
        if (score >= 85) {
            return "稳态领航";
        }
        if (score >= 70) {
            return "可控推进";
        }
        if (score >= 55) {
            return "高压编排";
        }
        return "紧急干预";
        }

        private String buildPulseMission(String roleCode, int score, long highRiskEvents, long pendingSubjectRequests) {
        String role = roleCode == null ? "治理角色" : roleCode;
        if (score < 60) {
            return role + " 当前应进入高压指挥模式，优先处理 " + highRiskEvents + " 个高危风险和 " + pendingSubjectRequests + " 个履约阻塞。";
        }
        if (score < 80) {
            return role + " 当前处于可控推进区间，建议把资源集中在风险压降和流程提速。";
        }
        return role + " 当前进入稳态领航区间，可以把重点转向自动化编排和跨组织治理协同。";
        }

        private List<GovernanceInsightDTO.Highlight> buildHighlights(long highSensitivityAssets,
                                     long highRiskModels,
                                     long todayAuditCount,
                                     long totalAiCalls,
                                     long totalCostCents) {
        List<GovernanceInsightDTO.Highlight> highlights = new ArrayList<>();
        highlights.add(new GovernanceInsightDTO.Highlight(
            "高敏资产纳管",
            highSensitivityAssets + " 项",
            "直接反映高风险数据资产纳入平台治理的密度，区别于传统平台只做库表登记。"
        ));
        highlights.add(new GovernanceInsightDTO.Highlight(
            "AI 模型治理",
            highRiskModels + " 个高风险模型",
            "模型被纳入风险等级、调用额度和状态治理，而不是作为平台外部黑盒。"
        ));
        highlights.add(new GovernanceInsightDTO.Highlight(
            "今日审计留痕",
            todayAuditCount + " 条",
            "审计从日志存储升级为可检索证据链，支持追溯与监管抽查。"
        ));
        highlights.add(new GovernanceInsightDTO.Highlight(
            "AI 调用与成本",
            totalAiCalls + " 次 / ¥" + String.format("%.2f", totalCostCents / 100.0),
            "平台具备 AI 使用量与成本的治理视角，传统规则平台通常没有该成本面板。"
        ));
        return highlights;
        }

        private List<GovernanceInsightDTO.Recommendation> buildRecommendations(long highSensitivityAssets,
                                           long openRiskEvents,
                                           long highRiskEvents,
                                           long pendingSubjectRequests,
                                           long todayAuditCount,
                                           long highRiskModels,
                                           long totalCostCents) {
        List<GovernanceInsightDTO.Recommendation> items = new ArrayList<>();
        if (highRiskEvents > 0) {
            items.add(new GovernanceInsightDTO.Recommendation(
                "open-high-risk-events",
                "P0",
                "优先闭环高风险事件",
                "当前存在高风险未闭环事件，建议先处理高危告警，避免治理平台只告警不闭环。",
                "/risk-event-manage",
                highRiskEvents + " 个高风险事件"
            ));
        }
        if (highSensitivityAssets > 0) {
            items.add(new GovernanceInsightDTO.Recommendation(
                "high-sensitivity-assets",
                "P0",
                "补齐高敏资产治理策略",
                "高敏资产越多，越需要配套脱敏、共享审批和审计规则，区别于传统台账式治理。",
                "/data-asset",
                highSensitivityAssets + " 个高敏资产"
            ));
        }
        if (pendingSubjectRequests > 0) {
            items.add(new GovernanceInsightDTO.Recommendation(
                "pending-subject-requests",
                "P1",
                "压降主体权利工单积压",
                "主体权利请求仍在处理中，平台已具备工单化闭环能力，应继续提升履约时效。",
                "/subject-request",
                pendingSubjectRequests + " 个待处理工单"
            ));
        }
        if (highRiskModels > 0) {
            items.add(new GovernanceInsightDTO.Recommendation(
                "high-risk-models",
                "P1",
                "强化高风险模型接入治理",
                "高风险模型已纳入平台，但仍需持续校验额度、状态和审计策略，避免模型成为监管盲区。",
                "/ai/risk-rating",
                highRiskModels + " 个高风险模型"
            ));
        }
        if (todayAuditCount == 0) {
            items.add(new GovernanceInsightDTO.Recommendation(
                "missing-audit-activity",
                "P1",
                "校验审计链路是否正常入库",
                "当日无审计留痕，说明系统可能未产生操作，也可能存在留痕链路缺口。",
                "/audit-log",
                "今日 0 条审计记录"
            ));
        }
        if (totalCostCents > 50000) {
            items.add(new GovernanceInsightDTO.Recommendation(
                "ai-cost-observe",
                "P2",
                "关注 AI 成本波动",
                "调用成本已明显增长，平台已具备成本治理基础，可进一步细化模型与业务维度归因。",
                "/operations-command",
                "累计 ¥" + String.format("%.2f", totalCostCents / 100.0)
            ));
        }
        if (items.isEmpty()) {
            items.add(new GovernanceInsightDTO.Recommendation(
                "steady-state",
                "P1",
                "治理态势稳定",
                "当前核心指标平稳，可继续扩展自动化策略编排与跨系统联动。",
                "/settings",
                "无明显阻塞项"
            ));
        }
        return items.stream()
            .sorted((left, right) -> left.getPriority().compareTo(right.getPriority()))
            .collect(Collectors.toList());
        }

        private List<WorkbenchOverviewDTO.Metric> buildMetrics(long highSensitivityAssets,
                                               long newHighSensitivityAssets,
                                               long previousHighSensitivityAssets,
                                               long openAlerts,
                                               long recentOpenAlerts,
                                               long previousOpenAlerts,
                                               long aiCallsLast7,
                                               long aiCallsPrevious7,
                                               long todayAuditCount,
                                               long yesterdayAuditCount,
                                               int windowDays) {
        List<WorkbenchOverviewDTO.Metric> items = new ArrayList<>();
        items.add(new WorkbenchOverviewDTO.Metric(
            "assets",
            "高敏资产纳管",
            highSensitivityAssets,
            "项",
            calcDelta(newHighSensitivityAssets, previousHighSensitivityAssets),
            "近" + windowDays + "日新增高敏资产纳管规模"
        ));
        items.add(new WorkbenchOverviewDTO.Metric(
            "alerts",
            "待闭环告警",
            openAlerts,
            "条",
            calcDelta(recentOpenAlerts, previousOpenAlerts),
            "仍需人工处置的风险与告警压力"
        ));
        items.add(new WorkbenchOverviewDTO.Metric(
            "aiCalls",
            windowDays + "日AI调用",
            aiCallsLast7,
            "次",
            calcDelta(aiCallsLast7, aiCallsPrevious7),
            "模型真实调用量与治理压力"
        ));
        items.add(new WorkbenchOverviewDTO.Metric(
            "audits",
            "今日审计留痕",
            todayAuditCount,
            "条",
            calcDelta(todayAuditCount, yesterdayAuditCount),
            "面向监管抽查的证据链产出"
        ));
        return items;
        }

        private WorkbenchOverviewDTO.Trend buildTrend(LocalDate start,
                                      List<RiskEvent> riskEvents,
                                      List<AuditLog> auditLogs,
                          List<ModelCallStat> modelStats,
                          int windowDays) {
        WorkbenchOverviewDTO.Trend trend = new WorkbenchOverviewDTO.Trend();
        trend.setRiskEventSampleCount((long) riskEvents.size());
        trend.setAuditLogSampleCount((long) auditLogs.size());
        trend.setModelStatSampleCount((long) modelStats.size());
        trend.setTrendWindowDays(windowDays);
        Map<LocalDate, Long> riskByDay = riskEvents.stream()
            .filter(item -> item.getCreateTime() != null)
            .collect(Collectors.groupingBy(item -> toLocalDate(item.getCreateTime()), Collectors.counting()));
        Map<LocalDate, Long> auditByDay = auditLogs.stream()
            .filter(item -> item.getOperationTime() != null)
            .collect(Collectors.groupingBy(item -> toLocalDate(item.getOperationTime()), Collectors.counting()));
        Map<LocalDate, Long> aiCallsByDay = modelStats.stream()
            .collect(Collectors.groupingBy(item -> toLocalDate(item.getDate()), Collectors.summingLong(item -> item.getCallCount() == null ? 0L : item.getCallCount())));
        Map<LocalDate, Long> costByDay = modelStats.stream()
            .collect(Collectors.groupingBy(item -> toLocalDate(item.getDate()), Collectors.summingLong(item -> item.getCostCents() == null ? 0L : item.getCostCents())));

        List<Long> riskSeries = new ArrayList<>();
        for (int index = 0; index < windowDays; index++) {
            LocalDate date = start.plusDays(index);
            trend.getLabels().add(date.getMonthValue() + "/" + date.getDayOfMonth());
            long risk = riskByDay.getOrDefault(date, 0L);
            long audit = auditByDay.getOrDefault(date, 0L);
            long aiCalls = aiCallsByDay.getOrDefault(date, 0L);
            long cost = costByDay.getOrDefault(date, 0L);
            riskSeries.add(risk);
            trend.getRiskSeries().add(risk);
            trend.getAuditSeries().add(audit);
            trend.getAiCallSeries().add(aiCalls);
            trend.getCostSeries().add(cost);
        }
        trend.setForecastNextDay(resolveForecastNextDayFromLstm(riskSeries));
        return trend;
        }

        private List<WorkbenchOverviewDTO.RiskBucket> buildRiskDistribution(List<RiskEvent> riskEvents) {
        Map<String, Long> grouped = new LinkedHashMap<>();
        grouped.put("高危", 0L);
        grouped.put("中危", 0L);
        grouped.put("低危", 0L);
        grouped.put("待研判", 0L);
        riskEvents.forEach(item -> {
            String level = normalizeLower(item.getLevel());
            if (Arrays.asList("high", "critical", "高").contains(level)) {
                grouped.computeIfPresent("高危", (key, value) -> value + 1);
            } else if (Arrays.asList("medium", "中").contains(level)) {
                grouped.computeIfPresent("中危", (key, value) -> value + 1);
            } else if (Arrays.asList("low", "低").contains(level)) {
                grouped.computeIfPresent("低危", (key, value) -> value + 1);
            } else {
                grouped.computeIfPresent("待研判", (key, value) -> value + 1);
            }
        });
        return grouped.entrySet().stream()
            .map(entry -> new WorkbenchOverviewDTO.RiskBucket(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
        }

        private List<WorkbenchOverviewDTO.TodoItem> buildTodos(long highRiskEvents,
                                               long openAlerts,
                                               long pendingSubjectRequests,
                                               long enabledModels) {
        List<WorkbenchOverviewDTO.TodoItem> todos = new ArrayList<>();
        if (highRiskEvents > 0) {
            todos.add(new WorkbenchOverviewDTO.TodoItem("P0", "闭环高风险事件", "优先压降高风险事件，避免平台只监测不处置。", "/risk-event-manage", highRiskEvents + " 个高危事件"));
        }
        if (openAlerts > 0) {
            todos.add(new WorkbenchOverviewDTO.TodoItem("P0", "处理待处置风险", "仍有高优先级风险待跟进，需进入风险编排视图。", "/risk-event-manage", openAlerts + " 条待处置风险"));
        }
        if (pendingSubjectRequests > 0) {
            todos.add(new WorkbenchOverviewDTO.TodoItem("P1", "履约主体权利请求", "访问、删除、导出类工单仍在队列中，影响隐私履约体验。", "/subject-request", pendingSubjectRequests + " 个待处理工单"));
        }
        todos.add(new WorkbenchOverviewDTO.TodoItem("P1", "巡检启用AI能力", "核验高风险模型额度、状态与绑定资产是否仍符合最新策略。", "/ai/risk-rating", enabledModels + " 个启用模型"));
        return todos.stream().limit(4).collect(Collectors.toList());
        }

        private List<WorkbenchOverviewDTO.ActivityFeed> buildFeeds(List<RiskEvent> riskEvents,
                               List<SubjectRequest> subjectRequests) {
        List<WorkbenchOverviewDTO.ActivityFeed> feeds = new ArrayList<>();
        riskEvents.stream()
            .filter(item -> item.getCreateTime() != null)
            .sorted(Comparator.comparing(RiskEvent::getCreateTime).reversed())
            .limit(3)
            .forEach(item -> feeds.add(new WorkbenchOverviewDTO.ActivityFeed(
                normalizeLower(item.getLevel()),
                "风险事件 · " + defaultText(item.getType(), "待识别类型"),
                "状态：" + defaultText(item.getStatus(), "open") + "，处置日志：" + defaultText(item.getProcessLog(), "待跟进"),
                "/risk-event-manage",
                formatTime(item.getCreateTime())
            )));
        subjectRequests.stream()
            .filter(item -> item.getCreateTime() != null)
            .sorted(Comparator.comparing(SubjectRequest::getCreateTime).reversed())
            .limit(2)
            .forEach(item -> feeds.add(new WorkbenchOverviewDTO.ActivityFeed(
                normalizeLower(item.getStatus()),
                "主体权利 · " + defaultText(item.getType(), "access"),
                defaultText(item.getComment(), "等待处理") + "，当前状态：" + defaultText(item.getStatus(), "pending"),
                "/subject-request",
                formatTime(item.getCreateTime())
            )));
        return feeds.stream()
            .sorted(Comparator.comparing(WorkbenchOverviewDTO.ActivityFeed::getTimeLabel).reversed())
            .limit(6)
            .collect(Collectors.toList());
        }

        private int calcDelta(long current, long previous) {
        if (previous <= 0) {
            return current > 0 ? 100 : 0;
        }
        return (int) Math.round(((double) current - previous) / previous * 100);
        }

        private LocalDate toLocalDate(Date value) {
        return value.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }

        private long resolveForecastNextDayFromLstm(List<Long> series) {
        try {
            List<Double> forecast = riskForecastScheduler.getLatest().forecast;
            if (forecast != null && !forecast.isEmpty()) {
                return Math.max(0L, Math.round(forecast.get(0)));
            }
        } catch (Exception ignored) {
            // fallback below keeps dashboard resilient when scheduler is temporarily unavailable
        }
        if (series == null || series.isEmpty()) {
            return 0L;
        }
        return Math.max(0L, series.get(series.size() - 1));
        }

        private String safeUserName(User user) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        if (user.getRealName() != null && !user.getRealName().isBlank()) {
            return user.getRealName();
        }
        return defaultText(user.getUsername(), "访客");
        }

        private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
        }

        private String normalizeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase();
        }

        private String formatTime(Date value) {
        LocalDateTime time = value.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        return String.format("%02d-%02d %02d:%02d", time.getMonthValue(), time.getDayOfMonth(), time.getHour(), time.getMinute());
        }
}
