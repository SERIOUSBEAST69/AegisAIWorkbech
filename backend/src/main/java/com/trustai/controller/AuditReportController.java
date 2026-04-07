package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.entity.AdversarialRecord;
import com.trustai.entity.AuditLog;
import com.trustai.entity.GovernanceEvent;
import com.trustai.service.AdversarialRecordService;
import com.trustai.service.AuditLogService;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.GovernanceEventService;
import com.trustai.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/audit-report")
public class AuditReportController {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private CompanyScopeService companyScopeService;

    @Autowired
    private GovernanceEventService governanceEventService;

    @Autowired
    private AdversarialRecordService adversarialRecordService;

    @GetMapping("/compare")
    @PreAuthorize("@currentUserService.hasAnyPermission('audit:report:view','ops:metrics:view')")
    public R<Map<String, Object>> compare(@RequestParam String from, @RequestParam String to) {
        String startAt = from + " 00:00:00";
        String endAt = to + " 23:59:59";

        List<Long> userIds = companyScopeService.companyUserIds();
        QueryWrapper<AuditLog> base = new QueryWrapper<>();
        base.between("operation_time", startAt, endAt);
        if (!userIds.isEmpty()) {
            base.in("user_id", userIds);
        }
        List<AuditLog> logs = auditLogService.list(base);
        long success = logs.stream().filter(l -> "成功".equals(l.getResult()) || "success".equalsIgnoreCase(l.getResult())).count();
        long fail = logs.size() - success;

        List<GovernanceEvent> governanceEvents = governanceEventService.list(
            companyScopeService.withCompany(new QueryWrapper<GovernanceEvent>().between("event_time", startAt, endAt))
        );
        long disposed = governanceEvents.stream()
            .filter(item -> "blocked".equalsIgnoreCase(item.getStatus()) || "ignored".equalsIgnoreCase(item.getStatus()))
            .count();

        Map<String, Long> eventTypeDistribution = new LinkedHashMap<>();
        eventTypeDistribution.put("PRIVACY_ALERT", 0L);
        eventTypeDistribution.put("ANOMALY_ALERT", 0L);
        eventTypeDistribution.put("SHADOW_AI_ALERT", 0L);
        eventTypeDistribution.put("SECURITY_ALERT", 0L);
        for (GovernanceEvent item : governanceEvents) {
            String key = item.getEventType() == null ? "UNKNOWN" : item.getEventType().toUpperCase();
            eventTypeDistribution.put(key, eventTypeDistribution.getOrDefault(key, 0L) + 1);
        }

        long adversarialRuns = adversarialRecordService.count(
            companyScopeService.withCompany(new QueryWrapper<AdversarialRecord>().between("create_time", startAt, endAt))
        );

        Map<String, Object> map = new HashMap<>();
        map.put("total", logs.size());
        map.put("success", success);
        map.put("fail", fail);
        map.put("governanceTotal", governanceEvents.size());
        map.put("governanceDisposed", disposed);
        map.put("governanceByType", eventTypeDistribution);
        map.put("adversarialRuns", adversarialRuns);
        return R.ok(map);
    }

    @GetMapping("/generate")
    @PreAuthorize("@currentUserService.hasRole('ADMIN') || @currentUserService.hasPermission('audit:report:generate')")
    public R<Map<String, String>> generate(@RequestParam(required = false) String range) {
        String safeRange = StringUtils.hasText(range) ? range.trim() : "latest";
        Map<String, String> map = new HashMap<>();
        map.put("title", "合规审计报告" + (StringUtils.hasText(range) ? ("-" + safeRange) : ""));
        map.put("downloadUrl", "/api/audit-report/download?range=" + safeRange + "&ts=" + System.currentTimeMillis());
        return R.ok(map);
    }

    @GetMapping("/download")
    @PreAuthorize("@currentUserService.hasAnyPermission('audit:report:view','audit:report:generate')")
    public ResponseEntity<byte[]> download(@RequestParam(required = false) String range,
                                           @RequestParam(required = false) Long ts) {
        String safeRange = StringUtils.hasText(range) ? range.trim() : "latest";
        String filename = "audit-" + safeRange + "-" + (ts == null ? System.currentTimeMillis() : ts) + ".txt";
        String content = "Aegis 审计报告\n"
            + "范围: " + safeRange + "\n"
            + "生成时间: " + System.currentTimeMillis() + "\n"
            + "说明: 当前环境返回在线下载占位文件，避免前端路由回落到首页。\n";
        byte[] body = content.getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
            .contentType(MediaType.TEXT_PLAIN)
            .body(body);
    }
}
