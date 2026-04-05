package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.entity.AuditLog;
import com.trustai.entity.CompliancePolicy;
import com.trustai.entity.GovernanceChangeRequest;
import com.trustai.entity.Permission;
import com.trustai.entity.Role;
import com.trustai.entity.RolePermission;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import com.trustai.service.AuditLogService;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CompliancePolicyService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.GovernanceChangeRequestService;
import com.trustai.service.PermissionService;
import com.trustai.service.RoleService;
import com.trustai.service.RolePermissionService;
import com.trustai.service.SensitiveOperationGuardService;
import com.trustai.service.SodEnforcementService;
import com.trustai.service.UserService;
import com.trustai.utils.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PathVariable;

@RestController
@RequestMapping("/api/governance-change")
public class GovernanceChangeController {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<Long, Object> APPROVE_LOCKS = new ConcurrentHashMap<>();
    private static final java.util.Set<String> PROTECTED_USERNAMES = java.util.Set.of("admin", "admin_reviewer", "admin_ops");
    private static final Pattern MODERN_PERMISSION_CODE_PATTERN = Pattern.compile("^[a-z][a-z0-9_-]*(?::[a-z][a-z0-9_-]*)+$");
    private static final Pattern LEGACY_PERMISSION_CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private static final Set<String> ALLOWED_PERMISSION_TYPES = Set.of("menu", "button");
    private static final Set<String> ALLOWED_PERMISSION_STATUS = Set.of("active", "disabled");

    private final GovernanceChangeRequestService governanceChangeRequestService;
    private final CurrentUserService currentUserService;
    private final CompanyScopeService companyScopeService;
    private final SensitiveOperationGuardService sensitiveOperationGuardService;
    private final SodEnforcementService sodEnforcementService;
    private final RoleService roleService;
    private final RolePermissionService rolePermissionService;
    private final PermissionService permissionService;
    private final CompliancePolicyService compliancePolicyService;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final HttpServletRequest httpServletRequest;

    public GovernanceChangeController(GovernanceChangeRequestService governanceChangeRequestService,
                                      CurrentUserService currentUserService,
                                      CompanyScopeService companyScopeService,
                                      SensitiveOperationGuardService sensitiveOperationGuardService,
                                      SodEnforcementService sodEnforcementService,
                                      RoleService roleService,
                                      RolePermissionService rolePermissionService,
                                      PermissionService permissionService,
                                      CompliancePolicyService compliancePolicyService,
                                      UserService userService,
                                      AuditLogService auditLogService,
                                      HttpServletRequest httpServletRequest) {
        this.governanceChangeRequestService = governanceChangeRequestService;
        this.currentUserService = currentUserService;
        this.companyScopeService = companyScopeService;
        this.sensitiveOperationGuardService = sensitiveOperationGuardService;
        this.sodEnforcementService = sodEnforcementService;
        this.roleService = roleService;
        this.rolePermissionService = rolePermissionService;
        this.permissionService = permissionService;
        this.compliancePolicyService = compliancePolicyService;
        this.userService = userService;
        this.auditLogService = auditLogService;
        this.httpServletRequest = httpServletRequest;
    }

    @PostMapping("/submit")
    @PreAuthorize("@currentUserService.hasRole('ADMIN') || @currentUserService.hasPermission('govern:change:create')")
    public R<?> submit(@Valid @RequestBody SubmitReq req) {
        User requester = currentUserService.requireCurrentUser();
        enforceGovernanceDuty(requester, "submit");
        sensitiveOperationGuardService.requireConfirmedOperator(requester, req.getConfirmPassword(), "governance_change_submit", req.getModule() + ":" + req.getAction());
        GovernanceChangeRequest request = new GovernanceChangeRequest();
        request.setCompanyId(companyScopeService.requireCompanyId());
        request.setModule(normalize(req.getModule()));
        request.setAction(normalize(req.getAction()));
        request.setTargetId(req.getTargetId());
        request.setPayloadJson(enrichPayloadWithTrace(req.getPayloadJson(), requester, request.getRequesterRoleCode()));
        request.setStatus(STATUS_PENDING);
        request.setRiskLevel(resolveRiskLevel(request.getModule(), request.getAction()));
        request.setRequesterId(requester.getId());
        request.setRequesterRoleCode(resolveRoleCode(requester));
        request.setCreateTime(new Date());
        request.setUpdateTime(new Date());
        governanceChangeRequestService.save(request);
        writeAudit(requester, "governance_change_submit", "requestId=" + request.getId());
        if ("PERMISSION".equalsIgnoreCase(request.getModule())) {
            Map<String, Object> payload = parsePayloadMap(request.getPayloadJson());
            writePermissionAudit(
                requester,
                "permission_review_submit",
                request,
                buildBeforeSnapshot(request),
                buildAfterSnapshot(request, payload),
                "submitted"
            );
        }
        return R.ok(request);
    }

    @GetMapping("/page")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS') || @currentUserService.hasAnyPermission('govern:change:view','govern:change:review')")
    public R<Map<String, Object>> page(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String module) {
        Long companyId = companyScopeService.requireCompanyId();
        QueryWrapper<GovernanceChangeRequest> qw = new QueryWrapper<GovernanceChangeRequest>().eq("company_id", companyId);
        if (StringUtils.hasText(status)) {
            qw.eq("status", normalize(status).toLowerCase(Locale.ROOT));
        }
        if (StringUtils.hasText(module)) {
            qw.eq("module", normalize(module));
        }
        qw.orderByDesc("update_time");
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        Page<GovernanceChangeRequest> result = governanceChangeRequestService.page(new Page<>(safePage, safePageSize), qw);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("current", result.getCurrent());
        payload.put("pages", result.getPages());
        payload.put("total", result.getTotal());
        payload.put("list", result.getRecords());
        return R.ok(payload);
    }

    @GetMapping("/todo-page")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS') || @currentUserService.hasPermission('govern:change:review')")
    public R<Map<String, Object>> todoPage(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int pageSize,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String module,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) Long startTime,
                                           @RequestParam(required = false) Long endTime) {
        Long companyId = companyScopeService.requireCompanyId();
        User reviewer = currentUserService.requireCurrentUser();
        QueryWrapper<GovernanceChangeRequest> qw = new QueryWrapper<GovernanceChangeRequest>()
            .eq("company_id", companyId)
            .ne(reviewer != null && reviewer.getId() != null, "requester_id", reviewer.getId());

        String normalizedStatus = StringUtils.hasText(status) ? status.trim().toLowerCase(Locale.ROOT) : STATUS_PENDING;
        qw.eq("status", normalizedStatus);

        if (StringUtils.hasText(module)) {
            qw.eq("module", normalize(module));
        }
        if (startTime != null && startTime > 0L) {
            qw.ge("create_time", new Date(startTime));
        }
        if (endTime != null && endTime > 0L) {
            qw.le("create_time", new Date(endTime));
        }

        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(wrapper -> wrapper
                .like("id", kw)
                .or()
                .like("payload_json", kw)
                .or()
                .like("approve_note", kw));
        }

        qw.orderByDesc("update_time");
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        Page<GovernanceChangeRequest> result = governanceChangeRequestService.page(new Page<>(safePage, safePageSize), qw);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("current", result.getCurrent());
        payload.put("pages", result.getPages());
        payload.put("total", result.getTotal());
        payload.put("list", result.getRecords().stream().map(this::toApprovalView).toList());
        return R.ok(payload);
    }

    @GetMapping("/my-page")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS') || @currentUserService.hasPermission('govern:change:create')")
    public R<Map<String, Object>> myPage(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String module,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Long startTime,
                                         @RequestParam(required = false) Long endTime) {
        Long companyId = companyScopeService.requireCompanyId();
        User requester = currentUserService.requireCurrentUser();
        QueryWrapper<GovernanceChangeRequest> qw = new QueryWrapper<GovernanceChangeRequest>()
            .eq("company_id", companyId)
            .eq("requester_id", requester.getId());

        if (StringUtils.hasText(status)) {
            qw.eq("status", status.trim().toLowerCase(Locale.ROOT));
        }
        if (StringUtils.hasText(module)) {
            qw.eq("module", normalize(module));
        }
        if (startTime != null && startTime > 0L) {
            qw.ge("create_time", new Date(startTime));
        }
        if (endTime != null && endTime > 0L) {
            qw.le("create_time", new Date(endTime));
        }
        if (StringUtils.hasText(keyword)) {
            String kw = keyword.trim();
            qw.and(wrapper -> wrapper
                .like("id", kw)
                .or()
                .like("payload_json", kw)
                .or()
                .like("approve_note", kw));
        }

        qw.orderByDesc("update_time");
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        Page<GovernanceChangeRequest> result = governanceChangeRequestService.page(new Page<>(safePage, safePageSize), qw);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("current", result.getCurrent());
        payload.put("pages", result.getPages());
        payload.put("total", result.getTotal());
        payload.put("list", result.getRecords().stream().map(this::toApprovalView).toList());
        return R.ok(payload);
    }

    @GetMapping("/detail/{id}")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS') || @currentUserService.hasAnyPermission('govern:change:view','govern:change:review')")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        GovernanceChangeRequest request = requireScopedRequest(id);
        User current = currentUserService.requireCurrentUser();
        ensureViewableByCurrentUser(request, current);
        return R.ok(toApprovalView(request));
    }

    @GetMapping("/diff/{id}")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS') || @currentUserService.hasAnyPermission('govern:change:view','govern:change:review')")
    public R<Map<String, Object>> diff(@PathVariable Long id) {
        GovernanceChangeRequest request = requireScopedRequest(id);
        User current = currentUserService.requireCurrentUser();
        ensureViewableByCurrentUser(request, current);

        Map<String, Object> payload = parsePayloadMap(request.getPayloadJson());
        Map<String, Object> before = buildBeforeSnapshot(request);
        Map<String, Object> after = buildAfterSnapshot(request, payload);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("requestId", request.getId());
        data.put("module", request.getModule());
        data.put("action", request.getAction());
        data.put("before", before);
        data.put("after", after);
        return R.ok(data);
    }

    @PostMapping("/revoke")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS') || @currentUserService.hasPermission('govern:change:create')")
    public R<?> revoke(@Valid @RequestBody RevokeReq req) {
        User requester = currentUserService.requireCurrentUser();
        enforceGovernanceDuty(requester, "submit");
        sensitiveOperationGuardService.requireConfirmedOperator(requester, req.getConfirmPassword(), "governance_change_revoke", "requestId=" + req.getRequestId());

        GovernanceChangeRequest request = requireScopedRequest(req.getRequestId());
        if (!java.util.Objects.equals(request.getRequesterId(), requester.getId())) {
            throw new BizException(40300, "仅申请发起人可撤回");
        }
        if (!STATUS_PENDING.equalsIgnoreCase(request.getStatus())) {
            throw new BizException(40000, "仅待审批申请可撤回");
        }

        request.setStatus("revoked");
        String note = StringUtils.hasText(req.getNote()) ? req.getNote().trim() : "发起人撤回";
        request.setApproveNote(appendTraceToNote(note, requester, resolveRoleCode(requester)));
        request.setUpdateTime(new Date());
        governanceChangeRequestService.updateById(request);
        writeAudit(requester, "governance_change_revoke", "requestId=" + request.getId());
        return R.ok(request);
    }

    @PostMapping("/approve")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS') || @currentUserService.hasPermission('govern:change:review')")
    public R<?> approve(@Valid @RequestBody ApproveReq req) {
        User approver = currentUserService.requireCurrentUser();
        enforceGovernanceDuty(approver, "review");
        String approverRole = resolveRoleCode(approver);
        sensitiveOperationGuardService.requireConfirmedOperator(approver, req.getConfirmPassword(), "governance_change_approve", "requestId=" + req.getRequestId());
        if (!StringUtils.hasText(req.getNote())) {
            throw new BizException(40000, "审批意见不能为空");
        }

        Object lock = APPROVE_LOCKS.computeIfAbsent(req.getRequestId(), key -> new Object());
        synchronized (lock) {
            GovernanceChangeRequest request = governanceChangeRequestService.getOne(
                new QueryWrapper<GovernanceChangeRequest>()
                    .eq("id", req.getRequestId())
                    .eq("company_id", companyScopeService.requireCompanyId())
            );
            if (request == null) {
                throw new BizException(40400, "变更申请不存在");
            }
            if (!STATUS_PENDING.equalsIgnoreCase(request.getStatus())) {
                throw new BizException(40000, "仅待复核申请可审批");
            }
            Map<String, Object> payload = parsePayloadMap(request.getPayloadJson());
            Map<String, Object> beforeSnapshot = buildBeforeSnapshot(request);
            User requester = null;
            if (request.getRequesterId() != null) {
                requester = findRequester(request.getRequesterId());
                if (requester.getId() != null && requester.getId().equals(approver.getId())) {
                    throw new BizException(40000, "SoD冲突：发起人与审批人不能是同一账号");
                }
                sodEnforcementService.enforceReviewerSeparation(requester, approver, "PRIVILEGE_CHANGE_REVIEW");
            }

            if (Boolean.TRUE.equals(req.getApprove())) {
                if (requester == null) {
                    throw new BizException(40000, "申请人信息缺失，无法通过该变更");
                }
                applyChangeRequest(request, approver);
                request.setStatus(STATUS_APPROVED);
            } else {
                request.setStatus(STATUS_REJECTED);
            }
            request.setApproverId(approver.getId());
            request.setApproverRoleCode(approverRole);
            request.setApproveNote(appendTraceToNote(req.getNote(), approver, approverRole));
            request.setApprovedAt(new Date());
            request.setUpdateTime(new Date());
            governanceChangeRequestService.updateById(request);
            writeAudit(approver, "governance_change_approve", "requestId=" + request.getId() + ", status=" + request.getStatus());
            if ("PERMISSION".equalsIgnoreCase(request.getModule())) {
                writePermissionAudit(
                    approver,
                    Boolean.TRUE.equals(req.getApprove()) ? "permission_review_approve" : "permission_review_reject",
                    request,
                    beforeSnapshot,
                    buildAfterSnapshot(request, payload),
                    Boolean.TRUE.equals(req.getApprove()) ? "approved" : "rejected"
                );
            }
            return R.ok(request);
        }
    }

    private User findRequester(Long requesterId) {
        if (requesterId == null) {
            throw new BizException(40000, "申请人信息缺失");
        }
        User requester = userService.getById(requesterId);
        if (requester == null) {
            throw new BizException(40400, "申请人不存在");
        }
        if (!java.util.Objects.equals(requester.getCompanyId(), companyScopeService.requireCompanyId())) {
            throw new BizException(40300, "申请人不在当前公司范围");
        }
        return requester;
    }

    private void applyChangeRequest(GovernanceChangeRequest request, User approver) {
        try {
            Map<String, Object> payload = MAPPER.readValue(
                StringUtils.hasText(request.getPayloadJson()) ? request.getPayloadJson() : "{}",
                new TypeReference<Map<String, Object>>() {}
            );
            if ("ROLE".equalsIgnoreCase(request.getModule())) {
                applyRoleChange(request, payload, approver);
                return;
            }
            if ("PERMISSION".equalsIgnoreCase(request.getModule())) {
                applyPermissionChange(request, payload, approver);
                return;
            }
            if ("POLICY".equalsIgnoreCase(request.getModule())) {
                applyPolicyChange(request, payload, approver);
                return;
            }
            if ("USER".equalsIgnoreCase(request.getModule())) {
                applyUserChange(request, payload, approver);
                return;
            }
            throw new BizException(40000, "不支持的治理变更模块");
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(40000, "变更载荷非法，无法执行");
        }
    }

    private void applyRoleChange(GovernanceChangeRequest request, Map<String, Object> payload, User approver) {
        String action = request.getAction();
        if ("ADD".equalsIgnoreCase(action)) {
            Role role = new Role();
            role.setCompanyId(companyScopeService.requireCompanyId());
            role.setName(stringValue(payload.get("name")));
            role.setCode(normalize(stringValue(payload.get("code"))));
            role.setDescription(stringValue(payload.get("description")));
            role.setAllowSelfRegister(booleanValue(payload.get("allowSelfRegister")));
            role.setCreateTime(new Date());
            role.setUpdateTime(new Date());
            roleService.save(role);
            replaceRolePermissionsByCodes(role.getId(), payload.get("permissionCodes"));
            return;
        }
        Role existing = roleService.getById(request.getTargetId());
        if (existing == null || !java.util.Objects.equals(existing.getCompanyId(), companyScopeService.requireCompanyId())) {
            throw new BizException(40400, "角色不存在或不在当前公司");
        }
        if ("DELETE".equalsIgnoreCase(action)) {
            if ("ADMIN".equalsIgnoreCase(existing.getCode())) {
                throw new BizException(40000, "默认治理管理员角色不允许删除");
            }
            long bindCount = userService.lambdaQuery().eq(User::getRoleId, existing.getId()).count();
            if (bindCount > 0) {
                throw new BizException(40000, "当前角色仍被用户绑定，无法删除");
            }
            roleService.removeById(existing.getId());
            return;
        }
        if ("UPDATE".equalsIgnoreCase(action)) {
            if (StringUtils.hasText(stringValue(payload.get("name")))) {
                existing.setName(stringValue(payload.get("name")).trim());
            }
            if (StringUtils.hasText(stringValue(payload.get("code")))) {
                existing.setCode(normalize(stringValue(payload.get("code"))));
            }
            if (payload.containsKey("description")) {
                existing.setDescription(stringValue(payload.get("description")));
            }
            if (payload.containsKey("allowSelfRegister")) {
                existing.setAllowSelfRegister(booleanValue(payload.get("allowSelfRegister")));
            }
            existing.setUpdateTime(new Date());
            roleService.updateById(existing);
            if (payload.containsKey("permissionCodes")) {
                replaceRolePermissionsByCodes(existing.getId(), payload.get("permissionCodes"));
            }
            return;
        }
        throw new BizException(40000, "不支持的角色变更动作");
    }

    private void applyPermissionChange(GovernanceChangeRequest request, Map<String, Object> payload, User approver) {
        String action = request.getAction();
        Long companyId = companyScopeService.requireCompanyId();
        if ("ADD".equalsIgnoreCase(action)) {
            Permission permission = new Permission();
            permission.setCompanyId(companyId);
            permission.setName(stringValue(payload.get("name")));
            permission.setCode(normalizePermissionCode(stringValue(payload.get("code")), null));
            permission.setType(normalizePermissionType(stringValue(payload.get("type"))));
            permission.setStatus(normalizePermissionStatus(stringValue(payload.get("status"))));
            permission.setParentId(parseLong(payload.get("parentId")));
            validatePermissionParent(permission.getParentId(), null, companyId);
            permission.setCreateTime(new Date());
            permission.setUpdateTime(new Date());
            permissionService.save(permission);
            writePermissionAudit(
                approver,
                "permission_add",
                request,
                Map.of(),
                permissionSnapshot(permission),
                "success"
            );
            return;
        }
        Permission existing = permissionService.getById(request.getTargetId());
        if (existing == null || !java.util.Objects.equals(existing.getCompanyId(), companyId)) {
            throw new BizException(40400, "权限不存在或不在当前公司");
        }
        Map<String, Object> beforeSnapshot = permissionSnapshot(existing);
        if ("DELETE".equalsIgnoreCase(action)) {
            long childCount = permissionService.lambdaQuery()
                .eq(Permission::getCompanyId, companyId)
                .eq(Permission::getParentId, existing.getId())
                .count();
            if (childCount > 0) {
                throw new BizException(40000, "当前权限存在子权限依赖，无法删除");
            }
            permissionService.removeById(existing.getId());
            Map<String, Object> afterSnapshot = new LinkedHashMap<>();
            afterSnapshot.put("deleted", true);
            writePermissionAudit(approver, "permission_delete", request, beforeSnapshot, afterSnapshot, "success");
            return;
        }
        if ("UPDATE".equalsIgnoreCase(action)) {
            String previousStatus = String.valueOf(existing.getStatus() == null ? "" : existing.getStatus()).trim().toLowerCase(Locale.ROOT);
            Long payloadId = parseLong(payload.get("id"));
            if (payloadId != null && !java.util.Objects.equals(payloadId, existing.getId())) {
                throw new BizException(40000, "权限ID不可修改");
            }
            if (StringUtils.hasText(stringValue(payload.get("name")))) {
                existing.setName(stringValue(payload.get("name")).trim());
            }
            if (StringUtils.hasText(stringValue(payload.get("code")))) {
                existing.setCode(normalizePermissionCode(stringValue(payload.get("code")), existing.getCode()));
            }
            if (StringUtils.hasText(stringValue(payload.get("type")))) {
                existing.setType(normalizePermissionType(stringValue(payload.get("type"))));
            }
            if (payload.containsKey("status")) {
                existing.setStatus(normalizePermissionStatus(stringValue(payload.get("status"))));
            }
            if (payload.containsKey("parentId")) {
                Long parentId = parseLong(payload.get("parentId"));
                validatePermissionParent(parentId, existing.getId(), companyId);
                existing.setParentId(parentId);
            }
            existing.setUpdateTime(new Date());
            permissionService.updateById(existing);
            String nextStatus = String.valueOf(existing.getStatus() == null ? "" : existing.getStatus()).trim().toLowerCase(Locale.ROOT);
            String operation = "permission_update";
            if (!previousStatus.equals(nextStatus)) {
                if ("active".equals(nextStatus)) {
                    operation = "permission_enable";
                } else if ("disabled".equals(nextStatus)) {
                    operation = "permission_disable";
                }
            }
            writePermissionAudit(approver, operation, request, beforeSnapshot, permissionSnapshot(existing), "success");
            return;
        }
        throw new BizException(40000, "不支持的权限变更动作");
    }

    private void validatePermissionParent(Long parentId, Long selfId, Long companyId) {
        if (parentId == null) {
            return;
        }
        if (selfId != null && java.util.Objects.equals(selfId, parentId)) {
            throw new BizException(40000, "父级权限不能是自身");
        }
        Permission parent = permissionService.getById(parentId);
        if (parent == null || !java.util.Objects.equals(parent.getCompanyId(), companyId)) {
            throw new BizException(40000, "父级权限不存在或不在当前公司");
        }
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

    private void applyPolicyChange(GovernanceChangeRequest request, Map<String, Object> payload, User approver) {
        String action = request.getAction();
        Long companyId = companyScopeService.requireCompanyId();
        if ("ADD".equalsIgnoreCase(action)) {
            CompliancePolicy policy = new CompliancePolicy();
            policy.setCompanyId(companyId);
            policy.setName(stringValue(payload.get("name")));
            policy.setPolicyType(normalizePolicyType(stringValue(payload.get("policyType"))));
            policy.setPriority(normalizePriority(parseInteger(payload.get("priority"))));
            policy.setRuleContent(stringValue(payload.get("ruleContent")));
            policy.setScope(stringValue(payload.get("scope")));
            policy.setScopeDepartments(stringValue(payload.get("scopeDepartments")));
            policy.setScopeUserGroups(stringValue(payload.get("scopeUserGroups")));
            policy.setScopeDataTypes(stringValue(payload.get("scopeDataTypes")));
            policy.setStatus(payload.containsKey("status") ? normalizePolicyStatus(payload.get("status")) : 1);
            policy.setLastModifier(resolveActorName(approver));
            policy.setLastModifiedAt(new Date());
            policy.setCreateTime(new Date());
            policy.setUpdateTime(new Date());
            compliancePolicyService.save(policy);
            return;
        }
        CompliancePolicy existing = compliancePolicyService.getOne(
            companyScopeService.withCompany(new QueryWrapper<CompliancePolicy>()).eq("id", request.getTargetId())
        );
        if (existing == null) {
            throw new BizException(40400, "策略不存在或不在当前公司");
        }
        if ("DELETE".equalsIgnoreCase(action)) {
            compliancePolicyService.removeById(existing.getId());
            return;
        }
        if ("UPDATE".equalsIgnoreCase(action)) {
            if (payload.containsKey("name")) {
                existing.setName(stringValue(payload.get("name")));
            }
            if (payload.containsKey("ruleContent")) {
                existing.setRuleContent(stringValue(payload.get("ruleContent")));
            }
            if (payload.containsKey("scope")) {
                existing.setScope(stringValue(payload.get("scope")));
            }
            if (payload.containsKey("policyType")) {
                existing.setPolicyType(normalizePolicyType(stringValue(payload.get("policyType"))));
            }
            if (payload.containsKey("priority")) {
                existing.setPriority(normalizePriority(parseInteger(payload.get("priority"))));
            }
            if (payload.containsKey("scopeDepartments")) {
                existing.setScopeDepartments(stringValue(payload.get("scopeDepartments")));
            }
            if (payload.containsKey("scopeUserGroups")) {
                existing.setScopeUserGroups(stringValue(payload.get("scopeUserGroups")));
            }
            if (payload.containsKey("scopeDataTypes")) {
                existing.setScopeDataTypes(stringValue(payload.get("scopeDataTypes")));
            }
            if (payload.containsKey("status") || payload.containsKey("enabled")) {
                Object statusPayload = payload.containsKey("status") ? payload.get("status") : payload.get("enabled");
                existing.setStatus(normalizePolicyStatus(statusPayload));
            }
            existing.setLastModifier(resolveActorName(approver));
            existing.setLastModifiedAt(new Date());
            existing.setUpdateTime(new Date());
            compliancePolicyService.updateById(existing);
            return;
        }
        throw new BizException(40000, "不支持的策略变更动作");
    }

    private void applyUserChange(GovernanceChangeRequest request, Map<String, Object> payload, User approver) {
        String action = request.getAction();
        User existing = userService.getById(request.getTargetId());
        if (existing == null || !java.util.Objects.equals(existing.getCompanyId(), companyScopeService.requireCompanyId())) {
            throw new BizException(40400, "用户不存在或不在当前公司");
        }
        String username = String.valueOf(existing.getUsername() == null ? "" : existing.getUsername()).trim().toLowerCase(Locale.ROOT);
        if (PROTECTED_USERNAMES.contains(username)) {
            throw new BizException(40000, "系统治理内置账号不允许通过该流程改动");
        }
        if ("DELETE".equalsIgnoreCase(action)) {
            if (java.util.Objects.equals(existing.getId(), approver.getId())) {
                throw new BizException(40000, "不允许删除当前登录账号");
            }
            userService.removeById(existing.getId());
            return;
        }
        if ("UPDATE".equalsIgnoreCase(action)) {
            if (payload.containsKey("realName")) {
                existing.setRealName(stringValue(payload.get("realName")));
            }
            if (payload.containsKey("department")) {
                existing.setDepartment(stringValue(payload.get("department")));
            }
            if (payload.containsKey("accountStatus")) {
                existing.setAccountStatus(stringValue(payload.get("accountStatus")));
            }
            if (payload.containsKey("roleId")) {
                Long roleId = parseLong(payload.get("roleId"));
                if (roleId != null) {
                    Role role = roleService.getById(roleId);
                    if (role == null || !java.util.Objects.equals(role.getCompanyId(), existing.getCompanyId())) {
                        throw new BizException(40000, "目标角色不存在或不在当前公司");
                    }
                    existing.setRoleId(roleId);
                }
            }
            existing.setUpdateTime(new Date());
            userService.updateById(existing);
            return;
        }
        throw new BizException(40000, "不支持的用户变更动作");
    }

    private String resolveRiskLevel(String module, String action) {
        if ("DELETE".equalsIgnoreCase(action)) {
            return "CRITICAL";
        }
        if ("ROLE".equalsIgnoreCase(module)
            || "PERMISSION".equalsIgnoreCase(module)
            || "POLICY".equalsIgnoreCase(module)
            || "USER".equalsIgnoreCase(module)) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private GovernanceChangeRequest requireScopedRequest(Long requestId) {
        GovernanceChangeRequest request = governanceChangeRequestService.getOne(
            new QueryWrapper<GovernanceChangeRequest>()
                .eq("id", requestId)
                .eq("company_id", companyScopeService.requireCompanyId())
        );
        if (request == null) {
            throw new BizException(40400, "变更申请不存在");
        }
        return request;
    }

    private void ensureViewableByCurrentUser(GovernanceChangeRequest request, User currentUser) {
        if (request == null || currentUser == null) {
            throw new BizException(40300, "无权限");
        }
        boolean canReview = currentUserService.hasPermission("govern:change:review") || currentUserService.hasAnyRole("ADMIN", "SECOPS");
        if (canReview) {
            return;
        }
        if (java.util.Objects.equals(request.getRequesterId(), currentUser.getId())) {
            return;
        }
        throw new BizException(40300, "仅可查看本人发起的申请");
    }

    private Map<String, Object> toApprovalView(GovernanceChangeRequest request) {
        Map<String, Object> payload = parsePayloadMap(request == null ? null : request.getPayloadJson());
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", request == null ? null : request.getId());
        view.put("companyId", request == null ? null : request.getCompanyId());
        view.put("module", request == null ? null : request.getModule());
        view.put("action", request == null ? null : request.getAction());
        view.put("status", request == null ? null : request.getStatus());
        view.put("riskLevel", request == null ? null : request.getRiskLevel());
        view.put("requesterId", request == null ? null : request.getRequesterId());
        view.put("requesterRoleCode", request == null ? null : request.getRequesterRoleCode());
        view.put("approverId", request == null ? null : request.getApproverId());
        view.put("approverRoleCode", request == null ? null : request.getApproverRoleCode());
        view.put("approveNote", request == null ? null : request.getApproveNote());
        view.put("approvedAt", request == null ? null : request.getApprovedAt());
        view.put("createTime", request == null ? null : request.getCreateTime());
        view.put("updateTime", request == null ? null : request.getUpdateTime());
        view.put("targetId", request == null ? null : request.getTargetId());
        view.put("payloadJson", request == null ? null : request.getPayloadJson());
        view.put("title", resolveTitle(request, payload));
        view.put("requestType", resolveRequestType(request));
        view.put("reason", stringValue(payload.get("reason")));
        view.put("impact", stringValue(payload.get("impact")));
        view.put("payload", payload);
        return view;
    }

    private String resolveRequestType(GovernanceChangeRequest request) {
        if (request == null) {
            return "";
        }
        String module = StringUtils.hasText(request.getModule()) ? request.getModule().trim().toUpperCase(Locale.ROOT) : "UNKNOWN";
        String action = StringUtils.hasText(request.getAction()) ? request.getAction().trim().toUpperCase(Locale.ROOT) : "UNKNOWN";
        return module + "_" + action;
    }

    private String resolveTitle(GovernanceChangeRequest request, Map<String, Object> payload) {
        if (payload != null && StringUtils.hasText(stringValue(payload.get("title")))) {
            return stringValue(payload.get("title")).trim();
        }
        String name = payload == null ? "" : stringValue(payload.get("name"));
        if (!StringUtils.hasText(name)) {
            name = payload == null ? "" : stringValue(payload.get("code"));
        }
        String module = request == null ? "变更" : stringValue(request.getModule());
        String action = request == null ? "提交" : stringValue(request.getAction());
        if (StringUtils.hasText(name)) {
            return module + " " + action + " - " + name;
        }
        return module + " " + action + " 申请";
    }

    private Map<String, Object> parsePayloadMap(String payloadJson) {
        try {
            return MAPPER.readValue(StringUtils.hasText(payloadJson) ? payloadJson : "{}", new TypeReference<Map<String, Object>>() {});
        } catch (Exception ignored) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("rawPayload", payloadJson == null ? "" : payloadJson);
            return fallback;
        }
    }

    private Map<String, Object> buildBeforeSnapshot(GovernanceChangeRequest request) {
        Map<String, Object> before = new LinkedHashMap<>();
        if (request == null || request.getTargetId() == null) {
            return before;
        }
        String module = stringValue(request.getModule()).toUpperCase(Locale.ROOT);
        if ("ROLE".equals(module)) {
            Role role = roleService.getById(request.getTargetId());
            if (role != null) {
                before.put("id", role.getId());
                before.put("name", role.getName());
                before.put("code", role.getCode());
                before.put("description", role.getDescription());
                before.put("allowSelfRegister", role.getAllowSelfRegister());
            }
            return before;
        }
        if ("PERMISSION".equals(module)) {
            Permission permission = permissionService.getById(request.getTargetId());
            if (permission != null) {
                before.put("id", permission.getId());
                before.put("name", permission.getName());
                before.put("code", permission.getCode());
                before.put("type", permission.getType());
                before.put("status", permission.getStatus());
                before.put("parentId", permission.getParentId());
            }
            return before;
        }
        if ("POLICY".equals(module)) {
            CompliancePolicy policy = compliancePolicyService.getById(request.getTargetId());
            if (policy != null) {
                before.put("id", policy.getId());
                before.put("name", policy.getName());
                before.put("policyType", policy.getPolicyType());
                before.put("priority", policy.getPriority());
                before.put("scope", policy.getScope());
                before.put("status", policy.getStatus());
                before.put("ruleContent", policy.getRuleContent());
            }
            return before;
        }
        if ("USER".equals(module)) {
            User user = userService.getById(request.getTargetId());
            if (user != null) {
                before.put("id", user.getId());
                before.put("username", user.getUsername());
                before.put("realName", user.getRealName());
                before.put("department", user.getDepartment());
                before.put("accountStatus", user.getAccountStatus());
                before.put("roleId", user.getRoleId());
            }
        }
        return before;
    }

    private Map<String, Object> buildAfterSnapshot(GovernanceChangeRequest request, Map<String, Object> payload) {
        Map<String, Object> after = new LinkedHashMap<>();
        if (request == null) {
            return after;
        }
        String action = stringValue(request.getAction()).toUpperCase(Locale.ROOT);
        if ("DELETE".equals(action)) {
            after.put("deleted", true);
            return after;
        }
        if (payload != null) {
            after.putAll(payload);
            after.remove("trace");
        }
        return after;
    }

    private String resolveRoleCode(User user) {
        Role role = currentUserService.getCurrentRole(user);
        return role == null ? "" : normalize(role.getCode());
    }

    private void enforceGovernanceDuty(User user, String action) {
        if ("submit".equalsIgnoreCase(action)) {
            boolean canSubmit = currentUserService.hasAnyRole("ADMIN", "ADMIN_OPS")
                || currentUserService.hasPermission("govern:change:create");
            if (!canSubmit) {
                throw new BizException(40300, "仅治理运维账号可发起治理变更");
            }
            return;
        }
        if ("review".equalsIgnoreCase(action)) {
            boolean canReview = currentUserService.hasAnyRole("ADMIN", "ADMIN_REVIEWER")
                || currentUserService.hasPermission("govern:change:review");
            if (!canReview) {
                throw new BizException(40300, "仅复核角色可审批/驳回治理变更");
            }
            return;
        }
        throw new BizException(40300, "治理职责校验失败");
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean booleanValue(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        String text = String.valueOf(value == null ? "" : value).trim().toLowerCase(Locale.ROOT);
        return "true".equals(text) || "1".equals(text) || "yes".equals(text) || "y".equals(text);
    }

    private Integer normalizePolicyStatus(Object payloadStatus) {
        if (payloadStatus instanceof Boolean bool) {
            return bool ? 1 : 0;
        }
        String normalized = String.valueOf(payloadStatus == null ? "" : payloadStatus).trim().toUpperCase(Locale.ROOT);
        if ("0".equals(normalized) || "FALSE".equals(normalized) || "INACTIVE".equals(normalized) || "DISABLED".equals(normalized)) {
            return 0;
        }
        if ("2".equals(normalized) || "DRAFT".equals(normalized)) {
            return 2;
        }
        return 1;
    }

    private String normalizePolicyType(String policyType) {
        String normalized = policyType == null ? "" : policyType.trim().toUpperCase(Locale.ROOT);
        if (!StringUtils.hasText(normalized)) {
            return "MASKING";
        }
        if ("ACCESS_CONTROL".equals(normalized) || "EXPORT_LIMIT".equals(normalized)) {
            return normalized;
        }
        return "MASKING";
    }

    private Integer normalizePriority(Integer priority) {
        int value = priority == null ? 50 : priority;
        value = Math.max(1, value);
        value = Math.min(999, value);
        return value;
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private String resolveActorName(User user) {
        if (user == null || !StringUtils.hasText(user.getUsername())) {
            return "system";
        }
        return user.getUsername().trim();
    }

    private Long parseLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception ignored) {
            return null;
        }
    }

    private void replaceRolePermissionsByCodes(Long roleId, Object payloadCodes) {
        rolePermissionService.remove(new QueryWrapper<RolePermission>().eq("role_id", roleId));
        if (!(payloadCodes instanceof java.util.List<?> codeList)) {
            return;
        }
        java.util.Set<String> normalizedCodes = codeList.stream()
            .filter(item -> item != null)
            .map(item -> String.valueOf(item).trim().toLowerCase(Locale.ROOT))
            .filter(StringUtils::hasText)
            .collect(java.util.stream.Collectors.toSet());
        if (normalizedCodes.isEmpty()) {
            return;
        }
        Long companyId = companyScopeService.requireCompanyId();
        java.util.List<Permission> permissions = permissionService.lambdaQuery()
            .eq(Permission::getCompanyId, companyId)
            .list()
            .stream()
            .filter(permission -> StringUtils.hasText(permission.getCode())
                && normalizedCodes.contains(permission.getCode().trim().toLowerCase(Locale.ROOT)))
            .toList();
        if (permissions.isEmpty()) {
            return;
        }
        java.util.List<RolePermission> mappings = new java.util.ArrayList<>();
        for (Permission permission : permissions) {
            RolePermission mapping = new RolePermission();
            mapping.setRoleId(roleId);
            mapping.setPermissionId(permission.getId());
            mappings.add(mapping);
        }
        rolePermissionService.saveBatch(mappings);
    }

    private String enrichPayloadWithTrace(String rawPayload, User operator, String roleCode) {
        Map<String, Object> payload;
        try {
            payload = MAPPER.readValue(StringUtils.hasText(rawPayload) ? rawPayload : "{}", new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            payload = new LinkedHashMap<>();
            payload.put("rawPayload", rawPayload == null ? "" : rawPayload);
        }
        payload.put("trace", buildTraceSnapshot(operator, roleCode));
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception ex) {
            return rawPayload == null ? "{}" : rawPayload;
        }
    }

    private String appendTraceToNote(String note, User operator, String roleCode) {
        String base = StringUtils.hasText(note) ? note.trim() : "";
        Map<String, Object> trace = buildTraceSnapshot(operator, roleCode);
        return base + String.format(" [TRACE username=%s userId=%s role=%s department=%s position=%s companyId=%s device=%s]",
            trace.getOrDefault("username", "-"),
            trace.getOrDefault("userId", "-"),
            trace.getOrDefault("role", "-"),
            trace.getOrDefault("department", "-"),
            trace.getOrDefault("position", "-"),
            trace.getOrDefault("companyId", "-"),
            trace.getOrDefault("device", "-")
        );
    }

    private Map<String, Object> buildTraceSnapshot(User user, String roleCode) {
        Map<String, Object> trace = new LinkedHashMap<>();
        if (user == null) {
            trace.put("username", "-");
            trace.put("userId", "-");
            trace.put("role", roleCode == null ? "-" : roleCode);
            trace.put("department", "-");
            trace.put("position", "-");
            trace.put("companyId", "-");
            trace.put("device", "-");
            return trace;
        }
        trace.put("username", user.getUsername() == null ? "-" : user.getUsername());
        trace.put("userId", user.getId() == null ? "-" : user.getId());
        trace.put("role", roleCode == null ? "-" : roleCode);
        trace.put("department", user.getDepartment() == null ? "-" : user.getDepartment());
        trace.put("position", user.getJobTitle() == null ? "-" : user.getJobTitle());
        trace.put("companyId", user.getCompanyId() == null ? "-" : user.getCompanyId());
        trace.put("device", user.getDeviceId() == null ? "-" : user.getDeviceId());
        return trace;
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

    private void writePermissionAudit(User operator,
                                      String operation,
                                      GovernanceChangeRequest request,
                                      Map<String, Object> before,
                                      Map<String, Object> after,
                                      String result) {
        try {
            Map<String, Object> payload = parsePayloadMap(request == null ? null : request.getPayloadJson());
            Long permissionId = request == null ? null : request.getTargetId();
            if (permissionId == null) {
                permissionId = parseLong(payload.get("id"));
            }
            if (permissionId == null) {
                permissionId = parseLong(payload.get("permissionId"));
            }
            String permissionName = stringValue(payload.get("name"));
            if (!StringUtils.hasText(permissionName) && permissionId != null) {
                Permission permission = permissionService.getById(permissionId);
                if (permission != null) {
                    permissionName = permission.getName();
                }
            }

            AuditLog log = new AuditLog();
            log.setUserId(operator.getId());
            log.setOperation(operation);
            log.setOperationTime(new Date());
            log.setIp(resolveRequestIp());
            log.setPermissionId(permissionId);
            log.setPermissionName(StringUtils.hasText(permissionName) ? permissionName.trim() : null);
            log.setInputOverview(safeJson(before));
            log.setOutputOverview(safeJson(after));
            log.setResult(StringUtils.hasText(result) ? result : "success");
            log.setRiskLevel("HIGH");
            log.setCreateTime(new Date());
            auditLogService.saveAudit(log);
        } catch (Exception ignored) {
            // Non-blocking audit write.
        }
    }

    private String safeJson(Map<String, Object> data) {
        try {
            return MAPPER.writeValueAsString(data == null ? Map.of() : data);
        } catch (Exception ex) {
            return String.valueOf(data == null ? Map.of() : data);
        }
    }

    private String resolveRequestIp() {
        if (httpServletRequest == null) {
            return null;
        }
        String forwarded = httpServletRequest.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = httpServletRequest.getHeader("X-Real-IP");
        if (StringUtils.hasText(realIp)) {
            return realIp.trim();
        }
        return httpServletRequest.getRemoteAddr();
    }

    private void writeAudit(User user, String operation, String detail) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(user.getId());
            log.setOperation(operation);
            log.setOperationTime(new Date());
            log.setIp(resolveRequestIp());
            log.setInputOverview(detail);
            log.setOutputOverview("governance_change");
            log.setResult("success");
            log.setRiskLevel("HIGH");
            log.setCreateTime(new Date());
            auditLogService.saveAudit(log);
        } catch (Exception ignored) {
            // Non-blocking audit write.
        }
    }

    public static class SubmitReq {
        @NotBlank(message = "模块不能为空")
        private String module;
        @NotBlank(message = "动作不能为空")
        private String action;
        private Long targetId;
        @NotBlank(message = "变更载荷不能为空")
        private String payloadJson;
        @NotBlank(message = "敏感操作需要二次密码")
        private String confirmPassword;

        public String getModule() { return module; }
        public void setModule(String module) { this.module = module; }
        public String getAction() { return action; }
        public void setAction(String action) { this.action = action; }
        public Long getTargetId() { return targetId; }
        public void setTargetId(Long targetId) { this.targetId = targetId; }
        public String getPayloadJson() { return payloadJson; }
        public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    }

    public static class ApproveReq {
        @NotNull(message = "申请ID不能为空")
        private Long requestId;
        @NotNull(message = "审批结果不能为空")
        private Boolean approve;
        private String note;
        @NotBlank(message = "敏感操作需要二次密码")
        private String confirmPassword;

        public Long getRequestId() { return requestId; }
        public void setRequestId(Long requestId) { this.requestId = requestId; }
        public Boolean getApprove() { return approve; }
        public void setApprove(Boolean approve) { this.approve = approve; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    }

    public static class RevokeReq {
        @NotNull(message = "申请ID不能为空")
        private Long requestId;
        private String note;
        @NotBlank(message = "敏感操作需要二次密码")
        private String confirmPassword;

        public Long getRequestId() { return requestId; }
        public void setRequestId(Long requestId) { this.requestId = requestId; }
        public String getNote() { return note; }
        public void setNote(String note) { this.note = note; }
        public String getConfirmPassword() { return confirmPassword; }
        public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
    }
}
