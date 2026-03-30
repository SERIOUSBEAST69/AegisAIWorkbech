package com.trustai.config;

import com.trustai.service.ApiMetricService;
import com.trustai.service.OpsTelemetryService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApiMetricHistoryScheduler {

    private final ApiMetricService apiMetricService;
    private final OpsTelemetryService opsTelemetryService;
    private final JdbcTemplate jdbcTemplate;

    @Scheduled(fixedRateString = "${ops.metrics.history-interval-ms:60000}")
    public void snapshotApiMetricHistory() {
        try {
            Map<String, Object> snapshot = apiMetricService.snapshot();
            List<Long> companyIds = jdbcTemplate.query(
                "SELECT DISTINCT id FROM company WHERE id IS NOT NULL",
                (rs, rowNum) -> rs.getLong(1)
            );
            for (Long companyId : companyIds) {
                if (companyId != null && companyId > 0L) {
                    opsTelemetryService.persistApiMetricSnapshot(companyId, snapshot);
                }
            }
        } catch (Exception ex) {
            log.debug("Skip api metric history snapshot: {}", ex.getMessage());
        }
    }
}
