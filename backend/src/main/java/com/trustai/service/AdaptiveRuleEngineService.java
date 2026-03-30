package com.trustai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.entity.GovernanceEvent;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdaptiveRuleEngineService {

    private final GovernanceEventService governanceEventService;
    private final CompanyScopeService companyScopeService;

    public Map<String, Object> innovationReport() {
        Long companyId = companyScopeService.requireCompanyId();
        Date since = Date.from(LocalDate.now().minusDays(30).atStartOfDay(ZoneId.systemDefault()).toInstant());
        List<GovernanceEvent> events = governanceEventService.list(new QueryWrapper<GovernanceEvent>()
            .eq("company_id", companyId)
            .ge("event_time", since)
            .orderByAsc("event_time")
            .last("limit 5000"));

        double staticThreshold = 75.0;
        double adaptiveThreshold = 75.0;
        double alpha = 0.18;
        long staticTriggered = 0L;
        long adaptiveTriggered = 0L;
        for (GovernanceEvent event : events) {
            double riskScore = eventRiskScore(event);
            if (riskScore >= staticThreshold) {
                staticTriggered++;
            }
            if (riskScore >= adaptiveThreshold) {
                adaptiveTriggered++;
            }
            adaptiveThreshold = (1 - alpha) * adaptiveThreshold + alpha * riskScore;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("windowDays", 30);
        result.put("sampleCount", events.size());
        result.put("staticThreshold", staticThreshold);
        result.put("adaptiveThresholdFinal", round2(adaptiveThreshold));
        result.put("staticTriggered", staticTriggered);
        result.put("adaptiveTriggered", adaptiveTriggered);
        result.put("triggerDeltaPct", staticTriggered == 0 ? 0.0 : round2((adaptiveTriggered - staticTriggered) * 100.0 / staticTriggered));
        return result;
    }

    private double eventRiskScore(GovernanceEvent event) {
        String severity = String.valueOf(event.getSeverity() == null ? "" : event.getSeverity()).toLowerCase();
        double base = switch (severity) {
            case "critical" -> 95.0;
            case "high" -> 82.0;
            case "medium" -> 64.0;
            case "low" -> 42.0;
            default -> 56.0;
        };
        String status = String.valueOf(event.getStatus() == null ? "" : event.getStatus()).toLowerCase();
        if ("pending".equals(status)) {
            base += 6.0;
        }
        if ("blocked".equals(status)) {
            base -= 4.0;
        }
        return Math.max(0.0, Math.min(100.0, base));
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
