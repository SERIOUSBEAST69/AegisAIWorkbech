package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.entity.RiskEvent;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.KeyTaskMetricService;
import com.trustai.service.RiskEventService;
import com.trustai.service.UserService;
import com.trustai.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.util.StringUtils;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/risk-event")
public class RiskEventController {
    @Autowired private RiskEventService riskEventService;
    @Autowired private CurrentUserService currentUserService;
    @Autowired private CompanyScopeService companyScopeService;
    @Autowired private KeyTaskMetricService keyTaskMetricService;
    @Autowired private UserService userService;

    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasAnyPermission('risk:event:view','risk:event:handle')")
    public R<List<RiskEvent>> list(@RequestParam(required = false) String type) {
        currentUserService.requireAnyPermission("risk:event:view", "risk:event:handle");
        QueryWrapper<RiskEvent> qw = new QueryWrapper<>();
        companyScopeService.withCompany(qw);
        String typeFilter = normalizeEventType(type);
        if (StringUtils.hasText(typeFilter)) {
            qw.like("type", typeFilter);
        }
        qw.orderByDesc("update_time");
        List<RiskEvent> scoped = riskEventService.list(qw);
        scoped = cleanupInvalidRiskData(scoped);
        return R.ok(filterWalkthroughRows(scoped));
    }

    private List<RiskEvent> cleanupInvalidRiskData(List<RiskEvent> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Long> deleteIds = new ArrayList<>();
        List<RiskEvent> updates = new ArrayList<>();
        List<RiskEvent> kept = new ArrayList<>();

        for (RiskEvent item : rows) {
            if (item == null) {
                continue;
            }
            String rawType = String.valueOf(item.getType() == null ? "" : item.getType()).trim();
            String processLog = String.valueOf(item.getProcessLog() == null ? "" : item.getProcessLog());
            if (!StringUtils.hasText(rawType)
                || "UNKNOWN".equalsIgnoreCase(rawType)
                || "NULL".equalsIgnoreCase(rawType)
                || "N/A".equalsIgnoreCase(rawType)
                || isWalkthrough(rawType)
                || containsWalkthrough(processLog)) {
                if (item.getId() != null) {
                    deleteIds.add(item.getId());
                }
                continue;
            }

            String normalizedType = normalizeEventType(rawType);
            String normalizedLevel = normalizeLevel(item.getLevel(), "MEDIUM");
            String normalizedStatus = normalizeStatus(item.getStatus(), "OPEN");

            boolean changed = false;
            if (StringUtils.hasText(normalizedType) && !normalizedType.equalsIgnoreCase(rawType)) {
                item.setType(normalizedType);
                changed = true;
            }
            if (!normalizedLevel.equalsIgnoreCase(String.valueOf(item.getLevel() == null ? "" : item.getLevel()))) {
                item.setLevel(normalizedLevel);
                changed = true;
            }
            if (!normalizedStatus.equalsIgnoreCase(String.valueOf(item.getStatus() == null ? "" : item.getStatus()))) {
                item.setStatus(normalizedStatus);
                changed = true;
            }
            if (changed) {
                item.setUpdateTime(new Date());
                updates.add(item);
            }
            kept.add(item);
        }

        if (!deleteIds.isEmpty()) {
            riskEventService.removeByIds(deleteIds);
        }
        if (!updates.isEmpty()) {
            riskEventService.updateBatchById(updates);
        }
        return kept;
    }

    private List<RiskEvent> filterWalkthroughRows(List<RiskEvent> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Set<Long> handlerIds = new HashSet<>();
        for (RiskEvent item : rows) {
            if (item != null && item.getHandlerId() != null) {
                handlerIds.add(item.getHandlerId());
            }
        }
        Map<Long, String> usernameById = new HashMap<>();
        if (!handlerIds.isEmpty()) {
            List<User> handlers = userService.lambdaQuery().in(User::getId, handlerIds).list();
            for (User user : handlers) {
                if (user != null && user.getId() != null) {
                    usernameById.put(user.getId(), String.valueOf(user.getUsername() == null ? "" : user.getUsername()));
                }
            }
        }
        return rows.stream()
            .filter(item -> {
                String handler = usernameById.getOrDefault(item.getHandlerId(), "");
                String processLog = String.valueOf(item.getProcessLog() == null ? "" : item.getProcessLog());
                return !isWalkthrough(handler) && !containsWalkthrough(processLog);
            })
            .collect(Collectors.toList());
    }

    private boolean isWalkthrough(String value) {
        return String.valueOf(value == null ? "" : value).trim().toLowerCase().startsWith("walkthrough_");
    }

    private boolean containsWalkthrough(String value) {
        return String.valueOf(value == null ? "" : value).toLowerCase().contains("walkthrough_");
    }

    @PostMapping("/add")
    @PreAuthorize("@currentUserService.hasPermission('risk:event:handle')")
    public R<?> add(@RequestBody RiskEvent event) {
        try {
            currentUserService.requirePermission("risk:event:handle");
            User currentUser = currentUserService.requireCurrentUser();
            String roleCode = currentUserService.currentRoleCode();
            enforceSecopsDuty(currentUser, "create", event);
            event.setId(null);
            event.setCompanyId(companyScopeService.requireCompanyId());
            event.setHandlerId(currentUser.getId());
            event.setCreateTime(new Date());
            event.setUpdateTime(new Date());
            event.setProcessLog(appendTrace(event.getProcessLog(), currentUser, roleCode, event.getCompanyId(), "create"));
            riskEventService.save(event);
            keyTaskMetricService.record("risk.dispose", true);
            return R.okMsg("添加成功");
        } catch (RuntimeException ex) {
            keyTaskMetricService.record("risk.dispose", false);
            throw ex;
        }
    }

    @PostMapping("/update")
    @PreAuthorize("@currentUserService.hasPermission('risk:event:handle')")
    public R<?> update(@RequestBody RiskEvent event) {
        try {
            currentUserService.requirePermission("risk:event:handle");
            if (event == null || event.getId() == null) {
                throw new BizException(40000, "风险事件ID不能为空");
            }
            RiskEvent scoped = riskEventService.getOne(companyScopeService.withCompany(new QueryWrapper<RiskEvent>()).eq("id", event.getId()));
            if (scoped == null) {
                throw new BizException(40400, "风险事件不存在或不在当前公司");
            }
            User currentUser = currentUserService.requireCurrentUser();
            String roleCode = currentUserService.currentRoleCode();
            enforceSecopsDuty(currentUser, "update", event);
            String mergedProcessLog = StringUtils.hasText(event.getProcessLog()) ? event.getProcessLog() : scoped.getProcessLog();
            scoped.setType(StringUtils.hasText(event.getType()) ? event.getType().trim() : scoped.getType());
            scoped.setLevel(normalizeLevel(event.getLevel(), scoped.getLevel()));
            scoped.setStatus(normalizeStatus(event.getStatus(), scoped.getStatus()));
            if (event.getRelatedLogId() != null) {
                scoped.setRelatedLogId(event.getRelatedLogId());
            }
            if (event.getAuditLogIds() != null) {
                scoped.setAuditLogIds(event.getAuditLogIds());
            }
            scoped.setHandlerId(currentUser.getId());
            scoped.setUpdateTime(new Date());
            scoped.setProcessLog(appendTrace(mergedProcessLog, currentUser, roleCode, scoped.getCompanyId(), "update"));
            riskEventService.updateById(scoped);
            keyTaskMetricService.record("risk.dispose", true);
            return R.okMsg("更新成功");
        } catch (RuntimeException ex) {
            keyTaskMetricService.record("risk.dispose", false);
            throw ex;
        }
    }

    private String normalizeLevel(String incoming, String fallback) {
        String value = String.valueOf(incoming == null ? "" : incoming).trim().toUpperCase();
        if ("LOW".equals(value) || "MEDIUM".equals(value) || "HIGH".equals(value) || "CRITICAL".equals(value)) {
            return value;
        }
        return StringUtils.hasText(fallback) ? fallback : "MEDIUM";
    }

    private String normalizeStatus(String incoming, String fallback) {
        String value = String.valueOf(incoming == null ? "" : incoming).trim().toUpperCase();
        if ("OPEN".equals(value) || "PENDING".equals(value)) {
            return "OPEN";
        }
        if ("PROCESSING".equals(value) || "IN_PROGRESS".equals(value)) {
            return "PROCESSING";
        }
        if ("RESOLVED".equals(value) || "DONE".equals(value) || "CLOSED".equals(value)) {
            return "RESOLVED";
        }
        if ("IGNORED".equals(value) || "REJECTED".equals(value) || "SKIPPED".equals(value)) {
            return "IGNORED";
        }
        return StringUtils.hasText(fallback) ? fallback : "OPEN";
    }

    private String normalizeEventType(String incoming) {
        String raw = String.valueOf(incoming == null ? "" : incoming).trim();
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String upper = raw.toUpperCase(Locale.ROOT);
        if (Set.of("PRIVACY_ALERT", "ANOMALY_ALERT", "SHADOW_AI_ALERT", "SECURITY_ALERT", "SENSITIVE_OPERATION", "DATA_EXPORT", "BEHAVIOR_ANOMALY", "PRIVILEGE_ABUSE", "ACCOUNT_COMPROMISE").contains(upper)) {
            return upper;
        }
        if (raw.contains("隐私")) return "PRIVACY_ALERT";
        if (raw.contains("影子AI") || raw.contains("影子模型") || upper.contains("SHADOW")) return "SHADOW_AI_ALERT";
        if (raw.contains("异常") || upper.contains("ANOMALY")) return "ANOMALY_ALERT";
        if (raw.contains("安全") || raw.contains("威胁") || upper.contains("SECURITY")) return "SECURITY_ALERT";
        if (raw.contains("敏感操作")) return "SENSITIVE_OPERATION";
        if (raw.contains("导出")) return "DATA_EXPORT";
        if (raw.contains("权限")) return "PRIVILEGE_ABUSE";
        if (raw.contains("账号")) return "ACCOUNT_COMPROMISE";
        return upper;
    }

    @PostMapping("/delete")
    @PreAuthorize("@currentUserService.hasPermission('risk:event:handle')")
    public R<?> delete(@RequestBody IdReq req) {
        currentUserService.requirePermission("risk:event:handle");
        User currentUser = currentUserService.requireCurrentUser();
        RiskEvent scoped = riskEventService.getOne(companyScopeService.withCompany(new QueryWrapper<RiskEvent>()).eq("id", req.getId()));
        if (scoped == null) {
            throw new BizException(40400, "风险事件不存在或不在当前公司");
        }
        enforceSecopsDuty(currentUser, "delete", scoped);
        riskEventService.removeById(req.getId());
        return R.okMsg("删除成功");
    }

    private void enforceSecopsDuty(User currentUser, String action, RiskEvent target) {
        if (currentUser == null || !currentUserService.hasRole("SECOPS")) {
            return;
        }
        // Canonical role model no longer differentiates secops sub-roles.
    }

    private String appendTrace(String processLog, User operator, String roleCode, Long companyId, String action) {
        String base = String.valueOf(processLog == null ? "" : processLog).trim();
        String trace = String.format("[TRACE operator=%s userId=%s role=%s department=%s position=%s companyId=%s device=%s action=%s at=%s]",
            operator == null || operator.getUsername() == null ? "-" : operator.getUsername(),
            operator == null || operator.getId() == null ? "-" : operator.getId(),
            roleCode == null ? "-" : roleCode,
            operator == null || operator.getDepartment() == null ? "-" : operator.getDepartment(),
            operator == null || operator.getJobTitle() == null ? "-" : operator.getJobTitle(),
            companyId == null ? "-" : companyId,
            operator == null || operator.getDeviceId() == null ? "-" : operator.getDeviceId(),
            action == null ? "-" : action,
            System.currentTimeMillis()
        );
        if (base.isEmpty()) {
            return trace;
        }
        return base + "\n" + trace;
    }

    public static class IdReq { public Long getId(){return id;} public void setId(Long id){this.id=id;} private Long id; }
}
