package com.trustai.controller;

import com.trustai.service.ApiMetricService;
import com.trustai.service.KeyTaskMetricService;
import com.trustai.service.OpsTelemetryService;
import jakarta.servlet.http.HttpServletRequest;
import com.trustai.utils.R;
import java.util.Map;
import org.springframework.util.StringUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ops-metrics")
public class OpsMetricsController {

    private final ApiMetricService apiMetricService;
    private final KeyTaskMetricService keyTaskMetricService;
    private final OpsTelemetryService opsTelemetryService;

    public OpsMetricsController(ApiMetricService apiMetricService,
                                KeyTaskMetricService keyTaskMetricService,
                                OpsTelemetryService opsTelemetryService) {
        this.apiMetricService = apiMetricService;
        this.keyTaskMetricService = keyTaskMetricService;
        this.opsTelemetryService = opsTelemetryService;
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
    public R<Map<String, Object>> webVitals(@RequestBody(required = false) Map<String, Object> payload,
                                            HttpServletRequest request) {
        Long companyId = resolveCompanyId(request);
        return R.ok(opsTelemetryService.saveWebVital(payload, companyId));
    }

    @GetMapping("/web-vitals/summary")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS','EXECUTIVE')")
    public R<Map<String, Object>> webVitalsSummary(@RequestParam(defaultValue = "7") int days,
                                                   HttpServletRequest request) {
        return R.ok(opsTelemetryService.webVitalSummary(resolveCompanyId(request), days));
    }

    @GetMapping("/http-history")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS','EXECUTIVE')")
    public R<Map<String, Object>> httpHistory(@RequestParam(defaultValue = "7") int days,
                                              @RequestParam(required = false) String api,
                                              HttpServletRequest request) {
        return R.ok(opsTelemetryService.httpHistory(resolveCompanyId(request), days, api));
    }

    @GetMapping("/slow-queries")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS','EXECUTIVE')")
    public R<Map<String, Object>> slowQueries(@RequestParam(defaultValue = "7") int days,
                                              HttpServletRequest request) {
        return R.ok(opsTelemetryService.slowQuerySummary(resolveCompanyId(request), days));
    }

    private Long resolveCompanyId(HttpServletRequest request) {
        String companyHeader = request == null ? null : request.getHeader("X-Company-Id");
        if (StringUtils.hasText(companyHeader)) {
            try {
                return Long.parseLong(companyHeader.trim());
            } catch (NumberFormatException ignored) {
                return 1L;
            }
        }
        return 1L;
    }
}
