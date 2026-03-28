package com.trustai.controller;

import com.trustai.service.ApiMetricService;
import com.trustai.service.KeyTaskMetricService;
import com.trustai.utils.R;
import java.util.HashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ops-metrics")
public class OpsMetricsController {

    private final ApiMetricService apiMetricService;
    private final KeyTaskMetricService keyTaskMetricService;

    public OpsMetricsController(ApiMetricService apiMetricService, KeyTaskMetricService keyTaskMetricService) {
        this.apiMetricService = apiMetricService;
        this.keyTaskMetricService = keyTaskMetricService;
    }

    @GetMapping("/http")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS','EXECUTIVE')")
    public R<Map<String, Object>> httpMetrics() {
        return R.ok(apiMetricService.snapshot());
    }

    @GetMapping("/key-tasks")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS','EXECUTIVE')")
    public R<Map<String, Object>> keyTasks() {
        return R.ok(keyTaskMetricService.snapshot());
    }

    @PostMapping("/web-vitals")
    public R<Map<String, Object>> webVitals(@RequestBody(required = false) Map<String, Object> payload) {
        Map<String, Object> data = new HashMap<>();
        data.put("accepted", true);
        data.put("metric", payload == null ? "unknown" : payload.getOrDefault("name", "unknown"));
        return R.ok(data);
    }
}
