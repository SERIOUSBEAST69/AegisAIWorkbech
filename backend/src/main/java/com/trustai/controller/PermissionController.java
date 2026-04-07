package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.entity.AuditLog;
import com.trustai.entity.Permission;
import com.trustai.entity.Role;
import com.trustai.entity.RolePermission;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import com.trustai.service.AuditLogService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.PermissionService;
import com.trustai.service.RolePermissionService;
import com.trustai.service.RoleService;
import com.trustai.service.SensitiveOperationGuardService;
import com.trustai.utils.R;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/permission")
public class PermissionController {

    private static final ObjectMapper AUDIT_MAPPER = new ObjectMapper();

    private static final Pattern MODERN_PERMISSION_CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_-]*(?::[a-z][a-z0-9_-]*)+$");
    private static final Pattern LEGACY_PERMISSION_CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private static final Set<String> ALLOWED_PERMISSION_TYPES = Set.of("menu", "button");
    private static final Set<String> ALLOWED_PERMISSION_STATUS = Set.of("active", "disabled");

    private static final Map<String, String> ROLE_CHILD_ALIAS = Map.of();

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
    @Autowired
    private RoleService roleService;
    @Autowired
    private RolePermissionService rolePermissionService;
    @Autowired
    private HttpServletRequest httpServletRequest;

    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasPermission('permission:manage') || @currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER')")
    public R<List<Permission>> list(@RequestParam(required = false) String name) {
        ensurePermissionReadAccess();
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
    @PreAuthorize("@currentUserService.hasPermission('permission:manage') || @currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER')")
    public R<Map<String, Object>> page(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) String name,
                                       @RequestParam(required = false) String code,
                                       @RequestParam(required = false) String type,
                                       @RequestParam(required = false) Long parentId,
                                       @RequestParam(required = false) Boolean rootOnly,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String sortBy,
                                       @RequestParam(required = false) String sortOrder) {
        ensurePermissionReadAccess();
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
            qw.like("code", code.trim());
        }
        if (StringUtils.hasText(type)) {
            qw.eq("type", normalizePermissionType(type));
        }
        if (Boolean.TRUE.equals(rootOnly)) {
            qw.isNull("parent_id");
        } else if (parentId != null && parentId > 0) {
            qw.eq("parent_id", parentId);
        }
        if (StringUtils.hasText(status)) {
            qw.eq("status", normalizePermissionStatus(status));
        }
        applyPageSort(qw, sortBy, sortOrder);

        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        Page<Permission> result = permissionService.page(new Page<>(safePage, safePageSize), qw);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("current", result.getCurrent());
        payload.put("pages", result.getPages());
        payload.put("total", result.getTotal());
        payload.put("list", result.getRecords());
        return R.ok(payload);
    }

    @PostMapping("/add")
    @PreAuthorize("@currentUserService.hasPermission('permission:manage')")
    public R<?> add(@jakarta.validation.Valid @RequestBody PermissionUpsertReq req) {
        currentUserService.requirePermission("permission:manage");
        assertPermissionTablesReady();
        User currentUser = currentUserService.requireCurrentUser();
        sensitiveOperationGuardService.requireDualReviewedOperator(
            currentUser,
            req.getConfirmPassword(),
            req.getReviewerUsername(),
            req.getReviewerPassword(),
            "permission_add",
            "permissionCode=" + req.getCode()
        );
        Permission permission = new Permission();
        permission.setName(req.getName());
        permission.setCode(req.getCode());
        permission.setType(normalizePermissionType(req.getType()));
        permission.setStatus(normalizePermissionStatus(req.getStatus()));
        permission.setParentId(req.getParentId());
        String code = normalizePermissionCode(permission.getCode(), null);
        boolean exists = permissionService.lambdaQuery()
            .eq(Permission::getCompanyId, currentUser.getCompanyId())
            .eq(Permission::getCode, code)
            .count() > 0;
        if (exists) {
            throw new BizException(40000, "当前公司已存在同编码权限");
        }
        permission.setCode(code);
        permission.setCompanyId(currentUser.getCompanyId());
        validateParentRelation(permission.getParentId(), null, currentUser.getCompanyId());
        permission.setCreateTime(new Date());
        permission.setUpdateTime(new Date());
        permissionService.save(permission);
        writePermissionAudit(currentUser, "permission_add", permission, Map.of(), permissionSnapshot(permission), "success");
        return R.okMsg("添加成功");
    }

    @PostMapping("/update")
    @PreAuthorize("@currentUserService.hasPermission('permission:manage')")
    public R<?> update(@jakarta.validation.Valid @RequestBody PermissionUpsertReq req) {
        currentUserService.requirePermission("permission:manage");
        assertPermissionTablesReady();
        User currentUser = currentUserService.requireCurrentUser();
        sensitiveOperationGuardService.requireDualReviewedOperator(
            currentUser,
            req.getConfirmPassword(),
            req.getReviewerUsername(),
            req.getReviewerPassword(),
            "permission_update",
            "permissionId=" + req.getId()
        );
        Permission permission = new Permission();
        permission.setId(req.getId());
        permission.setName(req.getName());
        permission.setCode(req.getCode());
        permission.setType(normalizePermissionType(req.getType()));
        permission.setParentId(req.getParentId());
        Permission existing = permissionService.getById(permission.getId());
        if (existing == null || !java.util.Objects.equals(existing.getCompanyId(), currentUser.getCompanyId())) {
            throw new BizException(40400, "权限不存在或不在当前公司");
        }
        if (!java.util.Objects.equals(req.getId(), existing.getId())) {
            throw new BizException(40000, "权限ID不可修改");
        }
        String previousStatus = normalizePermissionStatus(existing.getStatus());
        Map<String, Object> beforeSnapshot = permissionSnapshot(existing);
        if (StringUtils.hasText(permission.getCode())) {
            String code = normalizePermissionCode(permission.getCode(), existing.getCode());
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
        permission.setStatus(normalizePermissionStatus(req.getStatus()));
        validateParentRelation(permission.getParentId(), existing.getId(), currentUser.getCompanyId());
        permission.setCompanyId(existing.getCompanyId());
        permission.setUpdateTime(new Date());
        permissionService.updateById(permission);
        Permission after = permissionService.getById(existing.getId());
        String operation = "permission_update";
        String nextStatus = after == null ? previousStatus : normalizePermissionStatus(after.getStatus());
        if (!previousStatus.equals(nextStatus)) {
            if ("active".equals(nextStatus)) {
                operation = "permission_enable";
            } else if ("disabled".equals(nextStatus)) {
                operation = "permission_disable";
            }
        }
        writePermissionAudit(currentUser, operation, after == null ? existing : after, beforeSnapshot, permissionSnapshot(after), "success");
        return R.okMsg("更新成功");
    }

    @PostMapping("/delete")
    @PreAuthorize("@currentUserService.hasPermission('permission:manage')")
    public R<?> delete(@jakarta.validation.Valid @RequestBody IdReq req) {
        currentUserService.requireAdmin();
        User currentUser = currentUserService.requireCurrentUser();
        sensitiveOperationGuardService.requireDualReviewedOperator(
            currentUser,
            req.getConfirmPassword(),
            req.getReviewerUsername(),
            req.getReviewerPassword(),
            "permission_delete",
            "permissionId=" + req.getId()
        );
        currentUserService.requireAdmin();
        assertPermissionTablesReady();
        Permission existing = permissionService.getById(req.getId());
        if (existing == null || !java.util.Objects.equals(existing.getCompanyId(), currentUser.getCompanyId())) {
            throw new BizException(40400, "权限不存在或不在当前公司");
        }
        long childCount = permissionService.lambdaQuery()
            .eq(Permission::getCompanyId, currentUser.getCompanyId())
            .eq(Permission::getParentId, existing.getId())
            .count();
        if (childCount > 0) {
            throw new BizException(40000, "当前权限存在子权限依赖，无法删除");
        }
        permissionService.removeById(req.getId());
        Map<String, Object> afterSnapshot = new LinkedHashMap<>();
        afterSnapshot.put("deleted", true);
        writePermissionAudit(currentUser, "permission_delete", existing, permissionSnapshot(existing), afterSnapshot, "success");
        return R.okMsg("删除成功");
    }

    @GetMapping("/matrix")
    @PreAuthorize("@currentUserService.hasPermission('permission:matrix:view') || @currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER')")
    public R<Map<String, Object>> matrix() {
        User currentUser = currentUserService.requireCurrentUser();
        Long companyId = resolveCompanyId(currentUser);
        List<Role> roles = roleService.lambdaQuery()
            .eq(Role::getCompanyId, companyId)
            .orderByAsc(Role::getId)
            .list();
        List<Permission> permissions = permissionService.lambdaQuery()
            .eq(Permission::getCompanyId, companyId)
            .orderByAsc(Permission::getId)
            .list();

        Map<Long, java.util.Set<Long>> rolePermMap = new java.util.LinkedHashMap<>();
        List<Long> roleIds = roles.stream().map(Role::getId).filter(java.util.Objects::nonNull).toList();
        if (!roleIds.isEmpty()) {
            for (RolePermission rp : rolePermissionService.lambdaQuery().in(RolePermission::getRoleId, roleIds).list()) {
                if (rp.getRoleId() == null || rp.getPermissionId() == null) {
                    continue;
                }
                rolePermMap.computeIfAbsent(rp.getRoleId(), k -> new java.util.LinkedHashSet<>()).add(rp.getPermissionId());
            }
        }

        java.util.List<Map<String, Object>> rows = new java.util.ArrayList<>();
        java.util.Map<String, Map<String, Object>> rowsByCode = new java.util.LinkedHashMap<>();
        for (Role role : roles) {
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("roleId", role.getId());
            row.put("roleCode", role.getCode());
            row.put("roleName", normalizeRoleDisplayName(role.getCode(), role.getName()));
            row.put("permissionIds", rolePermMap.getOrDefault(role.getId(), java.util.Set.of()));
            rows.add(row);
            String roleCode = String.valueOf(role.getCode() == null ? "" : role.getCode()).trim().toUpperCase(Locale.ROOT);
            if (StringUtils.hasText(roleCode)) {
                rowsByCode.put(roleCode, row);
            }
        }

        for (Map.Entry<String, String> entry : ROLE_CHILD_ALIAS.entrySet()) {
            String childCode = entry.getKey();
            String parentCode = entry.getValue();
            if (rowsByCode.containsKey(childCode)) {
                continue;
            }
            Map<String, Object> parent = rowsByCode.get(parentCode);
            if (parent == null) {
                continue;
            }
            Map<String, Object> child = new java.util.LinkedHashMap<>();
            child.put("roleId", "virtual_" + childCode.toLowerCase(Locale.ROOT));
            child.put("roleCode", childCode);
            child.put("roleName", normalizeRoleDisplayName(childCode, null));
            child.put("permissionIds", parent.get("permissionIds"));
            rows.add(child);
        }

        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("roles", rows);
        result.put("permissions", permissions);
        return R.ok(result);
    }

    private String normalizeRoleDisplayName(String roleCode, String roleName) {
        String code = String.valueOf(roleCode == null ? "" : roleCode).trim().toUpperCase(Locale.ROOT);
        if ("ADMIN".equals(code)) {
            return "治理管理员";
        }
        if ("ADMIN_REVIEWER".equals(code)) {
            return "治理复核员";
        }
        if ("SECOPS".equals(code)) {
            return "安全运维";
        }
        if ("BUSINESS_OWNER".equals(code)) {
            return "业务负责人";
        }
        if ("AUDIT".equals(code)) {
            return "审计员";
        }
        return StringUtils.hasText(roleName) ? roleName : "-";
    }

    private String normalizePermissionType(String type) {
        String normalized = String.valueOf(type == null ? "" : type).trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_PERMISSION_TYPES.contains(normalized)) {
            throw new BizException(40000, "权限类型仅支持 menu 或 button");
        }
        return normalized;
    }

    private String normalizePermissionStatus(String status) {
        String normalized = String.valueOf(status == null ? "active" : status).trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_PERMISSION_STATUS.contains(normalized)) {
            throw new BizException(40000, "权限状态仅支持 active 或 disabled");
        }
        return normalized;
    }

    private String normalizePermissionCode(String code, String existingCode) {
        String candidate = String.valueOf(code == null ? "" : code).trim();
        if (!StringUtils.hasText(candidate)) {
            throw new BizException(40000, "权限编码不能为空");
        }
        if (MODERN_PERMISSION_CODE_PATTERN.matcher(candidate).matches()) {
            return candidate.toLowerCase(Locale.ROOT);
        }
        if (LEGACY_PERMISSION_CODE_PATTERN.matcher(candidate).matches() && StringUtils.hasText(existingCode) && existingCode.trim().equalsIgnoreCase(candidate)) {
            return existingCode.trim().toUpperCase(Locale.ROOT);
        }
        throw new BizException(40000, "权限编码格式需为 模块:操作，例如 user:manage");
    }

    private void applyPageSort(QueryWrapper<Permission> qw, String sortBy, String sortOrder) {
        boolean asc = "ascending".equalsIgnoreCase(sortOrder) || "asc".equalsIgnoreCase(sortOrder);
        String normalizedSortBy = String.valueOf(sortBy == null ? "" : sortBy).trim();
        switch (normalizedSortBy) {
            case "code" -> qw.orderBy(true, asc, "code");
            case "parentId" -> qw.orderBy(true, asc, "parent_id");
            case "name" -> qw.orderBy(true, asc, "name");
            default -> qw.orderByDesc("update_time");
        }
    }

    private void validateParentRelation(Long parentId, Long selfId, Long companyId) {
        if (parentId == null) {
            return;
        }
        if (selfId != null && parentId.equals(selfId)) {
            throw new BizException(40000, "父级权限不能是自身");
        }
        Permission parent = permissionService.getById(parentId);
        if (parent == null || !java.util.Objects.equals(parent.getCompanyId(), companyId)) {
            throw new BizException(40000, "父级权限不存在或不在当前公司");
        }
    }

    private Long resolveCompanyId(User currentUser) {
        if (currentUser != null && currentUser.getCompanyId() != null) {
            return currentUser.getCompanyId();
        }
        String companyIdHeader = httpServletRequest == null ? null : httpServletRequest.getHeader("X-Company-Id");
        if (StringUtils.hasText(companyIdHeader)) {
            try {
                String header = companyIdHeader == null ? "" : companyIdHeader.trim();
                long parsed = Long.parseLong(header);
                if (parsed > 0L) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // Fall through to consistent access error.
            }
        }
        throw new BizException(40300, "当前账号未绑定公司，无法访问权限矩阵");
    }

    private Map<String, Object> permissionSnapshot(Permission permission) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (permission == null) {
            return snapshot;
        }
        snapshot.put("id", permission.getId());
        snapshot.put("name", permission.getName());
        snapshot.put("code", permission.getCode());
        snapshot.put("type", permission.getType());
        snapshot.put("status", permission.getStatus());
        snapshot.put("parentId", permission.getParentId());
        return snapshot;
    }

    private String safeJson(Map<String, Object> content) {
        try {
            return AUDIT_MAPPER.writeValueAsString(content == null ? Map.of() : content);
        } catch (Exception ex) {
            return String.valueOf(content == null ? Map.of() : content);
        }
    }

    private String resolveRequestIp() {
        String forwardedFor = httpServletRequest == null ? null : httpServletRequest.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            return forwardedFor.split(",")[0].trim();
        }
        String realIp = httpServletRequest == null ? null : httpServletRequest.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return httpServletRequest == null ? null : httpServletRequest.getRemoteAddr();
    }

    private void writePermissionAudit(User operator,
                                      String operation,
                                      Permission permission,
                                      Map<String, Object> before,
                                      Map<String, Object> after,
                                      String result) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(operator.getId());
            log.setOperation(operation);
            log.setOperationTime(new Date());
            log.setIp(resolveRequestIp());
            log.setPermissionId(permission == null ? null : permission.getId());
            log.setPermissionName(permission == null ? null : permission.getName());
            log.setInputOverview(safeJson(before));
            log.setOutputOverview(safeJson(after));
            log.setResult(StringUtils.hasText(result) ? result : "success");
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

    private void ensurePermissionReadAccess() {
        if (currentUserService.hasPermission("permission:manage") || currentUserService.hasAnyRole("ADMIN", "ADMIN_REVIEWER")) {
            return;
        }
        throw new BizException(40300, "当前账号无权查看权限管理");
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

    public static class PermissionUpsertReq {
        private Long id;
        @NotBlank(message = "权限名称不能为空")
        private String name;
        @NotBlank(message = "权限编码不能为空")
        private String code;
        @NotBlank(message = "权限类型不能为空")
        private String type;
        private String status;
        private Long parentId;
        @NotBlank(message = "敏感操作需要二次密码")
        private String confirmPassword;
        @NotBlank(message = "敏感操作需要第二复核人账号")
        private String reviewerUsername;
        @NotBlank(message = "敏感操作需要第二复核人密码")
        private String reviewerPassword;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getCode() { return code; }
        public void setCode(String code) { this.code = code; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public Long getParentId() { return parentId; }
        public void setParentId(Long parentId) { this.parentId = parentId; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
        public String getReviewerUsername() { return reviewerUsername; }
        public void setReviewerUsername(String reviewerUsername) { this.reviewerUsername = reviewerUsername; }
        public String getReviewerPassword() { return reviewerPassword; }
        public void setReviewerPassword(String reviewerPassword) { this.reviewerPassword = reviewerPassword; }
    }
}
