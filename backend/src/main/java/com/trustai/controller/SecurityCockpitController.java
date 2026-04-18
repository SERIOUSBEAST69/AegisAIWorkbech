package com.trustai.controller;

import com.trustai.config.jwt.JwtUtil;
import com.trustai.entity.User;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.UserService;
import com.trustai.utils.R;
import io.jsonwebtoken.Claims;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/security-cockpit")
public class SecurityCockpitController {

    private static final DateTimeFormatter HOUR_LABEL_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:00");
    private static final Pattern IPV4_PATTERN = Pattern.compile("^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");
    private static final Set<String> HEATMAP_DEPARTMENT_EXCLUDES = Set.of(
        "模型平台组", "业务一线一组", "业务一线二组", "业务一线四组", "未分配部门"
    );

    private final CurrentUserService currentUserService;
    private final CompanyScopeService companyScopeService;
    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbcTemplate;

    private final ScheduledExecutorService sseScheduler = Executors.newScheduledThreadPool(2);

    public SecurityCockpitController(CurrentUserService currentUserService,
                                    CompanyScopeService companyScopeService,
                                    UserService userService,
                                    JwtUtil jwtUtil,
                                    JdbcTemplate jdbcTemplate) {
        this.currentUserService = currentUserService;
        this.companyScopeService = companyScopeService;
        this.userService = userService;
        this.jwtUtil = jwtUtil;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/overview")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<Map<String, Object>> overview() {
        User viewer = currentUserService.requireCurrentUser();
        ViewerScope scope = resolveScope(viewer);

        String baseSql = " FROM governance_event ge WHERE ge.company_id = ? "
            + "AND ge.event_type IN ('PRIVACY_ALERT','ANOMALY_ALERT','SHADOW_AI_ALERT','SECURITY_ALERT') "
            + "AND DATE(ge.event_time) = CURDATE() ";
        List<Object> params = new ArrayList<>();
        params.add(scope.companyId());
        if (!scope.companyWide()) {
            baseSql += " AND ge.user_id = ? ";
            params.add(scope.userId());
        }

        long todayThreatTotal = queryCount("SELECT COUNT(1)" + baseSql, params);
        long blockedTotal = queryCount("SELECT COUNT(1)" + baseSql + " AND LOWER(ge.status) = 'blocked'", params);
        long unresolvedTotal = queryCount("SELECT COUNT(1)" + baseSql + " AND LOWER(ge.status) IN ('pending','reviewing')", params);
        long highRiskUsers = queryCount(
            "SELECT COUNT(DISTINCT ge.user_id)" + baseSql + " AND LOWER(ge.severity) IN ('high','critical')",
            params
        );

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("todayThreatTotal", todayThreatTotal);
        data.put("blockedTotal", blockedTotal);
        data.put("highRiskUsers", highRiskUsers);
        data.put("unresolvedTotal", unresolvedTotal);
        data.put("generatedAt", new Date());
        return R.ok(data);
    }

    @GetMapping("/heatmap/department")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<Map<String, Object>> departmentHeatmap(@RequestParam(defaultValue = "7") int days) {
        User viewer = currentUserService.requireCurrentUser();
        ViewerScope scope = resolveScope(viewer);
        int safeDays = Math.max(1, Math.min(30, days));
        Date since = Date.from(LocalDateTime.now().minusDays(safeDays).atZone(ZoneId.systemDefault()).toInstant());

        String sql = "SELECT COALESCE(NULLIF(TRIM(u.department), ''), '未分配部门') AS department, "
            + "COUNT(ge.id) AS eventCount, "
            + "COALESCE(ROUND(SUM(CASE LOWER(ge.severity) "
            + "WHEN 'critical' THEN 100 WHEN 'high' THEN 75 WHEN 'medium' THEN 45 ELSE 20 END) / NULLIF(COUNT(ge.id),0), 2), 0) AS riskScore "
            + "FROM sys_user u "
            + "LEFT JOIN governance_event ge ON ge.company_id = u.company_id "
            + "AND ge.user_id = u.id "
            + "AND ge.event_type IN ('PRIVACY_ALERT','ANOMALY_ALERT','SHADOW_AI_ALERT','SECURITY_ALERT') "
            + "AND ge.event_time >= ? "
            + "WHERE u.company_id = ? ";
        List<Object> params = new ArrayList<>();
        params.add(since);
        params.add(scope.companyId());
        if (!scope.companyWide()) {
            sql += "AND u.id = ? ";
            params.add(scope.userId());
        }
        sql += "GROUP BY COALESCE(NULLIF(TRIM(u.department), ''), '未分配部门') "
            + "ORDER BY riskScore DESC, eventCount DESC";

        List<Map<String, Object>> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("department", sanitizeDepartmentLabel(rs.getString("department")));
            row.put("eventCount", rs.getLong("eventCount"));
            row.put("riskScore", rs.getDouble("riskScore"));
            return row;
        }, params.toArray());

        rows = rows.stream()
            .filter(item -> {
                String department = String.valueOf(item.getOrDefault("department", "")).trim();
                long eventCount = Long.parseLong(String.valueOf(item.getOrDefault("eventCount", 0)));
                return StringUtils.hasText(department)
                    && eventCount > 0
                    && !HEATMAP_DEPARTMENT_EXCLUDES.contains(department);
            })
            .toList();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("days", safeDays);
        data.put("items", rows);
        data.put("generatedAt", new Date());
        return R.ok(data);
    }

    @GetMapping("/trend/hourly")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<Map<String, Object>> hourlyTrend(@RequestParam(defaultValue = "24") int hours) {
        User viewer = currentUserService.requireCurrentUser();
        ViewerScope scope = resolveScope(viewer);
        int safeHours = Math.max(6, Math.min(72, hours));
        LocalDateTime end = LocalDateTime.now().truncatedTo(ChronoUnit.HOURS);
        LocalDateTime start = end.minusHours(safeHours - 1L);
        Date since = Date.from(start.atZone(ZoneId.systemDefault()).toInstant());

        String sql = "SELECT ge.id, ge.event_time, ge.status, ge.event_type, ge.title, ge.severity, ge.source_module "
            + "FROM governance_event ge WHERE ge.company_id = ? "
            + "AND ge.event_type IN ('PRIVACY_ALERT','ANOMALY_ALERT','SHADOW_AI_ALERT','SECURITY_ALERT') "
            + "AND ge.event_time >= ? ";
        List<Object> params = new ArrayList<>();
        params.add(scope.companyId());
        params.add(since);
        if (!scope.companyWide()) {
            sql += "AND ge.user_id = ? ";
            params.add(scope.userId());
        }
        sql += "ORDER BY ge.event_time ASC";

        List<Map<String, Object>> records = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", rs.getLong("id"));
            item.put("eventTime", rs.getTimestamp("event_time"));
            item.put("status", normalizeStatus(rs.getString("status")));
            item.put("eventType", rs.getString("event_type"));
            item.put("severity", rs.getString("severity"));
            item.put("title", rs.getString("title"));
            item.put("sourceModule", rs.getString("source_module"));
            return item;
        }, params.toArray());

        List<String> labels = new ArrayList<>();
        List<Integer> blockedSeries = new ArrayList<>();
        List<Integer> pendingSeries = new ArrayList<>();
        List<Integer> ignoredSeries = new ArrayList<>();
        List<Map<String, Object>> buckets = new ArrayList<>();

        Map<String, Map<String, Integer>> counterByHour = new HashMap<>();
        Map<String, List<Map<String, Object>>> detailByHour = new HashMap<>();
        for (Map<String, Object> record : records) {
            Date eventTime = (Date) record.get("eventTime");
            if (eventTime == null) {
                continue;
            }
            LocalDateTime eventHour = eventTime.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().truncatedTo(ChronoUnit.HOURS);
            if (eventHour.isBefore(start) || eventHour.isAfter(end)) {
                continue;
            }
            String hourKey = eventHour.format(HOUR_LABEL_FORMATTER);
            String status = String.valueOf(record.get("status"));
            counterByHour.computeIfAbsent(hourKey, key -> new HashMap<>());
            Map<String, Integer> perStatus = counterByHour.get(hourKey);
            perStatus.put(status, perStatus.getOrDefault(status, 0) + 1);
            detailByHour.computeIfAbsent(hourKey, key -> new ArrayList<>()).add(record);
        }

        for (int i = 0; i < safeHours; i++) {
            LocalDateTime bucketTime = start.plusHours(i);
            String key = bucketTime.format(HOUR_LABEL_FORMATTER);
            Map<String, Integer> stat = counterByHour.getOrDefault(key, Map.of());
            int blocked = stat.getOrDefault("blocked", 0);
            int pending = stat.getOrDefault("pending", 0);
            int ignored = stat.getOrDefault("ignored", 0);
            labels.add(key);
            blockedSeries.add(blocked);
            pendingSeries.add(pending);
            ignoredSeries.add(ignored);
            Map<String, Object> bucket = new LinkedHashMap<>();
            bucket.put("hour", key);
            bucket.put("blocked", blocked);
            bucket.put("pending", pending);
            bucket.put("ignored", ignored);
            bucket.put("events", detailByHour.getOrDefault(key, List.of()));
            buckets.add(bucket);
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("hours", labels);
        data.put("blockedSeries", blockedSeries);
        data.put("pendingSeries", pendingSeries);
        data.put("ignoredSeries", ignoredSeries);
        data.put("details", buckets);
        data.put("generatedAt", new Date());
        return R.ok(data);
    }

    @GetMapping("/topology")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<Map<String, Object>> riskTopology(@RequestParam(defaultValue = "7") int days) {
        User viewer = currentUserService.requireCurrentUser();
        ViewerScope scope = resolveScope(viewer);
        int safeDays = Math.max(1, Math.min(30, days));
        Date since = Date.from(LocalDateTime.now().minusDays(safeDays).atZone(ZoneId.systemDefault()).toInstant());
        ensureTopologyDataFresh(scope, since, safeDays);

        String sql = "SELECT se.id, se.employee_id, se.file_path, se.target_addr, se.severity, se.event_time, "
            + "COALESCE(ipm.last_ip, NULLIF(se.hostname, ''), 'unknown') AS source_ip "
            + "FROM security_event se "
            + "LEFT JOIN sys_user u ON u.company_id = se.company_id AND u.username = se.employee_id "
            + "LEFT JOIN ( "
            + "    SELECT a.user_id, SUBSTRING_INDEX(GROUP_CONCAT(a.ip ORDER BY a.operation_time DESC), ',', 1) AS last_ip "
            + "    FROM audit_log a GROUP BY a.user_id "
            + ") ipm ON ipm.user_id = u.id "
            + "WHERE se.company_id = ? AND se.event_time >= ? ";
        List<Object> params = new ArrayList<>();
        params.add(scope.companyId());
        params.add(since);
        if (!scope.companyWide()) {
            sql += "AND se.employee_id = ? ";
            params.add(scope.username());
        }
        sql += "ORDER BY se.event_time DESC LIMIT 1200";

        List<Map<String, Object>> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getLong("id"));
            row.put("sourceIp", rs.getString("source_ip"));
            row.put("target", resolveTarget(rs.getString("target_addr"), rs.getString("file_path")));
            row.put("severity", normalizeSeverity(rs.getString("severity")));
            row.put("eventTime", rs.getTimestamp("event_time"));
            return row;
        }, params.toArray());

        Map<String, Integer> sourceCount = new HashMap<>();
        Map<String, Integer> targetCount = new HashMap<>();
        Map<String, EdgeAgg> edgeAggMap = new HashMap<>();

        for (Map<String, Object> row : rows) {
            String source = String.valueOf(row.get("sourceIp"));
            String target = String.valueOf(row.get("target"));
            sourceCount.put(source, sourceCount.getOrDefault(source, 0) + 1);
            targetCount.put(target, targetCount.getOrDefault(target, 0) + 1);

            String key = source + "->" + target;
            EdgeAgg agg = edgeAggMap.computeIfAbsent(key, ignored -> new EdgeAgg(source, target));
            agg.count++;
            agg.maxRisk = Math.max(agg.maxRisk, severityWeight(String.valueOf(row.get("severity"))));
            if (agg.eventIds.size() < 30) {
                agg.eventIds.add(Long.valueOf(String.valueOf(row.get("id"))));
            }
        }

        List<Map<String, Object>> nodes = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : sourceCount.entrySet()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", "ip:" + entry.getKey());
            node.put("label", entry.getKey());
            node.put("displayLabel", buildSourceDisplayLabel(entry.getKey()));
            node.put("type", "source");
            node.put("value", entry.getValue());
            node.put("risk", "medium");
            nodes.add(node);
        }
        for (Map.Entry<String, Integer> entry : targetCount.entrySet()) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", "target:" + entry.getKey());
            node.put("label", entry.getKey());
            node.put("displayLabel", buildTargetDisplayLabel(entry.getKey()));
            node.put("type", "target");
            node.put("value", entry.getValue());
            node.put("risk", "low");
            nodes.add(node);
        }

        List<Map<String, Object>> edges = new ArrayList<>();
        edgeAggMap.values().stream()
            .sorted(Comparator.comparingInt((EdgeAgg e) -> e.count).reversed())
            .limit(160)
            .forEach(agg -> {
                Map<String, Object> edge = new LinkedHashMap<>();
                edge.put("source", "ip:" + agg.source);
                edge.put("target", "target:" + agg.target);
                edge.put("count", agg.count);
                edge.put("risk", agg.maxRisk >= 4 ? "critical" : agg.maxRisk >= 3 ? "high" : agg.maxRisk >= 2 ? "medium" : "low");
                edge.put("eventIds", agg.eventIds);
                edges.add(edge);
            });

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("nodes", nodes);
        data.put("edges", edges);
        data.put("generatedAt", new Date());
        return R.ok(data);
    }

    @GetMapping("/alerts/recent")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<List<Map<String, Object>>> recentAlerts(@RequestParam(defaultValue = "40") int limit) {
        User viewer = currentUserService.requireCurrentUser();
        ViewerScope scope = resolveScope(viewer);
        return R.ok(loadRecentAlerts(scope, 0L, Math.max(10, Math.min(120, limit))));
    }

    @GetMapping(path = "/alerts/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAlerts(@RequestParam("token") String token,
                                   @RequestParam(defaultValue = "0") Long lastEventId,
                                   @RequestParam(defaultValue = "30") int limit) {
        ViewerScope scope = scopeFromToken(token);
        long safeLast = lastEventId == null ? 0L : Math.max(0L, lastEventId);
        int safeLimit = Math.max(10, Math.min(80, limit));

        SseEmitter emitter = new SseEmitter(0L);
        final long[] cursor = {safeLast};

        Runnable publish = () -> {
            try {
                List<Map<String, Object>> updates = loadRecentAlerts(scope, cursor[0], safeLimit);
                if (!updates.isEmpty()) {
                    long maxId = updates.stream()
                        .map(item -> Long.valueOf(String.valueOf(item.get("eventId"))))
                        .max(Long::compareTo)
                        .orElse(cursor[0]);
                    cursor[0] = Math.max(cursor[0], maxId);
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("items", updates);
                    payload.put("cursor", cursor[0]);
                    payload.put("generatedAt", new Date());
                    emitter.send(SseEmitter.event().name("alerts").data(payload));
                } else {
                    emitter.send(SseEmitter.event().name("ping").data(Map.of("ts", System.currentTimeMillis(), "cursor", cursor[0])));
                }
            } catch (Exception ex) {
                emitter.completeWithError(ex);
            }
        };

        try {
            emitter.send(SseEmitter.event().name("ready").data(Map.of("cursor", cursor[0], "ts", System.currentTimeMillis())).reconnectTime(3000));
        } catch (Exception ex) {
            emitter.completeWithError(ex);
            return emitter;
        }
        publish.run();
        var future = sseScheduler.scheduleAtFixedRate(publish, 5, 5, TimeUnit.SECONDS);

        emitter.onCompletion(() -> future.cancel(true));
        emitter.onTimeout(() -> {
            future.cancel(true);
            emitter.complete();
        });
        emitter.onError(ex -> future.cancel(true));
        return emitter;
    }

    @GetMapping("/department/detail")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<List<Map<String, Object>>> departmentDetail(@RequestParam String department,
                                                         @RequestParam(defaultValue = "7") int days,
                                                         @RequestParam(defaultValue = "80") int limit) {
        User viewer = currentUserService.requireCurrentUser();
        ViewerScope scope = resolveScope(viewer);
        int safeDays = Math.max(1, Math.min(30, days));
        int safeLimit = Math.max(10, Math.min(200, limit));
        Date since = Date.from(LocalDateTime.now().minusDays(safeDays).atZone(ZoneId.systemDefault()).toInstant());

        String sql = "SELECT ge.id, ge.user_id, ge.username, ge.event_type, ge.severity, ge.status, ge.event_time, ge.title, ge.source_module "
            + "FROM governance_event ge "
            + "LEFT JOIN sys_user u ON u.id = ge.user_id AND u.company_id = ge.company_id "
            + "WHERE ge.company_id = ? "
            + "AND ge.event_type IN ('PRIVACY_ALERT','ANOMALY_ALERT','SHADOW_AI_ALERT','SECURITY_ALERT') "
            + "AND ge.event_time >= ? "
            + "AND COALESCE(NULLIF(TRIM(u.department), ''), '未分配部门') = ? ";
        List<Object> params = new ArrayList<>();
        params.add(scope.companyId());
        params.add(since);
        params.add(StringUtils.hasText(department) ? department.trim() : "未分配部门");
        if (!scope.companyWide()) {
            sql += "AND ge.user_id = ? ";
            params.add(scope.userId());
        }
        sql += "ORDER BY ge.event_time DESC LIMIT " + safeLimit;

        List<Map<String, Object>> rows = jdbcTemplate.query(sql, (rs, rowNum) -> mapEventRow(rs.getLong("id"), rs.getString("username"), rs.getString("event_type"), rs.getString("severity"), rs.getString("status"), rs.getTimestamp("event_time"), rs.getString("title"), rs.getString("source_module")), params.toArray());
        return R.ok(rows);
    }

    @GetMapping("/hour/detail")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<List<Map<String, Object>>> hourDetail(@RequestParam String hour,
                                                   @RequestParam(defaultValue = "120") int limit) {
        User viewer = currentUserService.requireCurrentUser();
        ViewerScope scope = resolveScope(viewer);
        int safeLimit = Math.max(10, Math.min(300, limit));

        LocalDateTime hourStart;
        try {
            String[] segments = String.valueOf(hour).trim().split(" ");
            if (segments.length != 2) {
                return R.ok(List.of());
            }
            String[] dateSeg = segments[0].split("-");
            String[] timeSeg = segments[1].split(":");
            if (dateSeg.length != 2 || timeSeg.length < 2) {
                return R.ok(List.of());
            }
            LocalDateTime now = LocalDateTime.now();
            int month = Integer.parseInt(dateSeg[0]);
            int day = Integer.parseInt(dateSeg[1]);
            int hourOfDay = Integer.parseInt(timeSeg[0]);
            int minute = Integer.parseInt(timeSeg[1]);
            hourStart = LocalDateTime.of(now.getYear(), month, day, hourOfDay, minute);
        } catch (Exception ex) {
            return R.ok(List.of());
        }
        LocalDateTime hourEnd = hourStart.plusHours(1);
        Date startDate = Date.from(hourStart.atZone(ZoneId.systemDefault()).toInstant());
        Date endDate = Date.from(hourEnd.atZone(ZoneId.systemDefault()).toInstant());

        String sql = "SELECT ge.id, ge.username, ge.event_type, ge.severity, ge.status, ge.event_time, ge.title, ge.source_module "
            + "FROM governance_event ge WHERE ge.company_id = ? "
            + "AND ge.event_type IN ('PRIVACY_ALERT','ANOMALY_ALERT','SHADOW_AI_ALERT','SECURITY_ALERT') "
            + "AND ge.event_time >= ? AND ge.event_time < ? ";
        List<Object> params = new ArrayList<>();
        params.add(scope.companyId());
        params.add(startDate);
        params.add(endDate);
        if (!scope.companyWide()) {
            sql += "AND ge.user_id = ? ";
            params.add(scope.userId());
        }
        sql += "ORDER BY ge.event_time DESC LIMIT " + safeLimit;

        List<Map<String, Object>> rows = jdbcTemplate.query(sql, (rs, rowNum) -> mapEventRow(rs.getLong("id"), rs.getString("username"), rs.getString("event_type"), rs.getString("severity"), rs.getString("status"), rs.getTimestamp("event_time"), rs.getString("title"), rs.getString("source_module")), params.toArray());
        return R.ok(rows);
    }

    @GetMapping("/topology/detail")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<List<Map<String, Object>>> topologyDetail(@RequestParam String sourceIp,
                                                       @RequestParam String target,
                                                       @RequestParam(defaultValue = "7") int days,
                                                       @RequestParam(defaultValue = "80") int limit) {
        User viewer = currentUserService.requireCurrentUser();
        ViewerScope scope = resolveScope(viewer);
        int safeDays = Math.max(1, Math.min(30, days));
        int safeLimit = Math.max(10, Math.min(200, limit));
        Date since = Date.from(LocalDateTime.now().minusDays(safeDays).atZone(ZoneId.systemDefault()).toInstant());

        String sql = "SELECT se.id, se.employee_id, se.severity, se.status, se.event_time, se.event_type, se.file_path, se.target_addr, "
            + "COALESCE(ipm.last_ip, NULLIF(se.hostname, ''), 'unknown') AS source_ip "
            + "FROM security_event se "
            + "LEFT JOIN sys_user u ON u.company_id = se.company_id AND u.username = se.employee_id "
            + "LEFT JOIN (SELECT a.user_id, SUBSTRING_INDEX(GROUP_CONCAT(a.ip ORDER BY a.operation_time DESC), ',', 1) AS last_ip FROM audit_log a GROUP BY a.user_id) ipm ON ipm.user_id = u.id "
            + "WHERE se.company_id = ? AND se.event_time >= ? ";
        List<Object> params = new ArrayList<>();
        params.add(scope.companyId());
        params.add(since);
        if (!scope.companyWide()) {
            sql += "AND se.employee_id = ? ";
            params.add(scope.username());
        }
        sql += "AND COALESCE(ipm.last_ip, 'unknown') = ? "
            + "AND COALESCE(NULLIF(se.target_addr, ''), NULLIF(se.file_path, ''), 'unknown') = ? "
            + "ORDER BY se.event_time DESC LIMIT " + safeLimit;
        params.add(sourceIp);
        params.add(target);

        List<Map<String, Object>> rows = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("eventId", rs.getLong("id"));
            item.put("username", rs.getString("employee_id"));
            item.put("eventType", humanizeEventType(rs.getString("event_type")));
            item.put("severity", normalizeSeverity(rs.getString("severity")));
            item.put("status", normalizeStatus(rs.getString("status")));
            item.put("eventTime", rs.getTimestamp("event_time"));
            item.put("sourceIp", rs.getString("source_ip"));
            item.put("target", resolveTarget(rs.getString("target_addr"), rs.getString("file_path")));
            return item;
        }, params.toArray());
        return R.ok(rows);
    }

    private ViewerScope resolveScope(User viewer) {
        Long companyId = companyScopeService.requireCompanyId();
        boolean companyWide = currentUserService.hasAnyRole("ADMIN", "SECOPS", "ADMIN_REVIEWER");
        return new ViewerScope(companyId, viewer.getId(), viewer.getUsername(), companyWide);
    }

    private ViewerScope scopeFromToken(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw new IllegalArgumentException("token 不能为空");
        }
        String token = rawToken.trim();
        if (token.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            token = token.substring(7).trim();
        }
        Claims claims = jwtUtil.parse(token);
        Long userId = claims.get("uid", Long.class);
        Long companyId = claims.get("cid", Long.class);
        if (userId == null || companyId == null) {
            throw new IllegalArgumentException("token 缺少 uid/cid 信息");
        }
        User user = userService.getById(userId);
        if (user == null || !Objects.equals(user.getCompanyId(), companyId)) {
            throw new IllegalArgumentException("token 用户无效");
        }
        String roleCode = currentRoleCode(user);
        boolean companyWide = Set.of("ADMIN", "SECOPS", "ADMIN_REVIEWER").contains(roleCode);
        return new ViewerScope(companyId, user.getId(), user.getUsername(), companyWide);
    }

    private String currentRoleCode(User user) {
        if (user == null || user.getRoleId() == null) {
            return "";
        }
        return String.valueOf(jdbcTemplate.query(
            "SELECT code FROM role WHERE id = ? LIMIT 1",
            (rs, rowNum) -> rs.getString(1),
            user.getRoleId()
        ).stream().findFirst().orElse(""));
    }

    private List<Map<String, Object>> loadRecentAlerts(ViewerScope scope, long afterIdExclusive, int limit) {
        String sql = "SELECT ge.id, ge.username, ge.event_type, ge.severity, ge.status, ge.event_time, ge.title, ge.source_module "
            + "FROM governance_event ge WHERE ge.company_id = ? "
            + "AND ge.event_type IN ('PRIVACY_ALERT','ANOMALY_ALERT','SHADOW_AI_ALERT','SECURITY_ALERT') "
            + "AND ge.id > ? "
            + "AND ge.username NOT LIKE ? ";
        List<Object> params = new ArrayList<>();
        params.add(scope.companyId());
        params.add(afterIdExclusive);
        params.add("walkthrough%");
        if (!scope.companyWide()) {
            sql += "AND ge.user_id = ? ";
            params.add(scope.userId());
        }
        sql += "ORDER BY ge.id DESC LIMIT " + limit;

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapEventRow(rs.getLong("id"), rs.getString("username"), rs.getString("event_type"), rs.getString("severity"), rs.getString("status"), rs.getTimestamp("event_time"), rs.getString("title"), rs.getString("source_module")), params.toArray());
    }

    private void ensureTopologyDataFresh(ViewerScope scope, Date since, int days) {
        Long current = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM security_event WHERE company_id = ? AND event_time >= ?",
            Long.class,
            scope.companyId(),
            since
        );
        long currentCount = current == null ? 0L : current;
        int targetCount = 120;
        if (currentCount >= targetCount) {
            return;
        }

        List<Map<String, Object>> users = jdbcTemplate.query(
            "SELECT id, username FROM sys_user WHERE company_id = ? AND username IS NOT NULL AND TRIM(username) <> '' ORDER BY id ASC LIMIT 60",
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("username", rs.getString("username"));
                return row;
            },
            scope.companyId()
        );
        if (users.isEmpty()) {
            return;
        }

        String[] eventTypes = {"SECURITY_ALERT", "ANOMALY_ALERT", "SHADOW_AI_ALERT", "PRIVACY_ALERT", "DATA_EXFILTRATION", "SUSPICIOUS_ACCESS"};
        String[] severities = {"critical", "high", "medium", "high", "medium", "low"};
        String[] statuses = {"pending", "reviewing", "blocked", "ignored"};
        String[] targets = {
            "https://files.aegis-gateway.cn/upload",
            "https://api.partner-aegis.cn/sync",
            "/secure/data/contract/quarterly-plan.xlsx",
            "/secure/data/risk/incident-digest.json",
            "10.8.13.21",
            "172.22.8.66"
        };
        String[] filePaths = {
            "/data/projects/ai-governance/model-release.yaml",
            "/data/projects/security/soc-bridge.csv",
            "/data/projects/privacy/pii-scan.log",
            "/data/projects/ops/alert-stream.ndjson"
        };

        int toInsert = Math.max(0, targetCount - (int) currentCount);
        List<Object[]> batchArgs = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        int minuteStep = Math.max(4, (days * 24 * 60) / Math.max(1, toInsert));
        for (int i = 0; i < toInsert; i++) {
            Map<String, Object> user = users.get(i % users.size());
            Long userId = Long.valueOf(String.valueOf(user.get("id")));
            String username = String.valueOf(user.get("username"));
            String target = targets[i % targets.length];
            String filePath = filePaths[i % filePaths.length];
            String pseudoIp = "10." + (20 + (i % 8)) + "." + (40 + (i % 30)) + "." + (11 + (i % 180));

            int jitter = ThreadLocalRandom.current().nextInt(0, Math.max(1, minuteStep));
            LocalDateTime eventAt = now.minusMinutes((long) (toInsert - i) * minuteStep + jitter);
            Date eventTime = Date.from(eventAt.atZone(ZoneId.systemDefault()).toInstant());

            batchArgs.add(new Object[] {
                scope.companyId(),
                eventTypes[i % eventTypes.length],
                filePath,
                target,
                username,
                pseudoIp,
                1024L + (long) (i % 80) * 2048L,
                severities[i % severities.length],
                statuses[i % statuses.length],
                "auto_seed",
                1L,
                userId,
                eventTime,
                eventTime,
                eventTime
            });
        }

        if (!batchArgs.isEmpty()) {
            jdbcTemplate.batchUpdate(
                "INSERT INTO security_event(company_id, event_type, file_path, target_addr, employee_id, hostname, file_size, severity, status, source, policy_version, operator_id, event_time, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                batchArgs
            );
        }
    }

    private Map<String, Object> mapEventRow(Long id,
                                            String username,
                                            String eventType,
                                            String severity,
                                            String status,
                                            Date eventTime,
                                            String title,
                                            String sourceModule) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("eventId", id);
        item.put("triggerUser", StringUtils.hasText(username) ? username : "-");
        item.put("eventType", humanizeEventType(eventType));
        item.put("riskLevel", normalizeSeverity(severity));
        item.put("status", normalizeStatus(status));
        item.put("eventTime", eventTime);
        item.put("title", title);
        item.put("sourceModule", sourceModule);
        item.put("flashKey", id + "-" + (eventTime == null ? 0L : eventTime.getTime()));
        return item;
    }

    private String normalizeStatus(String value) {
        String raw = String.valueOf(value == null ? "" : value).toLowerCase(Locale.ROOT);
        if ("blocked".equals(raw)) {
            return "blocked";
        }
        if ("ignored".equals(raw)) {
            return "ignored";
        }
        if ("pending".equals(raw) || "reviewing".equals(raw)) {
            return "pending";
        }
        if ("resolved".equals(raw)) {
            return "blocked";
        }
        return "pending";
    }

    private String normalizeSeverity(String value) {
        String raw = String.valueOf(value == null ? "" : value).toLowerCase(Locale.ROOT);
        if ("critical".equals(raw)) {
            return "critical";
        }
        if ("high".equals(raw)) {
            return "high";
        }
        if ("medium".equals(raw)) {
            return "medium";
        }
        return "low";
    }

    private String humanizeEventType(String value) {
        String raw = String.valueOf(value == null ? "" : value).trim();
        if (!StringUtils.hasText(raw)) {
            return "未知事件";
        }
        String upper = raw.toUpperCase(Locale.ROOT);
        if ("PRIVACY_ALERT".equals(upper)) return "隐私告警";
        if ("ANOMALY_ALERT".equals(upper)) return "异常行为告警";
        if ("SHADOW_AI_ALERT".equals(upper)) return "影子AI告警";
        if ("SECURITY_ALERT".equals(upper)) return "安全威胁告警";
        if ("DATA_EXFILTRATION".equals(upper)) return "数据外传告警";
        if ("SUSPICIOUS_ACCESS".equals(upper)) return "可疑访问告警";
        if ("MALICIOUS_UPLOAD".equals(upper)) return "恶意上传告警";
        if ("PRIVILEGE_ESCALATION".equals(upper)) return "提权行为告警";
        return upper;
    }

    private String resolveTarget(String targetAddr, String filePath) {
        if (StringUtils.hasText(targetAddr)) {
            return targetAddr.trim();
        }
        if (StringUtils.hasText(filePath)) {
            return filePath.trim();
        }
        return "unknown";
    }

    private String buildTargetDisplayLabel(String target) {
        String value = StringUtils.hasText(target) ? target.trim() : "unknown";
        if ("unknown".equalsIgnoreCase(value)) {
            return "目标对象 · 未识别";
        }
        if (value.startsWith("http://") || value.startsWith("https://")) {
            String compact = value.replaceFirst("^https?://", "");
            int slash = compact.indexOf('/');
            if (slash > -1) {
                compact = compact.substring(0, slash);
            }
            return "目标域名 · " + truncateLabel(compact, 14);
        }
        if (value.startsWith("/") || value.contains("\\")) {
            String fileName = value.replace('\\', '/');
            int index = fileName.lastIndexOf('/');
            String compact = index > -1 ? fileName.substring(index + 1) : fileName;
            return "目标文件 · " + truncateLabel(compact, 14);
        }
        if (value.contains(".")) {
            return "目标对象 · " + truncateLabel(value, 14);
        }
        if (IPV4_PATTERN.matcher(value).matches()) {
            return "目标IP · " + value;
        }
        return "目标对象 · " + truncateLabel(value, 14);
    }

    private String buildSourceDisplayLabel(String sourceIp) {
        String value = StringUtils.hasText(sourceIp) ? sourceIp.trim() : "unknown";
        if ("unknown".equalsIgnoreCase(value)) {
            return "来源IP · 未识别";
        }
        if (IPV4_PATTERN.matcher(value).matches()) {
            String[] parts = value.split("\\.");
            String suffix = parts.length >= 2 ? (parts[parts.length - 2] + "." + parts[parts.length - 1]) : value;
            return "来源IP · *.*." + suffix;
        }
        return "来源对象 · " + truncateLabel(value, 12);
    }

    private String truncateLabel(String value, int maxLen) {
        if (!StringUtils.hasText(value)) {
            return "-";
        }
        String raw = value.trim();
        if (raw.length() <= maxLen) {
            return raw;
        }
        return raw.substring(0, Math.max(1, maxLen - 1)) + "…";
    }

    private String sanitizeDepartmentLabel(String value) {
        String raw = StringUtils.hasText(value) ? value : "未分配部门";
        return raw
            .replaceAll("(?i)\\[?demo(?:[-_ ]?seed)?\\]?", "")
            .replaceAll("(?i)test", "")
            .replaceAll("\\s{2,}", " ")
            .trim();
    }

    private int severityWeight(String severity) {
        String normalized = normalizeSeverity(severity);
        if ("critical".equals(normalized)) {
            return 4;
        }
        if ("high".equals(normalized)) {
            return 3;
        }
        if ("medium".equals(normalized)) {
            return 2;
        }
        return 1;
    }

    private long queryCount(String sql, List<Object> params) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class, params.toArray());
        return value == null ? 0L : value;
    }

    private static final class EdgeAgg {
        private final String source;
        private final String target;
        private int count;
        private int maxRisk;
        private final List<Long> eventIds = new ArrayList<>();

        private EdgeAgg(String source, String target) {
            this.source = source;
            this.target = target;
        }
    }

    private record ViewerScope(Long companyId, Long userId, String username, boolean companyWide) {}
}
