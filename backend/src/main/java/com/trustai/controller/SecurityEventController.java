package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trustai.entity.SecurityDetectionRule;
import com.trustai.entity.SecurityEvent;
import com.trustai.entity.User;
import com.trustai.entity.AuditLog;
import com.trustai.service.ClientIngressAuthService;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.EventHubService;
import com.trustai.service.SecurityDetectionRuleService;
import com.trustai.service.SecurityEventService;
import com.trustai.service.AuditLogService;
import com.trustai.service.UserService;
import com.trustai.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 安全事件（威胁监控）API。
 *
 * <p>提供：
 * <ul>
 *   <li>GET  /api/security/events        — 分页查询安全事件（需登录）</li>
 *   <li>POST /api/security/block         — 阻拦某事件（需登录）</li>
 *   <li>POST /api/security/ignore        — 忽略某事件（需登录）</li>
 *   <li>POST /api/security/events/report — 上报事件（无需登录，供模拟程序使用）</li>
 *   <li>GET  /api/security/rules         — 查询检测规则（需登录）</li>
 *   <li>POST /api/security/rules         — 新增/更新检测规则（需登录）</li>
 *   <li>GET  /api/security/stats         — 事件统计摘要（需登录）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/security")
public class SecurityEventController {

    private static final Set<String> VALID_STATUSES = Set.of("pending", "blocked", "ignored", "reviewing");
    private static final Set<String> VALID_SEVERITIES = Set.of("critical", "high", "medium", "low");

    @Autowired
    private SecurityEventService securityEventService;

    @Autowired
    private SecurityDetectionRuleService ruleService;

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private CompanyScopeService companyScopeService;

    @Autowired
    private UserService userService;

    @Autowired
    private EventHubService eventHubService;

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private ClientIngressAuthService clientIngressAuthService;

    // ── 事件列表（分页） ──────────────────────────────────────────────────────────

    /**
     * GET /api/security/events
     *
     * @param page     页码（从 1 开始）
     * @param pageSize 每页条数（默认 20）
     * @param status   状态过滤（pending/blocked/ignored/reviewing）
     * @param severity 严重程度过滤（critical/high/medium/low）
     * @param keyword  关键字（匹配 filePath / hostname / employeeId）
     */
    @GetMapping("/events")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS')")
    public R<Map<String, Object>> events(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String severity,
            @RequestParam(required = false) String keyword) {

        currentUserService.requireAnyRole("ADMIN", "SECOPS");

        String normalizedStatus = status == null ? null : status.trim().toLowerCase();
        String normalizedSeverity = severity == null ? null : severity.trim().toLowerCase();
        // 非法枚举降级为无过滤，避免前端历史参数导致接口整体失败。
        if (normalizedStatus != null && !normalizedStatus.isBlank() && !VALID_STATUSES.contains(normalizedStatus)) {
            normalizedStatus = null;
        }
        if (normalizedSeverity != null && !normalizedSeverity.isBlank() && !VALID_SEVERITIES.contains(normalizedSeverity)) {
            normalizedSeverity = null;
        }
        if (keyword != null && keyword.length() > 200) {
            return R.error(40000, "keyword 长度不得超过 200 字符");
        }

        User currentUser = currentUserService.requireCurrentUser();
        boolean employeeOnly = currentUserService.isEmployeeUser();

        QueryWrapper<SecurityEvent> qw = new QueryWrapper<>();
        companyScopeService.withCompany(qw);
        if (normalizedStatus != null && !normalizedStatus.isBlank()) {
            qw.eq("status", normalizedStatus);
        }
        if (normalizedSeverity != null && !normalizedSeverity.isBlank()) {
            qw.eq("severity", normalizedSeverity);
        }
        if (keyword != null && !keyword.isBlank()) {
            qw.and(w -> w.like("file_path", keyword)
                    .or().like("hostname", keyword)
                    .or().like("employee_id", keyword));
        }
        if (employeeOnly) {
            qw.eq("employee_id", currentUser.getUsername());
        }
        qw.orderByDesc("event_time");

        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        Page<SecurityEvent> result = securityEventService.page(new Page<>(safePage, safePageSize), qw);
        List<SecurityEvent> mixedRecords = mixByEmployee(result.getRecords());
        alignHostByEmployee(mixedRecords);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", result.getTotal());
        data.put("pages", result.getPages());
        data.put("current", result.getCurrent());
        data.put("list", mixedRecords);
        return R.ok(data);
    }

    // ── 阻拦事件 ────────────────────────────────────────────────────────────────

    /**
     * POST /api/security/block
     *
     * <p>将指定事件状态改为 "blocked"。
     */
    @PostMapping("/block")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS') || @currentUserService.hasPermission('security:event:handle')")
    public R<?> block(@RequestBody IdReq req) {
        if (!currentUserService.hasAnyRole("ADMIN", "SECOPS")) {
            currentUserService.requirePermission("security:event:handle");
        }
        enforceSecopsDuty("block");
        SecurityEvent event = securityEventService.getOne(companyScopeService.withCompany(new QueryWrapper<SecurityEvent>()).eq("id", req.getId()));
        if (event == null) {
            return R.error(40400, "事件不存在");
        }
        event.setStatus("blocked");
        event.setOperatorId(currentUserService.requireCurrentUser().getId());
        event.setUpdateTime(new Date());
        securityEventService.updateById(event);
        eventHubService.syncGovernanceStatus("security", event.getId(), "blocked", event.getOperatorId(), "阻拦处置");
        saveAudit("security_block", event, "success");
        return R.okMsg("已阻拦");
    }

    // ── 忽略事件 ────────────────────────────────────────────────────────────────

    /**
     * POST /api/security/ignore
     *
     * <p>将指定事件状态改为 "ignored"。
     */
    @PostMapping("/ignore")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS') || @currentUserService.hasPermission('security:event:handle')")
    public R<?> ignore(@RequestBody IdReq req) {
        if (!currentUserService.hasAnyRole("ADMIN", "SECOPS")) {
            currentUserService.requirePermission("security:event:handle");
        }
        enforceSecopsDuty("ignore");
        SecurityEvent event = securityEventService.getOne(companyScopeService.withCompany(new QueryWrapper<SecurityEvent>()).eq("id", req.getId()));
        if (event == null) {
            return R.error(40400, "事件不存在");
        }
        event.setStatus("ignored");
        event.setOperatorId(currentUserService.requireCurrentUser().getId());
        event.setUpdateTime(new Date());
        securityEventService.updateById(event);
        eventHubService.syncGovernanceStatus("security", event.getId(), "ignored", event.getOperatorId(), "忽略处置");
        saveAudit("security_ignore", event, "success");
        return R.okMsg("已忽略");
    }

    // ── 上报事件（供模拟程序使用，无需登录） ──────────────────────────────────

    /**
     * POST /api/security/events/report
     *
     * <p>模拟程序通过此接口上报窃取事件。不需要登录 token，
     * 但需要请求体中包含合法的事件字段。
     */
    @PostMapping("/events/report")
    public R<?> report(@RequestHeader(value = "X-Client-Token", required = false) String clientToken,
                       @RequestHeader(value = "X-Company-Id", required = false) Long headerCompanyId,
                       @RequestBody SecurityEvent event) {
        if (!clientIngressAuthService.isAuthorized(clientToken)) {
            return R.error(40100, "客户端令牌无效");
        }

        if (event.getEmployeeId() == null || event.getEmployeeId().isBlank()) {
            return R.error(40000, "employeeId 不能为空");
        }

        Long companyId = null;
        User reporter = userService.lambdaQuery()
            .eq(User::getUsername, event.getEmployeeId())
            .eq(headerCompanyId != null, User::getCompanyId, headerCompanyId)
            .one();
        if (reporter != null) {
            companyId = reporter.getCompanyId();
        }
        if (headerCompanyId != null) {
            if (companyId != null && !headerCompanyId.equals(companyId)) {
                return R.error(40000, "companyId 与 employeeId 归属不一致");
            }
            companyId = headerCompanyId;
        }
        if (companyId == null || reporter == null) {
            return R.error(40000, "无法绑定合法账号与企业");
        }
        event.setCompanyId(companyId);
        event.setEmployeeId(reporter.getUsername());
        event.setHostname(normalizeHostname(event.getHostname(), reporter.getUsername()));
        if (event.getEventTime() == null) {
            event.setEventTime(new Date());
        }
        if (event.getStatus() == null || event.getStatus().isBlank()) {
            event.setStatus("pending");
        }
        if (event.getSource() == null || event.getSource().isBlank()) {
            event.setSource("agent");
        }
        if (event.getPolicyVersion() == null) {
            event.setPolicyVersion(eventHubService.resolvePolicyVersion(companyId));
        }
        event.setId(null);
        event.setCreateTime(new Date());
        event.setUpdateTime(new Date());
        securityEventService.save(event);
        var governanceEvent = eventHubService.ingestSecurityEvent(event, reporter, Map.of(
            "eventType", event.getEventType() == null ? "" : event.getEventType(),
            "filePath", event.getFilePath() == null ? "" : event.getFilePath(),
            "targetAddr", event.getTargetAddr() == null ? "" : event.getTargetAddr(),
            "source", event.getSource() == null ? "" : event.getSource()
        ));
        Long newId = event.getId();
        if (newId == null) {
            return R.ok(Map.of());
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", newId);
        data.put("governanceEventId", governanceEvent == null ? null : governanceEvent.getId());
        data.put("subjectUserId", reporter.getId());
        return R.ok(data);
    }

    // ── 检测规则 ────────────────────────────────────────────────────────────────

    /** GET /api/security/rules — 查询所有检测规则 */
    @GetMapping("/rules")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS') || @currentUserService.hasAnyPermission('security:rule:manage','security:event:view')")
    public R<List<SecurityDetectionRule>> rules() {
        if (!currentUserService.hasAnyRole("ADMIN", "SECOPS")) {
            currentUserService.requireAnyPermission("security:rule:manage", "security:event:view");
        }
        return R.ok(ruleService.list(new QueryWrapper<SecurityDetectionRule>().orderByAsc("id")));
    }

    /** POST /api/security/rules — 新增或更新检测规则 */
    @PostMapping("/rules")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS') || @currentUserService.hasPermission('security:rule:manage')")
    public R<?> saveRule(@RequestBody SecurityDetectionRule rule) {
        if (!currentUserService.hasAnyRole("ADMIN", "SECOPS")) {
            currentUserService.requirePermission("security:rule:manage");
        }
        enforceSecopsDuty("rule_manage");
        if (rule.getCreateTime() == null) rule.setCreateTime(new Date());
        rule.setUpdateTime(new Date());
        ruleService.saveOrUpdate(rule);
        return R.okMsg("保存成功");
    }

    /** DELETE /api/security/rules/{id} — 删除检测规则 */
    @DeleteMapping("/rules/{id}")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS') || @currentUserService.hasPermission('security:rule:manage')")
    public R<?> deleteRule(@PathVariable Long id) {
        if (!currentUserService.hasAnyRole("ADMIN", "SECOPS")) {
            currentUserService.requirePermission("security:rule:manage");
        }
        enforceSecopsDuty("rule_manage");
        ruleService.removeById(id);
        return R.okMsg("删除成功");
    }

    private void enforceSecopsDuty(String action) {
        if (!currentUserService.hasRole("SECOPS")) {
            return;
        }
        // Canonical role model no longer differentiates secops sub-roles.
    }

    // ── 统计摘要 ────────────────────────────────────────────────────────────────

    /** GET /api/security/stats — 事件统计摘要 */
    @GetMapping("/stats")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS') || @currentUserService.hasAnyPermission('security:event:view','security:event:handle')")
    public R<Map<String, Object>> stats() {
        if (!currentUserService.hasAnyRole("ADMIN", "SECOPS")) {
            currentUserService.requireAnyPermission("security:event:view", "security:event:handle");
        }
        User currentUser = currentUserService.requireCurrentUser();
        boolean employeeOnly = currentUserService.isEmployeeUser();

        long total;
        long pending;
        long blocked;
        long critical;
        long high;
        try {
            total = securityEventService.count(scopedQuery(currentUser, employeeOnly));
            pending = securityEventService.count(scopedQuery(currentUser, employeeOnly).eq("status", "pending"));
            blocked = securityEventService.count(scopedQuery(currentUser, employeeOnly).eq("status", "blocked"));
            critical = securityEventService.count(scopedQuery(currentUser, employeeOnly).eq("severity", "critical"));
            high = securityEventService.count(scopedQuery(currentUser, employeeOnly).eq("severity", "high"));
        } catch (Exception ex) {
            total = 0L;
            pending = 0L;
            blocked = 0L;
            critical = 0L;
            high = 0L;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("total", total);
        data.put("pending", pending);
        data.put("blocked", blocked);
        data.put("critical", critical);
        data.put("high", high);
        return R.ok(data);
    }

    private QueryWrapper<SecurityEvent> scopedQuery(User user, boolean employeeOnly) {
        QueryWrapper<SecurityEvent> query = companyScopeService.withCompany(new QueryWrapper<>());
        if (employeeOnly && user != null) {
            query.eq("employee_id", user.getUsername());
        }
        return query;
    }

    // ── 内部类 ────────────────────────────────────────────────────────────────

    public static class IdReq {
        private Long id;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
    }

    private void saveAudit(String operation, SecurityEvent event, String result) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(currentUserService.requireCurrentUser().getId());
            log.setOperation(operation);
            log.setOperationTime(new Date());
            log.setInputOverview("eventId=" + event.getId() + ", type=" + event.getEventType());
            log.setOutputOverview("status=" + event.getStatus());
            log.setResult(result);
            log.setRiskLevel("MEDIUM");
            log.setHash(String.valueOf(System.currentTimeMillis()));
            log.setCreateTime(new Date());
            auditLogService.saveAudit(log);
        } catch (Exception ignored) {
            // keep business flow stable
        }
    }

    private void alignHostByEmployee(List<SecurityEvent> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Map<String, String> canonical = new HashMap<>();
        for (SecurityEvent item : records) {
            if (item == null) {
                continue;
            }
            String employee = item.getEmployeeId() == null ? "" : item.getEmployeeId().trim().toLowerCase();
            if (employee.isBlank()) {
                continue;
            }
            String host = normalizeHostname(item.getHostname(), employee);
            String chosen = canonical.computeIfAbsent(employee, k -> host);
            item.setHostname(chosen);
        }
    }

    private List<SecurityEvent> mixByEmployee(List<SecurityEvent> records) {
        List<SecurityEvent> source = records == null ? List.of() : records;
        if (source.size() <= 2) {
            return source;
        }
        Map<String, List<SecurityEvent>> buckets = new LinkedHashMap<>();
        for (SecurityEvent item : source) {
            String key = item == null || item.getEmployeeId() == null ? "unknown" : item.getEmployeeId().trim().toLowerCase();
            buckets.computeIfAbsent(key, k -> new ArrayList<>()).add(item);
        }
        List<String> keys = new ArrayList<>(buckets.keySet());
        Collections.shuffle(keys);

        List<SecurityEvent> mixed = new ArrayList<>(source.size());
        boolean progressed = true;
        while (mixed.size() < source.size() && progressed) {
            progressed = false;
            for (String key : keys) {
                List<SecurityEvent> bucket = buckets.get(key);
                if (bucket != null && !bucket.isEmpty()) {
                    mixed.add(bucket.remove(0));
                    progressed = true;
                }
            }
        }
        return mixed;
    }

    private String normalizeHostname(String rawHost, String employee) {
        String host = rawHost == null ? "" : rawHost.trim();
        String emp = employee == null ? "" : employee.trim().toLowerCase();
        if (host.isBlank()) {
            return emp.isBlank() ? "unknown-host" : emp + "-host";
        }
        if (emp.isBlank()) {
            return host;
        }
        String normalizedHost = host.toLowerCase();
        if (normalizedHost.contains("node-") || normalizedHost.contains("shared") || normalizedHost.contains("cluster")) {
            return emp + "-host";
        }
        return host;
    }
}
