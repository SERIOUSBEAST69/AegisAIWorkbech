package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.entity.Permission;
import com.trustai.service.CurrentUserService;
import com.trustai.service.PermissionService;
import com.trustai.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Date;
import java.util.List;

@RestController
@RequestMapping("/api/permission")
public class PermissionController {

    @Autowired
    private PermissionService permissionService;
    @Autowired
    private CurrentUserService currentUserService;

    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<List<Permission>> list(@RequestParam(required = false) String name) {
        QueryWrapper<Permission> qw = new QueryWrapper<>();
        if (name != null && !name.isEmpty()) {
            qw.like("name", name);
        }
        return R.ok(permissionService.list(qw));
    }

    @PostMapping("/add")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> add(@RequestBody Permission permission) {
        currentUserService.requireAdmin();
        permission.setCreateTime(new Date());
        permission.setUpdateTime(new Date());
        permissionService.save(permission);
        return R.okMsg("添加成功");
    }

    @PostMapping("/update")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> update(@RequestBody Permission permission) {
        currentUserService.requireAdmin();
        permission.setUpdateTime(new Date());
        permissionService.updateById(permission);
        return R.okMsg("更新成功");
    }

    @PostMapping("/delete")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> delete(@RequestBody IdReq req) {
        currentUserService.requireAdmin();
        permissionService.removeById(req.getId());
        return R.okMsg("删除成功");
    }

    public static class IdReq {
        private Long id;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
    }
}
