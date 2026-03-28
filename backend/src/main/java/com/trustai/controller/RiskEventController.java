package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.entity.RiskEvent;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CurrentUserService;
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

    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS')")
    public R<List<RiskEvent>> list(@RequestParam(required = false) String type) {
        QueryWrapper<RiskEvent> qw = new QueryWrapper<>();
        companyScopeService.withCompany(qw);
        if (type != null && !type.isEmpty()) qw.like("type", type);
        return R.ok(riskEventService.list(qw));
    }

    @PostMapping("/add")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS')")
    public R<?> add(@RequestBody RiskEvent event) {
        currentUserService.requireAnyRole("ADMIN", "SECOPS");
        User currentUser = currentUserService.requireCurrentUser();
        event.setId(null);
        event.setCompanyId(companyScopeService.requireCompanyId());
        event.setHandlerId(currentUser.getId());
        event.setCreateTime(new Date());
        event.setUpdateTime(new Date());
        riskEventService.save(event);
        return R.okMsg("添加成功");
    }

    @PostMapping("/update")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS')")
    public R<?> update(@RequestBody RiskEvent event) {
        currentUserService.requireAnyRole("ADMIN", "SECOPS");
        RiskEvent scoped = riskEventService.getOne(companyScopeService.withCompany(new QueryWrapper<RiskEvent>()).eq("id", event.getId()));
        if (scoped == null) {
            throw new BizException(40400, "风险事件不存在或不在当前公司");
        }
        User currentUser = currentUserService.requireCurrentUser();
        event.setCompanyId(scoped.getCompanyId());
        event.setHandlerId(currentUser.getId());
        event.setUpdateTime(new Date());
        riskEventService.updateById(event);
        return R.okMsg("更新成功");
    }

    @PostMapping("/delete")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS')")
    public R<?> delete(@RequestBody IdReq req) {
        currentUserService.requireAnyRole("ADMIN", "SECOPS");
        RiskEvent scoped = riskEventService.getOne(companyScopeService.withCompany(new QueryWrapper<RiskEvent>()).eq("id", req.getId()));
        if (scoped == null) {
            throw new BizException(40400, "风险事件不存在或不在当前公司");
        }
        riskEventService.removeById(req.getId());
        return R.okMsg("删除成功");
    }

    public static class IdReq { public Long getId(){return id;} public void setId(Long id){this.id=id;} private Long id; }
}
