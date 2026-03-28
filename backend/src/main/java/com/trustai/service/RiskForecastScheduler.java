package com.trustai.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trustai.client.AiInferenceClient;
import com.trustai.dto.ai.RiskForecastRequest;
import com.trustai.dto.ai.RiskForecastResponse;
import com.trustai.entity.RiskEvent;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 每日 LSTM 风险预测定时任务
 *
 * <p>每天凌晨 02:00 自动：
 * <ol>
 *   <li>从 risk_event 表拉取最近 90 天的每日风险事件数作为时序输入</li>
 *   <li>调用 Python 微服务 POST /predict/risk（LSTM 模型）获取未来 7 天预测序列</li>
 *   <li>将预测结果缓存到内存，供 /api/risk/forecast 和首页工作台实时读取</li>
 * </ol>
 *
 * <p>降级策略：若 Python 微服务不可用，自动回退到移动平均预测，并在日志中标记。
 *
 * <p>自问自答 – Q2（数据来源）：
 * 本调度任务确保 forecastSeries 数据来源于真实历史数据库，而非写死的 Mock 数组。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RiskForecastScheduler {

    /** 最近 N 天的历史数据窗口（90 天可提供足够的季节性样本，LSTM 最低要求约 15 天） */
    private static final int HISTORY_DAYS = 90;

    /** 预测未来天数 */
    private static final int HORIZON = 7;

    private final RiskEventService riskEventService;
    private final AiInferenceClient aiInferenceClient;

    /** 最新 LSTM 预测结果（原子引用，线程安全）；启动时立即触发一次 */
    private final AtomicReference<ForecastResult> latestForecast = new AtomicReference<>(null);

    // ── 每天凌晨 02:00 执行 ────────────────────────────────────────────────────
    @Scheduled(cron = "0 0 2 * * ?")
    public void scheduledForecast() {
        log.info("[RiskForecastScheduler] Daily LSTM forecast job started");
        try {
            ForecastResult result = runForecast();
            latestForecast.set(result);
            log.info("[RiskForecastScheduler] Forecast complete: method={}, series={}",
                    result.method, result.forecast);
        } catch (Exception e) {
            log.error("[RiskForecastScheduler] Daily forecast job failed", e);
        }
    }

    /**
     * 立即执行一次预测并缓存结果（供控制器主动调用刷新）。
     *
     * @return 最新预测结果
     */
    public ForecastResult refreshNow() {
        ForecastResult result = runForecast();
        latestForecast.set(result);
        return result;
    }

    /**
     * 获取最新缓存的预测结果（若尚未运行则立即触发一次）。
     *
     * @return 最新预测列表（7 天），方法标记（lstm / moving_average_fallback）
     */
    public ForecastResult getLatest() {
        ForecastResult cached = latestForecast.get();
        if (cached == null) {
            cached = runForecast();
            latestForecast.set(cached);
        }
        return cached;
    }

    // ── 核心逻辑 ───────────────────────────────────────────────────────────────
    private ForecastResult runForecast() {
        List<Double> history = loadHistory();
        log.info("[RiskForecastScheduler] Loaded {} daily history points (last {} days)",
                history.size(), HISTORY_DAYS);

        if (history.isEmpty()) {
            log.warn("[RiskForecastScheduler] No history data, returning zero forecast");
            return new ForecastResult(Collections.nCopies(HORIZON, 0.0), "empty_history", history, null);
        }

        // 尝试调用 LSTM 微服务
        try {
            RiskForecastRequest req = new RiskForecastRequest();
            req.setSeries(history);
            req.setHorizon(HORIZON);
            RiskForecastResponse resp = aiInferenceClient.predictRisk(req);
            if (resp != null && resp.getForecast() != null && !resp.getForecast().isEmpty()) {
                return new ForecastResult(resp.getForecast(), "lstm", history, null);
            }
        } catch (Exception e) {
            log.warn("[RiskForecastScheduler] Python LSTM unavailable ({}), using moving-average fallback",
                    e.getMessage());
        }

        // 降级：移动平均
        double avg = history.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double rounded = Math.round(avg * 100.0) / 100.0;
        List<Double> fallback = Collections.nCopies(HORIZON, rounded);
        return new ForecastResult(fallback, "moving_average_fallback", history,
                "Python 微服务不可用，已降级至移动平均预测");
    }

    /**
     * 从 risk_event 表按日聚合最近 HISTORY_DAYS 天的事件数。
     *
     * <p>返回值长度 ≤ HISTORY_DAYS，有数据的日期才出现（稀疏序列）。
     * LSTM 端会处理缺失点，但对预测质量有一定影响，建议保持持续写入风险事件。
     */
    private List<Double> loadHistory() {
        LocalDate from = LocalDate.now().minusDays(HISTORY_DAYS);
        Date fromDate = Date.from(from.atStartOfDay(ZoneId.systemDefault()).toInstant());

        List<RiskEvent> events = riskEventService.list(
                new LambdaQueryWrapper<RiskEvent>()
                        .ge(RiskEvent::getCreateTime, fromDate)
                        .orderByAsc(RiskEvent::getCreateTime)
        );
        if (events.isEmpty()) {
            return List.of();
        }

        // 按日聚合
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        for (RiskEvent event : events) {
            if (event.getCreateTime() == null) continue;
            LocalDate day = event.getCreateTime()
                    .toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            counts.put(day, counts.getOrDefault(day, 0L) + 1);
        }

        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getValue().doubleValue())
                .collect(Collectors.toList());
    }

    // ── DTO ────────────────────────────────────────────────────────────────────
    public static class ForecastResult {
        public final List<Double> forecast;
        public final String method;
        public final List<Double> inputHistory;
        public final String note;

        public ForecastResult(List<Double> forecast, String method,
                              List<Double> inputHistory, String note) {
            this.forecast     = forecast;
            this.method       = method;
            this.inputHistory = inputHistory;
            this.note         = note;
        }
    }
}
