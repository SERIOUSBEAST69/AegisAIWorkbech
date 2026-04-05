package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.entity.RiskEvent;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.KeyTaskMetricService;
import com.trustai.service.RiskEventService;
import com.trustai.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/risk-event")
public class RiskEventController {
    @Autowired private RiskEventService riskEventService;
    @Autowired private CurrentUserService currentUserService;
    @Autowired private CompanyScopeService companyScopeService;
    @Autowired private KeyTaskMetricService keyTaskMetricService;

    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasAnyPermission('risk:event:view','risk:event:handle')")
    public R<List<RiskEvent>> list(@RequestParam(required = false) String type) {
        currentUserService.requireAnyPermission("risk:event:view", "risk:event:handle");
        QueryWrapper<RiskEvent> qw = new QueryWrapper<>();
        companyScopeService.withCompany(qw);
        if (type != null && !type.isEmpty()) qw.like("type", type);
        qw.orderByDesc("update_time");
        return R.ok(riskEventService.list(qw));
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
            event.setCompanyId(scoped.getCompanyId());
            event.setHandlerId(currentUser.getId());
            event.setCreateTime(scoped.getCreateTime());
            event.setUpdateTime(new Date());
            event.setProcessLog(appendTrace(event.getProcessLog(), currentUser, roleCode, scoped.getCompanyId(), "update"));
            riskEventService.updateById(event);
            keyTaskMetricService.record("risk.dispose", true);
            return R.okMsg("更新成功");
        } catch (RuntimeException ex) {
            keyTaskMetricService.record("risk.dispose", false);
            throw ex;
        }
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
