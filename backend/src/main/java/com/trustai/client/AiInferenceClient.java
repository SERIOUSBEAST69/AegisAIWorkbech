package com.trustai.client;

import com.trustai.dto.ai.AiBatchClassificationRequest;
import com.trustai.dto.ai.AiBatchClassificationResponse;
import com.trustai.dto.ai.AiClassificationRequest;
import com.trustai.dto.ai.AiClassificationResult;
import com.trustai.dto.ai.RiskForecastRequest;
import com.trustai.dto.ai.RiskForecastResponse;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "aiInferenceClient", url = "${ai.inference.base-url}")
public interface AiInferenceClient {

    @PostMapping("/predict")
    AiClassificationResult predict(@RequestBody AiClassificationRequest request);

    @PostMapping("/batch_predict")
    AiBatchClassificationResponse batchPredict(@RequestBody AiBatchClassificationRequest request);

    @PostMapping("/predict/risk")
    RiskForecastResponse predictRisk(@RequestBody RiskForecastRequest request);

    @GetMapping("/metrics")
    Map<String, Object> metrics();

    @GetMapping("/model-lineage")
    Map<String, Object> modelLineage();

    @GetMapping("/drift/status")
    Map<String, Object> driftStatus();

    @GetMapping("/explainability/report")
    Map<String, Object> explainabilityReport();

    @PostMapping("/data-factory/build")
    Map<String, Object> buildDataFactory(@RequestBody Map<String, Object> payload);

    @PostMapping("/train/factory")
    Map<String, Object> trainFactory(@RequestBody Map<String, Object> payload);

    @PostMapping("/train/adversarial-feedback")
    Map<String, Object> trainAdversarialFeedback(@RequestBody Map<String, Object> payload);

    @GetMapping("/model-release/status")
    Map<String, Object> modelReleaseStatus();

    @GetMapping("/model-release/traffic-stats")
    Map<String, Object> modelReleaseTrafficStats();

    @PostMapping("/model-release/register-candidate")
    Map<String, Object> registerModelReleaseCandidate(@RequestBody Map<String, Object> payload);

    @PostMapping("/model-release/promote-canary")
    Map<String, Object> promoteModelReleaseCanary(@RequestBody Map<String, Object> payload);

    @PostMapping("/model-release/promote-stable")
    Map<String, Object> promoteModelReleaseStable(@RequestBody Map<String, Object> payload);

    @PostMapping("/model-release/rollback")
    Map<String, Object> rollbackModelRelease(@RequestBody Map<String, Object> payload);

    @GetMapping("/innovation/report")
    Map<String, Object> innovationReport();

    // ── AI 服务风险评级 API ────────────────────────────────────────────────────

    /** 获取所有已收录 AI 服务的风险评级摘要列表。 */
    @GetMapping("/api/risk/list")
    Map<String, Object> riskList();

    /** 查询单个 AI 服务的详细风险评分。 */
    @GetMapping("/api/risk/score")
    Map<String, Object> riskScore(@RequestParam("service") String serviceId);

    /** 动态刷新 AI 风险评级数据（从文件重载或接受新数据）。 */
    @PostMapping("/api/risk/refresh")
    Map<String, Object> riskRefresh(@RequestBody Map<String, Object> payload);

    // ── 员工行为异常检测 API ──────────────────────────────────────────────────

    /** 检测单条行为记录是否异常。 */
    @PostMapping("/api/anomaly/check")
    Map<String, Object> anomalyCheck(@RequestBody Map<String, Object> payload);

    /** 查询异常事件日志。 */
    @GetMapping("/api/anomaly/events")
    Map<String, Object> anomalyEvents();

    /** 获取异常检测模型状态。 */
    @GetMapping("/api/anomaly/status")
    Map<String, Object> anomalyStatus();

    // ── OpenClaw 攻防对弈 API ───────────────────────────────────────────────

    @GetMapping("/api/adversarial/meta")
    Map<String, Object> adversarialMeta();

    @PostMapping("/api/adversarial/run")
    Map<String, Object> adversarialRun(@RequestBody Map<String, Object> payload);

    @PostMapping("/api/adversarial/start")
    Map<String, Object> adversarialStart(@RequestBody Map<String, Object> payload);
}
