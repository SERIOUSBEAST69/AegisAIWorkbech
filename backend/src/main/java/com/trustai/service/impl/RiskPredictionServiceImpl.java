package com.trustai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trustai.client.AiInferenceClient;
import com.trustai.dto.ai.RiskForecastRequest;
import com.trustai.dto.ai.RiskForecastResponse;
import com.trustai.entity.RiskEvent;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.RiskEventService;
import com.trustai.service.RiskPredictionService;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskPredictionServiceImpl implements RiskPredictionService {

    private final RiskEventService riskEventService;
    private final AiInferenceClient aiInferenceClient;
    private final CompanyScopeService companyScopeService;

    @Override
    public List<Double> forecastNext7Days() {
        List<Double> history = loadHistory();
        if (history.isEmpty()) {
            return Collections.nCopies(7, 0.0);
        }
        try {
            RiskForecastRequest req = new RiskForecastRequest();
            req.setSeries(history);
            req.setHorizon(7);
            RiskForecastResponse resp = aiInferenceClient.predictRisk(req);
            if (resp != null && resp.getForecast() != null && !resp.getForecast().isEmpty()) {
                return resp.getForecast();
            }
        } catch (Exception e) {
            log.warn("AI risk forecast unavailable, using moving average fallback", e);
        }
        return movingAverage(history, 7);
    }

    private List<Double> loadHistory() {
        Long companyId = companyScopeService.requireCompanyId();
        List<RiskEvent> events = riskEventService.list(
            new LambdaQueryWrapper<RiskEvent>()
                .eq(RiskEvent::getCompanyId, companyId)
                .orderByAsc(RiskEvent::getCreateTime)
        );
        if (events.isEmpty()) {
            return List.of();
        }
        Map<LocalDate, Long> counts = new LinkedHashMap<>();
        for (RiskEvent event : events) {
            LocalDate day = event.getCreateTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
            counts.put(day, counts.getOrDefault(day, 0L) + 1);
        }
        return counts.entrySet().stream()
                .sorted(Comparator.comparing(Map.Entry::getKey))
                .map(e -> e.getValue().doubleValue())
                .toList();
    }

    private List<Double> movingAverage(List<Double> history, int horizon) {
        double avg = history.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        List<Double> forecast = new ArrayList<>();
        for (int i = 0; i < horizon; i++) {
            forecast.add(Math.round(avg * 100.0) / 100.0);
        }
        return forecast;
    }
}
