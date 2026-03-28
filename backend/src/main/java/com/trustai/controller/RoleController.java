package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.entity.Role;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import com.trustai.service.CurrentUserService;
import com.trustai.service.RoleService;
import com.trustai.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/role")
public class RoleController {

    @Autowired
    private RoleService roleService;
    @Autowired
    private CurrentUserService currentUserService;

    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<List<Role>> list(@RequestParam(required = false) String name) {
        currentUserService.requireAdmin();
        User currentUser = currentUserService.requireCurrentUser();
        QueryWrapper<Role> qw = new QueryWrapper<>();
        if (currentUser.getCompanyId() != null) {
            qw.eq("company_id", currentUser.getCompanyId());
        }
        if (name != null && !name.isEmpty()) {
            qw.like("name", name);
        }
        return R.ok(roleService.list(qw));
    }

    @PostMapping("/add")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> add(@RequestBody Role role) {
        User currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin();
        if (currentUser.getCompanyId() == null) {
            throw new BizException(40300, "当前账号未绑定公司，无法新增角色");
        }
        String code = role.getCode() == null ? "" : role.getCode().trim().toUpperCase();
        if (code.isEmpty()) {
            throw new BizException(40000, "角色编码不能为空");
        }
        boolean exists = roleService.lambdaQuery()
            .eq(Role::getCompanyId, currentUser.getCompanyId())
            .eq(Role::getCode, code)
            .count() > 0;
        if (exists) {
            throw new BizException(40000, "当前公司已存在同编码角色");
        }
        role.setCode(code);
        role.setCompanyId(currentUser.getCompanyId());
        role.setCreateTime(new Date());
        role.setUpdateTime(new Date());
        roleService.save(role);
        return R.okMsg("添加成功");
    }

    @PostMapping("/update")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> update(@RequestBody Role role) {
        User currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin();
        Role existing = roleService.getById(role.getId());
        if (existing == null || !java.util.Objects.equals(existing.getCompanyId(), currentUser.getCompanyId())) {
            throw new BizException(40400, "角色不存在或不在当前公司");
        }
        if (role.getCode() != null) {
            String code = role.getCode().trim().toUpperCase();
            boolean exists = roleService.lambdaQuery()
                .eq(Role::getCompanyId, currentUser.getCompanyId())
                .eq(Role::getCode, code)
                .ne(Role::getId, existing.getId())
                .count() > 0;
            if (exists) {
                throw new BizException(40000, "当前公司已存在同编码角色");
            }
            role.setCode(code);
        }
        role.setCompanyId(existing.getCompanyId());
        role.setUpdateTime(new Date());
        roleService.updateById(role);
        return R.okMsg("更新成功");
    }

    @PostMapping("/delete")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> delete(@RequestBody IdReq req) {
        User currentUser = currentUserService.requireCurrentUser();
        currentUserService.requireAdmin();
        Role existing = roleService.getById(req.getId());
        if (existing == null || !java.util.Objects.equals(existing.getCompanyId(), currentUser.getCompanyId())) {
            throw new BizException(40400, "角色不存在或不在当前公司");
        }
        roleService.removeById(req.getId());
        return R.okMsg("删除成功");
    }

    public static class IdReq {
        private Long id;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
    }
}
