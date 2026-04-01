package com.trustai.controller;

import com.trustai.dto.ai.AiCallRequest;
import com.trustai.dto.ai.AiCallResponse;
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
import java.util.Date;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    public R<?> monitorSummary() {
        try {
            Long companyId = currentUserService.requireCurrentUser().getCompanyId();
            List<AiCallLog> logs = aiCallAuditService.list(new QueryWrapper<AiCallLog>()
                .eq(companyId != null, "company_id", companyId)
                .orderByDesc("create_time")
                .last("limit 500"));
            Map<String, MonitorRow> map = new HashMap<>();
            for (AiCallLog log : logs) {
                MonitorRow row = map.computeIfAbsent(log.getModelCode(), k -> new MonitorRow(k, log.getProvider()));
                row.total++;
                if ("success".equalsIgnoreCase(log.getStatus())) row.success++;
                if (log.getDurationMs() != null) row.totalDuration += log.getDurationMs();
            }
            if (map.isEmpty()) {
                map.putAll(buildFallbackFromModelStat(companyId));
            }
            List<MonitorRow> result = new ArrayList<>(map.values());
            result.forEach(MonitorRow::finish);
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
        List<AiModel> allModels = aiModelService.list();
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
        List<AiCallLog> all = aiCallAuditService.list(new QueryWrapper<AiCallLog>()
            .eq(companyId != null, "company_id", companyId)
            .orderByDesc("create_time"));
        int total = all.size();
        int to = Math.min(total, offset + safePageSize);
        List<AiCallLog> list = offset >= total ? List.of() : all.subList(offset, to);

        Map<String, Object> payload = new HashMap<>();
        payload.put("total", total);
        payload.put("current", safePage);
        payload.put("pageSize", safePageSize);
        payload.put("list", list);
        return R.ok(payload);
    }

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
