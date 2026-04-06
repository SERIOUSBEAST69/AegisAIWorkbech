package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.dto.ChangePasswordDTO;
import com.trustai.dto.UserProfileDTO;
import com.trustai.dto.UserUpdateDTO;
import com.trustai.entity.AuditLog;
import com.trustai.entity.Company;
import com.trustai.entity.Role;
import com.trustai.entity.User;
import com.trustai.entity.UserRole;
import com.trustai.entity.UserRecycleBin;
import com.trustai.exception.BizException;
import com.trustai.service.AuditLogService;
import com.trustai.service.CompanyService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.RoleService;
import com.trustai.service.SensitiveOperationGuardService;
import com.trustai.service.UserService;
import com.trustai.service.UserRoleService;
import com.trustai.service.UserRecycleBinService;
import com.trustai.utils.R;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user")
@Validated
public class UserController {
    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private static final String ACCOUNT_STATUS_PENDING = "pending";
    private static final String ACCOUNT_STATUS_ACTIVE = "active";
    private static final String ACCOUNT_STATUS_REJECTED = "rejected";
    private static final String GOV_ADMIN_PRIMARY = "admin";
    private static final String GOV_ADMIN_REVIEWER = "admin_reviewer";
    private static final String GOV_ADMIN_OPS = "admin_ops";
    private static final Long DEFAULT_COMPANY_ID = 1L;
    private static final long MAX_AVATAR_SIZE_BYTES = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_AVATAR_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp");
    private static final Set<String> ALLOWED_AVATAR_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");
    private static final Map<String, List<String>> COMPANY_ONE_PRESET_USER_ROLE = Map.ofEntries(
        Map.entry("admin", List.of("ADMIN")),
        Map.entry("admin_reviewer", List.of("ADMIN_REVIEWER", "ADMIN")),
        Map.entry("admin_ops", List.of("ADMIN_OPS", "ADMIN")),
        Map.entry("executive", List.of("EXECUTIVE")),
        Map.entry("executive_2", List.of("EXECUTIVE_OVERVIEW", "EXECUTIVE")),
        Map.entry("executive_3", List.of("EXECUTIVE_COMPLIANCE", "EXECUTIVE")),
        Map.entry("secops", List.of("SECOPS")),
        Map.entry("secops_2", List.of("SECOPS_TRIAGE", "SECOPS")),
        Map.entry("secops_3", List.of("SECOPS_RESPONDER", "SECOPS")),
        Map.entry("dataadmin", List.of("DATA_ADMIN")),
        Map.entry("dataadmin_2", List.of("DATA_ADMIN_MAINTAINER", "DATA_ADMIN")),
        Map.entry("dataadmin_3", List.of("DATA_ADMIN_APPROVER", "DATA_ADMIN")),
        Map.entry("aibuilder", List.of("AI_BUILDER")),
        Map.entry("aibuilder_2", List.of("AI_BUILDER_PROMPT", "AI_BUILDER")),
        Map.entry("aibuilder_3", List.of("AI_BUILDER_AUDITOR", "AI_BUILDER")),
        Map.entry("bizowner", List.of("BUSINESS_OWNER")),
        Map.entry("bizowner_2", List.of("BUSINESS_OWNER_APPROVER", "BUSINESS_OWNER")),
        Map.entry("bizowner_3", List.of("BUSINESS_OWNER_REVIEWER", "BUSINESS_OWNER")),
        Map.entry("employee1", List.of("EMPLOYEE")),
        Map.entry("employee2", List.of("EMPLOYEE_REQUESTER_FULL", "EMPLOYEE")),
        Map.entry("employee3", List.of("EMPLOYEE_OBSERVER", "EMPLOYEE"))
    );
    private static final Map<String, String> USERNAME_REAL_NAME = Map.ofEntries(
        Map.entry("admin", "张政"),
        Map.entry("admin_reviewer", "李审言"),
        Map.entry("admin_ops", "王运维"),
        Map.entry("executive", "陈明远"),
        Map.entry("executive_2", "赵景行"),
        Map.entry("executive_3", "孙知衡"),
        Map.entry("secops", "周锐"),
        Map.entry("secops_2", "吴凯"),
        Map.entry("secops_3", "郑航"),
        Map.entry("dataadmin", "钱思源"),
        Map.entry("dataadmin_2", "冯嘉禾"),
        Map.entry("dataadmin_3", "谢亦宁"),
        Map.entry("aibuilder", "韩启明"),
        Map.entry("aibuilder_2", "林彦博"),
        Map.entry("aibuilder_3", "蒋若凡"),
        Map.entry("bizowner", "许承业"),
        Map.entry("bizowner_2", "邓文轩"),
        Map.entry("bizowner_3", "曹远航"),
        Map.entry("employee1", "郭一帆"),
        Map.entry("employee2", "彭子轩"),
        Map.entry("employee3", "梁可欣")
    );

    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CurrentUserService currentUserService;
    @Autowired private CompanyService companyService;
    @Autowired private AuditLogService auditLogService;
    @Autowired private RoleService roleService;
    @Autowired private UserRoleService userRoleService;
    @Autowired private UserRecycleBinService userRecycleBinService;
    @Autowired private SensitiveOperationGuardService sensitiveOperationGuardService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasPermission('user:manage') || @currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER')")
    public R<List<User>> list(@RequestParam(required = false) String username,
                              @RequestParam(required = false) String accountStatus,
                              @RequestParam(required = false) String accountType) {
        requireUserManageReadAccess();
        Long companyId = requireBoundCompanyId();
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("company_id", companyId);
        if (username != null && !username.isEmpty()) qw.like("username", username);
        if (accountStatus != null && !accountStatus.isEmpty()) qw.eq("account_status", accountStatus);
        if (accountType != null && !accountType.isEmpty()) {
            qw.eq("account_type", accountType);
        } else {
            qw.eq("account_type", "real");
        }
        List<User> list = userService.list(qw);
        list.forEach(u -> u.setPassword(null));
        hydrateUserRoles(list);
        normalizeUserMasterData(list, companyId);
        return R.ok(list);
    }

    @GetMapping("/page")
    @PreAuthorize("@currentUserService.hasPermission('user:manage') || @currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER')")
    public R<Map<String, Object>> page(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) String username,
                                       @RequestParam(required = false) String accountStatus,
                                       @RequestParam(required = false) String accountType) {
        requireUserManageReadAccess();
        Long companyId = requireBoundCompanyId();
        QueryWrapper<User> qw = new QueryWrapper<>();
        qw.eq("company_id", companyId);
        if (StringUtils.hasText(username)) {
            qw.like("username", username);
        }
        if (StringUtils.hasText(accountStatus)) {
            qw.eq("account_status", accountStatus);
        }
        if (StringUtils.hasText(accountType)) {
            qw.eq("account_type", accountType);
        } else {
            qw.eq("account_type", "real");
        }
        qw.orderByDesc("update_time");

        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        Page<User> result = userService.page(new Page<>(safePage, safePageSize), qw);
        result.getRecords().forEach(item -> item.setPassword(null));
        hydrateUserRoles(result.getRecords());
        normalizeUserMasterData(result.getRecords(), companyId);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("current", result.getCurrent());
        payload.put("pages", result.getPages());
        payload.put("total", result.getTotal());
        payload.put("list", result.getRecords());
        return R.ok(payload);
    }

    @GetMapping("/pending")
    @PreAuthorize("@currentUserService.hasPermission('user:manage') || @currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER')")
    public R<List<User>> pendingList() {
        requireUserManageReadAccess();
        Long companyId = requireBoundCompanyId();
        QueryWrapper<User> qw = new QueryWrapper<User>()
            .eq("account_type", "real")
            .eq("account_status", ACCOUNT_STATUS_PENDING)
            .eq("company_id", companyId);
        List<User> list = userService.list(qw);
        list.forEach(u -> u.setPassword(null));
        normalizeUserMasterData(list, companyId);
        return R.ok(list);
    }

    @GetMapping("/role-recommend/{userId}")
    @PreAuthorize("@currentUserService.hasPermission('user:manage') || @currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER')")
    public R<Map<String, Object>> recommendRole(@PathVariable String userId,
                                                @RequestParam(required = false) String username) {
        requireUserManageReadAccess();
        Long companyId = requireBoundCompanyId();
        User target = resolveRecommendationTarget(userId, username, companyId);

        Date from = new Date(System.currentTimeMillis() - 90L * 24L * 3600_000L);
        List<User> companyUsers = userService.list(new QueryWrapper<User>()
            .eq("company_id", companyId)
            .orderByDesc("update_time")
            .last("limit 300"));
        List<Long> userIds = companyUsers.stream().map(User::getId).filter(Objects::nonNull).toList();

        Map<Long, Set<String>> opSetByUser = new HashMap<>();
        if (!userIds.isEmpty()) {
            List<AuditLog> logs = auditLogService.list(new QueryWrapper<AuditLog>()
                .in("user_id", userIds)
                .ge("operation_time", from)
                .orderByDesc("operation_time")
                .last("limit 5000"));
            for (AuditLog log : logs) {
                if (log.getUserId() == null || !StringUtils.hasText(log.getOperation())) {
                    continue;
                }
                opSetByUser.computeIfAbsent(log.getUserId(), k -> new HashSet<>())
                    .add(log.getOperation().trim().toLowerCase(Locale.ROOT));
            }
        }

        Set<String> targetOps = opSetByUser.getOrDefault(target.getId(), Set.of());
        Map<Long, Double> roleScore = new HashMap<>();
        Map<Long, Integer> supporters = new HashMap<>();
        Map<Long, Set<String>> roleOps = new HashMap<>();
        for (User peer : companyUsers) {
            if (peer.getId() == null || Objects.equals(peer.getId(), target.getId())) {
                continue;
            }
            List<Long> peerRoleIds = resolveUserRoleIds(peer);
            if (peerRoleIds.isEmpty()) {
                continue;
            }
            Set<String> peerOps = opSetByUser.getOrDefault(peer.getId(), Set.of());
            double score = jaccardSimilarity(targetOps, peerOps);
            if (targetOps.isEmpty()) {
                score = String.valueOf(target.getDepartment()).equalsIgnoreCase(String.valueOf(peer.getDepartment())) ? 0.35d : 0.08d;
            }
            if (String.valueOf(target.getDepartment()).equalsIgnoreCase(String.valueOf(peer.getDepartment()))) {
                score += 0.10d;
            }
            if (String.valueOf(target.getJobTitle()).equalsIgnoreCase(String.valueOf(peer.getJobTitle()))) {
                score += 0.08d;
            }
            score = Math.max(0d, Math.min(1d, score));
            for (Long roleId : peerRoleIds) {
                roleScore.put(roleId, roleScore.getOrDefault(roleId, 0d) + score);
                supporters.put(roleId, supporters.getOrDefault(roleId, 0) + 1);
                roleOps.computeIfAbsent(roleId, key -> new HashSet<>()).addAll(peerOps);
            }
        }

        Map<Long, Role> roleMap = roleService.list(new QueryWrapper<Role>().eq("company_id", companyId)).stream()
            .filter(role -> role.getId() != null)
            .collect(java.util.stream.Collectors.toMap(Role::getId, role -> role, (a, b) -> a));

        List<Long> currentRoleIds = resolveUserRoleIds(target);
        Set<Long> currentRoleIdSet = new HashSet<>(currentRoleIds);

        List<Map<String, Object>> ownedRoles = currentRoleIds.stream()
            .filter(roleMap::containsKey)
            .map(roleId -> {
                Role role = roleMap.get(roleId);
                Set<String> roleProfileOps = roleOps.getOrDefault(roleId, Set.of());
                int matchedOps = sharedOpsCount(targetOps, roleProfileOps);
                int support = Math.max(1, supporters.getOrDefault(roleId, 0));
                double behaviorMatch = targetOps.isEmpty() ? 0.60d : ((double) matchedOps / Math.max(1, targetOps.size()));
                double confidence = clamp01(behaviorMatch);
                double score = Math.round(confidence * 1000.0d) / 10.0d;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("roleId", role.getId());
                row.put("roleCode", role.getCode());
                row.put("roleName", role.getName());
                row.put("confidence", confidence);
                row.put("supportUsers", support);
                row.put("score", score);
                row.put("source", "current-role");
                row.put("reason", "近90天有" + matchedOps + "次操作与该角色权限画像匹配，建议保留");
                return row;
            })
            .toList();

        List<Map<String, Object>> recommendedBySimilarity = roleScore.entrySet().stream()
            .filter(entry -> roleMap.containsKey(entry.getKey()))
            .filter(entry -> !currentRoleIdSet.contains(entry.getKey()))
            .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
            .limit(5)
            .map(entry -> {
                Role role = roleMap.get(entry.getKey());
                int support = supporters.getOrDefault(entry.getKey(), 0);
                double avgSimilarity = support <= 0 ? 0.0d : (entry.getValue() / support);
                double confidence = clamp01(avgSimilarity);
                double score = Math.round(confidence * 1000.0d) / 10.0d;
                int matchedOps = sharedOpsCount(targetOps, roleOps.getOrDefault(entry.getKey(), Set.of()));
                int deptMatch = support;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("roleId", role.getId());
                row.put("roleCode", role.getCode());
                row.put("roleName", role.getName());
                row.put("confidence", confidence);
                row.put("supportUsers", support);
                row.put("score", score);
                row.put("source", "behavior-similarity");
                row.put("reason", "近90天有" + matchedOps + "次操作与该角色权限匹配，部门同类样本" + deptMatch + "人，建议开通");
                return row;
            })
            .toList();

        List<Map<String, Object>> recommended = new ArrayList<>(recommendedBySimilarity);

        if (recommended.isEmpty()) {
            Map<Long, Integer> deptRoleCount = new HashMap<>();
            for (User peer : companyUsers) {
                if (Objects.equals(peer.getId(), target.getId())) continue;
                if (!String.valueOf(target.getDepartment()).equalsIgnoreCase(String.valueOf(peer.getDepartment()))) continue;
                for (Long roleId : resolveUserRoleIds(peer)) {
                    deptRoleCount.put(roleId, deptRoleCount.getOrDefault(roleId, 0) + 1);
                }
            }
            recommended = deptRoleCount.entrySet().stream()
                .filter(entry -> roleMap.containsKey(entry.getKey()))
                .filter(entry -> !currentRoleIdSet.contains(entry.getKey()))
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(5)
                .map(entry -> {
                    Role role = roleMap.get(entry.getKey());
                    double confidence = clamp01(0.35d + Math.min(0.45d, entry.getValue() * 0.06d));
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("roleId", role.getId());
                    row.put("roleCode", role.getCode());
                    row.put("roleName", role.getName());
                    row.put("confidence", confidence);
                    row.put("supportUsers", entry.getValue());
                    row.put("score", Math.round(confidence * 1000.0d) / 10.0d);
                    row.put("source", "department-fallback");
                    row.put("reason", "与当前部门岗位匹配度" + Math.round(confidence * 100) + "% ，建议开通");
                    return row;
                })
                .toList();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", target.getId());
        payload.put("username", target.getUsername());
        payload.put("department", target.getDepartment());
        payload.put("jobTitle", target.getJobTitle());
        payload.put("currentRoleIds", currentRoleIds);
        payload.put("currentRoles", ownedRoles.stream().map(row -> {
            Map<String, Object> basic = new LinkedHashMap<>();
            basic.put("roleId", row.get("roleId"));
            basic.put("roleCode", row.get("roleCode"));
            basic.put("roleName", row.get("roleName"));
            return basic;
        }).toList());
        payload.put("ownedRoles", ownedRoles);
        payload.put("recommendedRoles", recommended);
        payload.put("basedOnDays", 90);
        payload.put("sampleUsers", companyUsers.size());
        payload.put("sampleOperations", targetOps.size());
        return R.ok(payload);
    }

    private User resolveRecommendationTarget(String userIdOrUsername, String fallbackUsername, Long companyId) {
        Long userId = parsePositiveLong(userIdOrUsername);
        if (userId != null) {
            User existing = userService.getById(userId);
            if (existing != null) {
                if (existing.getCompanyId() == null && companyId != null && companyId > 0L) {
                    existing.setCompanyId(companyId);
                    existing.setUpdateTime(new Date());
                    userService.updateById(existing);
                }
                if (Objects.equals(existing.getCompanyId(), companyId)) {
                    return existing;
                }
                User byOriginalUsername = queryCompanyUserByUsername(companyId, existing.getUsername());
                if (byOriginalUsername != null) {
                    return byOriginalUsername;
                }
            }
        }

        String candidateUsername = StringUtils.hasText(fallbackUsername)
            ? fallbackUsername.trim()
            : (userId == null ? String.valueOf(userIdOrUsername == null ? "" : userIdOrUsername).trim() : null);
        User byUsername = queryCompanyUserByUsername(companyId, candidateUsername);
        if (byUsername != null) {
            return byUsername;
        }
        throw new BizException(40400, "目标用户不存在或不在当前公司，请刷新用户列表后重试");
    }

    private User queryCompanyUserByUsername(Long companyId, String username) {
        if (!StringUtils.hasText(username)) {
            return null;
        }
        List<User> rows = userService.list(new QueryWrapper<User>()
            .eq("company_id", companyId)
            .eq("username", username.trim())
            .orderByDesc("update_time")
            .last("limit 1"));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private Long parsePositiveLong(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            long parsed = Long.parseLong(value.trim());
            return parsed > 0L ? parsed : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @PostMapping("/approve")
    @PreAuthorize("@currentUserService.hasPermission('user:manage') || @currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER')")
    public R<?> approve(@Valid @RequestBody ApproveReq req) {
        User admin = requireUserManageAccess();
        ensureGovernanceDuty(admin, "approve");
        User user = requireCompanyUser(req.getId(), requireBoundCompanyId());
        user.setAccountStatus(ACCOUNT_STATUS_ACTIVE);
        user.setRejectReason(null);
        user.setApprovedBy(admin.getId());
        user.setApprovedAt(new Date());
        user.setStatus(1);
        user.setUpdateTime(new Date());
        userService.updateById(user);
        writeApprovalAudit(admin, user, "approve", "账号审批通过");
        return R.okMsg("审批通过");
    }

    @PostMapping("/reject")
    @PreAuthorize("@currentUserService.hasPermission('user:manage') || @currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER')")
    public R<?> reject(@Valid @RequestBody RejectReq req) {
        User admin = requireUserManageAccess();
        ensureGovernanceDuty(admin, "approve");
        User user = requireCompanyUser(req.getId(), requireBoundCompanyId());
        user.setAccountStatus(ACCOUNT_STATUS_REJECTED);
        user.setRejectReason(req.getReason());
        user.setApprovedBy(admin.getId());
        user.setApprovedAt(new Date());
        user.setUpdateTime(new Date());
        userService.updateById(user);
        writeApprovalAudit(admin, user, "reject", StringUtils.hasText(req.getReason()) ? req.getReason() : "账号审批拒绝");
        return R.okMsg("审批已拒绝");
    }

    @PostMapping("/register")
    @PreAuthorize("@currentUserService.hasPermission('user:manage') || @currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER')")
    public R<?> register(@Valid @RequestBody User user) {
        User currentUser = requireUserManageAccess();
        ensureGovernanceDuty(currentUser, "write");
        if (!StringUtils.hasText(user.getUsername())) {
            throw new BizException(40000, "用户名不能为空");
        }
        String normalizedUsername = user.getUsername().trim();
        boolean usernameExists = userService.lambdaQuery()
            .eq(User::getUsername, normalizedUsername)
            .count() > 0;
        if (usernameExists) {
            throw new BizException(40000, "用户名已存在");
        }
        user.setUsername(normalizedUsername);
        user.setCompanyId(currentUser.getCompanyId());
        Long primaryRoleId = resolvePrimaryRoleId(user.getRoleId(), user.getRoleIds());
        ensureRoleInCompany(primaryRoleId, currentUser.getCompanyId());
        user.setRoleId(primaryRoleId);
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (!StringUtils.hasText(user.getAccountType())) {
            user.setAccountType("real");
        }
        if (!StringUtils.hasText(user.getAccountStatus())) {
            user.setAccountStatus(ACCOUNT_STATUS_ACTIVE);
        }
        user.setApprovedBy(currentUserService.requireCurrentUser().getId());
        user.setApprovedAt(new Date());
        user.setRejectReason(null);
        user.setCreateTime(new Date());
        user.setUpdateTime(new Date());
        userService.save(user);
        syncUserRoles(user.getId(), user.getRoleIds(), primaryRoleId);
        writeApprovalAudit(currentUser, user, "create", "管理员创建账号");
        return R.okMsg("注册成功");
    }

    @PostMapping("/update")
    @PreAuthorize("@currentUserService.hasPermission('user:manage') || @currentUserService.hasAnyRole('ADMIN','SECOPS')")
    public R<?> update(@Valid @RequestBody User user) {
        User operator = requireUserManageAccess();
        ensureGovernanceDuty(operator, "write");
        User existing = userService.getById(user.getId());
        if (existing == null || !java.util.Objects.equals(existing.getCompanyId(), operator.getCompanyId())) {
            throw new BizException(40400, "用户不存在或不在当前公司");
        }
        if (user.getRoleId() != null) {
            ensureRoleInCompany(user.getRoleId(), existing.getCompanyId());
        }
        Long primaryRoleId = resolvePrimaryRoleId(user.getRoleId(), user.getRoleIds());
        if (primaryRoleId != null) {
            ensureRoleInCompany(primaryRoleId, existing.getCompanyId());
            user.setRoleId(primaryRoleId);
        }
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        user.setCompanyId(existing.getCompanyId());
        user.setUpdateTime(new Date());
        userService.updateById(user);
        syncUserRoles(existing.getId(), user.getRoleIds(), primaryRoleId == null ? existing.getRoleId() : primaryRoleId);
        writeApprovalAudit(operator, existing, "update", "管理员更新账号");
        return R.okMsg("更新成功");
    }

    @PostMapping("/delete")
    @PreAuthorize("@currentUserService.hasPermission('user:manage') || @currentUserService.hasAnyRole('ADMIN','SECOPS')")
    public R<?> delete(@Valid @RequestBody IdReq req) {
        User operator = requireUserManageAccess();
        sensitiveOperationGuardService.requireConfirmedOperator(operator, req.getConfirmPassword(), "user_delete", "userId=" + req.getId());
        ensureGovernanceDuty(operator, "write");
        User existing = userService.getById(req.getId());
        if (existing == null || !java.util.Objects.equals(existing.getCompanyId(), operator.getCompanyId())) {
            throw new BizException(40400, "用户不存在或不在当前公司");
        }
        if (Objects.equals(existing.getId(), operator.getId())) {
            throw new BizException(40000, "不允许删除当前登录账号");
        }
        archiveDeletedUser(existing, operator, req.getDeleteReason());
        userService.removeById(req.getId());
        writeApprovalAudit(operator, existing, "delete", "管理员删除账号");
        return R.okMsg("删除成功");
    }

    @GetMapping("/recycle-bin/page")
    @PreAuthorize("@currentUserService.hasPermission('user:manage') || @currentUserService.hasAnyRole('ADMIN','SECOPS')")
    public R<Map<String, Object>> recycleBinPage(@RequestParam(defaultValue = "1") int page,
                                                 @RequestParam(defaultValue = "10") int pageSize,
                                                 @RequestParam(required = false) String username) {
        User currentUser = requireUserManageAccess();
        QueryWrapper<UserRecycleBin> qw = new QueryWrapper<UserRecycleBin>()
            .eq("company_id", currentUser.getCompanyId())
            .orderByDesc("deleted_at");
        if (StringUtils.hasText(username)) {
            qw.like("username", username.trim());
        }
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        Page<UserRecycleBin> result = userRecycleBinService.page(new Page<>(safePage, safePageSize), qw);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("current", result.getCurrent());
        payload.put("pages", result.getPages());
        payload.put("total", result.getTotal());
        payload.put("list", result.getRecords());
        return R.ok(payload);
    }

    @PostMapping("/recycle-bin/restore")
    @PreAuthorize("@currentUserService.hasPermission('user:manage') || @currentUserService.hasAnyRole('ADMIN','SECOPS')")
    public R<?> restoreFromRecycleBin(@Valid @RequestBody RestoreReq req) {
        User operator = requireUserManageAccess();
        sensitiveOperationGuardService.requireConfirmedOperator(operator, req.getConfirmPassword(), "user_restore", "recycleId=" + req.getRecycleId());
        ensureGovernanceDuty(operator, "write");
        UserRecycleBin recycle = userRecycleBinService.getById(req.getRecycleId());
        if (recycle == null || !Objects.equals(recycle.getCompanyId(), operator.getCompanyId())) {
            throw new BizException(40400, "回收记录不存在或不在当前公司");
        }
        if ("restored".equalsIgnoreCase(recycle.getRestoreStatus())) {
            throw new BizException(40000, "该记录已恢复");
        }
        User snapshot = parseSnapshot(recycle.getSnapshotJson());
        if (snapshot == null || !StringUtils.hasText(snapshot.getUsername())) {
            throw new BizException(40000, "回收记录快照损坏，无法恢复");
        }
        boolean usernameExists = userService.lambdaQuery().eq(User::getUsername, snapshot.getUsername()).count() > 0;
        if (usernameExists) {
            throw new BizException(40000, "恢复失败：用户名已被占用");
        }
        snapshot.setId(null);
        snapshot.setCompanyId(operator.getCompanyId());
        snapshot.setCreateTime(new Date());
        snapshot.setUpdateTime(new Date());
        userService.save(snapshot);

        recycle.setRestoreStatus("restored");
        recycle.setRestoredBy(operator.getId());
        recycle.setRestoredAt(new Date());
        recycle.setUpdateTime(new Date());
        userRecycleBinService.updateById(recycle);

        writeApprovalAudit(operator, snapshot, "restore", "管理员恢复回收站账号");
        return R.okMsg("恢复成功");
    }

    public static class IdReq {
        @NotNull(message = "用户ID不能为空")
        private Long id;
        @NotBlank(message = "敏感操作需要二次密码")
        private String confirmPassword;
        private String reviewerUsername;
        private String reviewerPassword;
        @Size(max = 200, message = "删除原因不能超过200字符")
        private String deleteReason;
        public Long getId(){return id;}
        public void setId(Long id){this.id=id;}
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
        public String getReviewerUsername() { return reviewerUsername; }
        public void setReviewerUsername(String reviewerUsername) { this.reviewerUsername = reviewerUsername; }
        public String getReviewerPassword() { return reviewerPassword; }
        public void setReviewerPassword(String reviewerPassword) { this.reviewerPassword = reviewerPassword; }
        public String getDeleteReason() { return deleteReason; }
        public void setDeleteReason(String deleteReason) { this.deleteReason = deleteReason; }
    }

    public static class RestoreReq {
        @NotNull(message = "回收记录ID不能为空")
        private Long recycleId;
        @NotBlank(message = "敏感操作需要二次密码")
        private String confirmPassword;
        public Long getRecycleId() { return recycleId; }
        public void setRecycleId(Long recycleId) { this.recycleId = recycleId; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    }

    public static class ApproveReq {
        @NotNull(message = "用户ID不能为空")
        private Long id;
        public Long getId(){return id;}
        public void setId(Long id){this.id=id;}
    }

    public static class RejectReq {
        @NotNull(message = "用户ID不能为空")
        private Long id;
        @Size(max = 200, message = "拒绝原因不能超过200字符")
        private String reason;
        public Long getId(){return id;}
        public void setId(Long id){this.id=id;}
        public String getReason(){return reason;}
        public void setReason(String reason){this.reason=reason;}
    }

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public R<UserProfileDTO> profile() {
        User user = currentUserService.requireCurrentUser();
        return R.ok(toProfile(user));
    }

    @PutMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public R<UserProfileDTO> updateProfile(@ModelAttribute UserUpdateDTO req) {
        User user = currentUserService.requireCurrentUser();
        if (req.getNickname() != null) user.setNickname(req.getNickname());
        if (req.getRealName() != null) user.setRealName(req.getRealName());
        if (req.getEmail() != null) user.setEmail(req.getEmail());
        if (req.getPhone() != null) user.setPhone(req.getPhone());
        if (req.getDepartment() != null) user.setDepartment(req.getDepartment());
        MultipartFile avatar = req.getAvatar();
        if (avatar != null && !avatar.isEmpty()) {
            user.setAvatar(storeAvatar(avatar));
        }
        user.setUpdateTime(new Date());
        userService.updateById(user);
        return R.ok(toProfile(user));
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public R<?> changePassword(@Validated @RequestBody ChangePasswordDTO req) {
        User user = currentUserService.requireCurrentUser();
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new BizException(40000, "旧密码不正确");
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        user.setUpdateTime(new Date());
        userService.updateById(user);
        return R.okMsg("密码已更新");
    }

    private UserProfileDTO toProfile(User user) {
        Role role = currentUserService.getCurrentRole(user);
        Company company = user.getCompanyId() == null ? null : companyService.getById(user.getCompanyId());
        return UserProfileDTO.builder()
            .id(user.getId())
            .companyId(user.getCompanyId())
            .companyName(company == null ? null : company.getCompanyName())
            .accountType(user.getAccountType())
            .accountStatus(resolveAccountStatus(user))
            .username(user.getUsername())
            .avatar(user.getAvatar())
            .nickname(user.getNickname())
            .realName(user.getRealName())
            .email(user.getEmail())
            .phone(user.getPhone())
            .department(user.getDepartment())
            .roleName(role == null ? null : role.getName())
            .roleCode(role == null ? null : role.getCode())
            .lastActiveAt(user.getUpdateTime() == null ? null : user.getUpdateTime().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime())
            .build();
    }

    private String resolveAccountStatus(User user) {
        if (StringUtils.hasText(user.getAccountStatus())) {
            return user.getAccountStatus();
        }
        return user.getStatus() != null && user.getStatus() == 0 ? "disabled" : ACCOUNT_STATUS_ACTIVE;
    }

    private User requireCompanyUser(Long id, Long companyId) {
        User existing = userService.getById(id);
        if (existing == null) {
            throw new BizException(40400, "用户不存在或不在当前公司");
        }
        if (existing.getCompanyId() == null && companyId != null && companyId > 0L) {
            existing.setCompanyId(companyId);
            existing.setUpdateTime(new Date());
            userService.updateById(existing);
        }
        if (!Objects.equals(existing.getCompanyId(), companyId)) {
            throw new BizException(40400, "用户不存在或不在当前公司");
        }
        return existing;
    }

    private Long requireBoundCompanyId() {
        Long companyId = currentUserService.requireCurrentUser().getCompanyId();
        if (companyId == null || companyId <= 0L) {
            throw new BizException(40300, "当前账号未绑定企业，无法执行公司范围管理操作");
        }
        return companyId;
    }

    private void writeApprovalAudit(User admin, User target, String action, String detail) {
        try {
            AuditLog auditLog = new AuditLog();
            auditLog.setUserId(admin.getId());
            auditLog.setOperation("user_registration_" + action);
            auditLog.setOperationTime(new Date());
            auditLog.setDevice(admin.getDeviceId());
            auditLog.setInputOverview("targetUser=" + target.getUsername());
            auditLog.setOutputOverview(detail);
            auditLog.setResult("success");
            auditLog.setRiskLevel("NORMAL");
            auditLog.setCreateTime(new Date());
            auditLogService.saveAudit(auditLog);
        } catch (Exception ex) {
            log.warn("Skip approval audit due to non-blocking error: {}", ex.getMessage());
        }
    }

    private void ensureRoleInCompany(Long roleId, Long companyId) {
        if (roleId == null) {
            throw new BizException(40000, "角色不能为空");
        }
        Role role = roleService.getById(roleId);
        if (role == null || !Objects.equals(role.getCompanyId(), companyId)) {
            throw new BizException(40000, "角色不存在或不属于当前公司");
        }
    }

    private Long resolvePrimaryRoleId(Long roleId, List<Long> roleIds) {
        if (roleId != null) {
            return roleId;
        }
        if (roleIds == null || roleIds.isEmpty()) {
            return null;
        }
        return roleIds.get(0);
    }

    private void syncUserRoles(Long userId, List<Long> roleIds, Long primaryRoleId) {
        if (userId == null) {
            return;
        }
        try {
            userRoleService.remove(new QueryWrapper<UserRole>().eq("user_id", userId));
        } catch (Exception ignored) {
            // user_role table may not be available in test/legacy environments.
            return;
        }
        List<Long> merged = new ArrayList<>();
        if (primaryRoleId != null) {
            merged.add(primaryRoleId);
        }
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                if (roleId != null && !merged.contains(roleId)) {
                    merged.add(roleId);
                }
            }
        }
        Date now = new Date();
        for (Long roleId : merged) {
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRole.setCreateTime(now);
            userRole.setUpdateTime(now);
            try {
                userRoleService.save(userRole);
            } catch (Exception ignored) {
                // user_role table may not be available in test/legacy environments.
                return;
            }
        }
    }

    private void hydrateUserRoles(List<User> users) {
        if (users == null || users.isEmpty()) {
            return;
        }
        List<Long> userIds = users.stream().map(User::getId).filter(Objects::nonNull).toList();
        if (userIds.isEmpty()) {
            return;
        }
        Map<Long, List<Long>> roleMap = new HashMap<>();
        try {
            for (UserRole userRole : userRoleService.lambdaQuery().in(UserRole::getUserId, userIds).list()) {
                if (userRole.getUserId() == null || userRole.getRoleId() == null) {
                    continue;
                }
                roleMap.computeIfAbsent(userRole.getUserId(), key -> new ArrayList<>()).add(userRole.getRoleId());
            }
        } catch (Exception ignored) {
            // user_role may not be available in legacy environments.
        }

        for (User user : users) {
            List<Long> roles = roleMap.getOrDefault(user.getId(), new ArrayList<>());
            if (user.getRoleId() != null && !roles.contains(user.getRoleId())) {
                roles.add(0, user.getRoleId());
            }
            user.setRoleIds(roles);
        }

        Set<Long> allRoleIds = new HashSet<>();
        for (User user : users) {
            if (user.getRoleIds() != null) {
                allRoleIds.addAll(user.getRoleIds().stream().filter(Objects::nonNull).toList());
            }
        }
        Map<Long, Role> roleById = allRoleIds.isEmpty()
            ? Map.of()
            : roleService.listByIds(allRoleIds).stream()
                .filter(role -> role != null && role.getId() != null)
                .collect(java.util.stream.Collectors.toMap(Role::getId, role -> role, (left, right) -> left));

        for (User user : users) {
            List<Long> roleIds = user.getRoleIds() == null ? List.of() : user.getRoleIds();
            List<String> roleCodes = new ArrayList<>();
            List<String> roleNames = new ArrayList<>();
            for (Long roleId : roleIds) {
                Role role = roleById.get(roleId);
                if (role == null) {
                    continue;
                }
                if (StringUtils.hasText(role.getCode())) {
                    roleCodes.add(role.getCode().trim().toUpperCase(Locale.ROOT));
                }
                if (StringUtils.hasText(role.getName())) {
                    roleNames.add(role.getName().trim());
                }
            }
            user.setRoleCodes(roleCodes);
            user.setRoleNames(roleNames);
        }
    }

    private void normalizeUserMasterData(List<User> users, Long companyId) {
        if (users == null || users.isEmpty()) {
            return;
        }
        Map<String, Role> roleByCode = roleService.lambdaQuery()
            .eq(Role::getCompanyId, companyId)
            .list()
            .stream()
            .filter(role -> StringUtils.hasText(role.getCode()))
            .collect(java.util.stream.Collectors.toMap(
                role -> role.getCode().trim().toUpperCase(Locale.ROOT),
                role -> role,
                (left, right) -> left
            ));
        Date now = new Date();
        for (User user : users) {
            if (user == null || user.getId() == null) {
                continue;
            }
            boolean changed = false;
            boolean syncRoles = false;
            List<Long> syncRoleIds = List.of();
            Long syncPrimaryRoleId = null;
            String normalizedUsername = String.valueOf(user.getUsername() == null ? "" : user.getUsername()).trim().toLowerCase(Locale.ROOT);

            String mappedRealName = USERNAME_REAL_NAME.get(normalizedUsername);
            String existingRealName = String.valueOf(user.getRealName() == null ? "" : user.getRealName()).trim();
            if (!StringUtils.hasText(existingRealName) || existingRealName.equalsIgnoreCase(String.valueOf(user.getUsername()))) {
                user.setRealName(StringUtils.hasText(mappedRealName)
                    ? mappedRealName
                    : (StringUtils.hasText(user.getNickname()) ? user.getNickname() : user.getUsername()));
                changed = true;
            }
            String existingNickname = String.valueOf(user.getNickname() == null ? "" : user.getNickname()).trim();
            if (!StringUtils.hasText(existingNickname) || existingNickname.equalsIgnoreCase(String.valueOf(user.getUsername()))) {
                user.setNickname(StringUtils.hasText(user.getRealName()) ? user.getRealName() : user.getUsername());
                changed = true;
            }

            List<String> expectedRoleCodes = COMPANY_ONE_PRESET_USER_ROLE.get(normalizedUsername);
            if (expectedRoleCodes != null && !expectedRoleCodes.isEmpty()) {
                List<Role> expectedRoles = resolvePresetRoles(roleByCode, expectedRoleCodes);
                Long expectedRoleId = expectedRoles.isEmpty() ? null : expectedRoles.get(0).getId();
                if (expectedRoleId != null && !Objects.equals(user.getRoleId(), expectedRoleId)) {
                    user.setRoleId(expectedRoleId);
                    changed = true;
                }
                List<Long> expectedRoleIds = expectedRoles.stream()
                    .map(Role::getId)
                    .filter(Objects::nonNull)
                    .toList();
                if (needSyncCompanyOneRoleBindings(user, expectedRoleIds)) {
                    syncRoles = true;
                    syncPrimaryRoleId = expectedRoleIds.isEmpty() ? null : expectedRoleIds.get(0);
                    syncRoleIds = expectedRoleIds;
                }
            } else {
                Role currentRole = user.getRoleId() == null ? null : roleService.getById(user.getRoleId());
                if (currentRole == null || !Objects.equals(currentRole.getCompanyId(), companyId)) {
                    if (user.getRoleId() != null) {
                        user.setRoleId(null);
                        changed = true;
                    }
                    if (hasAnyRoleBinding(user)) {
                        syncRoles = true;
                        syncPrimaryRoleId = null;
                        syncRoleIds = List.of();
                    }
                }
            }

            if (changed) {
                user.setUpdateTime(now);
                userService.updateById(user);
            }
            if (syncRoles) {
                syncUserRoles(user.getId(), syncRoleIds, syncPrimaryRoleId);
            }
        }
        hydrateUserRoles(users);
    }

    private boolean hasAnyRoleBinding(User user) {
        if (user == null) {
            return false;
        }
        if (user.getRoleId() != null) {
            return true;
        }
        return user.getRoleIds() != null && !user.getRoleIds().isEmpty();
    }

    private boolean needSyncCompanyOneRoleBindings(User user, Long expectedRoleId) {
        if (user == null) {
            return false;
        }
        List<Long> expectedRoleIds = expectedRoleId == null ? List.of() : List.of(expectedRoleId);
        return needSyncCompanyOneRoleBindings(user, expectedRoleIds);
    }

    private boolean needSyncCompanyOneRoleBindings(User user, List<Long> expectedRoleIds) {
        if (user == null) {
            return false;
        }
        List<Long> boundRoleIds = user.getRoleIds() == null ? List.of() : user.getRoleIds().stream().filter(Objects::nonNull).toList();
        List<Long> expected = expectedRoleIds == null ? List.of() : expectedRoleIds.stream().filter(Objects::nonNull).toList();
        if (expected.isEmpty()) {
            return hasAnyRoleBinding(user);
        }
        if (boundRoleIds.size() != expected.size()) {
            return true;
        }
        for (Long expectedRoleId : expected) {
            if (!boundRoleIds.contains(expectedRoleId)) {
                return true;
            }
        }
        return !Objects.equals(user.getRoleId(), expected.get(0));
    }

    private Role resolvePresetRole(Map<String, Role> roleByCode, List<String> candidateCodes) {
        List<Role> resolved = resolvePresetRoles(roleByCode, candidateCodes);
        return resolved.isEmpty() ? null : resolved.get(0);
    }

    private List<Role> resolvePresetRoles(Map<String, Role> roleByCode, List<String> candidateCodes) {
        List<Role> resolved = new ArrayList<>();
        if (roleByCode == null || roleByCode.isEmpty() || candidateCodes == null || candidateCodes.isEmpty()) {
            return resolved;
        }
        for (String candidateCode : candidateCodes) {
            if (!StringUtils.hasText(candidateCode)) {
                continue;
            }
            Role role = roleByCode.get(candidateCode.trim().toUpperCase(Locale.ROOT));
            if (role != null && !resolved.stream().anyMatch(item -> Objects.equals(item.getId(), role.getId()))) {
                resolved.add(role);
            }
        }
        return resolved;
    }

    private void archiveDeletedUser(User target, User operator, String reason) {
        try {
            UserRecycleBin recycleBin = new UserRecycleBin();
            recycleBin.setCompanyId(operator.getCompanyId());
            recycleBin.setUserId(target.getId());
            recycleBin.setUsername(target.getUsername());
            recycleBin.setSnapshotJson(MAPPER.writeValueAsString(target));
            recycleBin.setDeletedBy(operator.getId());
            recycleBin.setDeleteReason(StringUtils.hasText(reason) ? reason.trim() : "治理管理员删除账号");
            recycleBin.setDeletedAt(new Date());
            recycleBin.setRestoreStatus("deleted");
            recycleBin.setCreateTime(new Date());
            recycleBin.setUpdateTime(new Date());
            userRecycleBinService.save(recycleBin);
        } catch (Exception ex) {
            throw new BizException(50000, "删除失败：无法写入回收站");
        }
    }

    private User parseSnapshot(String snapshotJson) {
        try {
            if (!StringUtils.hasText(snapshotJson)) {
                return null;
            }
            return MAPPER.readValue(snapshotJson, User.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private String storeAvatar(MultipartFile file) {
        try {
            if (file.getSize() > MAX_AVATAR_SIZE_BYTES) {
                throw new BizException(40000, "头像大小不能超过 2MB");
            }
            String original = file.getOriginalFilename();
            if (!StringUtils.hasText(original) || !original.contains(".")) {
                throw new BizException(40000, "头像文件名非法");
            }
            String ext = original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT);
            if (!ALLOWED_AVATAR_EXTENSIONS.contains(ext)) {
                throw new BizException(40000, "仅支持 png/jpg/jpeg/gif/webp 格式头像");
            }
            String contentType = StringUtils.hasText(file.getContentType())
                    ? file.getContentType().toLowerCase(Locale.ROOT)
                    : "";
            if (!ALLOWED_AVATAR_CONTENT_TYPES.contains(contentType)) {
                throw new BizException(40000, "头像 MIME 类型不合法");
            }
            String filename = UUID.randomUUID() + ext;
            Path dir = Paths.get("uploads");
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return dir.resolve(filename).toString().replace('\\', '/');
        } catch (IOException e) {
            throw new BizException(50000, "头像上传失败: " + e.getMessage());
        }
    }

    private void ensureGovernanceDuty(User operator, String action) {
        if (operator == null || !StringUtils.hasText(operator.getUsername())) {
            throw new BizException(40300, "当前账号无权执行该操作");
        }
        String username = operator.getUsername().trim().toLowerCase(Locale.ROOT);
        if (GOV_ADMIN_PRIMARY.equals(username)) {
            return;
        }
        if ("approve".equalsIgnoreCase(action)) {
            if (!GOV_ADMIN_REVIEWER.equals(username)) {
                throw new BizException(40300, "仅治理复核员可执行审批动作");
            }
            return;
        }
        if ("write".equalsIgnoreCase(action)) {
            if (!GOV_ADMIN_OPS.equals(username)) {
                throw new BizException(40300, "仅治理运营专员可执行账号写操作");
            }
            return;
        }
        throw new BizException(40300, "当前账号无权执行该操作");
    }

    private User requireUserManageReadAccess() {
        User currentUser = currentUserService.requireCurrentUser();
        if (currentUserService.hasPermission("user:manage") || currentUserService.hasAnyRole("ADMIN", "ADMIN_REVIEWER")) {
            return currentUser;
        }
        throw new BizException(40300, "当前账号无权查看用户管理");
    }

    private User requireUserManageAccess() {
        User currentUser = currentUserService.requireCurrentUser();
        if (currentUserService.hasPermission("user:manage") || currentUserService.hasRole("ADMIN")) {
            return currentUser;
        }
        throw new BizException(40300, "当前账号无权管理用户");
    }

    private List<Long> resolveUserRoleIds(User user) {
        if (user == null || user.getId() == null) {
            return List.of();
        }
        List<Long> roleIds = new ArrayList<>();
        if (user.getRoleId() != null) {
            roleIds.add(user.getRoleId());
        }
        try {
            List<Long> extra = userRoleService.lambdaQuery().eq(UserRole::getUserId, user.getId()).list().stream()
                .map(UserRole::getRoleId)
                .filter(Objects::nonNull)
                .toList();
            for (Long roleId : extra) {
                if (!roleIds.contains(roleId)) {
                    roleIds.add(roleId);
                }
            }
        } catch (Exception ignored) {
            // user_role may not be available in legacy environments.
        }
        return roleIds;
    }

    private double jaccardSimilarity(Set<String> left, Set<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0d;
        }
        Set<String> union = new HashSet<>(left);
        union.addAll(right);
        if (union.isEmpty()) {
            return 0d;
        }
        Set<String> inter = new HashSet<>(left);
        inter.retainAll(right);
        return (double) inter.size() / union.size();
    }

    private int sharedOpsCount(Set<String> left, Set<String> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        Set<String> inter = new HashSet<>(left);
        inter.retainAll(right);
        return inter.size();
    }

    private double clamp01(double value) {
        if (Double.isNaN(value)) {
            return 0d;
        }
        return Math.max(0d, Math.min(1d, value));
    }
}
