package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trustai.entity.SodConflictRule;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.SensitiveOperationGuardService;
import com.trustai.service.SodConflictRuleService;
import com.trustai.utils.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sod-rules")
public class SodConflictRuleController {

    private final SodConflictRuleService sodConflictRuleService;
    private final CompanyScopeService companyScopeService;
    private final CurrentUserService currentUserService;
    private final SensitiveOperationGuardService sensitiveOperationGuardService;

    public SodConflictRuleController(SodConflictRuleService sodConflictRuleService,
                                     CompanyScopeService companyScopeService,
                                     CurrentUserService currentUserService,
                                     SensitiveOperationGuardService sensitiveOperationGuardService) {
        this.sodConflictRuleService = sodConflictRuleService;
        this.companyScopeService = companyScopeService;
        this.currentUserService = currentUserService;
        this.sensitiveOperationGuardService = sensitiveOperationGuardService;
    }

    @GetMapping("/page")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS')")
    public R<Map<String, Object>> page(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) String scenario,
                                       @RequestParam(required = false) Integer enabled) {
        Long companyId = companyScopeService.requireCompanyId();
        QueryWrapper<SodConflictRule> qw = new QueryWrapper<SodConflictRule>().eq("company_id", companyId);
        if (StringUtils.hasText(scenario)) {
            qw.like("scenario", normalize(scenario));
        }
        if (enabled != null) {
            qw.eq("enabled", enabled > 0 ? 1 : 0);
        }
        qw.orderByDesc("update_time");
        Page<SodConflictRule> result = sodConflictRuleService.page(new Page<>(Math.max(1, page), Math.max(1, pageSize)), qw);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("current", result.getCurrent());
        payload.put("pages", result.getPages());
        payload.put("total", result.getTotal());
        payload.put("list", result.getRecords());
        return R.ok(payload);
    }

    @PostMapping("/save")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> save(@Valid @RequestBody SaveReq req) {
        currentUserService.requireAdmin();
        Long companyId = companyScopeService.requireCompanyId();

        SodConflictRule entity;
        if (req.getId() == null) {
            entity = new SodConflictRule();
            entity.setCompanyId(companyId);
            entity.setCreateTime(new Date());
        } else {
            entity = sodConflictRuleService.getById(req.getId());
            if (entity == null || !java.util.Objects.equals(entity.getCompanyId(), companyId)) {
                throw new BizException(40400, "SoD规则不存在或不在当前公司");
            }
        }

        entity.setScenario(normalize(req.getScenario()));
        entity.setRoleCodeA(normalize(req.getRoleCodeA()));
        entity.setRoleCodeB(normalize(req.getRoleCodeB()));
        entity.setEnabled(req.getEnabled() == null || req.getEnabled() > 0 ? 1 : 0);
        entity.setDescription(StringUtils.hasText(req.getDescription()) ? req.getDescription().trim() : "");
        entity.setUpdateTime(new Date());
        sodConflictRuleService.saveOrUpdate(entity);
        return R.ok(entity);
    }

    @PostMapping("/delete")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> delete(@Valid @RequestBody DeleteReq req) {
        User operator = sensitiveOperationGuardService.requireConfirmedAdmin(req.getConfirmPassword(), "sod_rule_delete", "ruleId=" + req.getId());
        SodConflictRule existing = sodConflictRuleService.getById(req.getId());
        if (existing == null || !java.util.Objects.equals(existing.getCompanyId(), operator.getCompanyId())) {
            throw new BizException(40400, "SoD规则不存在或不在当前公司");
        }
        sodConflictRuleService.removeById(existing.getId());
        return R.okMsg("删除成功");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    public static class SaveReq {
        private Long id;
        @NotBlank(message = "场景不能为空")
        private String scenario;
        @NotBlank(message = "角色A不能为空")
        private String roleCodeA;
        @NotBlank(message = "角色B不能为空")
        private String roleCodeB;
        private Integer enabled;
        private String description;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getScenario() { return scenario; }
        public void setScenario(String scenario) { this.scenario = scenario; }
        public String getRoleCodeA() { return roleCodeA; }
        public void setRoleCodeA(String roleCodeA) { this.roleCodeA = roleCodeA; }
        public String getRoleCodeB() { return roleCodeB; }
        public void setRoleCodeB(String roleCodeB) { this.roleCodeB = roleCodeB; }
        public Integer getEnabled() { return enabled; }
        public void setEnabled(Integer enabled) { this.enabled = enabled; }
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
    }

    public static class DeleteReq {
        @NotNull(message = "规则ID不能为空")
        private Long id;
        @NotBlank(message = "敏感操作需要二次密码")
        private String confirmPassword;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    }
}