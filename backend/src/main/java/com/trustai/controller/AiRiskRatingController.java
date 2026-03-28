package com.trustai.controller;

import com.trustai.client.AiInferenceClient;
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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 服务风险评级 API。
 *
 * <p>代理至 Python 推理服务（ai_risk_data.json 静态数据库），
 * 提供各常见 AI 服务的多维度风险评分，供管理后台展示。
 *
 * <p>评分维度：
 * <ul>
 *   <li>隐私政策（是否默认用数据训练）×30</li>
 *   <li>数据存储地（境内/境外）×25</li>
 *   <li>安全认证（ISO 27001 / SOC2 / 等保三级）×20</li>
 *   <li>历史泄露事件 ×25</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ai-risk")
public class AiRiskRatingController {

    private static final Logger log = LoggerFactory.getLogger(AiRiskRatingController.class);

    @Autowired
    private AiInferenceClient aiInferenceClient;

    /**
     * 获取所有已收录 AI 服务的风险评级摘要列表。
     *
     * <p>GET /api/ai-risk/list
     */
    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS','AI_BUILDER')")
    public R<Map<String, Object>> listServices() {
        try {
            Map<String, Object> result = aiInferenceClient.riskList();
            return R.ok(result);
        } catch (Exception e) {
            log.error("[AiRisk] 获取风险评级列表失败: {}", e.getMessage());
            return R.ok(buildFallbackList("风险评级引擎暂不可用，已切换后端降级数据"));
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
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS','AI_BUILDER')")
    public R<Map<String, Object>> serviceScore(@RequestParam("service") String serviceId) {
        if (serviceId == null || serviceId.isBlank()) {
            return R.error("缺少参数 service");
        }
        try {
            Map<String, Object> result = aiInferenceClient.riskScore(serviceId.toLowerCase().strip());
            return R.ok(result);
        } catch (Exception e) {
            log.error("[AiRisk] 查询服务 {} 风险评分失败: {}", serviceId, e.getMessage());
            Map<String, Object> fallback = buildFallbackScore(serviceId);
            if (fallback != null) {
                return R.ok(fallback);
            }
            return R.error("未找到服务或评级服务暂不可用");
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
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS','AI_BUILDER')")
    public R<Map<String, Object>> refresh(@RequestBody(required = false) Map<String, Object> payload) {
        try {
            Map<String, Object> body = payload != null ? payload : Collections.emptyMap();
            Map<String, Object> result = aiInferenceClient.riskRefresh(body);
            return R.ok(result);
        } catch (Exception e) {
            log.error("[AiRisk] 刷新风险评级数据失败: {}", e.getMessage());
            Map<String, Object> degraded = Collections.unmodifiableMap(Map.of(
                "status", "degraded",
                "message", "刷新失败，已保留当前后端降级数据",
                "updated", List.of(),
                "total", fallbackServices().size()
            ));
            return R.ok(degraded);
        }
    }

    private Map<String, Object> buildFallbackList(String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("services", fallbackServices());
        payload.put("total", fallbackServices().size());
        payload.put("updated_at", null);
        payload.put("degraded", true);
        payload.put("message", message);
        return Collections.unmodifiableMap(payload);
    }

    private Map<String, Object> buildFallbackScore(String serviceId) {
        String normalized = serviceId == null ? "" : serviceId.trim().toLowerCase();
        for (Map<String, Object> item : fallbackServices()) {
            if (normalized.equals(String.valueOf(item.get("id")).toLowerCase())) {
                Map<String, Object> scores = new LinkedHashMap<>();
                scores.put("trains_on_data", Map.of("value", 12, "max", 30, "detail", "训练策略未知时按中等风险估算"));
                scores.put("data_location", Map.of("value", 12, "max", 25, "detail", "数据存储地按公开资料估算"));
                scores.put("security_cert", Map.of("value", 10, "max", 20, "detail", "认证项按保守分值处理"));
                scores.put("breach_history", Map.of("value", 12, "max", 25, "detail", "历史事件按公开记录估算"));

                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("id", item.get("id"));
                payload.put("name", item.get("name"));
                payload.put("provider", item.get("provider"));
                payload.put("logo", item.get("logo"));
                payload.put("category", item.get("category"));
                payload.put("total_risk_score", item.get("total_risk_score"));
                payload.put("risk_level", item.get("risk_level"));
                payload.put("tags", item.get("tags"));
                payload.put("description", "后端降级风险画像（Python 引擎不可达时提供）");
                payload.put("scores", scores);
                payload.put("recommendations", "建议通过专线代理与白名单策略后再开放该服务");
                payload.put("degraded", true);
                return Collections.unmodifiableMap(payload);
            }
        }
        return null;
    }

    private List<Map<String, Object>> fallbackServices() {
        return List.of(
            Map.of("id", "chatgpt", "name", "ChatGPT", "provider", "OpenAI", "logo", "🤖", "category", "chat", "total_risk_score", 55, "risk_level", "medium", "tags", List.of("境外存储", "默认训练", "SOC2认证")),
            Map.of("id", "claude", "name", "Claude", "provider", "Anthropic", "logo", "🧡", "category", "chat", "total_risk_score", 44, "risk_level", "medium", "tags", List.of("境外存储", "安全对齐")),
            Map.of("id", "doubao", "name", "豆包", "provider", "字节跳动", "logo", "🫘", "category", "chat", "total_risk_score", 43, "risk_level", "medium", "tags", List.of("境内存储", "等保三级")),
            Map.of("id", "wenxin", "name", "文心一言", "provider", "百度", "logo", "🔮", "category", "chat", "total_risk_score", 36, "risk_level", "low", "tags", List.of("境内存储", "ISO27001")),
            Map.of("id", "tongyi", "name", "通义千问", "provider", "阿里云", "logo", "☁️", "category", "chat", "total_risk_score", 30, "risk_level", "low", "tags", List.of("境内存储", "企业版不训练"))
        );
    }
}
