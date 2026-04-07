package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.entity.AiCallLog;
import com.trustai.entity.AuditLog;
import com.trustai.entity.PrivacyEvent;
import com.trustai.entity.RiskEvent;
import com.trustai.entity.SecurityEvent;
import com.trustai.entity.TenantHealthReport;
import com.trustai.entity.User;
import com.trustai.service.AiCallAuditService;
import com.trustai.service.AuditLogService;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.PrivacyEventService;
import com.trustai.service.RiskEventService;
import com.trustai.service.SecurityEventService;
import com.trustai.service.TenantHealthReportService;
import com.trustai.service.UserService;
import com.trustai.utils.R;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/company")
public class CompanyHealthController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CompanyScopeService companyScopeService;
    private final CurrentUserService currentUserService;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final PrivacyEventService privacyEventService;
    private final RiskEventService riskEventService;
    private final SecurityEventService securityEventService;
    private final AiCallAuditService aiCallAuditService;
    private final TenantHealthReportService tenantHealthReportService;

    public CompanyHealthController(CompanyScopeService companyScopeService,
                                   CurrentUserService currentUserService,
                                   UserService userService,
                                   AuditLogService auditLogService,
                                   PrivacyEventService privacyEventService,
                                   RiskEventService riskEventService,
                                   SecurityEventService securityEventService,
                                   AiCallAuditService aiCallAuditService,
                                   TenantHealthReportService tenantHealthReportService) {
        this.companyScopeService = companyScopeService;
        this.currentUserService = currentUserService;
        this.userService = userService;
        this.auditLogService = auditLogService;
        this.privacyEventService = privacyEventService;
        this.riskEventService = riskEventService;
        this.securityEventService = securityEventService;
        this.aiCallAuditService = aiCallAuditService;
        this.tenantHealthReportService = tenantHealthReportService;
    }

    @PostMapping("/health-check")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS')")
    public R<Map<String, Object>> runHealthCheck() {
        Long companyId = companyScopeService.requireCompanyId();
        User operator = currentUserService.requireCurrentUser();

        List<User> users = userService.list(new QueryWrapper<User>().eq("company_id", companyId));
        List<Long> userIds = users.stream().map(User::getId).filter(id -> id != null).toList();
        Set<Long> roleBoundUsers = users.stream().filter(item -> item.getRoleId() != null).map(User::getId).collect(Collectors.toSet());

        int totalUsers = users.size();
        List<User> noRoleUsers = users.stream().filter(item -> item.getRoleId() == null).toList();

        Date from = new Date(System.currentTimeMillis() - 30L * 24L * 3600_000L);
        Set<Long> auditedUsers = auditLogService.list(new QueryWrapper<AuditLog>()
                .in(!userIds.isEmpty(), "user_id", userIds)
                .ge("operation_time", from)
                .last("limit 5000"))
            .stream()
            .map(AuditLog::getUserId)
            .filter(id -> id != null)
            .collect(Collectors.toSet());
        double auditCoverage = totalUsers <= 0 ? 0d : (auditedUsers.size() * 100.0d / totalUsers);

        long privacyTotal = privacyEventService.count(new QueryWrapper<PrivacyEvent>()
            .eq("company_id", companyId)
            .ge("event_time", from));
        long privacyIgnore = privacyEventService.count(new QueryWrapper<PrivacyEvent>()
            .eq("company_id", companyId)
            .eq("action", "ignore")
            .ge("event_time", from));
        long privacyDesense = privacyEventService.count(new QueryWrapper<PrivacyEvent>()
            .eq("company_id", companyId)
            .eq("action", "desensitize")
            .ge("event_time", from));

        long openRisk = riskEventService.count(new QueryWrapper<RiskEvent>()
            .eq("company_id", companyId)
            .in("status", List.of("open", "pending", "processing")));
        long pendingSecurity = securityEventService.count(new QueryWrapper<SecurityEvent>()
            .eq("company_id", companyId)
            .in("status", List.of("pending", "reviewing")));
        long highSecurity = securityEventService.count(new QueryWrapper<SecurityEvent>()
            .eq("company_id", companyId)
            .in("severity", List.of("high", "critical"))
            .ge("event_time", from));
        long aiCalls = aiCallAuditService.count(new QueryWrapper<AiCallLog>()
            .eq("company_id", companyId)
            .ge("create_time", from));

        int privacyDebtScore = calcPrivacyDebtScore(privacyTotal, privacyIgnore, privacyDesense);
        int riskScore = calcRiskScore(auditCoverage, noRoleUsers.size(), openRisk, pendingSecurity, highSecurity, privacyDebtScore, aiCalls);
        String status = riskScore >= 70 ? "critical" : (riskScore >= 40 ? "warning" : "healthy");

        Map<String, Object> permissionGaps = new LinkedHashMap<>();
        permissionGaps.put("rolelessUserCount", noRoleUsers.size());
        permissionGaps.put("roleBoundUserCount", roleBoundUsers.size());
        permissionGaps.put("rolelessSample", noRoleUsers.stream().limit(10).map(User::getUsername).toList());

        Map<String, Object> riskMetrics = new LinkedHashMap<>();
        riskMetrics.put("totalUsers", totalUsers);
        riskMetrics.put("auditedUsers30d", auditedUsers.size());
        riskMetrics.put("openRiskEvents", openRisk);
        riskMetrics.put("pendingSecurityEvents", pendingSecurity);
        riskMetrics.put("highSecurityEvents30d", highSecurity);
        riskMetrics.put("privacyEvents30d", privacyTotal);
        riskMetrics.put("privacyIgnore30d", privacyIgnore);
        riskMetrics.put("privacyDesense30d", privacyDesense);
        riskMetrics.put("aiCalls30d", aiCalls);
        riskMetrics.put("riskScore", riskScore);

        TenantHealthReport report = new TenantHealthReport();
        report.setCompanyId(companyId);
        report.setCheckAt(new Date());
        report.setPermissionGapsJson(toJson(permissionGaps));
        report.setAuditCoverage(round2(auditCoverage));
        report.setPrivacyDebtScore(privacyDebtScore);
        report.setRiskMetricsJson(toJson(riskMetrics));
        report.setStatus(status);
        report.setCreatedBy(operator.getId());
        report.setCreateTime(new Date());
        report.setUpdateTime(new Date());
        tenantHealthReportService.save(report);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", report.getId());
        payload.put("companyId", companyId);
        payload.put("status", status);
        payload.put("auditCoverage", report.getAuditCoverage());
        payload.put("privacyDebtScore", privacyDebtScore);
        payload.put("permissionGaps", permissionGaps);
        payload.put("riskMetrics", riskMetrics);
        payload.put("checkedAt", report.getCheckAt());
        return R.ok(payload);
    }

    @GetMapping("/health/latest")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS')")
    public R<Map<String, Object>> latestHealthReport() {
        Long companyId = companyScopeService.requireCompanyId();
        List<TenantHealthReport> rows = tenantHealthReportService.list(new QueryWrapper<TenantHealthReport>()
            .eq("company_id", companyId)
            .orderByDesc("check_at")
            .last("limit 1"));
        if (rows.isEmpty()) {
            return R.ok(Map.of("exists", false));
        }
        TenantHealthReport latest = rows.get(0);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exists", true);
        payload.put("id", latest.getId());
        payload.put("companyId", latest.getCompanyId());
        payload.put("status", latest.getStatus());
        payload.put("auditCoverage", latest.getAuditCoverage());
        payload.put("privacyDebtScore", latest.getPrivacyDebtScore());
        payload.put("permissionGaps", latest.getPermissionGapsJson());
        payload.put("riskMetrics", latest.getRiskMetricsJson());
        payload.put("checkedAt", latest.getCheckAt());
        return R.ok(payload);
    }

    private int calcPrivacyDebtScore(long total, long ignore, long desense) {
        if (total <= 0) {
            return 0;
        }
        double ignoreRatio = (double) ignore / total;
        double desenseRatio = (double) desense / total;
        int ignorePenalty = (int) Math.round(ignoreRatio * 70);
        int desenseBonus = (int) Math.round(desenseRatio * 25);
        int score = Math.max(0, Math.min(100, ignorePenalty - desenseBonus + 20));
        return score;
    }

    private int calcRiskScore(double auditCoverage,
                              int rolelessUsers,
                              long openRisk,
                              long pendingSecurity,
                              long highSecurity,
                              int privacyDebtScore,
                              long aiCalls) {
        int auditPenalty = (int) Math.round(Math.max(0d, (100d - auditCoverage) * 0.25d));
        int rolePenalty = Math.min(20, rolelessUsers * 2);
        int governancePenalty = Math.min(20, (int) (openRisk + pendingSecurity));
        int securityPenalty = Math.min(20, (int) (highSecurity * 2));
        int aiPressure = Math.min(12, (int) Math.round(Math.min(aiCalls, 3000L) / 3000.0d * 12));
        return Math.min(100, auditPenalty + rolePenalty + governancePenalty + securityPenalty + aiPressure + (int) Math.round(privacyDebtScore * 0.35d));
    }

    private String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private double round2(double value) {
        return Math.round(value * 100.0d) / 100.0d;
    }
}
