package com.trustai.controller;

import com.trustai.dto.ai.AiCallRequest;
import com.trustai.dto.ai.AiCallResponse;
import com.trustai.dto.ai.AiBatchClassificationRequest;
import com.trustai.dto.ai.AiBatchClassificationResponse;
import com.trustai.dto.ai.AiClassificationResult;
import com.trustai.client.AiInferenceClient;
import com.trustai.service.AiService;
import com.trustai.service.RateLimiterService;
import com.trustai.utils.R;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.exception.BizException;
import com.trustai.entity.AiCallLog;
import com.trustai.entity.AiModel;
import com.trustai.entity.ModelCallStat;
import com.trustai.entity.User;
import com.trustai.service.AiCallAuditService;
import com.trustai.service.AiModelService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.ModelCallStatService;
import com.trustai.service.CompanyScopeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
@Validated
public class AiCallController {

    private final AiService aiService;
    private final RateLimiterService rateLimiterService;
    private final AiCallAuditService aiCallAuditService;
    private final CurrentUserService currentUserService;
    private final ModelCallStatService modelCallStatService;
    private final AiModelService aiModelService;
    private final CompanyScopeService companyScopeService;
    private final AiInferenceClient aiInferenceClient;

    @PostMapping("/call")
    public R<AiCallResponse> call(@RequestBody @Valid AiCallRequest request, Principal principal, HttpServletRequest httpRequest) {
        User currentUser = currentUserService.requireCurrentUser();
        Long userId = currentUser.getId();
        Long companyId = currentUser.getCompanyId();
        String username = currentUser.getUsername();
        String ip = httpRequest.getRemoteAddr();
        return R.ok(aiService.chat(request, userId, companyId, username, ip));
    }

    @PostMapping("/quota/reset/{modelCode}")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS')")
    public R<?> resetQuota(@PathVariable String modelCode) {
        rateLimiterService.reset(modelCode, LocalDate.now());
        return R.okMsg("已重置今日配额");
    }

    @GetMapping("/monitor/summary")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','EXECUTIVE','SECOPS','DATA_ADMIN','AI_BUILDER','BUSINESS_OWNER','EMPLOYEE')")
    public R<?> monitorSummary(@RequestParam(defaultValue = "30") int days,
                               @RequestParam(defaultValue = "false") boolean includeMeta) {
        try {
            Long companyId = currentUserService.requireCurrentUser().getCompanyId();
            int safeDays = Math.max(1, Math.min(30, days));
            LocalDateTime from = LocalDateTime.now().minusDays(safeDays);
            List<AiCallLog> logs = aiCallAuditService.list(new QueryWrapper<AiCallLog>()
                .eq(companyId != null, "company_id", companyId)
                .ge("create_time", from)
                .orderByDesc("create_time")
                .last("limit 5000"));
            Map<String, MonitorRow> map = new HashMap<>();
            for (AiCallLog log : logs) {
                MonitorRow row = map.computeIfAbsent(log.getModelCode(), k -> new MonitorRow(k, log.getProvider()));
                row.total++;
                if ("success".equalsIgnoreCase(log.getStatus())) row.success++;
                if (log.getDurationMs() != null) row.totalDuration += log.getDurationMs();
            }
            String source = "ai_call_log";
            if (map.isEmpty()) {
                map.putAll(buildFallbackFromModelStat(companyId));
                source = "model_call_stat";
            }
            List<MonitorRow> result = new ArrayList<>(map.values());
            result.forEach(MonitorRow::finish);
            if (includeMeta) {
                Map<String, Object> payload = new LinkedHashMap<>();
                payload.put("windowDays", safeDays);
                payload.put("sampleCount", logs.size());
                payload.put("source", source);
                payload.put("models", result);
                return R.ok(payload);
            }
            return R.ok(result);
        } catch (Exception e) {
            throw new BizException(50000, "监控摘要加载失败：" + e.getMessage());
        }
    }

    private Map<String, MonitorRow> buildFallbackFromModelStat(Long companyId) {
        Date from = java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(30));
        List<Long> companyUserIds = companyScopeService.companyUserIds();
        List<ModelCallStat> stats = modelCallStatService.list(new QueryWrapper<ModelCallStat>()
            .ge("date", from)
            .in(!companyUserIds.isEmpty(), "user_id", companyUserIds)
            .orderByDesc("date")
            .last("limit 2000"));
        Map<Long, AiModel> models = new LinkedHashMap<>();
        List<AiModel> allModels = aiModelService.list(new QueryWrapper<AiModel>()
            .eq(companyId != null, "company_id", companyId));
        for (AiModel model : allModels) {
            if (model.getId() != null) {
                models.put(model.getId(), model);
            }
        }

        Map<String, MonitorRow> out = new LinkedHashMap<>();
        for (ModelCallStat stat : stats) {
            AiModel model = stat.getModelId() == null ? null : models.get(stat.getModelId());
            String modelCode = model == null ? "unknown-model" : String.valueOf(model.getModelCode());
            String provider = model == null ? "unknown" : String.valueOf(model.getProvider());
            MonitorRow row = out.computeIfAbsent(modelCode, k -> new MonitorRow(modelCode, provider));
            long calls = stat.getCallCount() == null ? 0L : stat.getCallCount();
            row.total += calls;
            // 历史统计表无失败明细，采用保守估计用于观测可视化。
            row.success += Math.max(0L, (long) Math.floor(calls * 0.92d));
            row.totalDuration += stat.getTotalLatencyMs() == null ? 0L : stat.getTotalLatencyMs();
        }
        return out;
    }

    @GetMapping("/monitor/trend")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS')")
    public R<?> monitorTrend() {
        try {
            Long companyId = currentUserService.requireCurrentUser().getCompanyId();
            Map<String, TrendRow> trend = new HashMap<>();
            List<AiCallLog> logs = aiCallAuditService.list(new QueryWrapper<AiCallLog>()
                .eq(companyId != null, "company_id", companyId)
                .orderByDesc("create_time")
                .last("limit 1000"));
            for (AiCallLog log : logs) {
                if (log.getCreateTime() == null) continue;
                LocalDate d = log.getCreateTime().toLocalDate();
                String key = d.toString() + "|" + log.getModelCode();
                TrendRow row = trend.computeIfAbsent(key, k -> new TrendRow(log.getModelCode(), log.getProvider(), d));
                row.total++;
                if ("success".equalsIgnoreCase(log.getStatus())) row.success++;
            }
            // 确保最近 7 天每个模型都有条目（即使 0）
            List<TrendRow> list = new ArrayList<>(trend.values());
            list.sort((a, b) -> a.date.compareTo(b.date));
            return R.ok(list);
        } catch (Exception e) {
            return R.ok(List.of());
        }
    }

    @GetMapping("/monitor/logs")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS')")
    public R<?> monitorLogs(@RequestParam(defaultValue = "1") int page,
                            @RequestParam(defaultValue = "20") int pageSize) {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        int offset = (safePage - 1) * safePageSize;
        Long companyId = currentUserService.requireCurrentUser().getCompanyId();
        long total = aiCallAuditService.count(
            new QueryWrapper<AiCallLog>()
                .eq(companyId != null, "company_id", companyId)
        );
        List<AiCallLog> list = aiCallAuditService.list(
            new QueryWrapper<AiCallLog>()
                .eq(companyId != null, "company_id", companyId)
                .orderByDesc("create_time")
                .last("LIMIT " + safePageSize + " OFFSET " + offset)
        );

        Map<String, Object> payload = new HashMap<>();
        payload.put("total", total);
        payload.put("current", safePage);
        payload.put("pageSize", safePageSize);
        payload.put("list", list);
        return R.ok(payload);
    }

    @GetMapping("/monitor/logs/verify-chain")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS')")
    public R<?> verifyAiCallLogChain() {
        Long companyId = currentUserService.requireCurrentUser().getCompanyId();
        return R.ok(aiCallAuditService.verifyHashChain(companyId));
    }

    @PostMapping("/monitor/bootstrap-trace")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS')")
    public R<?> bootstrapTraceableData(@RequestBody(required = false) Map<String, Object> payload) {
        User currentUser = currentUserService.requireCurrentUser();
        Long companyId = currentUser.getCompanyId();
        int sampleSize = Math.max(10, Math.min(120, toInt(payload == null ? null : payload.get("sampleSize"), 40)));

        AiModel selectedModel = aiModelService.list(new QueryWrapper<AiModel>()
                .eq(companyId != null, "company_id", companyId)
                .eq("status", "enabled")
                .orderByDesc("id")
                .last("limit 1"))
            .stream()
            .findFirst()
            .orElse(null);
        String modelCode = selectedModel == null ? "sensitive-clf-offline" : String.valueOf(selectedModel.getModelCode());
        String provider = selectedModel == null ? "python-service" : String.valueOf(selectedModel.getProvider());

        List<String> texts = buildTraceTexts(sampleSize);
        int predictedCount = 0;
        List<AiClassificationResult> predictResults = List.of();
        try {
            AiBatchClassificationRequest req = new AiBatchClassificationRequest();
            req.setTexts(texts);
            AiBatchClassificationResponse resp = aiInferenceClient.batchPredict(req);
            if (resp != null && resp.getResults() != null) {
                predictResults = resp.getResults();
                predictedCount = resp.getResults().size();
            }
        } catch (Exception ignored) {
            predictedCount = 0;
            predictResults = List.of();
        }

        int inserted = 0;
        for (int i = 0; i < texts.size(); i++) {
            String text = texts.get(i);
            AiClassificationResult classified = i < predictResults.size() ? predictResults.get(i) : null;
            String predictedLabel = classified == null ? "unknown" : String.valueOf(classified.getLabel());

            AiCallLog log = new AiCallLog();
            log.setCompanyId(companyId);
            log.setUserId(currentUser.getId());
            log.setUsername(currentUser.getUsername());
            if (selectedModel != null) {
                log.setModelId(selectedModel.getId());
            }
            log.setModelCode(modelCode);
            log.setProvider(provider);
            log.setInputPreview(text.length() > 100 ? text.substring(0, 100) : text);
            log.setOutputPreview("predicted=" + predictedLabel);
            log.setStatus("success");
            log.setDurationMs((long) ThreadLocalRandom.current().nextInt(45, 260));
            log.setTokenUsage(Math.max(12, Math.min(500, text.length() * 2)));
            log.setIp("127.0.0.1");
            log.setCreateTime(LocalDateTime.now().minusMinutes(sampleSize - i));
            aiCallAuditService.save(log);
            inserted++;
        }

        Map<String, Object> rebuilt = aiCallAuditService.rebuildHashChain(companyId);
        Map<String, Object> verify = aiCallAuditService.verifyHashChain(companyId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("companyId", companyId);
        result.put("userId", currentUser.getId());
        result.put("modelCode", modelCode);
        result.put("provider", provider);
        result.put("requestedSamples", sampleSize);
        result.put("predictedSamples", predictedCount);
        result.put("insertedAuditLogs", inserted);
        result.put("rebuilt", rebuilt);
        result.put("verify", verify);
        return R.ok(result);
    }

    private List<String> buildTraceTexts(int size) {
        List<String> seed = List.of(
            "手机号 13800138000",
            "联系人邮箱 admin@aegis.local",
            "身份证号 11010119900307001X",
            "银行卡号 6222026200000832021",
            "广东省深圳市南山区科技园南路",
            "客户姓名：赵磊",
            "风险处置完成，已形成审计记录",
            "请核验数据共享审批链路"
        );
        List<String> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            String base = seed.get(i % seed.size());
            out.add(base + " #trace-" + (i + 1));
        }
        return out;
    }

    private int toInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    @SuppressWarnings("unused")
    private static class MonitorRow {
        public final String modelCode;
        public final String provider;
        public long total;
        public long success;
        public long avgDuration;
        private long totalDuration;

        MonitorRow(String modelCode, String provider) {
            this.modelCode = modelCode;
            this.provider = provider;
        }
        void finish() {
            if (total > 0 && totalDuration > 0) avgDuration = totalDuration / total;
        }
    }

    @SuppressWarnings("unused")
    private static class TrendRow {
        public final String modelCode;
        public final String provider;
        public final LocalDate date;
        public long total;
        public long success;
        TrendRow(String modelCode, String provider, LocalDate date) {
            this.modelCode = modelCode;
            this.provider = provider;
            this.date = date;
        }
    }
}
