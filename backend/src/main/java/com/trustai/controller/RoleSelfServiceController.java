package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trustai.entity.Permission;
import com.trustai.entity.Role;
import com.trustai.entity.RolePermission;
import com.trustai.entity.User;
import com.trustai.entity.UserRole;
import com.trustai.exception.BizException;
import com.trustai.service.CurrentUserService;
import com.trustai.service.PermissionService;
import com.trustai.service.RolePermissionService;
import com.trustai.service.RoleService;
import com.trustai.service.UserRoleService;
import com.trustai.service.UserService;
import com.trustai.utils.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Data;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class RoleSelfServiceController {

    private final RoleService roleService;
    private final PermissionService permissionService;
    private final RolePermissionService rolePermissionService;
    private final CurrentUserService currentUserService;
    private final UserService userService;
    private final UserRoleService userRoleService;
    private final JdbcTemplate jdbcTemplate;

    private static final Set<String> HIGH_RISK_SELF_REGISTER_CODES = Set.of("ADMIN", "EXECUTIVE", "SECOPS", "DATA_ADMIN");

    public RoleSelfServiceController(RoleService roleService,
                                     PermissionService permissionService,
                                     RolePermissionService rolePermissionService,
                                     CurrentUserService currentUserService,
                                     UserService userService,
                                     UserRoleService userRoleService,
                                     JdbcTemplate jdbcTemplate) {
        this.roleService = roleService;
        this.permissionService = permissionService;
        this.rolePermissionService = rolePermissionService;
        this.currentUserService = currentUserService;
        this.userService = userService;
        this.userRoleService = userRoleService;
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/roles")
    public R<Map<String, Object>> pageRoles(@RequestParam(defaultValue = "1") int page,
                                            @RequestParam(defaultValue = "10") int pageSize,
                                            @RequestParam(required = false) String keyword) {
        User currentUser = requireRoleManageAccess();
        QueryWrapper<Role> wrapper = new QueryWrapper<Role>()
            .eq("company_id", currentUser.getCompanyId())
            .orderByDesc("is_system")
            .orderByDesc("update_time");
        if (StringUtils.hasText(keyword)) {
            wrapper.and(w -> w.like("name", keyword).or().like("code", keyword));
        }

        Page<Role> result = roleService.page(new Page<>(Math.max(page, 1), Math.max(pageSize, 1)), wrapper);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("current", result.getCurrent());
        payload.put("pages", result.getPages());
        payload.put("total", result.getTotal());
        payload.put("list", result.getRecords());
        return R.ok(payload);
    }

    @PostMapping("/roles")
    public R<?> createRole(@Valid @RequestBody RoleUpsertReq req) {
        User currentUser = requireRoleManageAccess();
        String code = normalizeRoleCode(req.getCode());
        ensureRoleCodeUnique(currentUser.getCompanyId(), code, null);
        boolean requestedSelfRegister = Boolean.TRUE.equals(req.getAllowSelfRegister());
        boolean needsApproval = requestedSelfRegister && requiresSelfRegisterApproval(code);

        Role role = new Role();
        role.setCompanyId(currentUser.getCompanyId());
        role.setName(req.getName().trim());
        role.setCode(code);
        role.setDescription(req.getDescription());
        role.setAllowSelfRegister(requestedSelfRegister && !needsApproval);
        role.setIsSystem(false);
        role.setCreateTime(new Date());
        role.setUpdateTime(new Date());
        roleService.save(role);

        replaceRolePermissions(currentUser.getCompanyId(), role.getId(), req.getPermissionCodes());
        if (needsApproval) {
            Long requestId = createSelfRegisterApprovalRequest(currentUser, role, true, req.getReviewNote());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("role", role);
            payload.put("pendingApproval", true);
            payload.put("requestId", requestId);
            payload.put("message", "高风险角色开放注册已提交审批，审批通过后生效");
            return R.ok(payload);
        }
        return R.ok(role);
    }

    @PutMapping("/roles/{id}")
    public R<?> updateRole(@PathVariable Long id, @Valid @RequestBody RoleUpsertReq req) {
        User currentUser = requireRoleManageAccess();
        Role role = requireCompanyRole(id, currentUser.getCompanyId());

        String code = normalizeRoleCode(req.getCode());
        ensureRoleCodeUnique(currentUser.getCompanyId(), code, role.getId());

        role.setName(req.getName().trim());
        role.setCode(code);
        role.setDescription(req.getDescription());
        boolean requestedSelfRegister = Boolean.TRUE.equals(req.getAllowSelfRegister());
        boolean needsApproval = requestedSelfRegister && requiresSelfRegisterApproval(code) && !Boolean.TRUE.equals(role.getAllowSelfRegister());
        role.setAllowSelfRegister(requestedSelfRegister && !needsApproval);
        role.setUpdateTime(new Date());
        roleService.updateById(role);

        replaceRolePermissions(currentUser.getCompanyId(), role.getId(), req.getPermissionCodes());
        if (needsApproval) {
            Long requestId = createSelfRegisterApprovalRequest(currentUser, role, true, req.getReviewNote());
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("role", role);
            payload.put("pendingApproval", true);
            payload.put("requestId", requestId);
            payload.put("message", "高风险角色开放注册已提交审批，审批通过后生效");
            return R.ok(payload);
        }
        return R.ok(role);
    }

    @GetMapping("/roles/self-register-requests")
    public R<?> listSelfRegisterRequests(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String status) {
        User currentUser = requireRoleManageAccess();
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, pageSize));
        int offset = (safePage - 1) * safeSize;
        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toLowerCase(Locale.ROOT) : null;
        boolean hasStatus = StringUtils.hasText(normalizedStatus);

        Integer total = jdbcTemplate.queryForObject(
            hasStatus
                ? "SELECT COUNT(1) FROM role_self_register_change WHERE company_id = ? AND status = ?"
                : "SELECT COUNT(1) FROM role_self_register_change WHERE company_id = ?",
            Integer.class,
            hasStatus ? new Object[] { currentUser.getCompanyId(), normalizedStatus } : new Object[] { currentUser.getCompanyId() }
        );

        List<Map<String, Object>> list = jdbcTemplate.query(
            hasStatus
                ? "SELECT id, role_id, role_code, requested_allow_self_register, status, requested_by, reviewed_by, review_note, create_time, update_time FROM role_self_register_change WHERE company_id = ? AND status = ? ORDER BY id DESC LIMIT ? OFFSET ?"
                : "SELECT id, role_id, role_code, requested_allow_self_register, status, requested_by, reviewed_by, review_note, create_time, update_time FROM role_self_register_change WHERE company_id = ? ORDER BY id DESC LIMIT ? OFFSET ?",
            ps -> {
                int idx = 1;
                ps.setLong(idx++, currentUser.getCompanyId());
                if (hasStatus) {
                    ps.setString(idx++, normalizedStatus);
                }
                ps.setInt(idx++, safeSize);
                ps.setInt(idx, offset);
            },
            (rs, rowNum) -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", rs.getLong("id"));
                row.put("roleId", rs.getLong("role_id"));
                row.put("roleCode", rs.getString("role_code"));
                row.put("requestedAllowSelfRegister", rs.getBoolean("requested_allow_self_register"));
                row.put("status", rs.getString("status"));
                row.put("requestedBy", rs.getObject("requested_by"));
                row.put("reviewedBy", rs.getObject("reviewed_by"));
                row.put("reviewNote", rs.getString("review_note"));
                row.put("createTime", rs.getTimestamp("create_time"));
                row.put("updateTime", rs.getTimestamp("update_time"));
                return row;
            }
        );

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("current", safePage);
        payload.put("pageSize", safeSize);
        payload.put("total", total == null ? 0 : total);
        payload.put("list", list);
        return R.ok(payload);
    }

    @PutMapping("/roles/self-register-requests/{id}/approve")
    public R<?> approveSelfRegisterRequest(@PathVariable Long id, @RequestBody(required = false) ReviewReq req) {
        User reviewer = requireRoleManageAccess();
        Map<String, Object> request = requirePendingSelfRegisterRequest(id, reviewer.getCompanyId());
        Long requestedBy = toLong(request.get("requestedBy"));
        if (requestedBy != null && requestedBy.equals(reviewer.getId())) {
            throw new BizException(40000, "申请人与审批人不能是同一人");
        }
        Long roleId = toLong(request.get("roleId"));
        Boolean requestedAllow = (Boolean) request.get("requestedAllowSelfRegister");
        if (roleId == null || requestedAllow == null) {
            throw new BizException(40000, "审批请求数据异常");
        }

        Role role = requireCompanyRole(roleId, reviewer.getCompanyId());
        role.setAllowSelfRegister(requestedAllow);
        role.setUpdateTime(new Date());
        roleService.updateById(role);

        jdbcTemplate.update(
            "UPDATE role_self_register_change SET status = 'approved', reviewed_by = ?, review_note = ?, update_time = CURRENT_TIMESTAMP WHERE id = ? AND company_id = ?",
            reviewer.getId(),
            req == null ? null : req.getReviewNote(),
            id,
            reviewer.getCompanyId()
        );
        return R.okMsg("审批通过，角色开放注册状态已更新");
    }

    @PutMapping("/roles/self-register-requests/{id}/reject")
    public R<?> rejectSelfRegisterRequest(@PathVariable Long id, @RequestBody(required = false) ReviewReq req) {
        User reviewer = requireRoleManageAccess();
        Map<String, Object> request = requirePendingSelfRegisterRequest(id, reviewer.getCompanyId());
        Long requestedBy = toLong(request.get("requestedBy"));
        if (requestedBy != null && requestedBy.equals(reviewer.getId())) {
            throw new BizException(40000, "申请人与审批人不能是同一人");
        }

        jdbcTemplate.update(
            "UPDATE role_self_register_change SET status = 'rejected', reviewed_by = ?, review_note = ?, update_time = CURRENT_TIMESTAMP WHERE id = ? AND company_id = ?",
            reviewer.getId(),
            req == null ? null : req.getReviewNote(),
            id,
            reviewer.getCompanyId()
        );
        return R.okMsg("审批已拒绝");
    }

    @DeleteMapping("/roles/{id}")
    public R<?> deleteRole(@PathVariable Long id) {
        User currentUser = requireRoleManageAccess();
        Role role = requireCompanyRole(id, currentUser.getCompanyId());
        if (Boolean.TRUE.equals(role.getIsSystem())) {
            throw new BizException(40000, "系统预设角色不允许删除");
        }

        long userBound = userService.lambdaQuery().eq(User::getRoleId, role.getId()).count();
        if (userBound > 0) {
            throw new BizException(40000, "角色仍有关联用户，无法删除");
        }
        try {
            long userRoleBound = userRoleService.lambdaQuery().eq(UserRole::getRoleId, role.getId()).count();
            if (userRoleBound > 0) {
                throw new BizException(40000, "角色仍有关联用户，无法删除");
            }
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ignored) {
            // user_role table may not exist in older environments
        }

        rolePermissionService.remove(new QueryWrapper<RolePermission>().eq("role_id", role.getId()));
        roleService.removeById(role.getId());
        return R.okMsg("删除成功");
    }

    @GetMapping("/roles/{id}/permissions")
    public R<List<String>> getRolePermissions(@PathVariable Long id) {
        User currentUser = requireRoleManageAccess();
        Role role = requireCompanyRole(id, currentUser.getCompanyId());
        List<Long> permissionIds = rolePermissionService.lambdaQuery()
            .eq(RolePermission::getRoleId, role.getId())
            .list()
            .stream()
            .map(RolePermission::getPermissionId)
            .filter(Objects::nonNull)
            .toList();
        if (permissionIds.isEmpty()) {
            return R.ok(List.of());
        }
        List<String> codes = permissionService.lambdaQuery()
            .in(Permission::getId, permissionIds)
            .eq(Permission::getCompanyId, currentUser.getCompanyId())
            .list()
            .stream()
            .map(Permission::getCode)
            .filter(StringUtils::hasText)
            .sorted()
            .toList();
        return R.ok(codes);
    }

    @PutMapping("/roles/{id}/permissions")
    public R<?> updateRolePermissions(@PathVariable Long id, @Valid @RequestBody RolePermissionUpdateReq req) {
        User currentUser = requireRoleManageAccess();
        Role role = requireCompanyRole(id, currentUser.getCompanyId());
        replaceRolePermissions(currentUser.getCompanyId(), role.getId(), req.getPermissionCodes());
        return R.okMsg("权限更新成功");
    }

    @GetMapping("/public/roles")
    public R<List<Map<String, Object>>> publicSelfRegisterRoles(@RequestParam Long companyId) {
        if (companyId == null || companyId <= 0L) {
            throw new BizException(40000, "companyId 不能为空");
        }
        List<Map<String, Object>> roles = roleService.lambdaQuery()
            .eq(Role::getCompanyId, companyId)
            .eq(Role::getAllowSelfRegister, true)
            .orderByAsc(Role::getId)
            .list()
            .stream()
            .map(role -> {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", role.getId());
                row.put("name", role.getName());
                row.put("code", role.getCode());
                return row;
            })
            .toList();
        return R.ok(roles);
    }

    private User requireRoleManageAccess() {
        User currentUser = currentUserService.requireCurrentUser();
        if (currentUserService.hasRole("ADMIN") || hasAuthority("role:manage")) {
            return currentUser;
        }
        throw new BizException(40300, "无权限");
    }

    private boolean hasAuthority(String permissionCode) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        String required = String.valueOf(permissionCode == null ? "" : permissionCode).trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(required)) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            if (authority == null) {
                continue;
            }
            String code = String.valueOf(authority.getAuthority() == null ? "" : authority.getAuthority()).trim().toLowerCase(Locale.ROOT);
            if (required.equals(code)) {
                return true;
            }
        }
        return false;
    }

    private Role requireCompanyRole(Long roleId, Long companyId) {
        Role role = roleService.getById(roleId);
        if (role == null || !Objects.equals(role.getCompanyId(), companyId)) {
            throw new BizException(40400, "角色不存在或不属于当前公司");
        }
        return role;
    }

    private String normalizeRoleCode(String code) {
        String normalized = String.valueOf(code == null ? "" : code).trim().toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized)) {
            throw new BizException(40000, "角色编码不能为空");
        }
        return normalized;
    }

    private void ensureRoleCodeUnique(Long companyId, String code, Long excludeRoleId) {
        long count = roleService.lambdaQuery()
            .eq(Role::getCompanyId, companyId)
            .eq(Role::getCode, code)
            .ne(excludeRoleId != null, Role::getId, excludeRoleId)
            .count();
        if (count > 0) {
            throw new BizException(40000, "当前公司已存在同编码角色");
        }
    }

    private void replaceRolePermissions(Long companyId, Long roleId, List<String> permissionCodes) {
        rolePermissionService.remove(new QueryWrapper<RolePermission>().eq("role_id", roleId));
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return;
        }
        Set<String> normalizedCodes = permissionCodes.stream()
            .filter(StringUtils::hasText)
            .map(code -> code.trim().toLowerCase(Locale.ROOT))
            .collect(Collectors.toSet());
        if (normalizedCodes.isEmpty()) {
            return;
        }

        List<Permission> permissions = permissionService.lambdaQuery()
            .eq(Permission::getCompanyId, companyId)
            .list()
            .stream()
            .filter(permission -> StringUtils.hasText(permission.getCode())
                && normalizedCodes.contains(permission.getCode().trim().toLowerCase(Locale.ROOT)))
            .toList();

        List<RolePermission> mappings = new ArrayList<>();
        for (Permission permission : permissions) {
            RolePermission mapping = new RolePermission();
            mapping.setRoleId(roleId);
            mapping.setPermissionId(permission.getId());
            mappings.add(mapping);
        }
        if (!mappings.isEmpty()) {
            rolePermissionService.saveBatch(mappings);
        }
    }

    private boolean requiresSelfRegisterApproval(String roleCode) {
        return StringUtils.hasText(roleCode) && HIGH_RISK_SELF_REGISTER_CODES.contains(roleCode.trim().toUpperCase(Locale.ROOT));
    }

    private Long createSelfRegisterApprovalRequest(User requester, Role role, boolean allowSelfRegister, String reviewNote) {
        jdbcTemplate.update(
            "INSERT INTO role_self_register_change(company_id, role_id, role_code, requested_allow_self_register, status, requested_by, review_note, create_time, update_time) VALUES(?, ?, ?, ?, 'pending', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
            requester.getCompanyId(),
            role.getId(),
            role.getCode(),
            allowSelfRegister,
            requester.getId(),
            StringUtils.hasText(reviewNote) ? reviewNote.trim() : null
        );
        return jdbcTemplate.query(
            "SELECT id FROM role_self_register_change WHERE company_id = ? AND role_id = ? ORDER BY id DESC LIMIT 1",
            ps -> {
                ps.setLong(1, requester.getCompanyId());
                ps.setLong(2, role.getId());
            },
            rs -> rs.next() ? rs.getLong(1) : null
        );
    }

    private Map<String, Object> requirePendingSelfRegisterRequest(Long id, Long companyId) {
        Map<String, Object> row = jdbcTemplate.query(
            "SELECT role_id, requested_allow_self_register, status, requested_by FROM role_self_register_change WHERE id = ? AND company_id = ? LIMIT 1",
            ps -> {
                ps.setLong(1, id);
                ps.setLong(2, companyId);
            },
            rs -> {
                if (!rs.next()) {
                    return null;
                }
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("roleId", rs.getLong("role_id"));
                item.put("requestedAllowSelfRegister", rs.getBoolean("requested_allow_self_register"));
                item.put("status", rs.getString("status"));
                item.put("requestedBy", rs.getObject("requested_by"));
                return item;
            }
        );
        if (row == null) {
            throw new BizException(40400, "审批请求不存在");
        }
        String status = String.valueOf(row.get("status") == null ? "" : row.get("status")).trim().toLowerCase(Locale.ROOT);
        if (!"pending".equals(status)) {
            throw new BizException(40000, "审批请求已处理");
        }
        return row;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return null;
        }
    }

    @Data
    public static class RoleUpsertReq {
        @NotBlank(message = "角色名称不能为空")
        private String name;
        @NotBlank(message = "角色编码不能为空")
        private String code;
        private String description;
        private Boolean allowSelfRegister;
        private List<String> permissionCodes;
        private String reviewNote;
    }

    @Data
    public static class RolePermissionUpdateReq {
        @NotNull(message = "权限列表不能为空")
        private List<String> permissionCodes;
    }

    @Data
    public static class ReviewReq {
        private String reviewNote;
    }
}
