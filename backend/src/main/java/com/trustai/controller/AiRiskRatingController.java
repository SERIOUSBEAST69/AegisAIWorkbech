package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.entity.AiCallLog;
import com.trustai.entity.AiModel;
import com.trustai.entity.User;
import com.trustai.service.AiCallAuditService;
import com.trustai.service.AiModelService;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.PrivacyShieldConfigService;
import com.trustai.service.UserService;
import com.trustai.utils.R;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import org.springframework.util.StringUtils;

/**
 * AI 服务风险评级 API（仅官方可信白名单模型，基于真实近30天调用日志）。
 */
@RestController
@RequestMapping("/api/ai-risk")
public class AiRiskRatingController {

    private static final Logger log = LoggerFactory.getLogger(AiRiskRatingController.class);

    @Autowired private AiModelService aiModelService;
    @Autowired private AiCallAuditService aiCallAuditService;
    @Autowired private CurrentUserService currentUserService;
    @Autowired private PrivacyShieldConfigService privacyShieldConfigService;
    @Autowired private CompanyScopeService companyScopeService;
    @Autowired private UserService userService;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final String WINDOW_LABEL = "近30天";
    private static final Set<String> TRUSTED_SERVICE_IDS = new LinkedHashSet<>(List.of(
        "tongyi",
        "wenxin",
        "deepseek",
        "doubao",
        "hunyuan",
        "kimi",
        "spark",
        "zhipu",
        "modelwhale"
    ));

    /**
     * 获取所有已收录 AI 服务的风险评级摘要列表。
     *
     * <p>GET /api/ai-risk/list
     */
    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER')")
    public R<Map<String, Object>> listServices() {
        try {
            Map<String, Object> result = buildRiskList();
            return R.ok(result);
        } catch (Exception e) {
            log.error("[AiRisk] 获取风险评级列表失败: {}", e.getMessage());
            return R.error("风险评级服务暂不可用");
        }
    }

    /**
     * 查询单个 AI 服务的详细风险评分。
     *
     * <p>GET /api/ai-risk/score?service=chatgpt
     *
     * @param serviceId 服务 ID（小写，如 chatgpt / wenxin / doubao 等）
     */
    @GetMapping("/score")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER')")
    public R<Map<String, Object>> serviceScore(@RequestParam("service") String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            return R.error("缺少参数 service");
        }
        try {
            Map<String, Object> result = buildRiskDetail(serviceId.toLowerCase(Locale.ROOT).strip());
            if (result == null) {
                return R.error("未找到官方AI服务或当前无真实数据");
            }
            return R.ok(result);
        } catch (Exception e) {
            log.error("[AiRisk] 查询服务 {} 风险评分失败: {}", serviceId, e.getMessage());
            return R.error("查询失败");
        }
    }

    /**
     * 动态刷新风险评级数据。
     *
     * <p>POST /api/ai-risk/refresh
     *
     * <p>空请求体：重新从 ai_risk_data.json 加载数据。
     * <p>带 services 数组：合并更新指定服务的评分数据并持久化。
     */
    @PostMapping("/refresh")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<Map<String, Object>> refresh(@RequestBody(required = false) Map<String, Object> payload) {
        try {
            Map<String, Object> result = buildRiskList();
            result.put("message", "已按真实近30天调用日志重新计算");
            return R.ok(result);
        } catch (Exception e) {
            log.error("[AiRisk] 刷新风险评级数据失败: {}", e.getMessage());
            return R.error("刷新失败");
        }
    }

    @GetMapping("/whitelist")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER')")
    public R<Map<String, Object>> whitelist() {
        Long companyId = currentUserService.requireCurrentUser().getCompanyId();
        Map<String, Object> config = privacyShieldConfigService.getOrCreateConfig();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("catalog", toStringList(config.get("aiCatalog")));
        data.put("whitelist", resolveCompanyWhitelist(config, companyId));
        data.put("configVersion", config.getOrDefault("configVersion", 1L));
        data.put("updatedAt", config.get("updatedAt"));
        boolean canReview = currentUserService.hasRole("ADMIN");
        boolean canRequest = currentUserService.hasAnyRole("SECOPS", "BUSINESS_OWNER");
        data.put("canRequest", canRequest);
        data.put("canReview", canReview);
        if (canRequest || canReview) {
            data.put("pending", pendingRequests(config, companyId));
        } else {
            data.put("pending", List.of());
        }
        return R.ok(data);
    }

    @GetMapping("/profile/radar")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER')")
    public R<Map<String, Object>> riskRadarProfile(@RequestParam(required = false) String username) {
        User current = currentUserService.requireCurrentUser();
        Long companyId = companyScopeService.requireCompanyId();
        boolean canQueryOthers = currentUserService.hasAnyRole("ADMIN", "ADMIN_REVIEWER", "SECOPS");

        User target = current;
        if (StringUtils.hasText(username) && canQueryOthers) {
            User hit = userService.lambdaQuery()
                .eq(User::getCompanyId, companyId)
                .eq(User::getUsername, username.trim())
                .last("LIMIT 1")
                .one();
            if (hit != null) {
                target = hit;
            }
        }

        Date sinceDate = java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(30));

        long privacyAlerts = queryLong(
            "SELECT COUNT(1) FROM governance_event WHERE company_id = ? AND user_id = ? AND event_type = 'PRIVACY_ALERT' AND event_time >= ?",
            companyId, target.getId(), sinceDate
        );
        long anomalyAlerts = queryLong(
            "SELECT COUNT(1) FROM governance_event WHERE company_id = ? AND user_id = ? AND event_type = 'ANOMALY_ALERT' AND event_time >= ?",
            companyId, target.getId(), sinceDate
        );
        long shadowAlerts = queryLong(
            "SELECT COUNT(1) FROM governance_event WHERE company_id = ? AND user_id = ? AND event_type = 'SHADOW_AI_ALERT' AND event_time >= ?",
            companyId, target.getId(), sinceDate
        );
        long pendingSecurity = queryLong(
            "SELECT COUNT(1) FROM governance_event WHERE company_id = ? AND user_id = ? AND event_type = 'SECURITY_ALERT' AND event_time >= ? AND LOWER(status) IN ('pending','reviewing')",
            companyId, target.getId(), sinceDate
        );

        long aiCallTotal = queryLong(
            "SELECT COUNT(1) FROM ai_call_log WHERE company_id = ? AND user_id = ? AND create_time >= ?",
            companyId, target.getId(), sinceDate
        );
        long aiCallFail = queryLong(
            "SELECT COUNT(1) FROM ai_call_log WHERE company_id = ? AND user_id = ? AND create_time >= ? AND LOWER(status) <> 'success'",
            companyId, target.getId(), sinceDate
        );
        long aiCallAvgLatency = queryLong(
            "SELECT COALESCE(AVG(duration_ms), 0) FROM ai_call_log WHERE company_id = ? AND user_id = ? AND create_time >= ?",
            companyId, target.getId(), sinceDate
        );

        int privacyRisk = clampRisk((int) Math.round(Math.min(100D, privacyAlerts * 12D + (privacyAlerts > 0 ? 10D : 0D))));
        int behaviorRisk = clampRisk((int) Math.round(Math.min(100D, anomalyAlerts * 14D + (aiCallFail > 0 ? Math.min(30D, (aiCallFail * 100D) / Math.max(1D, aiCallTotal)) : 0D))));
        int shadowRisk = clampRisk((int) Math.round(Math.min(100D, shadowAlerts * 16D + (shadowAlerts > 0 ? 8D : 0D))));
        int complianceRisk = clampRisk((int) Math.round(Math.min(100D, pendingSecurity * 18D + (pendingSecurity > 0 ? 6D : 0D))));
        int reliabilityRisk = clampRisk((int) Math.round(Math.min(100D,
            (aiCallAvgLatency >= 3000 ? 50D : (aiCallAvgLatency >= 1800 ? 35D : (aiCallAvgLatency >= 900 ? 18D : 6D)))
                + (aiCallFail > 0 ? Math.min(35D, (aiCallFail * 100D) / Math.max(1D, aiCallTotal)) : 0D)
        )));

        int totalRisk = clampRisk((int) Math.round(
            privacyRisk * 0.24 + behaviorRisk * 0.22 + shadowRisk * 0.2 + complianceRisk * 0.2 + reliabilityRisk * 0.14
        ));

        List<Map<String, Object>> dimensions = new ArrayList<>();
        dimensions.add(radarDimension("privacyDiscipline", "隐私纪律风险", privacyRisk, "近30天隐私告警" + privacyAlerts + " 次"));
        dimensions.add(radarDimension("behaviorStability", "行为稳定性风险", behaviorRisk, "近30天异常行为" + anomalyAlerts + " 次"));
        dimensions.add(radarDimension("shadowAiExposure", "影子AI暴露风险", shadowRisk, "近30天影子AI事件" + shadowAlerts + " 次"));
        dimensions.add(radarDimension("securityCompliance", "安全处置合规风险", complianceRisk, "未闭环安全告警" + pendingSecurity + " 次"));
        dimensions.add(radarDimension("modelReliability", "模型调用可靠性风险", reliabilityRisk, "调用" + aiCallTotal + " 次，失败" + aiCallFail + " 次，均延" + aiCallAvgLatency + "ms"));

        List<Map<String, Object>> recommendations = new ArrayList<>();
        recommendations.add(recommendationNode(
            "隐私泄露防控加固",
            privacyRisk >= 60 ? "优先" : "建议",
            "收紧隐私字段拦截规则并复核最近告警样本",
            "/threat-monitor",
            "events"
        ));
        recommendations.add(recommendationNode(
            "异常行为与账号画像复核",
            behaviorRisk >= 60 ? "优先" : "建议",
            "检查异常行为轨迹并确认是否需要审批升级",
            "/ai/anomaly",
            "profile"
        ));
        recommendations.add(recommendationNode(
            "影子AI治理闭环",
            shadowRisk >= 60 ? "优先" : "建议",
            "核查非白名单调用并执行白名单审批流程",
            "/shadow-ai",
            "risk"
        ));
        recommendations.add(recommendationNode(
            "安全事件快速阻断",
            complianceRisk >= 55 ? "优先" : "建议",
            "针对待处置安全事件执行阻断并触发攻防验证",
            "/operations-command",
            "pending"
        ));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("window", WINDOW_LABEL);
        data.put("username", target.getUsername());
        data.put("userId", target.getId());
        data.put("companyId", companyId);
        data.put("totalRisk", totalRisk);
        data.put("riskLevel", totalRisk >= 70 ? "high" : (totalRisk >= 40 ? "medium" : "low"));
        data.put("dimensions", dimensions);
        data.put("recommendations", recommendations);
        data.put("evidence", Map.of(
            "privacyAlerts30d", privacyAlerts,
            "anomalyAlerts30d", anomalyAlerts,
            "shadowAlerts30d", shadowAlerts,
            "pendingSecurity30d", pendingSecurity,
            "aiCalls30d", aiCallTotal,
            "aiCallFailures30d", aiCallFail,
            "aiCallAvgLatencyMs30d", aiCallAvgLatency
        ));
        data.put("generatedAt", new Date());
        return R.ok(data);
    }

    @GetMapping("/profile/users")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
    public R<Map<String, Object>> riskRadarUsers() {
        User current = currentUserService.requireCurrentUser();
        Long companyId = companyScopeService.requireCompanyId();
        boolean canQueryOthers = currentUserService.hasAnyRole("ADMIN", "ADMIN_REVIEWER", "SECOPS");

        List<Map<String, Object>> users = jdbcTemplate.query(
            "SELECT u.id, u.username, COALESCE(NULLIF(TRIM(u.real_name), ''), u.username) AS real_name, "
                + "COALESCE(NULLIF(TRIM(u.department), ''), '-') AS department, COALESCE(r.code, '') AS role_code "
                + "FROM sys_user u "
                + "LEFT JOIN role r ON r.id = u.role_id "
                + "WHERE u.company_id = ? AND COALESCE(u.account_status, 'active') = 'active' "
                + "ORDER BY u.username ASC LIMIT 500",
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("username", rs.getString("username"));
                row.put("realName", rs.getString("real_name"));
                row.put("department", rs.getString("department"));
                row.put("roleCode", rs.getString("role_code"));
                return row;
            },
            companyId
        );

        if (!canQueryOthers) {
            users = users.stream()
                .filter(item -> String.valueOf(item.get("username")).equalsIgnoreCase(String.valueOf(current.getUsername())))
                .toList();
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("users", users);
        data.put("current", current.getUsername());
        data.put("canQueryOthers", canQueryOthers);
        return R.ok(data);
    }

    @PostMapping("/whitelist/request")
    @PreAuthorize("@currentUserService.hasAnyRole('SECOPS','BUSINESS_OWNER')")
    public R<Map<String, Object>> submitWhitelistRequest(@RequestBody(required = false) Map<String, Object> payload) {
        if (payload == null) {
            return R.error("请求体不能为空");
        }
        Long companyId = currentUserService.requireCurrentUser().getCompanyId();
        List<String> requested = toStringList(payload.get("whitelist"));
        if (requested.isEmpty()) {
            return R.error("白名单不能为空");
        }
        Map<String, Object> config = new LinkedHashMap<>(privacyShieldConfigService.getOrCreateConfig());
        Set<String> catalog = new LinkedHashSet<>(toStringList(config.get("aiCatalog")));
        for (String item : requested) {
            if (!catalog.contains(item)) {
                return R.error("存在未收录的AI服务: " + item);
            }
        }

        User requester = currentUserService.requireCurrentUser();
        List<Map<String, Object>> pending = pendingRequests(config, null);
        long requestId = pending.stream()
            .mapToLong(item -> toLong(item.get("requestId"), 0L))
            .max()
            .orElse(0L) + 1L;
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("requestId", requestId);
        req.put("status", "pending");
        req.put("requestedBy", requester.getUsername());
        req.put("requestedById", requester.getId());
        req.put("companyId", companyId == null ? 0L : companyId);
        req.put("requestedAt", new Date().getTime());
        req.put("targetWhitelist", requested);
        req.put("note", String.valueOf(payload.getOrDefault("note", "")));
        pending.add(req);
        config.put("aiWhitelistPending", pending);
        privacyShieldConfigService.updateConfig(config);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestId", requestId);
        data.put("status", "pending");
        data.put("message", "白名单变更已提交，待治理管理员审批后生效");
        return R.ok(data);
    }

    @PostMapping("/whitelist/review")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<Map<String, Object>> reviewWhitelistRequest(@RequestBody(required = false) Map<String, Object> payload) {
        if (payload == null) {
            return R.error("请求体不能为空");
        }
        long requestId = toLong(payload.get("requestId"), 0L);
        String decision = String.valueOf(payload.getOrDefault("decision", "")).trim().toLowerCase(Locale.ROOT);
        Long companyId = currentUserService.requireCurrentUser().getCompanyId();
        if (requestId <= 0L) {
            return R.error("requestId 不合法");
        }
        if (!"approve".equals(decision) && !"reject".equals(decision)) {
            return R.error("decision 仅支持 approve/reject");
        }

        Map<String, Object> config = new LinkedHashMap<>(privacyShieldConfigService.getOrCreateConfig());
        List<Map<String, Object>> pending = pendingRequests(config, null);
        Map<String, Object> target = null;
        for (Map<String, Object> row : pending) {
            if (toLong(row.get("requestId"), 0L) == requestId && toLong(row.get("companyId"), 0L) == toLong(companyId, 0L)) {
                target = row;
                break;
            }
        }
        if (target == null) {
            return R.error("审批单不存在");
        }
        if (!"pending".equalsIgnoreCase(String.valueOf(target.get("status")))) {
            return R.error("审批单已处理");
        }

        User reviewer = currentUserService.requireCurrentUser();
        target.put("status", "approve".equals(decision) ? "approved" : "rejected");
        target.put("reviewedBy", reviewer.getUsername());
        target.put("reviewedById", reviewer.getId());
        target.put("reviewedAt", new Date().getTime());
        if (StringUtils.hasText(String.valueOf(payload.getOrDefault("note", "")))) {
            target.put("reviewNote", String.valueOf(payload.get("note")));
        }

        if ("approve".equals(decision)) {
            List<String> nextWhitelist = toStringList(target.get("targetWhitelist"));
            setCompanyWhitelist(config, companyId, nextWhitelist);
        }
        config.put("aiWhitelistPending", pending);
        privacyShieldConfigService.updateConfig(config);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestId", requestId);
        data.put("status", target.get("status"));
        data.put("whitelist", resolveCompanyWhitelist(config, companyId));
        return R.ok(data);
    }

    private List<Map<String, Object>> pendingRequests(Map<String, Object> config, Long companyId) {
        Object raw = config.get("aiWhitelistPending");
        if (!(raw instanceof List<?> list)) {
            return new ArrayList<>();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object row : list) {
            if (row instanceof Map<?, ?> map) {
                Map<String, Object> normalized = new LinkedHashMap<>();
                map.forEach((k, v) -> normalized.put(String.valueOf(k), v));
                if (companyId == null || toLong(normalized.get("companyId"), 0L) == toLong(companyId, 0L)) {
                    result.add(normalized);
                }
            }
        }
        return result;
    }

    private Map<String, Object> radarDimension(String code, String label, int value, String explain) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("code", code);
        node.put("label", label);
        node.put("value", value);
        node.put("explain", explain);
        return node;
    }

    private Map<String, Object> recommendationNode(String title, String priority, String action, String route, String jumpHint) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("title", title);
        node.put("priority", priority);
        node.put("action", action);
        node.put("route", route);
        node.put("jumpHint", jumpHint);
        return node;
    }

    private int clampRisk(int value) {
        return Math.max(0, Math.min(100, value));
    }

    private long queryLong(String sql, Object... params) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, params);
        return value == null ? 0L : value.longValue();
    }

    private List<String> resolveCompanyWhitelist(Map<String, Object> config, Long companyId) {
        Object raw = config.get("aiWhitelistByCompany");
        if (raw instanceof Map<?, ?> map) {
            Object companyList = map.get(String.valueOf(companyId == null ? 0L : companyId));
            List<String> list = toStringList(companyList);
            if (!list.isEmpty()) {
                return list;
            }
        }
        return toStringList(config.get("aiWhitelist"));
    }

    private void setCompanyWhitelist(Map<String, Object> config, Long companyId, List<String> whitelist) {
        Map<String, Object> bucket;
        Object raw = config.get("aiWhitelistByCompany");
        if (raw instanceof Map<?, ?> map) {
            bucket = new LinkedHashMap<>();
            map.forEach((k, v) -> bucket.put(String.valueOf(k), v));
        } else {
            bucket = new LinkedHashMap<>();
        }
        bucket.put(String.valueOf(companyId == null ? 0L : companyId), whitelist);
        config.put("aiWhitelistByCompany", bucket);
    }

    private List<String> toStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : list) {
            String text = String.valueOf(item == null ? "" : item).trim();
            if (StringUtils.hasText(text) && !result.contains(text)) {
                result.add(text);
            }
        }
        return result;
    }

    private long toLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private Map<String, Object> buildRiskList() {
        LocalDateTime from = LocalDateTime.now().minusDays(30);
        Long companyId = currentUserService.requireCurrentUser().getCompanyId();
        List<AiCallLog> logs = safeListAiCallLogs(new QueryWrapper<AiCallLog>()
            .eq(companyId != null, "company_id", companyId)
            .ge("create_time", from));

        Map<String, List<AiCallLog>> logsByCode = new HashMap<>();
        for (AiCallLog log : logs) {
            String key = normalize(log.getModelCode());
            if (key.isEmpty()) continue;
            logsByCode.computeIfAbsent(key, k -> new ArrayList<>()).add(log);
        }

        Map<String, AiModel> official = new LinkedHashMap<>();
        List<AiModel> scopedModels = aiModelService.list(new QueryWrapper<AiModel>()
            .eq(companyId != null, "company_id", companyId)
            .eq("status", "enabled"));
        for (AiModel model : scopedModels) {
            String officialId = officialId(model);
            if (officialId != null && TRUSTED_SERVICE_IDS.contains(officialId) && !official.containsKey(officialId)) {
                official.put(officialId, model);
            }
        }
        for (String trustedId : TRUSTED_SERVICE_IDS) {
            official.computeIfAbsent(trustedId, this::fallbackTrustedModel);
        }

        List<Map<String, Object>> services = new ArrayList<>();
        for (Map.Entry<String, AiModel> entry : official.entrySet()) {
            AiModel model = entry.getValue();
            List<AiCallLog> modelLogs = logsByCode.getOrDefault(normalize(model.getModelCode()), List.of());
            services.add(buildServiceSummary(entry.getKey(), model, modelLogs));
        }
        services.sort(Comparator.comparingInt(item -> -toInt(item.get("total_risk_score"))));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("services", services);
        result.put("total", services.size());
        result.put("updated_at", LocalDateTime.now().toString());
        result.put("window", WINDOW_LABEL);
        return result;
    }

    private Map<String, Object> buildRiskDetail(String serviceId) {
        LocalDateTime from = LocalDateTime.now().minusDays(30);
        Long companyId = currentUserService.requireCurrentUser().getCompanyId();
        List<AiModel> models = aiModelService.list(new QueryWrapper<AiModel>()
            .eq(companyId != null, "company_id", companyId)
            .eq("status", "enabled"));
        AiModel hit = null;
        String officialKey = null;
        for (AiModel model : models) {
            String key = officialId(model);
            if (key != null && TRUSTED_SERVICE_IDS.contains(key) && key.equals(serviceId)) {
                hit = model;
                officialKey = key;
                break;
            }
        }
        if (hit == null) {
            if (!TRUSTED_SERVICE_IDS.contains(serviceId)) {
                return null;
            }
            officialKey = serviceId;
            hit = fallbackTrustedModel(serviceId);
        }

        List<AiCallLog> logs = safeListAiCallLogs(new QueryWrapper<AiCallLog>()
            .eq(companyId != null, "company_id", companyId)
            .eq("model_code", hit.getModelCode())
            .ge("create_time", from));

        return buildServiceDetail(officialKey, hit, logs);
    }

    private List<AiCallLog> safeListAiCallLogs(QueryWrapper<AiCallLog> wrapper) {
        try {
            return aiCallAuditService.list(wrapper);
        } catch (Exception ex) {
            log.warn("[AiRisk] 调用日志不可用，降级为0样本评分: {}", ex.getMessage());
            return List.of();
        }
    }

    private AiModel fallbackTrustedModel(String officialId) {
        AiModel model = new AiModel();
        model.setModelCode(officialId);
        model.setModelName(fallbackModelName(officialId));
        model.setProvider(officialId);
        model.setModelType("chat");
        model.setRiskLevel("medium");
        model.setIsolationLevel("L2");
        model.setStatus("enabled");
        return model;
    }

    private String fallbackModelName(String officialId) {
        return switch (officialId) {
            case "tongyi" -> "通义千问";
            case "wenxin" -> "文心一言";
            case "deepseek" -> "DeepSeek";
            case "doubao" -> "豆包";
            case "hunyuan" -> "混元";
            case "kimi" -> "Kimi";
            case "spark" -> "讯飞星火";
            case "zhipu" -> "智谱GLM";
            case "modelwhale" -> "和鲸";
            default -> officialId;
        };
    }

    private Map<String, Object> buildServiceSummary(String officialId, AiModel model, List<AiCallLog> logs) {
        RiskSnapshot rs = calculateRisk(model, logs);
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", officialId);
        item.put("name", displayName(officialId, model));
        item.put("provider", model.getProvider());
        item.put("logo", officialLogo(officialId));
        item.put("category", categoryFromType(model.getModelType()));
        item.put("isolation_level", normalizeIsolationLevel(model.getIsolationLevel()));
        item.put("total_risk_score", rs.totalScore);
        item.put("risk_level", rs.level);
        item.put("tags", rs.tags);
        return item;
    }

    private Map<String, Object> buildServiceDetail(String officialId, AiModel model, List<AiCallLog> logs) {
        RiskSnapshot rs = calculateRisk(model, logs);

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("id", officialId);
        detail.put("name", displayName(officialId, model));
        detail.put("provider", model.getProvider());
        detail.put("logo", officialLogo(officialId));
        detail.put("category", categoryFromType(model.getModelType()));
        detail.put("isolation_level", normalizeIsolationLevel(model.getIsolationLevel()));
        detail.put("description", "基于" + WINDOW_LABEL + "真实调用日志计算风险评级，不包含演示数据。");
        detail.put("total_risk_score", rs.totalScore);
        detail.put("risk_level", rs.level);
        detail.put("tags", rs.tags);
        detail.put("recommendations", rs.recommendation);

        Map<String, Object> scores = new LinkedHashMap<>();
        scores.put("base_risk", scoreNode(rs.baseRisk, 40, "模型固有风险（按模型风险级别映射）"));
        scores.put("privacy_exposure", scoreNode(rs.privacyExposureRisk, 15, "隐私暴露风险（按模型服务特征映射）"));
        scores.put("usage_volume", scoreNode(rs.usageRisk, 20, WINDOW_LABEL + "调用量风险因子"));
        scores.put("failure_rate", scoreNode(rs.failureRisk, 15, WINDOW_LABEL + "失败率风险因子"));
        scores.put("latency", scoreNode(rs.latencyRisk, 10, WINDOW_LABEL + "高延时风险因子"));
        detail.put("scores", scores);

        return detail;
    }

    private Map<String, Object> scoreNode(int value, int max, String detail) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("value", value);
        node.put("max", max);
        node.put("detail", detail);
        node.put("evidence_url", null);
        return node;
    }

    private RiskSnapshot calculateRisk(AiModel model, List<AiCallLog> logs) {
        int baseRisk = switch (normalize(model.getRiskLevel())) {
            case "high", "高" -> 34;
            case "medium", "中" -> 24;
            default -> 14;
        };
        int privacyExposureRisk = switch (officialId(model)) {
            case "hunyuan", "deepseek" -> 12;
            case "doubao" -> 10;
            case "wenxin" -> 9;
            default -> 8;
        };

        List<AiCallLog> safeLogs = logs == null ? List.of() : logs;
        int totalCalls = safeLogs.size();
        int failure = 0;
        long durationSum = 0L;
        for (AiCallLog log : safeLogs) {
            if (!"success".equalsIgnoreCase(String.valueOf(log.getStatus()))) {
                failure++;
            }
            durationSum += log.getDurationMs() == null ? 0L : log.getDurationMs();
        }
        double failRate = totalCalls == 0 ? 0.0 : (double) failure / totalCalls;
        long avgDuration = totalCalls == 0 ? 0L : durationSum / totalCalls;

        int usageRisk = Math.min(20, (int) Math.round(Math.min(totalCalls, 120) / 120.0 * 20));
        int failureRisk = Math.min(15, (int) Math.round(failRate * 15));
        int latencyRisk = avgDuration > 2500 ? 10 : (avgDuration > 1500 ? 6 : (avgDuration > 900 ? 3 : 0));

        int total = Math.min(100, baseRisk + privacyExposureRisk + usageRisk + failureRisk + latencyRisk);
        String level = total >= 70 ? "high" : (total >= 40 ? "medium" : "low");

        List<String> tags = new ArrayList<>();
        tags.add("隔离等级" + normalizeIsolationLevel(model.getIsolationLevel()));
        tags.add(WINDOW_LABEL + "调用" + totalCalls + "次");
        tags.add("失败率" + Math.round(failRate * 100) + "%");
        tags.add("平均耗时" + avgDuration + "ms");

        String recommendation = total >= 70
            ? "建议限制高风险场景调用并加强提示词与输出审计。"
            : (total >= 40 ? "建议持续观察失败率与延时波动，按需收紧调用配额。" : "风险可控，保持常态化审计与调用监控。");

        return new RiskSnapshot(baseRisk, privacyExposureRisk, usageRisk, failureRisk, latencyRisk, total, level, tags, recommendation);
    }

    private String categoryFromType(String modelType) {
        String t = normalize(modelType);
        if ("image".equals(t)) return "image";
        if ("platform".equals(t)) return "platform";
        return "chat";
    }

    private String normalizeIsolationLevel(String isolationLevel) {
        if (isolationLevel == null || isolationLevel.isBlank()) {
            return "L2";
        }
        String value = isolationLevel.trim().toUpperCase(Locale.ROOT);
        return switch (value) {
            case "L0", "L1", "L2", "L3", "L4" -> value;
            case "0" -> "L0";
            case "1" -> "L1";
            case "2" -> "L2";
            case "3" -> "L3";
            case "4" -> "L4";
            default -> "L2";
        };
    }

    private String displayName(String officialId, AiModel model) {
        if (model != null && model.getModelName() != null && !model.getModelName().isBlank()) {
            return model.getModelName();
        }
        return officialId;
    }

    private String officialLogo(String id) {
        return switch (id) {
            case "tongyi" -> "🟢";
            case "wenxin" -> "🔵";
            case "deepseek" -> "🧠";
            case "doubao" -> "🫘";
            case "hunyuan" -> "🌀";
            case "kimi" -> "🌙";
            case "spark" -> "✨";
            case "zhipu" -> "🧩";
            case "modelwhale" -> "🐋";
            default -> "🤖";
        };
    }

    private String officialId(AiModel model) {
        if (model == null) return null;
        String name = normalize(model.getModelName());
        String provider = normalize(model.getProvider());
        String code = normalize(model.getModelCode());

        if (containsAny(name, provider, code, "tongyi", "qwen", "通义")) return "tongyi";
        if (containsAny(name, provider, code, "wenxin", "ernie", "文心")) return "wenxin";
        if (containsAny(name, provider, code, "deepseek")) return "deepseek";
        if (containsAny(name, provider, code, "gaoding", "稿定")) return "gaoding";
        if (containsAny(name, provider, code, "modelwhale", "和鲸")) return "modelwhale";
        if (containsAny(name, provider, code, "jimeng", "即梦")) return "jimeng";
        if (containsAny(name, provider, code, "doubao", "豆包")) return "doubao";
        if (containsAny(name, provider, code, "spark", "xinghuo", "星火", "ifly")) return "spark";
        if (containsAny(name, provider, code, "kimi", "moonshot")) return "kimi";
        if (containsAny(name, provider, code, "hunyuan", "混元", "tencent")) return "hunyuan";
        if (containsAny(name, provider, code, "zhipu", "chatglm", "智谱", "bigmodel")) return "zhipu";
        return null;
    }

    private boolean containsAny(String a, String b, String c, String... keys) {
        for (String key : keys) {
            if (a.contains(key) || b.contains(key) || c.contains(key)) return true;
        }
        return false;
    }

    private int toInt(Object v) {
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v));
        } catch (Exception ex) {
            return 0;
        }
    }

    private String normalize(String v) {
        return v == null ? "" : v.trim().toLowerCase(Locale.ROOT);
    }

    private record RiskSnapshot(
        int baseRisk,
        int privacyExposureRisk,
        int usageRisk,
        int failureRisk,
        int latencyRisk,
        int totalScore,
        String level,
        List<String> tags,
        String recommendation
    ) {}
}
