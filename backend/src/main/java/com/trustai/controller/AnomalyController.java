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
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Date;
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
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS','DATA_ADMIN','AI_BUILDER','BUSINESS_OWNER','EMPLOYEE')")
    public R<Map<String, Object>> check(@RequestBody(required = false) Map<String, Object> payload) {
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
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','EXECUTIVE','SECOPS','DATA_ADMIN','AI_BUILDER','BUSINESS_OWNER','EMPLOYEE')")
    public R<Map<String, Object>> events() {
        Set<String> companyUsers = new HashSet<>(companyScopeService.companyUsernames());
        try {
            Map<String, Object> result = aiInferenceClient.anomalyEvents();
            result = filterEventsForCompany(result, companyUsers);
            if (currentUserService.hasRole("EXECUTIVE")) {
                return R.ok(summaryForExecutive(result));
            }
            if (!currentUserService.hasAnyRole("ADMIN", "SECOPS")) {
                result = filterEventsForEmployee(result, currentUserService.requireCurrentUser().getUsername());
            }
            return R.ok(result);
        } catch (Exception e) {
            log.warn("[Anomaly] Python 事件接口不可用，降级为本地真实数据: {}", e.getMessage());
            Map<String, Object> fallback = buildFallbackEvents(companyUsers);
            if (currentUserService.hasRole("EXECUTIVE")) {
                return R.ok(summaryForExecutive(fallback));
            }
            if (!currentUserService.hasAnyRole("ADMIN", "SECOPS")) {
                fallback = filterEventsForEmployee(fallback, currentUserService.requireCurrentUser().getUsername());
            }
            return R.ok(fallback);
        }
    }

    /**
     * 获取异常检测模型当前状态（是否已训练、元信息等）。
     *
     * <p>GET /api/anomaly/status
     */
    @GetMapping("/status")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','EXECUTIVE','SECOPS','DATA_ADMIN','AI_BUILDER','BUSINESS_OWNER','EMPLOYEE')")
    public R<Map<String, Object>> status() {
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

    private Map<String, Object> filterEventsForCompany(Map<String, Object> result, Set<String> companyUsers) {
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
            if (employee != null && companyUsers.contains(String.valueOf(employee))) {
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
        return scoped;
    }

    private Map<String, Object> buildFallbackEvents(Set<String> companyUsers) {
        Long companyId = companyScopeService.requireCompanyId();
        List<Map<String, Object>> events = new ArrayList<>();

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
            event.put("department", "security");
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
            Map<String, Object> event = new LinkedHashMap<>();
            event.put("event_id", "risk-" + item.getId());
            event.put("employee_id", resolveHandler(item));
            event.put("department", "risk");
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

    private Map<String, Object> summaryForExecutive(Map<String, Object> result) {
        Map<String, Object> source = result == null ? Map.of() : result;
        Object rawEvents = source.get("events");
        int total = 0;
        int anomaly = 0;
        if (rawEvents instanceof List<?> eventList) {
            total = eventList.size();
            for (Object item : eventList) {
                if (item instanceof Map<?, ?> map) {
                    Object flag = map.get("is_anomaly");
                    if (Boolean.TRUE.equals(flag)
                            || "true".equalsIgnoreCase(String.valueOf(flag))
                            || "1".equals(String.valueOf(flag))) {
                        anomaly++;
                    }
                }
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("summaryOnly", true);
        summary.put("total", total);
        summary.put("anomalyCount", anomaly);
        summary.put("normalCount", Math.max(0, total - anomaly));
        summary.put("anomalyRate", total == 0 ? 0 : (double) anomaly / (double) total);
        summary.put("events", List.of());
        summary.put("count", 0);
        return summary;
    }

    private void ingestAnomalyIfNeeded(Map<String, Object> payload, Map<String, Object> result) {
        if (result == null || !isAnomaly(result.get("is_anomaly"))) {
            return;
        }
        Long companyId = companyScopeService.requireCompanyId();
        String employeeId = String.valueOf(payload.getOrDefault("employee_id", currentUserService.requireCurrentUser().getUsername()));
        String level = normalizeRisk(String.valueOf(result.getOrDefault("risk_level", "high")));
        String description = String.valueOf(result.getOrDefault("description", "AI行为异常"));

        RiskEvent riskEvent = new RiskEvent();
        riskEvent.setCompanyId(companyId);
        riskEvent.setType("behavior_anomaly");
        riskEvent.setLevel(level);
        riskEvent.setStatus("open");
        riskEvent.setHandlerId(currentUserService.requireCurrentUser().getId());
        riskEvent.setProcessLog(description);
        riskEvent.setCreateTime(new Date());
        riskEvent.setUpdateTime(new Date());
        riskEventService.save(riskEvent);

        User employee = userService.lambdaQuery().eq(User::getCompanyId, companyId).eq(User::getUsername, employeeId).one();
        eventHubService.ingestAnomalyEvent(companyId, employee, Map.of(
            "employeeId", employeeId,
            "riskEventId", riskEvent.getId() == null ? "" : String.valueOf(riskEvent.getId()),
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
