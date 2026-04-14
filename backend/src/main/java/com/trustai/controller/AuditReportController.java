package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.entity.AdversarialRecord;
import com.trustai.entity.AuditLog;
import com.trustai.entity.GovernanceEvent;
import com.trustai.service.AdversarialRecordService;
import com.trustai.service.AuditLogService;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.GovernanceEventService;
import com.trustai.service.UserService;
import com.trustai.utils.R;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/audit-report")
public class AuditReportController {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private CompanyScopeService companyScopeService;

    @Autowired
    private GovernanceEventService governanceEventService;

    @Autowired
    private AdversarialRecordService adversarialRecordService;

    @Autowired
    private UserService userService;

    @GetMapping("/compare")
    @PreAuthorize("@currentUserService.hasAnyPermission('audit:report:view','ops:metrics:view')")
    public R<Map<String, Object>> compare(@RequestParam String from, @RequestParam String to) {
        LocalDate fromDate = parseDate(from, LocalDate.now(ZONE).minusDays(30));
        LocalDate toDate = parseDate(to, LocalDate.now(ZONE));
        return R.ok(buildReport(fromDate, toDate, "对比统计"));
    }

    @GetMapping("/generate")
    @PreAuthorize("@currentUserService.hasRole('ADMIN') || @currentUserService.hasPermission('audit:report:generate')")
    public R<Map<String, Object>> generate(@RequestParam(required = false) String range,
                                           @RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to) {
        LocalDate[] window = resolveWindow(from, to);
        String safeRange = StringUtils.hasText(range) ? range.trim() : "latest";
        Map<String, Object> report = buildReport(window[0], window[1], safeRange);
        report.put("title", buildTitle(safeRange, window[0], window[1]));
        report.put("downloadUrl", buildDownloadUrl(safeRange, window[0], window[1]));
        return R.ok(report);
    }

    @GetMapping("/download")
    @PreAuthorize("@currentUserService.hasAnyPermission('audit:report:view','audit:report:generate')")
    public ResponseEntity<byte[]> download(@RequestParam(required = false) String range,
                                           @RequestParam(required = false) String from,
                                           @RequestParam(required = false) String to,
                                           @RequestParam(required = false) Long ts) {
        LocalDate[] window = resolveWindow(from, to);
        String safeRange = StringUtils.hasText(range) ? range.trim() : "latest";
        Map<String, Object> report = buildReport(window[0], window[1], safeRange);
        report.put("title", buildTitle(safeRange, window[0], window[1]));
        report.put("generatedAt", new Date(ts == null ? System.currentTimeMillis() : ts));
        byte[] body;
        try {
            body = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(report);
        } catch (Exception ex) {
            body = ("{\"error\":\"" + ex.getMessage() + "\"}").getBytes(StandardCharsets.UTF_8);
        }
        String filename = "audit-" + safeRange + "-" + (ts == null ? System.currentTimeMillis() : ts) + ".json";
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.APPLICATION_JSON)
            .body(body);
    }

    private Map<String, Object> buildReport(LocalDate fromDate, LocalDate toDate, String label) {
        Date from = Date.from(fromDate.atStartOfDay(ZONE).toInstant());
        Date to = Date.from(toDate.plusDays(1).atStartOfDay(ZONE).minusNanos(1).toInstant());

        List<Long> userIds = companyScopeService.companyUserIds();
        List<AuditLog> logs = auditLogService.list(
            companyScopeService.withCompany(new QueryWrapper<AuditLog>().between("operation_time", from, to))
        );
        if (!userIds.isEmpty()) {
            Set<Long> allowedIds = userIds.stream().collect(Collectors.toSet());
            logs = logs.stream()
                .filter(item -> item.getUserId() != null && allowedIds.contains(item.getUserId()))
                .toList();
        }
        logs = logs.stream()
            .sorted((left, right) -> compareDates(right.getOperationTime(), left.getOperationTime()))
            .toList();

        long success = logs.stream().filter(this::isSuccess).count();
        long fail = Math.max(0L, logs.size() - success);
        long systemLogs = logs.stream().filter(item -> isSystemActor(item.getUserId())).count();

        Map<String, Long> operationDistribution = new TreeMap<>();
        Map<String, Long> riskDistribution = new LinkedHashMap<>();
        riskDistribution.put("高", 0L);
        riskDistribution.put("中", 0L);
        riskDistribution.put("低", 0L);
        riskDistribution.put("未知", 0L);
        Map<String, Long> actorDistribution = new LinkedHashMap<>();
        actorDistribution.put("系统任务", 0L);
        actorDistribution.put("普通用户", 0L);

        for (AuditLog item : logs) {
            String operationLabel = normalizeOperationLabel(item.getOperation());
            operationDistribution.put(operationLabel, operationDistribution.getOrDefault(operationLabel, 0L) + 1);
            String riskLabel = normalizeRiskLabel(item.getRiskLevel());
            riskDistribution.put(riskLabel, riskDistribution.getOrDefault(riskLabel, 0L) + 1);
            String actorKey = isSystemActor(item.getUserId()) ? "系统任务" : "普通用户";
            actorDistribution.put(actorKey, actorDistribution.getOrDefault(actorKey, 0L) + 1);
        }

        Set<Long> auditedUsers = logs.stream()
            .map(AuditLog::getUserId)
            .filter(id -> id != null && id > 0)
            .collect(Collectors.toSet());
        double coverage = userIds.isEmpty() ? 0d : auditedUsers.size() * 100.0d / userIds.size();

        List<GovernanceEvent> governanceEvents = governanceEventService.list(
            companyScopeService.withCompany(new QueryWrapper<GovernanceEvent>().between("event_time", from, to))
        );
        long disposed = governanceEvents.stream()
            .filter(item -> "blocked".equalsIgnoreCase(item.getStatus()) || "ignored".equalsIgnoreCase(item.getStatus()))
            .count();

        Map<String, Long> eventTypeDistribution = new LinkedHashMap<>();
        for (GovernanceEvent item : governanceEvents) {
            String key = normalizeEventType(item.getEventType());
            eventTypeDistribution.put(key, eventTypeDistribution.getOrDefault(key, 0L) + 1);
        }

        long adversarialRuns = adversarialRecordService.count(
            companyScopeService.withCompany(new QueryWrapper<AdversarialRecord>().between("create_time", from, to))
        );

        List<Map<String, Object>> recentLogs = logs.stream()
            .limit(20)
            .map(this::toLogView)
            .collect(Collectors.toCollection(ArrayList::new));

        Map<String, Object> report = new LinkedHashMap<>();
        report.put("title", buildTitle(label, fromDate, toDate));
        report.put("range", Map.of(
            "label", label,
            "from", fromDate.format(DATE_FORMAT),
            "to", toDate.format(DATE_FORMAT)
        ));
        report.put("generatedAt", new Date());
        report.put("auditLogTotal", logs.size());
        report.put("total", logs.size());
        report.put("success", success);
        report.put("fail", fail);
        report.put("systemAuditTotal", systemLogs);
        report.put("distinctUsers", auditedUsers.size());
        report.put("coverage", round2(coverage));
        report.put("operationDistribution", operationDistribution);
        report.put("riskDistribution", riskDistribution);
        report.put("actorDistribution", actorDistribution);
        report.put("recentLogs", recentLogs);
        report.put("governanceTotal", governanceEvents.size());
        report.put("governanceDisposed", disposed);
        report.put("governanceByType", eventTypeDistribution);
        report.put("adversarialRuns", adversarialRuns);
        report.put("summary", Map.of(
            "label", label,
            "window", fromDate.format(DATE_FORMAT) + " ~ " + toDate.format(DATE_FORMAT),
            "auditCoverage", round2(coverage),
            "systemLogRatio", logs.isEmpty() ? 0d : round2(systemLogs * 100.0d / logs.size())
        ));
        return report;
    }

    private Map<String, Object> toLogView(AuditLog log) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", log.getId());
        row.put("userId", log.getUserId());
        row.put("actorType", isSystemActor(log.getUserId()) ? "system" : "user");
        row.put("operation", normalizeOperationLabel(log.getOperation()));
        row.put("operationTime", log.getOperationTime());
        row.put("result", normalizeResultLabel(log.getResult()));
        row.put("riskLevel", normalizeRiskLabel(log.getRiskLevel()));
        row.put("inputOverview", log.getInputOverview());
        row.put("outputOverview", log.getOutputOverview());
        row.put("permissionName", log.getPermissionName());
        row.put("assetId", log.getAssetId());
        return row;
    }

    private LocalDate parseDate(String value, LocalDate fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        try {
            return LocalDate.parse(value.trim(), DATE_FORMAT);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private LocalDate[] resolveWindow(String from, String to) {
        LocalDate end = parseDate(to, LocalDate.now(ZONE));
        LocalDate start = parseDate(from, end.minusDays(30));
        if (start.isAfter(end)) {
            return new LocalDate[]{end.minusDays(30), end};
        }
        return new LocalDate[]{start, end};
    }

    private String buildTitle(String label, LocalDate from, LocalDate to) {
        String safeLabel = StringUtils.hasText(label) ? label.trim() : "latest";
        return "合规审计报告-" + safeLabel + "（" + from.format(DATE_FORMAT) + " 至 " + to.format(DATE_FORMAT) + "）";
    }

    private String buildDownloadUrl(String range, LocalDate from, LocalDate to) {
        return "/api/audit-report/download?range=" + encode(range)
            + "&from=" + from.format(DATE_FORMAT)
            + "&to=" + to.format(DATE_FORMAT)
            + "&ts=" + System.currentTimeMillis();
    }

    private String encode(String value) {
        if (!StringUtils.hasText(value)) {
            return "latest";
        }
        return value.trim().replace(" ", "%20");
    }

    private boolean isSuccess(AuditLog log) {
        if (log == null) {
            return false;
        }
        String result = String.valueOf(log.getResult() == null ? "" : log.getResult()).trim().toLowerCase(Locale.ROOT);
        return "success".equals(result) || "ok".equals(result) || "成功".equals(result) || result.contains("success") || result.contains("ok");
    }

    private String normalizeResultLabel(String value) {
        String result = String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
        if (result.contains("fail") || result.contains("error") || result.contains("deny") || result.contains("拒绝")) {
            return "失败";
        }
        return "成功";
    }

    private String normalizeRiskLabel(String value) {
        String risk = String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
        if (risk.contains("critical") || risk.contains("high") || risk.contains("高")) {
            return "高";
        }
        if (risk.contains("medium") || risk.contains("中")) {
            return "中";
        }
        if (risk.contains("low") || risk.contains("低")) {
            return "低";
        }
        return "未知";
    }

    private String normalizeOperationLabel(String value) {
        String operation = String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
        if (operation.isEmpty()) {
            return "访问";
        }
        if (operation.contains("login")) {
            return "登录";
        }
        if (operation.contains("logout")) {
            return "退出登录";
        }
        if (operation.contains("export") || operation.contains("download")) {
            return "导出";
        }
        if (operation.contains("delete") || operation.contains("remove")) {
            return "删除";
        }
        if (operation.contains("update") || operation.contains("edit") || operation.contains("modify")) {
            return "修改";
        }
        if (operation.contains("approve") || operation.contains("review") || operation.contains("audit")) {
            return "审批/审计";
        }
        if (operation.contains("security")) {
            return "安全处置";
        }
        return value;
    }

    private String normalizeEventType(String value) {
        String eventType = String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
        if (eventType.contains("privacy")) {
            return "隐私告警";
        }
        if (eventType.contains("anomaly")) {
            return "异常行为";
        }
        if (eventType.contains("shadow")) {
            return "影子 AI";
        }
        if (eventType.contains("security")) {
            return "安全告警";
        }
        return StringUtils.hasText(value) ? value : "未分类";
    }

    private boolean isSystemActor(Long userId) {
        if (userId == null) {
            return false;
        }
        return userService.getById(userId) != null && "system".equalsIgnoreCase(userService.getById(userId).getUsername());
    }

    private int compareDates(Date left, Date right) {
        if (left == right) {
            return 0;
        }
        if (left == null) {
            return -1;
        }
        if (right == null) {
            return 1;
        }
        return left.compareTo(right);
    }

    private double round2(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }
}
