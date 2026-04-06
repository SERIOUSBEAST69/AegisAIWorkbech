package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trustai.entity.CompliancePolicy;
import com.trustai.exception.BizException;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CompliancePolicyService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.SensitiveOperationGuardService;
import com.trustai.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Locale;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/policy")
public class PolicyController {
    @Autowired private CompliancePolicyService compliancePolicyService;
    @Autowired private CompanyScopeService companyScopeService;
    @Autowired private CurrentUserService currentUserService;
    @Autowired private SensitiveOperationGuardService sensitiveOperationGuardService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasPermission('policy:view')")
    public R<List<CompliancePolicy>> list(@RequestParam(required = false) String name) {
        assertPolicyTableReady();
        QueryWrapper<CompliancePolicy> qw = companyScopeService.withCompany(new QueryWrapper<>());
        if (name != null && !name.isEmpty()) qw.like("name", name);
        qw.orderByAsc("priority").orderByDesc("update_time");
        return R.ok(compliancePolicyService.list(qw));
    }

    @GetMapping("/page")
    @PreAuthorize("@currentUserService.hasPermission('policy:view')")
    public R<Map<String, Object>> page(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) String name,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String policyType,
                                       @RequestParam(required = false) String scope) {
        assertPolicyTableReady();
        QueryWrapper<CompliancePolicy> qw = companyScopeService.withCompany(new QueryWrapper<>());
        if (name != null && !name.trim().isEmpty()) {
            qw.like("name", name.trim());
        }
        Integer normalizedStatus = normalizeStatusCode(status);
        if (normalizedStatus != null) {
            qw.eq("status", normalizedStatus);
        }
        if (policyType != null && !policyType.trim().isEmpty()) {
            qw.eq("policy_type", policyType.trim().toUpperCase(Locale.ROOT));
        }
        if (scope != null && !scope.trim().isEmpty()) {
            qw.eq("scope", scope.trim());
        }
        qw.orderByAsc("priority").orderByDesc("update_time");

        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        Page<CompliancePolicy> result = compliancePolicyService.page(new Page<>(safePage, safePageSize), qw);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("current", result.getCurrent());
        payload.put("pages", result.getPages());
        payload.put("total", result.getTotal());
        payload.put("list", result.getRecords());
        return R.ok(payload);
    }

    @PostMapping("/save")
    @PreAuthorize("@currentUserService.hasPermission('policy:structure:manage')")
    public R<?> save(@Valid @RequestBody SaveReq req) {
        currentUserService.requirePermission("policy:structure:manage");
        assertPolicyTableReady();
        var operator = currentUserService.requireCurrentUser();
        sensitiveOperationGuardService.requireDualReviewedOperator(
            operator,
            req.getConfirmPassword(),
            req.getReviewerUsername(),
            req.getReviewerPassword(),
            "policy_save",
            "policyId=" + req.getId()
        );

        CompliancePolicy policy = new CompliancePolicy();
        policy.setId(req.getId());
        policy.setName(req.getName());
        policy.setPolicyType(normalizePolicyType(req.getPolicyType()));
        policy.setPriority(normalizePriority(req.getPriority()));
        policy.setRuleContent(req.getRuleContent());
        policy.setScope(req.getScope());
        policy.setScopeDepartments(req.getScopeDepartments());
        policy.setScopeUserGroups(req.getScopeUserGroups());
        policy.setScopeDataTypes(req.getScopeDataTypes());
        policy.setStatus(normalizeStatus(req.getStatus()));
        policy.setLastModifier(resolveOperatorName(operator));
        policy.setLastModifiedAt(new Date());

        Long companyId = companyScopeService.requireCompanyId();
        policy.setUpdateTime(new Date());
        if (policy.getId() == null) {
            policy.setCompanyId(companyId);
            policy.setCreateTime(new Date());
            compliancePolicyService.save(policy);
        } else {
            CompliancePolicy existing = compliancePolicyService.getOne(
                companyScopeService.withCompany(new QueryWrapper<CompliancePolicy>()).eq("id", policy.getId())
            );
            if (existing == null) {
                throw new BizException(40400, "策略不存在或不在当前公司");
            }
            policy.setCompanyId(existing.getCompanyId());
            compliancePolicyService.updateById(policy);
        }
        return R.okMsg("保存成功");
    }

    @PostMapping("/delete")
    @PreAuthorize("@currentUserService.hasPermission('policy:structure:manage')")
    public R<?> delete(@Valid @RequestBody IdReq req) {
        currentUserService.requirePermission("policy:structure:manage");
        assertPolicyTableReady();
        var operator = currentUserService.requireCurrentUser();
        sensitiveOperationGuardService.requireDualReviewedOperator(
            operator,
            req.getConfirmPassword(),
            req.getReviewerUsername(),
            req.getReviewerPassword(),
            "policy_delete",
            "policyId=" + req.getId()
        );
        CompliancePolicy existing = compliancePolicyService.getOne(
            companyScopeService.withCompany(new QueryWrapper<CompliancePolicy>()).eq("id", req.getId())
        );
        if (existing == null) {
            throw new BizException(40400, "策略不存在或不在当前公司");
        }
        compliancePolicyService.removeById(req.getId());
        return R.okMsg("删除成功");
    }

    @PostMapping("/toggle-status")
    @PreAuthorize("@currentUserService.hasPermission('policy:status:toggle')")
    public R<?> toggleStatus(@Valid @RequestBody ToggleReq req) {
        currentUserService.requirePermission("policy:status:toggle");
        assertPolicyTableReady();
        var operator = currentUserService.requireCurrentUser();
        sensitiveOperationGuardService.requireDualReviewedOperator(
            operator,
            req.getConfirmPassword(),
            req.getReviewerUsername(),
            req.getReviewerPassword(),
            "policy_toggle_status",
            "policyId=" + req.getId()
        );
        CompliancePolicy existing = compliancePolicyService.getOne(
            companyScopeService.withCompany(new QueryWrapper<CompliancePolicy>()).eq("id", req.getId())
        );
        if (existing == null) {
            throw new BizException(40400, "策略不存在或不在当前公司");
        }
        existing.setStatus(req.getEnabled() != null && req.getEnabled() ? 1 : 0);
        existing.setUpdateTime(new Date());
        compliancePolicyService.updateById(existing);
        return R.okMsg("状态更新成功");
    }

    private Integer normalizeStatus(String status) {
        Integer normalized = normalizeStatusCode(status);
        if (normalized != null) {
            return normalized;
        }
        return 1;
    }

    private Integer normalizeStatusCode(String status) {
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return null;
        }
        if ("0".equals(normalized) || "INACTIVE".equals(normalized) || "DISABLED".equals(normalized)) {
            return 0;
        }
        if ("2".equals(normalized) || "DRAFT".equals(normalized)) {
            return 2;
        }
        return 1;
    }

    private String normalizePolicyType(String policyType) {
        String normalized = policyType == null ? "" : policyType.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "MASKING";
        }
        if ("ACCESS_CONTROL".equals(normalized)
            || "EXPORT_LIMIT".equals(normalized)
            || "AUDIT_GOVERNANCE".equals(normalized)
            || "ALERT_GOVERNANCE".equals(normalized)
            || "MASKING".equals(normalized)) {
            return normalized;
        }
        return "MASKING";
    }

    private Integer normalizePriority(Integer priority) {
        int value = priority == null ? 50 : priority;
        value = Math.max(1, value);
        value = Math.min(999, value);
        return value;
    }

    private String resolveOperatorName(com.trustai.entity.User operator) {
        if (operator == null) {
            return "system";
        }
        String username = operator.getUsername();
        return username == null || username.trim().isEmpty() ? "system" : username.trim();
    }

    public static class SaveReq {
        private Long id;
        @NotBlank(message = "策略名称不能为空")
        private String name;
        @NotBlank(message = "规则内容不能为空")
        private String ruleContent;
        @NotBlank(message = "生效范围不能为空")
        private String scope;
        private String policyType;
        private Integer priority;
        private String scopeDepartments;
        private String scopeUserGroups;
        private String scopeDataTypes;
        private String status;
        @NotBlank(message = "敏感操作需要二次密码")
        private String confirmPassword;
        @NotBlank(message = "敏感操作需要第二复核人账号")
        private String reviewerUsername;
        @NotBlank(message = "敏感操作需要第二复核人密码")
        private String reviewerPassword;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getRuleContent() { return ruleContent; }
        public void setRuleContent(String ruleContent) { this.ruleContent = ruleContent; }
        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
        public String getPolicyType() { return policyType; }
        public void setPolicyType(String policyType) { this.policyType = policyType; }
        public Integer getPriority() { return priority; }
        public void setPriority(Integer priority) { this.priority = priority; }
        public String getScopeDepartments() { return scopeDepartments; }
        public void setScopeDepartments(String scopeDepartments) { this.scopeDepartments = scopeDepartments; }
        public String getScopeUserGroups() { return scopeUserGroups; }
        public void setScopeUserGroups(String scopeUserGroups) { this.scopeUserGroups = scopeUserGroups; }
        public String getScopeDataTypes() { return scopeDataTypes; }
        public void setScopeDataTypes(String scopeDataTypes) { this.scopeDataTypes = scopeDataTypes; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
        public String getReviewerUsername() { return reviewerUsername; }
        public void setReviewerUsername(String reviewerUsername) { this.reviewerUsername = reviewerUsername; }
        public String getReviewerPassword() { return reviewerPassword; }
        public void setReviewerPassword(String reviewerPassword) { this.reviewerPassword = reviewerPassword; }
    }

    public static class IdReq {
        @NotNull(message = "策略ID不能为空")
        private Long id;
        @NotBlank(message = "敏感操作需要二次密码")
        private String confirmPassword;
        @NotBlank(message = "敏感操作需要第二复核人账号")
        private String reviewerUsername;
        @NotBlank(message = "敏感操作需要第二复核人密码")
        private String reviewerPassword;
        public Long getId(){return id;}
        public void setId(Long id){this.id=id;}
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
        public String getReviewerUsername() { return reviewerUsername; }
        public void setReviewerUsername(String reviewerUsername) { this.reviewerUsername = reviewerUsername; }
        public String getReviewerPassword() { return reviewerPassword; }
        public void setReviewerPassword(String reviewerPassword) { this.reviewerPassword = reviewerPassword; }
    }

    public static class ToggleReq {
        @NotNull(message = "策略ID不能为空")
        private Long id;
        @NotNull(message = "启停状态不能为空")
        private Boolean enabled;
        @NotBlank(message = "敏感操作需要二次密码")
        private String confirmPassword;
        @NotBlank(message = "敏感操作需要第二复核人账号")
        private String reviewerUsername;
        @NotBlank(message = "敏感操作需要第二复核人密码")
        private String reviewerPassword;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
        public String getReviewerUsername() { return reviewerUsername; }
        public void setReviewerUsername(String reviewerUsername) { this.reviewerUsername = reviewerUsername; }
        public String getReviewerPassword() { return reviewerPassword; }
        public void setReviewerPassword(String reviewerPassword) { this.reviewerPassword = reviewerPassword; }
    }

    private void assertPolicyTableReady() {
        if (!policyTableReady()) {
            throw new BizException(40000, "策略模块未初始化，请先完成数据库迁移");
        }
    }

    private boolean policyTableReady() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.tables WHERE lower(table_name) = 'compliance_policy'",
                Integer.class
            );
            return count != null && count > 0;
        } catch (Exception ex) {
            return false;
        }
    }
}
