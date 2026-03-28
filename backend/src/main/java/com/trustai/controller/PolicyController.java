package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.entity.CompliancePolicy;
import com.trustai.exception.BizException;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CompliancePolicyService;
import com.trustai.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/policy")
public class PolicyController {
    @Autowired private CompliancePolicyService compliancePolicyService;
    @Autowired private CompanyScopeService companyScopeService;

    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS','DATA_ADMIN','AI_BUILDER')")
    public R<List<CompliancePolicy>> list(@RequestParam(required = false) String name) {
        QueryWrapper<CompliancePolicy> qw = companyScopeService.withCompany(new QueryWrapper<>());
        if (name != null && !name.isEmpty()) qw.like("name", name);
        return R.ok(compliancePolicyService.list(qw));
    }

    @PostMapping("/save")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> save(@RequestBody CompliancePolicy policy) {
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
    public R<?> delete(@RequestBody IdReq req) {
        CompliancePolicy existing = compliancePolicyService.getOne(
            companyScopeService.withCompany(new QueryWrapper<CompliancePolicy>()).eq("id", req.getId())
        );
        if (existing == null) {
            throw new BizException(40400, "策略不存在或不在当前公司");
        }
        compliancePolicyService.removeById(req.getId());
        return R.okMsg("删除成功");
    }

    public static class IdReq { public Long getId(){return id;} public void setId(Long id){this.id=id;} private Long id; }
}
