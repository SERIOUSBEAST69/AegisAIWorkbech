package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.entity.AuditLog;
import com.trustai.entity.GovernanceChangeRequest;
import com.trustai.entity.Permission;
import com.trustai.entity.Role;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import com.trustai.service.AuditLogService;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.GovernanceChangeRequestService;
import com.trustai.service.PermissionService;
import com.trustai.service.RoleService;
import com.trustai.service.SensitiveOperationGuardService;
import com.trustai.service.SodEnforcementService;
import com.trustai.service.UserService;
import com.trustai.utils.R;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/governance-change")
public class GovernanceChangeController {

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_APPROVED = "approved";
    private static final String STATUS_REJECTED = "rejected";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<Long, Object> APPROVE_LOCKS = new ConcurrentHashMap<>();

    private final GovernanceChangeRequestService governanceChangeRequestService;
    private final CurrentUserService currentUserService;
    private final CompanyScopeService companyScopeService;
    private final SensitiveOperationGuardService sensitiveOperationGuardService;
    private final SodEnforcementService sodEnforcementService;
    private final RoleService roleService;
    private final PermissionService permissionService;
    private final UserService userService;
    private final AuditLogService auditLogService;

    public GovernanceChangeController(GovernanceChangeRequestService governanceChangeRequestService,
                                      CurrentUserService currentUserService,
                                      CompanyScopeService companyScopeService,
                                      SensitiveOperationGuardService sensitiveOperationGuardService,
                                      SodEnforcementService sodEnforcementService,
                                      RoleService roleService,
                                      PermissionService permissionService,
                                      UserService userService,
                                      AuditLogService auditLogService) {
        this.governanceChangeRequestService = governanceChangeRequestService;
        this.currentUserService = currentUserService;
        this.companyScopeService = companyScopeService;
        this.sensitiveOperationGuardService = sensitiveOperationGuardService;
        this.sodEnforcementService = sodEnforcementService;
        this.roleService = roleService;
        this.permissionService = permissionService;
        this.userService = userService;
        this.auditLogService = auditLogService;
    }

    @PostMapping("/submit")
    @PreAuthorize("@currentUserService.hasRole('ADMIN') || @currentUserService.hasPermission('govern:change:create')")
    public R<?> submit(@Valid @RequestBody SubmitReq req) {
        User requester = currentUserService.requireCurrentUser();
        if (!(currentUserService.hasRole("ADMIN") || currentUserService.hasPermission("govern:change:create"))) {
            throw new BizException(40300, "当前身份无权发起治理变更");
        }
        sensitiveOperationGuardService.requireConfirmedOperator(requester, req.getConfirmPassword(), "governance_change_submit", req.getModule() + ":" + req.getAction());
        GovernanceChangeRequest request = new GovernanceChangeRequest();
        request.setCompanyId(companyScopeService.requireCompanyId());
        request.setModule(normalize(req.getModule()));
        request.setAction(normalize(req.getAction()));
        request.setTargetId(req.getTargetId());
        request.setPayloadJson(req.getPayloadJson());
        request.setStatus(STATUS_PENDING);
        request.setRiskLevel(resolveRiskLevel(request.getModule(), request.getAction()));
        request.setRequesterId(requester.getId());
        request.setRequesterRoleCode(resolveRoleCode(requester));
        request.setCreateTime(new Date());
        request.setUpdateTime(new Date());
        governanceChangeRequestService.save(request);
        writeAudit(requester, "governance_change_submit", "requestId=" + request.getId());
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
        Page<GovernanceChangeRequest> result = governanceChangeRequestService.page(new Page<>(Math.max(1, page), Math.max(1, pageSize)), qw);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("current", result.getCurrent());
        payload.put("pages", result.getPages());
        payload.put("total", result.getTotal());
        payload.put("list", result.getRecords());
        return R.ok(payload);
    }

    @PostMapping("/approve")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS') || @currentUserService.hasPermission('govern:change:review')")
    public R<?> approve(@Valid @RequestBody ApproveReq req) {
        User approver = currentUserService.requireCurrentUser();
        String approverRole = resolveRoleCode(approver);
        if (!("ADMIN".equalsIgnoreCase(approverRole) || "SECOPS".equalsIgnoreCase(approverRole)
            || currentUserService.hasPermission("govern:change:review"))) {
            throw new BizException(40300, "当前身份无权复核权限变更");
        }
        sensitiveOperationGuardService.requireConfirmedOperator(approver, req.getConfirmPassword(), "governance_change_approve", "requestId=" + req.getRequestId());

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
            request.setApproveNote(StringUtils.hasText(req.getNote()) ? req.getNote().trim() : "");
            request.setApprovedAt(new Date());
            request.setUpdateTime(new Date());
            governanceChangeRequestService.updateById(request);
            writeAudit(approver, "governance_change_approve", "requestId=" + request.getId() + ", status=" + request.getStatus());
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
            role.setCreateTime(new Date());
            role.setUpdateTime(new Date());
            roleService.save(role);
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
            existing.setDescription(stringValue(payload.get("description")));
            existing.setUpdateTime(new Date());
            roleService.updateById(existing);
            return;
        }
        throw new BizException(40000, "不支持的角色变更动作");
    }

    private void applyPermissionChange(GovernanceChangeRequest request, Map<String, Object> payload, User approver) {
        String action = request.getAction();
        if ("ADD".equalsIgnoreCase(action)) {
            Permission permission = new Permission();
            permission.setCompanyId(companyScopeService.requireCompanyId());
            permission.setName(stringValue(payload.get("name")));
            permission.setCode(normalize(stringValue(payload.get("code"))));
            permission.setType(stringValue(payload.get("type")));
            permission.setCreateTime(new Date());
            permission.setUpdateTime(new Date());
            permissionService.save(permission);
            return;
        }
        Permission existing = permissionService.getById(request.getTargetId());
        if (existing == null || !java.util.Objects.equals(existing.getCompanyId(), companyScopeService.requireCompanyId())) {
            throw new BizException(40400, "权限不存在或不在当前公司");
        }
        if ("DELETE".equalsIgnoreCase(action)) {
            permissionService.removeById(existing.getId());
            return;
        }
        if ("UPDATE".equalsIgnoreCase(action)) {
            if (StringUtils.hasText(stringValue(payload.get("name")))) {
                existing.setName(stringValue(payload.get("name")).trim());
            }
            if (StringUtils.hasText(stringValue(payload.get("code")))) {
                existing.setCode(normalize(stringValue(payload.get("code"))));
            }
            if (StringUtils.hasText(stringValue(payload.get("type")))) {
                existing.setType(stringValue(payload.get("type")).trim());
            }
            existing.setUpdateTime(new Date());
            permissionService.updateById(existing);
            return;
        }
        throw new BizException(40000, "不支持的权限变更动作");
    }

    private String resolveRiskLevel(String module, String action) {
        if ("DELETE".equalsIgnoreCase(action)) {
            return "CRITICAL";
        }
        if ("ROLE".equalsIgnoreCase(module) || "PERMISSION".equalsIgnoreCase(module)) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private String resolveRoleCode(User user) {
        Role role = currentUserService.getCurrentRole(user);
        return role == null ? "" : normalize(role.getCode());
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private void writeAudit(User user, String operation, String detail) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(user.getId());
            log.setOperation(operation);
            log.setOperationTime(new Date());
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
}
