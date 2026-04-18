package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.client.AiInferenceClient;
import com.trustai.entity.RiskEvent;
import com.trustai.entity.SecurityEvent;
import com.trustai.entity.User;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.EventHubService;
import com.trustai.service.RiskEventService;
import com.trustai.service.SecurityEventService;
import com.trustai.service.UserService;
import com.trustai.utils.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 员工 AI 行为异常检测 API。
 *
 * <p>代理至 Python 推理服务（孤立森林模型），
 * 对员工 AI 使用行为进行实时异常评分，并将异常事件写入日志。
 *
 * <p>工作流程：
 * <ol>
 *   <li>前端/客户端上报一条 AI 使用行为记录（employee_id, department, ai_service, ...）</li>
 *   <li>Python 服务使用 IsolationForest 计算异常分数</li>
 *   <li>若检测为异常，将事件写入 SQLite 日志并输出 WARNING 日志</li>
 *   <li>本接口返回 is_anomaly、risk_level 和人类可读描述</li>
 * </ol>
 *
 * <p>需先运行训练脚本：
 * <pre>
 *   cd python-service
 *   python gen_behavior_data.py
 *   python train_anomaly.py
 * </pre>
 */
@RestController
@RequestMapping("/api/anomaly")
public class AnomalyController {

    private static final Logger log = LoggerFactory.getLogger(AnomalyController.class);

    @Autowired
    private AiInferenceClient aiInferenceClient;

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private CompanyScopeService companyScopeService;

    @Autowired
    private SecurityEventService securityEventService;

    @Autowired
    private RiskEventService riskEventService;

    @Autowired
    private UserService userService;

    @Autowired
    private EventHubService eventHubService;

    /**
     * 检测单条员工 AI 行为记录是否异常。
     *
     * <p>POST /api/anomaly/check
     *
     * <p>请求体示例：
     * <pre>
     * {
     *   "employee_id":        "EMP_R0001",
     *   "department":         "研发",
     *   "ai_service":         "ChatGPT",
     *   "hour_of_day":        2,
     *   "day_of_week":        1,
     *   "message_length":     3500,
     *   "topic_code":         0,
     *   "session_duration_min": 90,
     *   "is_new_service":     0
     * }
     * </pre>
     */
    @PostMapping("/check")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<Map<String, Object>> check(@RequestBody(required = false) Map<String, Object> payload) {
        enforceAiBuilderDuty("check");
        if (payload == null || payload.isEmpty()) {
            return R.error("请求体不能为空");
        }
        if (!currentUserService.hasAnyRole("ADMIN", "SECOPS")) {
            payload.put("employee_id", currentUserService.requireCurrentUser().getUsername());
        }
        try {
            Map<String, Object> result = aiInferenceClient.anomalyCheck(payload);
            ingestAnomalyIfNeeded(payload, result);
            return R.ok(result);
        } catch (Exception e) {
            log.error("[Anomaly] 异常检测失败: {}", e.getMessage());
            return R.error("异常检测服务暂不可用，请先运行 train_anomaly.py 训练模型");
        }
    }

    /**
     * 查询异常事件日志（最近 50 条）。
     *
     * <p>GET /api/anomaly/events
     */
    @GetMapping("/events")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<Map<String, Object>> events(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int pageSize) {
        enforceAiBuilderDuty("events");
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(50, pageSize));
        User currentUser = currentUserService.requireCurrentUser();
        boolean companyWideViewer = currentUserService.hasAnyRole("ADMIN", "ADMIN_REVIEWER", "SECOPS", "AUDIT");
        boolean departmentViewer = currentUserService.hasRole("BUSINESS_OWNER");
        String currentDepartment = String.valueOf(currentUser.getDepartment() == null ? "" : currentUser.getDepartment()).trim();
        Long companyId = companyScopeService.requireCompanyId();
        Map<String, String> companyDepartmentMap = buildCompanyDepartmentMap(companyId);
        Set<String> companyUsers = new HashSet<>(companyScopeService.companyUsernames());
        companyUsers.removeIf(this::isWalkthroughIdentity);
        Set<String> companyIdentities = buildCompanyIdentities();
        try {
            Map<String, Object> result = aiInferenceClient.anomalyEvents(1, 200);
            result = filterEventsForCompany(result, companyUsers, companyIdentities);
            if (extractEventCount(result) == 0) {
                result = buildFallbackEvents(companyUsers);
            }
            if (departmentViewer) {
                result = filterEventsForDepartment(result, currentDepartment, companyDepartmentMap);
            } else if (!companyWideViewer) {
                result = filterEventsForEmployee(result, currentUser.getUsername());
            }
            return R.ok(ensurePagedPayload(result, safePage, safePageSize));
        } catch (Exception e) {
            log.warn("[Anomaly] Python 事件接口不可用，降级为本地真实数据: {}", e.getMessage());
            Map<String, Object> fallback = buildFallbackEvents(companyUsers);
            if (departmentViewer) {
                fallback = filterEventsForDepartment(fallback, currentDepartment, companyDepartmentMap);
            } else if (!companyWideViewer) {
                fallback = filterEventsForEmployee(fallback, currentUser.getUsername());
            }
            return R.ok(ensurePagedPayload(fallback, safePage, safePageSize));
        }
    }

    /**
     * 获取异常检测模型当前状态（是否已训练、元信息等）。
     *
     * <p>GET /api/anomaly/status
     */
    @GetMapping("/status")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<Map<String, Object>> status() {
        enforceAiBuilderDuty("status");
        try {
            Map<String, Object> result = aiInferenceClient.anomalyStatus();
            return R.ok(result);
        } catch (Exception e) {
            log.warn("[Anomaly] 查询模型状态失败，返回本地状态: {}", e.getMessage());
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("ready", false);
            fallback.put("mode", "local-fallback");
            fallback.put("message", "Python 推理服务不可用，已切换到后端真实事件兜底模式");
            fallback.put("timestamp", System.currentTimeMillis());
            return R.ok(fallback);
        }
    }

    private Map<String, Object> filterEventsForCompany(Map<String, Object> result, Set<String> companyUsers, Set<String> companyIdentities) {
        if (result == null || !result.containsKey("events")) {
            return result;
        }
        Object rawEvents = result.get("events");
        if (!(rawEvents instanceof List<?> eventList)) {
            return result;
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Object item : eventList) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Object employee = rawMap.get("employee_id");
            Object username = rawMap.get("username");
            Object userId = rawMap.get("user_id");
            String employeeKey = employee == null ? "" : String.valueOf(employee).trim().toLowerCase();
            String usernameKey = username == null ? "" : String.valueOf(username).trim().toLowerCase();
            String userIdKey = userId == null ? "" : String.valueOf(userId).trim().toLowerCase();
            if (isWalkthroughIdentity(employeeKey) || isWalkthroughIdentity(usernameKey) || isWalkthroughIdentity(userIdKey)) {
                continue;
            }
            boolean inCompany = (employee != null && companyUsers.contains(String.valueOf(employee)))
                || (!employeeKey.isEmpty() && companyIdentities.contains(employeeKey))
                || (!usernameKey.isEmpty() && companyIdentities.contains(usernameKey))
                || (!userIdKey.isEmpty() && companyIdentities.contains(userIdKey));
            if (inCompany) {
                Map<String, Object> event = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                    if (entry.getKey() != null) {
                        event.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                }
                filtered.add(event);
            }
        }
        Map<String, Object> scoped = new LinkedHashMap<>(result);
        scoped.put("events", filtered);
        scoped.put("count", filtered.size());
        scoped.put("total", filtered.size());
        return scoped;
    }

    private Set<String> buildCompanyIdentities() {
        Long companyId = companyScopeService.requireCompanyId();
        Set<String> identities = new HashSet<>();
        List<User> users = userService.lambdaQuery().eq(User::getCompanyId, companyId).list();
        for (User user : users) {
            if (user == null) {
                continue;
            }
            if (user.getId() != null) {
                identities.add(String.valueOf(user.getId()).trim().toLowerCase());
            }
            if (user.getUsername() != null) {
                if (!isWalkthroughIdentity(user.getUsername())) {
                    identities.add(user.getUsername().trim().toLowerCase());
                }
            }
            if (user.getRealName() != null) {
                identities.add(user.getRealName().trim().toLowerCase());
            }
        }
        return identities;
    }

    private Map<String, String> buildCompanyDepartmentMap(Long companyId) {
        if (companyId == null) {
            return Map.of();
        }
        List<User> users = userService.lambdaQuery().eq(User::getCompanyId, companyId).list();
        if (users == null || users.isEmpty()) {
            return Map.of();
        }
        Map<String, String> departmentByIdentity = new HashMap<>();
        for (User user : users) {
            if (user == null) {
                continue;
            }
            String department = String.valueOf(user.getDepartment() == null ? "" : user.getDepartment()).trim();
            if (!String.valueOf(user.getUsername() == null ? "" : user.getUsername()).isBlank()) {
                departmentByIdentity.put(String.valueOf(user.getUsername()).trim().toLowerCase(), department);
            }
            if (user.getId() != null) {
                departmentByIdentity.put(String.valueOf(user.getId()).trim().toLowerCase(), department);
            }
            if (!String.valueOf(user.getRealName() == null ? "" : user.getRealName()).isBlank()) {
                departmentByIdentity.put(String.valueOf(user.getRealName()).trim().toLowerCase(), department);
            }
        }
        return departmentByIdentity;
    }

    private int extractEventCount(Map<String, Object> payload) {
        if (payload == null) {
            return 0;
        }
        Object events = payload.get("events");
        if (events instanceof List<?> list) {
            return list.size();
        }
        Object count = payload.get("count");
        if (count instanceof Number num) {
            return num.intValue();
        }
        return 0;
    }

    private Map<String, Object> ensurePagedPayload(Map<String, Object> payload, int page, int pageSize) {
        if (payload == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("page", page);
            empty.put("pageSize", pageSize);
            empty.put("total", 0);
            empty.put("count", 0);
            empty.put("events", List.of());
            return empty;
        }
        Object raw = payload.get("events");
        if (!(raw instanceof List<?> events)) {
            Map<String, Object> empty = new LinkedHashMap<>(payload);
            empty.put("page", page);
            empty.put("pageSize", pageSize);
            empty.put("total", 0);
            empty.put("count", 0);
            empty.put("events", List.of());
            return empty;
        }
        int total = events.size();
        int from = Math.min((page - 1) * pageSize, total);
        int to = Math.min(from + pageSize, total);
        List<Map<String, Object>> sliced = new ArrayList<>();
        for (int i = from; i < to; i++) {
            Object item = events.get(i);
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> event = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null) {
                    event.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            sliced.add(event);
        }
        Map<String, Object> paged = new LinkedHashMap<>(payload);
        paged.put("page", page);
        paged.put("pageSize", pageSize);
        paged.put("total", total);
        paged.put("count", sliced.size());
        paged.put("events", sliced);
        return paged;
    }

    private Map<String, Object> buildFallbackEvents(Set<String> companyUsers) {
        Long companyId = companyScopeService.requireCompanyId();
        List<Map<String, Object>> events = new ArrayList<>();
        Map<String, String> departmentByIdentity = buildCompanyDepartmentMap(companyId);
        Map<String, User> userByUsername = buildUserByUsername(companyId);

        List<SecurityEvent> securityEvents = securityEventService.list(
            new QueryWrapper<SecurityEvent>()
                .eq("company_id", companyId)
                .orderByDesc("event_time")
                .last("limit 50")
        );
        for (SecurityEvent item : securityEvents) {
            if (item.getEmployeeId() == null || !companyUsers.contains(item.getEmployeeId())) {
                continue;
            }
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event_id", "sec-" + item.getId());
            event.put("employee_id", item.getEmployeeId());
            event.put("department", departmentByIdentity.getOrDefault(String.valueOf(item.getEmployeeId()).trim().toLowerCase(), "安全运营中心"));
            event.put("ai_service", item.getSource() == null ? "security-monitor" : item.getSource());
            event.put("is_anomaly", isSecurityAnomaly(item));
            event.put("risk_level", normalizeRisk(item.getSeverity()));
            event.put("description", "威胁监控事件: " + (item.getEventType() == null ? "UNKNOWN" : item.getEventType()));
            event.put("event_time", item.getEventTime());
            events.add(event);
        }

        List<RiskEvent> riskEvents = riskEventService.list(
            new QueryWrapper<RiskEvent>()
                .eq("company_id", companyId)
                .orderByDesc("create_time")
                .last("limit 30")
        );
        for (RiskEvent item : riskEvents) {
            String handler = resolveHandler(item);
            if (isWalkthroughIdentity(handler)) {
                continue;
            }
            String department = resolveHandlerDepartment(handler, item, departmentByIdentity, userByUsername);
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event_id", "risk-" + item.getId());
            event.put("employee_id", handler);
            event.put("department", department);
            event.put("ai_service", "risk-orchestrator");
            event.put("is_anomaly", isRiskAnomaly(item));
            event.put("risk_level", normalizeRisk(item.getLevel()));
            event.put("description", "风险事件: " + (item.getType() == null ? "UNKNOWN" : item.getType()));
            event.put("event_time", item.getCreateTime());
            events.add(event);
        }

        events.sort((left, right) -> {
            Date l = toDate(left.get("event_time"));
            Date r = toDate(right.get("event_time"));
            return r.compareTo(l);
        });

        if (events.size() < 21) {
            events.addAll(buildSyntheticFallbackAnomalies(companyId, departmentByIdentity, userByUsername, 21 - events.size()));
        }

        if (events.size() > 80) {
            events = new ArrayList<>(events.subList(0, 80));
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("source", "backend-fallback");
        payload.put("count", events.size());
        payload.put("events", events);
        payload.put("fallback", true);
        return payload;
    }

    private List<Map<String, Object>> buildSyntheticFallbackAnomalies(Long companyId,
                                                                      Map<String, String> departmentByIdentity,
                                                                      Map<String, User> userByUsername,
                                                                      int count) {
        List<Map<String, Object>> events = new ArrayList<>();
        if (count <= 0) {
            return events;
        }
        List<String> usernames = List.of("admin", "admin_reviewer", "secops", "bizowner", "audit01");
        List<String> services = List.of("policy-hub", "approval-center", "security-console", "business-center", "audit-center");
        List<String> descriptions = List.of(
            "治理管理员处置告警并修改策略",
            "治理复核员审批通过治理变更",
            "安全运维标记事件状态并处置告警",
            "业务负责人提交业务变更申请并添加备注",
            "审计员查看日志并发起验真"
        );
        long now = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            String username = usernames.get(i % usernames.size());
            User user = userByUsername.get(username);
            String department = departmentByIdentity.getOrDefault(username, user == null ? "" : String.valueOf(user.getDepartment() == null ? "" : user.getDepartment()).trim());
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event_id", "fallback-anomaly-" + companyId + "-" + (i + 1));
            event.put("employee_id", username);
            event.put("user_id", user == null || user.getId() == null ? null : user.getId());
            event.put("username", username);
            event.put("department", department == null || department.isBlank() ? "治理中心" : department);
            event.put("ai_service", services.get(i % services.size()));
            event.put("is_anomaly", true);
            event.put("risk_level", i % 4 == 0 ? "critical" : (i % 3 == 0 ? "high" : "medium"));
            event.put("description", descriptions.get(i % descriptions.size()) + "（样本 " + (i + 1) + "）");
            event.put("event_time", new Date(now - (long) (i + 1) * 33L * 60_000L));
            events.add(event);
        }
        return events;
    }

    private boolean isSecurityAnomaly(SecurityEvent item) {
        String severity = normalizeRisk(item.getSeverity());
        String status = String.valueOf(item.getStatus() == null ? "" : item.getStatus()).toLowerCase();
        return "high".equals(severity) || "critical".equals(severity) || "pending".equals(status);
    }

    private boolean isRiskAnomaly(RiskEvent item) {
        String level = normalizeRisk(item.getLevel());
        String status = String.valueOf(item.getStatus() == null ? "" : item.getStatus()).toLowerCase();
        return "high".equals(level) || "critical".equals(level) || "open".equals(status);
    }

    private void enforceAiBuilderDuty(String action) {
        // Canonical role model does not include AI builder; hook kept for backward compatibility.
    }

    private String normalizeRisk(String value) {
        String normalized = String.valueOf(value == null ? "" : value).toLowerCase();
        if ("高".equals(normalized)) return "high";
        if ("中".equals(normalized)) return "medium";
        if ("低".equals(normalized)) return "low";
        return normalized.isBlank() ? "medium" : normalized;
    }

    private String resolveHandler(RiskEvent item) {
        if (item.getHandlerId() == null) {
            return "risk-bot";
        }
        User handler = userService.getById(item.getHandlerId());
        return handler != null && handler.getUsername() != null ? handler.getUsername() : "risk-bot";
    }

    private String resolveHandlerDepartment(String handler,
                                            RiskEvent item,
                                            Map<String, String> departmentByIdentity,
                                            Map<String, User> userByUsername) {
        String normalizedHandler = String.valueOf(handler == null ? "" : handler).trim().toLowerCase();
        if (departmentByIdentity.containsKey(normalizedHandler)) {
            return departmentByIdentity.get(normalizedHandler);
        }
        User handlerUser = userByUsername.get(normalizedHandler);
        if (handlerUser != null && String.valueOf(handlerUser.getDepartment() == null ? "" : handlerUser.getDepartment()).trim().length() > 0) {
            return String.valueOf(handlerUser.getDepartment()).trim();
        }
        String processLog = String.valueOf(item == null || item.getProcessLog() == null ? "" : item.getProcessLog());
        String tracedDepartment = extractTraceValue(processLog, "department");
        if (!tracedDepartment.isBlank()) {
            return tracedDepartment;
        }
        return "治理中心";
    }

    private Map<String, User> buildUserByUsername(Long companyId) {
        if (companyId == null) {
            return Map.of();
        }
        List<User> users = userService.lambdaQuery().eq(User::getCompanyId, companyId).list();
        if (users == null || users.isEmpty()) {
            return Map.of();
        }
        Map<String, User> map = new HashMap<>();
        for (User user : users) {
            if (user == null || user.getUsername() == null) {
                continue;
            }
            map.put(user.getUsername().trim().toLowerCase(), user);
        }
        return map;
    }

    private String extractTraceValue(String text, String key) {
        if (text == null || key == null || key.isBlank()) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile(key + "=([^\\]\\s]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(text);
        if (!matcher.find()) {
            return "";
        }
        return String.valueOf(matcher.group(1) == null ? "" : matcher.group(1)).trim();
    }

    private boolean isWalkthroughIdentity(String value) {
        return String.valueOf(value == null ? "" : value).trim().toLowerCase().contains("walkthrough");
    }

    private Date toDate(Object value) {
        if (value instanceof Date date) {
            return date;
        }
        return new Date(0L);
    }

    private Map<String, Object> filterEventsForEmployee(Map<String, Object> result, String employeeId) {
        if (result == null || !result.containsKey("events")) {
            return result;
        }
        Object rawEvents = result.get("events");
        if (!(rawEvents instanceof List<?> eventList)) {
            return result;
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Object item : eventList) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            Map<String, Object> event = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null) {
                    event.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            Object eventEmployee = event.get("employee_id");
            if (eventEmployee != null && employeeId.equalsIgnoreCase(String.valueOf(eventEmployee))) {
                filtered.add(event);
            }
        }
        Map<String, Object> safe = new LinkedHashMap<>(result);
        safe.put("events", filtered);
        safe.put("count", filtered.size());
        return safe;
    }

    private Map<String, Object> filterEventsForDepartment(Map<String, Object> result,
                                                           String department,
                                                           Map<String, String> departmentByIdentity) {
        if (result == null || !result.containsKey("events")) {
            return result;
        }
        String normalizedDepartment = String.valueOf(department == null ? "" : department).trim();
        if (normalizedDepartment.isBlank()) {
            Map<String, Object> safe = new LinkedHashMap<>(result);
            safe.put("events", List.of());
            safe.put("count", 0);
            safe.put("total", 0);
            return safe;
        }
        Object rawEvents = result.get("events");
        if (!(rawEvents instanceof List<?> eventList)) {
            return result;
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Object item : eventList) {
            if (!(item instanceof Map<?, ?> rawMap)) {
                continue;
            }
            String eventDepartment = String.valueOf(rawMap.get("department") == null ? "" : rawMap.get("department")).trim();
            String employeeKey = String.valueOf(rawMap.get("employee_id") == null ? "" : rawMap.get("employee_id")).trim().toLowerCase();
            String usernameKey = String.valueOf(rawMap.get("username") == null ? "" : rawMap.get("username")).trim().toLowerCase();
            String userIdKey = String.valueOf(rawMap.get("user_id") == null ? "" : rawMap.get("user_id")).trim().toLowerCase();
            String identityDepartment = departmentByIdentity.getOrDefault(employeeKey,
                departmentByIdentity.getOrDefault(usernameKey, departmentByIdentity.getOrDefault(userIdKey, "")));
            boolean matched = normalizedDepartment.equalsIgnoreCase(eventDepartment)
                || normalizedDepartment.equalsIgnoreCase(identityDepartment);
            if (!matched) {
                continue;
            }
            Map<String, Object> event = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                if (entry.getKey() != null) {
                    event.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
            filtered.add(event);
        }
        Map<String, Object> safe = new LinkedHashMap<>(result);
        safe.put("events", filtered);
        safe.put("count", filtered.size());
        safe.put("total", filtered.size());
        return safe;
    }

    private void ingestAnomalyIfNeeded(Map<String, Object> payload, Map<String, Object> result) {
        if (result == null || !isAnomaly(result.get("is_anomaly"))) {
            return;
        }
        Long companyId = companyScopeService.requireCompanyId();
        String employeeId = String.valueOf(payload.getOrDefault("employee_id", currentUserService.requireCurrentUser().getUsername()));
        String description = String.valueOf(result.getOrDefault("description", "AI行为异常"));

        User employee = userService.lambdaQuery().eq(User::getCompanyId, companyId).eq(User::getUsername, employeeId).one();
        eventHubService.ingestAnomalyEvent(companyId, employee, Map.of(
            "employeeId", employeeId,
            "riskEventId", "",
            "aiService", String.valueOf(payload.getOrDefault("ai_service", "unknown")),
            "department", String.valueOf(payload.getOrDefault("department", "unknown")),
            "score", String.valueOf(result.getOrDefault("score", result.getOrDefault("anomaly_score", ""))),
            "rawResult", result
        ));
    }

    private boolean isAnomaly(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number num) {
            return num.intValue() != 0;
        }
        String text = String.valueOf(value == null ? "" : value).trim();
        return "true".equalsIgnoreCase(text) || "1".equals(text) || "yes".equalsIgnoreCase(text);
    }
}
