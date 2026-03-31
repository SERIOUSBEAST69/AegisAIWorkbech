package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
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
import java.util.List;

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
        return R.ok(compliancePolicyService.list(qw));
    }

    @PostMapping("/save")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> save(@Valid @RequestBody SaveReq req) {
        currentUserService.requireAdmin();
        assertPolicyTableReady();
        var operator = currentUserService.requireCurrentUser();
        sensitiveOperationGuardService.requireConfirmedOperator(operator, req.getConfirmPassword(), "policy_save", "policyId=" + req.getId());

        CompliancePolicy policy = new CompliancePolicy();
        policy.setId(req.getId());
        policy.setName(req.getName());
        policy.setRuleContent(req.getRuleContent());
        policy.setScope(req.getScope());
        policy.setStatus(normalizeStatus(req.getStatus()));

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
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> delete(@Valid @RequestBody IdReq req) {
        currentUserService.requireAdmin();
        assertPolicyTableReady();
        var operator = currentUserService.requireCurrentUser();
        sensitiveOperationGuardService.requireConfirmedOperator(operator, req.getConfirmPassword(), "policy_delete", "policyId=" + req.getId());
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
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> toggleStatus(@Valid @RequestBody ToggleReq req) {
        currentUserService.requireAdmin();
        assertPolicyTableReady();
        var operator = currentUserService.requireCurrentUser();
        sensitiveOperationGuardService.requireConfirmedOperator(operator, req.getConfirmPassword(), "policy_toggle_status", "policyId=" + req.getId());
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
        String normalized = status == null ? "" : status.trim().toUpperCase(Locale.ROOT);
        if ("0".equals(normalized) || "INACTIVE".equals(normalized) || "DISABLED".equals(normalized)) {
            return 0;
        }
        return 1;
    }

    public static class SaveReq {
        private Long id;
        @NotBlank(message = "策略名称不能为空")
        private String name;
        @NotBlank(message = "规则内容不能为空")
        private String ruleContent;
        @NotBlank(message = "生效范围不能为空")
        private String scope;
        private String status;
        @NotBlank(message = "敏感操作需要二次密码")
        private String confirmPassword;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getRuleContent() { return ruleContent; }
        public void setRuleContent(String ruleContent) { this.ruleContent = ruleContent; }
        public String getScope() { return scope; }
        public void setScope(String scope) { this.scope = scope; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    }

    public static class IdReq {
        @NotNull(message = "策略ID不能为空")
        private Long id;
        @NotBlank(message = "敏感操作需要二次密码")
        private String confirmPassword;
        public Long getId(){return id;}
        public void setId(Long id){this.id=id;}
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    }

    public static class ToggleReq {
        @NotNull(message = "策略ID不能为空")
        private Long id;
        @NotNull(message = "启停状态不能为空")
        private Boolean enabled;
        @NotBlank(message = "敏感操作需要二次密码")
        private String confirmPassword;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
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
