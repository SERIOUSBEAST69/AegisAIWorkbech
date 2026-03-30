package com.trustai.config;

import com.trustai.service.ApiMetricService;
import com.trustai.service.OpsTelemetryService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiMetricHistoryScheduler {

    private final ApiMetricService apiMetricService;
    private final OpsTelemetryService opsTelemetryService;

    @Scheduled(fixedRateString = "${ops.metrics.history-interval-ms:60000}")
    public void snapshotApiMetricHistory() {
        try {
            Map<String, Object> snapshot = apiMetricService.snapshot();
            opsTelemetryService.persistApiMetricSnapshot(1L, snapshot);
        } catch (Exception ex) {
            log.debug("Skip api metric history snapshot: {}", ex.getMessage());
        }
    }
}
