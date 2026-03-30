package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trustai.entity.AuditLog;
import com.trustai.entity.Permission;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import com.trustai.service.AuditLogService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.PermissionService;
import com.trustai.service.SensitiveOperationGuardService;
import com.trustai.utils.R;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/permission")
public class PermissionController {

    @Autowired
    private PermissionService permissionService;
    @Autowired
    private CurrentUserService currentUserService;
    @Autowired
    private AuditLogService auditLogService;
    @Autowired
    private SensitiveOperationGuardService sensitiveOperationGuardService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<List<Permission>> list(@RequestParam(required = false) String name) {
        currentUserService.requireAdmin();
        if (!permissionTableReady()) {
            return R.ok(java.util.Collections.emptyList());
        }
        User currentUser = currentUserService.requireCurrentUser();
        QueryWrapper<Permission> qw = new QueryWrapper<>();
        if (currentUser.getCompanyId() != null) {
            qw.eq("company_id", currentUser.getCompanyId());
        }
        if (name != null && !name.isEmpty()) {
            qw.like("name", name);
        }
        qw.orderByDesc("update_time");
        return R.ok(permissionService.list(qw));
    }

    @GetMapping("/page")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<Map<String, Object>> page(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) String name,
                                       @RequestParam(required = false) String code) {
        currentUserService.requireAdmin();
        if (!permissionTableReady()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("current", Math.max(1, page));
            payload.put("pages", 0);
            payload.put("total", 0);
            payload.put("list", java.util.Collections.emptyList());
            return R.ok(payload);
        }
        User currentUser = currentUserService.requireCurrentUser();
        QueryWrapper<Permission> qw = new QueryWrapper<>();
        if (currentUser.getCompanyId() != null) {
            qw.eq("company_id", currentUser.getCompanyId());
        }
        if (StringUtils.hasText(name)) {
            qw.like("name", name);
        }
        if (StringUtils.hasText(code)) {
            qw.like("code", code.trim().toUpperCase());
        }
        qw.orderByDesc("update_time");

        Page<Permission> result = permissionService.page(new Page<>(Math.max(1, page), Math.max(1, pageSize)), qw);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("current", result.getCurrent());
        payload.put("pages", result.getPages());
        payload.put("total", result.getTotal());
        payload.put("list", result.getRecords());
        return R.ok(payload);
    }

    @PostMapping("/add")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> add(@RequestBody Permission permission) {
        currentUserService.requireAdmin();
        assertPermissionTablesReady();
        User currentUser = currentUserService.requireCurrentUser();
        String code = permission.getCode() == null ? "" : permission.getCode().trim().toUpperCase();
        if (!StringUtils.hasText(code)) {
            throw new BizException(40000, "权限编码不能为空");
        }
        boolean exists = permissionService.lambdaQuery()
            .eq(Permission::getCompanyId, currentUser.getCompanyId())
            .eq(Permission::getCode, code)
            .count() > 0;
        if (exists) {
            throw new BizException(40000, "当前公司已存在同编码权限");
        }
        permission.setCode(code);
        permission.setCompanyId(currentUser.getCompanyId());
        permission.setCreateTime(new Date());
        permission.setUpdateTime(new Date());
        permissionService.save(permission);
        writePermissionAudit(currentUser, "permission_add", "permissionId=" + permission.getId() + ", code=" + permission.getCode());
        return R.okMsg("添加成功");
    }

    @PostMapping("/update")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> update(@RequestBody Permission permission) {
        currentUserService.requireAdmin();
        assertPermissionTablesReady();
        User currentUser = currentUserService.requireCurrentUser();
        Permission existing = permissionService.getById(permission.getId());
        if (existing == null || !java.util.Objects.equals(existing.getCompanyId(), currentUser.getCompanyId())) {
            throw new BizException(40400, "权限不存在或不在当前公司");
        }
        if (StringUtils.hasText(permission.getCode())) {
            String code = permission.getCode().trim().toUpperCase();
            boolean exists = permissionService.lambdaQuery()
                .eq(Permission::getCompanyId, currentUser.getCompanyId())
                .eq(Permission::getCode, code)
                .ne(Permission::getId, existing.getId())
                .count() > 0;
            if (exists) {
                throw new BizException(40000, "当前公司已存在同编码权限");
            }
            permission.setCode(code);
        }
        permission.setCompanyId(existing.getCompanyId());
        permission.setUpdateTime(new Date());
        permissionService.updateById(permission);
        writePermissionAudit(currentUser, "permission_update", "permissionId=" + existing.getId());
        return R.okMsg("更新成功");
    }

    @PostMapping("/delete")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> delete(@jakarta.validation.Valid @RequestBody IdReq req) {
        User currentUser = sensitiveOperationGuardService.requireConfirmedAdmin(req.getConfirmPassword(), "permission_delete", "permissionId=" + req.getId());
        currentUserService.requireAdmin();
        assertPermissionTablesReady();
        Permission existing = permissionService.getById(req.getId());
        if (existing == null || !java.util.Objects.equals(existing.getCompanyId(), currentUser.getCompanyId())) {
            throw new BizException(40400, "权限不存在或不在当前公司");
        }
        permissionService.removeById(req.getId());
        writePermissionAudit(currentUser, "permission_delete", "permissionId=" + existing.getId() + ", code=" + existing.getCode());
        return R.okMsg("删除成功");
    }

    private void writePermissionAudit(User operator, String operation, String detail) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(operator.getId());
            log.setOperation(operation);
            log.setOperationTime(new Date());
            log.setInputOverview(detail);
            log.setOutputOverview("permission_manage");
            log.setResult("success");
            log.setRiskLevel("LOW");
            log.setCreateTime(new Date());
            auditLogService.saveAudit(log);
        } catch (Exception ignored) {
            // Non-blocking audit write.
        }
    }

    private void assertPermissionTablesReady() {
        if (!permissionTableReady()) {
            throw new BizException(40000, "权限模块未初始化，请先完成数据库迁移");
        }
    }

    private boolean permissionTableReady() {
        return tableExists("permission") && tableExists("role_permission");
    }

    private boolean tableExists(String tableName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.tables WHERE lower(table_name) = lower(?)",
                Integer.class,
                tableName
            );
            return count != null && count > 0;
        } catch (Exception ex) {
            return false;
        }
    }

    public static class IdReq {
        @NotNull(message = "权限ID不能为空")
        private Long id;
        @NotBlank(message = "敏感操作需要二次密码")
        private String confirmPassword;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    }
}
