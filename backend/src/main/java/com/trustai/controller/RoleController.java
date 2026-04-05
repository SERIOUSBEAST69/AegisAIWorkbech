package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trustai.entity.AuditLog;
import com.trustai.entity.Role;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import com.trustai.service.AuditLogService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.RoleService;
import com.trustai.service.SensitiveOperationGuardService;
import com.trustai.service.UserService;
import com.trustai.utils.R;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/role")
public class RoleController {

    @Autowired
    private RoleService roleService;
    @Autowired
    private CurrentUserService currentUserService;
    @Autowired
    private UserService userService;
    @Autowired
    private AuditLogService auditLogService;
    @Autowired
    private SensitiveOperationGuardService sensitiveOperationGuardService;

    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER') || @currentUserService.hasPermission('role:manage')")
    public R<List<Role>> list(@RequestParam(required = false) String name) {
        ensureRoleReadAccess();
        User currentUser = currentUserService.requireCurrentUser();
        QueryWrapper<Role> qw = new QueryWrapper<>();
        if (currentUser.getCompanyId() != null) {
            qw.eq("company_id", currentUser.getCompanyId());
        }
        if (name != null && !name.isEmpty()) {
            qw.like("name", name);
        }
        qw.orderByDesc("update_time");
        return R.ok(roleService.list(qw));
    }

    @GetMapping("/page")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER') || @currentUserService.hasPermission('role:manage')")
    public R<Map<String, Object>> page(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) String name,
                                       @RequestParam(required = false) String code) {
        ensureRoleReadAccess();
        User currentUser = currentUserService.requireCurrentUser();
        QueryWrapper<Role> qw = new QueryWrapper<>();
        if (currentUser.getCompanyId() != null) {
            qw.eq("company_id", currentUser.getCompanyId());
        }
        if (name != null && !name.isEmpty()) {
            qw.like("name", name);
        }
        if (code != null && !code.isEmpty()) {
            qw.like("code", code.trim().toUpperCase());
        }
        qw.orderByDesc("update_time");

        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        Page<Role> result = roleService.page(new Page<>(safePage, safePageSize), qw);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("current", result.getCurrent());
        payload.put("pages", result.getPages());
        payload.put("total", result.getTotal());
        payload.put("list", result.getRecords());
        return R.ok(payload);
    }

    @PostMapping("/add")
    @PreAuthorize("@currentUserService.hasPermission('role:manage')")
    public R<?> add(@RequestBody Role role) {
        User currentUser = currentUserService.requireCurrentUser();
        currentUserService.requirePermission("role:manage");
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
        writeRoleAudit(currentUser, "role_add", "roleId=" + role.getId() + ", roleCode=" + role.getCode());
        return R.okMsg("添加成功");
    }

    @PostMapping("/update")
    @PreAuthorize("@currentUserService.hasPermission('role:manage')")
    public R<?> update(@RequestBody Role role) {
        User currentUser = currentUserService.requireCurrentUser();
        currentUserService.requirePermission("role:manage");
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
        writeRoleAudit(currentUser, "role_update", "roleId=" + existing.getId() + ", roleCode=" + (role.getCode() == null ? existing.getCode() : role.getCode()));
        return R.okMsg("更新成功");
    }

    @PostMapping("/delete")
    @PreAuthorize("@currentUserService.hasPermission('role:manage')")
    public R<?> delete(@jakarta.validation.Valid @RequestBody IdReq req) {
        currentUserService.requireAdmin();
        User currentUser = currentUserService.requireCurrentUser();
        sensitiveOperationGuardService.requireDualReviewedOperator(
            currentUser,
            req.getConfirmPassword(),
            req.getReviewerUsername(),
            req.getReviewerPassword(),
            "role_delete",
            "roleId=" + req.getId()
        );
        currentUserService.requireAdmin();
        Role existing = roleService.getById(req.getId());
        if (existing == null || !java.util.Objects.equals(existing.getCompanyId(), currentUser.getCompanyId())) {
            throw new BizException(40400, "角色不存在或不在当前公司");
        }
        if ("ADMIN".equalsIgnoreCase(existing.getCode())) {
            throw new BizException(40000, "默认治理管理员角色不允许删除");
        }
        long bindCount = userService.lambdaQuery().eq(User::getRoleId, req.getId()).count();
        if (bindCount > 0) {
            throw new BizException(40000, "当前角色仍被用户绑定，无法删除");
        }
        roleService.removeById(req.getId());
        writeRoleAudit(currentUser, "role_delete", "roleId=" + existing.getId() + ", roleCode=" + existing.getCode());
        return R.okMsg("删除成功");
    }

    private void writeRoleAudit(User operator, String operation, String detail) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(operator.getId());
            log.setOperation(operation);
            log.setOperationTime(new Date());
            log.setInputOverview(detail);
            log.setOutputOverview("role_manage");
            log.setResult("success");
            log.setRiskLevel("LOW");
            log.setCreateTime(new Date());
            auditLogService.saveAudit(log);
        } catch (Exception ignored) {
            // Non-blocking audit write.
        }
    }

    private void ensureRoleReadAccess() {
        if (currentUserService.hasPermission("role:manage") || currentUserService.hasAnyRole("ADMIN", "ADMIN_REVIEWER")) {
            return;
        }
        throw new BizException(40300, "无权限");
    }

    public static class IdReq {
        @NotNull(message = "角色ID不能为空")
        private Long id;
        @NotBlank(message = "敏感操作需要二次密码")
        private String confirmPassword;
        @NotBlank(message = "敏感操作需要第二复核人账号")
        private String reviewerUsername;
        @NotBlank(message = "敏感操作需要第二复核人密码")
        private String reviewerPassword;
        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
        public String getReviewerUsername() { return reviewerUsername; }
        public void setReviewerUsername(String reviewerUsername) { this.reviewerUsername = reviewerUsername; }
        public String getReviewerPassword() { return reviewerPassword; }
        public void setReviewerPassword(String reviewerPassword) { this.reviewerPassword = reviewerPassword; }
    }
}
