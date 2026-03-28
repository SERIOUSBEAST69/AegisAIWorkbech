package com.trustai.controller;

import com.trustai.dto.ai.AiCallRequest;
import com.trustai.dto.ai.AiCallResponse;
import com.trustai.service.AiService;
import com.trustai.service.RateLimiterService;
import com.trustai.utils.R;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.entity.AiCallLog;
import com.trustai.service.AiCallAuditService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
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

    @PostMapping("/call")
    public R<AiCallResponse> call(@RequestBody @Valid AiCallRequest request, Principal principal, HttpServletRequest httpRequest) {
        Long userId = principal == null ? null : 0L; // TODO: 从登录上下文获取真实用户ID
        String ip = httpRequest.getRemoteAddr();
        return R.ok(aiService.chat(request, userId, ip));
    }

    @PostMapping("/quota/reset/{modelCode}")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS','AI_BUILDER')")
    public R<?> resetQuota(@PathVariable String modelCode) {
        rateLimiterService.reset(modelCode, LocalDate.now());
        return R.okMsg("已重置今日配额");
    }

    @GetMapping("/monitor/summary")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS','AI_BUILDER')")
    public R<?> monitorSummary() {
        try {
            List<AiCallLog> logs = aiCallAuditService.list(new QueryWrapper<AiCallLog>().orderByDesc("create_time").last("limit 500"));
            Map<String, MonitorRow> map = new HashMap<>();
            for (AiCallLog log : logs) {
                MonitorRow row = map.computeIfAbsent(log.getModelCode(), k -> new MonitorRow(k, log.getProvider()));
                row.total++;
                if ("success".equalsIgnoreCase(log.getStatus())) row.success++;
                if (log.getDurationMs() != null) row.totalDuration += log.getDurationMs();
            }
            List<MonitorRow> result = new ArrayList<>(map.values());
            result.forEach(MonitorRow::finish);
            return R.ok(result);
        } catch (Exception e) {
            // 若表尚未建好或无数据，返回空列表避免前端报错
            return R.ok(List.of());
        }
    }

    @GetMapping("/monitor/trend")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS','AI_BUILDER')")
    public R<?> monitorTrend() {
        try {
            LocalDate today = LocalDate.now();
            Map<String, TrendRow> trend = new HashMap<>();
            List<AiCallLog> logs = aiCallAuditService.list(new QueryWrapper<AiCallLog>().orderByDesc("create_time").last("limit 1000"));
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
