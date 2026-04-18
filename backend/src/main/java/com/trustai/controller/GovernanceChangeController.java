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
import com.trustai.service.PrivacyShieldConfigService;
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
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
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
    private final PrivacyShieldConfigService privacyShieldConfigService;
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
                                      PrivacyShieldConfigService privacyShieldConfigService,
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
        this.privacyShieldConfigService = privacyShieldConfigService;
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
        Long companyId = companyScopeService.requireCompanyId();
        String requesterRoleCode = resolveRoleCode(requester);
        Long normalizedTargetId = normalizeTargetIdForSubmit(req, companyId);
        boolean aiWhitelistChange = isAiWhitelistModule(req.getModule());
        if (aiWhitelistChange && !"ADMIN".equalsIgnoreCase(requesterRoleCode)) {
            throw new BizException(40300, "公司AI白名单仅支持治理管理员发起");
        }
        User approver = aiWhitelistChange
            ? resolveAiWhitelistReviewer(requester, companyId)
            : resolveDefaultApprover(requester, companyId);
        if (aiWhitelistChange && approver == null) {
            throw new BizException(40000, "未找到可用审核员，请先配置 ADMIN_REVIEWER 账号");
        }
        GovernanceChangeRequest request = new GovernanceChangeRequest();
        request.setCompanyId(companyId);
        request.setModule(normalize(req.getModule()));
        request.setAction(normalize(req.getAction()));
        request.setTargetId(normalizedTargetId);
        request.setPayloadJson(enrichPayloadWithTrace(req.getPayloadJson(), requester, requesterRoleCode));
        request.setStatus(STATUS_PENDING);
        request.setRiskLevel(resolveRiskLevel(request.getModule(), request.getAction()));
        request.setRequesterId(requester.getId());
        request.setRequesterRoleCode(requesterRoleCode);
        if (approver != null) {
            request.setApproverId(approver.getId());
            request.setApproverRoleCode(resolveRoleCode(approver));
        }
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

    private Long normalizeTargetIdForSubmit(SubmitReq req, Long companyId) {
        String module = normalize(req == null ? null : req.getModule());
        String action = normalize(req == null ? null : req.getAction());
        Long targetId = req == null ? null : req.getTargetId();

        if ("AI_WHITELIST".equalsIgnoreCase(module)) {
            return companyId;
        }

        if ("ROLE".equalsIgnoreCase(module) && ("UPDATE".equalsIgnoreCase(action) || "DELETE".equalsIgnoreCase(action))) {
            if (targetId == null || targetId <= 0L) {
                Map<String, Object> payload = parsePayloadMap(req == null ? null : req.getPayloadJson());
                Long payloadRoleId = parseLong(payload.get("roleId"));
                if (payloadRoleId != null && payloadRoleId > 0L) {
                    targetId = payloadRoleId;
                } else {
                    String payloadCode = stringValue(payload.get("code"));
                    String payloadName = stringValue(payload.get("name"));
                    Role compatible = findCompatibleRoleInCompany(companyId, payloadCode, payloadName);
                    if (compatible != null) {
                        targetId = compatible.getId();
                    }
                }
            }
            if (targetId == null || targetId <= 0L) {
                throw new BizException(40000, "角色变更缺少目标ID");
            }
            Role resolvedRole = requireCompanyRole(targetId, companyId, "角色不存在或不在当前公司");
            return resolvedRole.getId();
        }

        if ("USER".equalsIgnoreCase(module) && ("UPDATE".equalsIgnoreCase(action) || "DELETE".equalsIgnoreCase(action))) {
            Map<String, Object> payload = parsePayloadMap(req == null ? null : req.getPayloadJson());
            if (targetId == null || targetId <= 0L) {
                targetId = parseLong(payload.get("id"));
            }
            if (targetId == null || targetId <= 0L) {
                targetId = parseLong(payload.get("userId"));
            }
            if (targetId == null || targetId <= 0L) {
                targetId = parseLong(payload.get("targetId"));
            }
            if (targetId != null && targetId > 0L) {
                User resolvedUser = requireCompanyUser(targetId, companyId, "用户不存在或不在当前公司");
                return resolvedUser.getId();
            }
            String username = stringValue(payload.get("username"));
            User compatible = findCompatibleUserInCompany(companyId, username, stringValue(payload.get("realName")));
            if (compatible == null) {
                throw new BizException(40000, "用户变更缺少目标ID");
            }
            return compatible.getId();
        }
        return targetId;
    }

    @GetMapping("/page")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','BUSINESS_OWNER') || @currentUserService.hasAnyPermission('govern:change:view','govern:change:review')")
    public R<Map<String, Object>> page(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String module) {
        currentUserService.requireAnyRole("ADMIN", "ADMIN_REVIEWER", "BUSINESS_OWNER");
        Long companyId = companyScopeService.requireCompanyId();
        QueryWrapper<GovernanceChangeRequest> qw = new QueryWrapper<GovernanceChangeRequest>().eq("company_id", companyId);
        if (isBusinessOwnerScopeUser()) {
            qw.eq("module", "USER");
        }
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
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS') || @currentUserService.hasPermission('govern:change:review')")
    public R<Map<String, Object>> todoPage(@RequestParam(defaultValue = "1") int page,
                                           @RequestParam(defaultValue = "10") int pageSize,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String module,
                                           @RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) Long startTime,
                                           @RequestParam(required = false) Long endTime) {
        currentUserService.requireAnyRole("ADMIN", "ADMIN_REVIEWER", "SECOPS");
        Long companyId = companyScopeService.requireCompanyId();
        User reviewer = currentUserService.requireCurrentUser();
        Long reviewerId = reviewer == null ? null : reviewer.getId();
        if (reviewerId == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("current", Math.max(1, page));
            empty.put("pages", 0);
            empty.put("total", 0);
            empty.put("list", List.of());
            return R.ok(empty);
        }
        QueryWrapper<GovernanceChangeRequest> qw = new QueryWrapper<GovernanceChangeRequest>()
            .eq("company_id", companyId)
            .isNotNull("requester_id");
        String statusFilter = normalizeListStatus(status);
        if ("processed".equals(statusFilter)) {
            qw.in("status", List.of(STATUS_APPROVED, STATUS_REJECTED, "revoked"));
        } else if (StringUtils.hasText(statusFilter)) {
            qw.eq("status", statusFilter);
        }
        if (!canReviewAllPending(reviewer)) {
            qw.eq("approver_id", reviewerId);
        }
        if ("ADMIN_REVIEWER".equalsIgnoreCase(resolveRoleCode(reviewer))) {
            qw.eq("requester_role_code", "ADMIN");
        }
        if (isBusinessOwnerScopeUser()) {
            qw.eq("module", "USER");
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
        payload.put("list", result.getRecords().stream().map(record -> toApprovalView(record, reviewer, true)).toList());
        return R.ok(payload);
    }

    @GetMapping("/my-page")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT') || @currentUserService.hasPermission('govern:change:create')")
    public R<Map<String, Object>> myPage(@RequestParam(defaultValue = "1") int page,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(required = false) String module,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Long startTime,
                                         @RequestParam(required = false) Long endTime) {
        if (!currentUserService.hasAnyRole("ADMIN", "ADMIN_REVIEWER", "SECOPS", "BUSINESS_OWNER", "AUDIT")
            && !currentUserService.hasPermission("govern:change:create")) {
            throw new org.springframework.security.access.AccessDeniedException("当前身份无权查看我发起审批");
        }
        Long companyId = companyScopeService.requireCompanyId();
        User requester = currentUserService.requireCurrentUser();
        QueryWrapper<GovernanceChangeRequest> qw = new QueryWrapper<GovernanceChangeRequest>()
            .eq("company_id", companyId)
            .eq("requester_id", requester.getId());
        if (isBusinessOwnerScopeUser()) {
            qw.eq("module", "USER");
        }

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
        payload.put("list", result.getRecords().stream().map(record -> toApprovalView(record, requester, false)).toList());
        return R.ok(payload);
    }

    @GetMapping("/detail/{id}")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','ADMIN_OPS','SECOPS') || @currentUserService.hasAnyPermission('govern:change:view','govern:change:review')")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        GovernanceChangeRequest request = requireScopedRequest(id);
        User current = currentUserService.requireCurrentUser();
        ensureViewableByCurrentUser(request, current);
        return R.ok(toApprovalView(request, current, false));
    }

    @GetMapping("/diff/{id}")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','ADMIN_OPS','SECOPS') || @currentUserService.hasAnyPermission('govern:change:view','govern:change:review')")
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

    @DeleteMapping("/draft/{id}")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_OPS') || @currentUserService.hasPermission('govern:change:create')")
    public R<?> deleteDraft(@PathVariable Long id) {
        User requester = currentUserService.requireCurrentUser();
        GovernanceChangeRequest request = requireScopedRequest(id);
        if (!java.util.Objects.equals(request.getRequesterId(), requester.getId())) {
            throw new BizException(40300, "仅申请发起人可删除草稿");
        }
        if (!isDraftStatus(request.getStatus())) {
            throw new BizException(40000, "仅草稿可删除");
        }
        governanceChangeRequestService.removeById(request.getId());
        writeAudit(requester, "governance_change_draft_delete", "requestId=" + request.getId());
        return R.okMsg("草稿已删除");
    }

    @PostMapping("/approve")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS') || @currentUserService.hasPermission('govern:change:review')")
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
            if (isAiWhitelistModule(request.getModule())) {
                String approverRoleCode = resolveRoleCode(approver);
                if (!"ADMIN_REVIEWER".equalsIgnoreCase(approverRoleCode)) {
                    throw new BizException(40300, "公司AI白名单仅支持审核员审批");
                }
            }
            if ("ADMIN_REVIEWER".equalsIgnoreCase(resolveRoleCode(approver)) && !"ADMIN".equalsIgnoreCase(request.getRequesterRoleCode())) {
                throw new BizException(40300, "复核员仅可审批治理管理员发起的申请");
            }
            if (!STATUS_PENDING.equalsIgnoreCase(request.getStatus())) {
                throw new BizException(40000, "仅待复核申请可审批");
            }
            Map<String, Object> payload = parsePayloadMap(request.getPayloadJson());
            Map<String, Object> beforeSnapshot = buildBeforeSnapshot(request);
            boolean approving = Boolean.TRUE.equals(req.getApprove());
            User requester = resolveRequesterForApproval(request, approving);
            if (requester != null && requester.getId() != null && requester.getId().equals(approver.getId())) {
                throw new BizException(40000, "SoD冲突：发起人与审批人不能是同一账号");
            }
            if (requester != null) {
                sodEnforcementService.enforceReviewerSeparation(requester, approver, "PRIVILEGE_CHANGE_REVIEW");
            }

            if (approving) {
                if (requester == null) {
                    throw new BizException(40000, "申请人信息缺失，无法通过该变更");
                }
                try {
                    applyChangeRequest(request, approver);
                    request.setStatus(STATUS_APPROVED);
                } catch (BizException ex) {
                    String message = String.valueOf(ex.getMessage() == null ? "" : ex.getMessage());
                    if (message.contains("不存在") || message.contains("不在当前公司")) {
                        request.setStatus(STATUS_REJECTED);
                        req.setNote(req.getNote() + "；系统自动驳回：目标对象已失效，请发起新申请");
                    } else {
                        throw ex;
                    }
                }
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

    private User resolveRequesterForApproval(GovernanceChangeRequest request, boolean strictForApprove) {
        if (request == null) {
            return null;
        }
        Long companyId = companyScopeService.requireCompanyId();

        Long requesterId = request.getRequesterId();
        if (requesterId != null) {
            User requesterById = userService.getById(requesterId);
            if (requesterById != null && java.util.Objects.equals(requesterById.getCompanyId(), companyId)) {
                return requesterById;
            }
        }

        User requesterByTrace = resolveRequesterFromTrace(request.getPayloadJson(), companyId);
        if (requesterByTrace != null) {
            return requesterByTrace;
        }

        if (strictForApprove) {
            if (requesterId == null) {
                throw new BizException(40000, "申请人信息缺失");
            }
            throw new BizException(40400, "申请人不存在或不在当前公司");
        }
        return null;
    }

    private User resolveRequesterFromTrace(String payloadJson, Long companyId) {
        Map<String, Object> payload = parsePayloadMap(payloadJson);
        Object traceObject = payload.get("trace");
        if (!(traceObject instanceof Map<?, ?> traceMap)) {
            return null;
        }

        Long traceUserId = parseLong(traceMap.get("userId"));
        if (traceUserId != null) {
            User byId = userService.getById(traceUserId);
            if (byId != null && java.util.Objects.equals(byId.getCompanyId(), companyId)) {
                return byId;
            }
        }

        String traceUsername = stringValue(traceMap.get("username")).trim();
        if (!StringUtils.hasText(traceUsername) || "-".equals(traceUsername)) {
            return null;
        }
        return userService.lambdaQuery()
            .eq(User::getCompanyId, companyId)
            .eq(User::getUsername, traceUsername)
            .last("LIMIT 1")
            .one();
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
            if (isAiWhitelistModule(request.getModule())) {
                applyAiWhitelistChange(request, payload, approver);
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
        Role existing = requireCompanyRole(request.getTargetId(), companyScopeService.requireCompanyId(), "角色不存在或不在当前公司");
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
        Long companyId = companyScopeService.requireCompanyId();
        User existing = resolveTargetUserForRequest(request, payload, companyId);
        if (existing == null || !java.util.Objects.equals(existing.getCompanyId(), companyId)) {
            throw new BizException(40400, "用户不存在或不在当前公司");
        }
        request.setTargetId(existing.getId());
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
                    Long roleCompanyId = existing.getCompanyId() == null ? companyScopeService.requireCompanyId() : existing.getCompanyId();
                    Role resolvedRole = null;
                    try {
                        resolvedRole = requireCompanyRole(roleId, roleCompanyId, "目标角色不存在或不在当前公司");
                    } catch (BizException ignored) {
                        // Fallback 1: keep current role if still valid for current company.
                        Long currentRoleId = existing.getRoleId();
                        if (currentRoleId != null && currentRoleId > 0L) {
                            try {
                                resolvedRole = requireCompanyRole(currentRoleId, roleCompanyId, "目标角色不存在或不在当前公司");
                            } catch (BizException ignored2) {
                                resolvedRole = null;
                            }
                        }
                        // Fallback 2: find by payload role code/name in current company.
                        if (resolvedRole == null) {
                            String payloadRoleCode = stringValue(payload.get("roleCode"));
                            String payloadRoleName = stringValue(payload.get("roleName"));
                            resolvedRole = findCompatibleRoleInCompany(roleCompanyId, payloadRoleCode, payloadRoleName);
                        }
                    }
                    if (resolvedRole != null) {
                        existing.setRoleId(resolvedRole.getId());
                    }
                }
            }
            existing.setUpdateTime(new Date());
            userService.updateById(existing);
            return;
        }
        throw new BizException(40000, "不支持的用户变更动作");
    }

    private void applyAiWhitelistChange(GovernanceChangeRequest request, Map<String, Object> payload, User approver) {
        String action = request == null ? "" : request.getAction();
        if (!"UPDATE".equalsIgnoreCase(action)) {
            throw new BizException(40000, "公司AI白名单仅支持 UPDATE 变更动作");
        }

        Long companyId = companyScopeService.requireCompanyId();
        Map<String, Object> config = new LinkedHashMap<>(privacyShieldConfigService.getOrCreateConfig());
        List<String> nextWhitelist = toDistinctStringList(payload == null ? null : payload.get("whitelist"));

        setCompanyWhitelist(config, companyId, nextWhitelist);
        privacyShieldConfigService.updateConfig(config);
    }

    private String resolveRiskLevel(String module, String action) {
        if ("DELETE".equalsIgnoreCase(action)) {
            return "CRITICAL";
        }
        if ("ROLE".equalsIgnoreCase(module)
            || "PERMISSION".equalsIgnoreCase(module)
            || "POLICY".equalsIgnoreCase(module)
            || "AI_WHITELIST".equalsIgnoreCase(module)
            || "USER".equalsIgnoreCase(module)) {
            return "HIGH";
        }
        return "MEDIUM";
    }

    private Role requireCompanyRole(Long roleId, Long companyId, String notFoundMessage) {
        Role role = roleService.getById(roleId);
        if (role == null) {
            throw new BizException(40400, notFoundMessage);
        }
        if (java.util.Objects.equals(role.getCompanyId(), companyId)) {
            return role;
        }

        // Repair legacy rows that missed company binding so governance edit flow can proceed.
        if ((role.getCompanyId() == null || role.getCompanyId() <= 0L) && companyId != null && companyId > 0L) {
            role.setCompanyId(companyId);
            role.setUpdateTime(new Date());
            roleService.updateById(role);
            role.setCompanyId(companyId);
            return role;
        }

        // If request references a cross-company row, map to compatible role in current company.
        if (companyId != null && companyId > 0L && (StringUtils.hasText(role.getCode()) || StringUtils.hasText(role.getName()))) {
            Role sameCodeRole = findCompatibleRoleInCompany(companyId, role.getCode(), role.getName());
            if (sameCodeRole != null) {
                return sameCodeRole;
            }
        }
        throw new BizException(40400, notFoundMessage);
    }

    private Role findCompatibleRoleInCompany(Long companyId, String roleCode, String roleName) {
        if (companyId == null || companyId <= 0L) {
            return null;
        }
        java.util.List<Role> candidates = roleService.lambdaQuery()
            .eq(Role::getCompanyId, companyId)
            .orderByDesc(Role::getUpdateTime)
            .list();
        String normalizedCode = String.valueOf(roleCode == null ? "" : roleCode).trim().toLowerCase(Locale.ROOT);
        String normalizedName = String.valueOf(roleName == null ? "" : roleName).trim();
        for (Role candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String code = String.valueOf(candidate.getCode() == null ? "" : candidate.getCode()).trim().toLowerCase(Locale.ROOT);
            if (StringUtils.hasText(normalizedCode) && normalizedCode.equals(code)) {
                return candidate;
            }
        }
        for (Role candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String name = String.valueOf(candidate.getName() == null ? "" : candidate.getName()).trim();
            if (StringUtils.hasText(normalizedName) && normalizedName.equals(name)) {
                return candidate;
            }
        }
        return null;
    }

    private User requireCompanyUser(Long userId, Long companyId, String notFoundMessage) {
        User user = userService.getById(userId);
        if (user == null) {
            throw new BizException(40400, notFoundMessage);
        }
        
        if (java.util.Objects.equals(user.getCompanyId(), companyId)) {
            return user;
        }

        if ((user.getCompanyId() == null || user.getCompanyId() <= 0L) && companyId != null && companyId > 0L) {
            user.setCompanyId(companyId);
            user.setUpdateTime(new Date());
            userService.updateById(user);
            user.setCompanyId(companyId);
            return user;
        }

        if (companyId != null && companyId > 0L && StringUtils.hasText(user.getUsername())) {
            User compatible = findCompatibleUserInCompany(companyId, user.getUsername(), user.getRealName());
            if (compatible != null) {
                return compatible;
            }
        }
        
        if (companyId != null && companyId > 0L) {
            user.setCompanyId(companyId);
            user.setUpdateTime(new Date());
            userService.updateById(user);
            return user;
        }
        
        throw new BizException(40400, notFoundMessage);
    }

    private User findCompatibleUserInCompany(Long companyId, String username, String realName) {
        if (companyId == null || companyId <= 0L) {
            return null;
        }
        String normalizedUsername = String.valueOf(username == null ? "" : username).trim();
        if (StringUtils.hasText(normalizedUsername)) {
            User byUsername = userService.lambdaQuery()
                .eq(User::getCompanyId, companyId)
                .eq(User::getUsername, normalizedUsername)
                .last("LIMIT 1")
                .one();
            if (byUsername != null) {
                return byUsername;
            }
        }
        String normalizedRealName = String.valueOf(realName == null ? "" : realName).trim();
        if (StringUtils.hasText(normalizedRealName)) {
            return userService.lambdaQuery()
                .eq(User::getCompanyId, companyId)
                .eq(User::getRealName, normalizedRealName)
                .last("LIMIT 1")
                .one();
        }
        return null;
    }

    private User resolveTargetUserForRequest(GovernanceChangeRequest request, Map<String, Object> payload, Long companyId) {
        Long requestTargetId = request == null ? null : request.getTargetId();
        if (requestTargetId != null && requestTargetId > 0L) {
            try {
                return requireCompanyUser(requestTargetId, companyId, "用户不存在或不在当前公司");
            } catch (BizException ignored) {
                // Fall through to payload-based recovery for legacy requests.
            }
        }

        Long payloadUserId = extractFirstLong(payload, "id", "userId", "targetId", "targetUserId", "uid");
        if (payloadUserId != null && payloadUserId > 0L) {
            try {
                return requireCompanyUser(payloadUserId, companyId, "用户不存在或不在当前公司");
            } catch (BizException ignored) {
                // Continue resolving by username/realName.
            }
        }

        String payloadUsername = extractFirstString(payload, "username", "targetUsername", "account", "userName");
        String payloadRealName = extractFirstString(payload, "realName", "targetRealName", "name");
        User compatible = findCompatibleUserInCompany(companyId, payloadUsername, payloadRealName);
        if (compatible != null) {
            return compatible;
        }

        Map<String, Object> trace = asMap(payload == null ? null : payload.get("trace"));
        compatible = findCompatibleUserInCompany(companyId, extractFirstString(trace, "username"), extractFirstString(trace, "realName", "name"));
        if (compatible != null) {
            return compatible;
        }

        Map<String, Object> target = asMap(payload == null ? null : payload.get("target"));
        compatible = findCompatibleUserInCompany(companyId, extractFirstString(target, "username", "userName"), extractFirstString(target, "realName", "name"));
        if (compatible != null) {
            return compatible;
        }

        Map<String, Object> userNode = asMap(payload == null ? null : payload.get("user"));
        compatible = findCompatibleUserInCompany(companyId, extractFirstString(userNode, "username", "userName"), extractFirstString(userNode, "realName", "name"));
        if (compatible != null) {
            return compatible;
        }

        Map<String, Object> before = asMap(payload == null ? null : payload.get("before"));
        compatible = findCompatibleUserInCompany(companyId, extractFirstString(before, "username", "userName"), extractFirstString(before, "realName", "name"));
        return compatible;
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
        if (isBusinessOwnerScopeUser() && !"USER".equalsIgnoreCase(String.valueOf(request.getModule()))) {
            throw new BizException(40300, "仅可查看业务类申请");
        }
        boolean canReview = currentUserService.hasPermission("govern:change:review")
            || currentUserService.hasAnyRole("ADMIN", "ADMIN_REVIEWER", "ADMIN_OPS", "SECOPS");
        if (canReview) {
            return;
        }
        if (java.util.Objects.equals(request.getRequesterId(), currentUser.getId())) {
            return;
        }
        throw new BizException(40300, "仅可查看本人发起的申请");
    }

    private boolean isBusinessOwnerScopeUser() {
        return currentUserService.hasAnyRole("BUSINESS_OWNER", "BUSINESS_OWNER_APPROVER", "BUSINESS_OWNER_REVIEWER");
    }

    private Map<String, Object> toApprovalView(GovernanceChangeRequest request, User viewer, boolean todoView) {
        Map<String, Object> payload = parsePayloadMap(request == null ? null : request.getPayloadJson());
        String targetName = resolveTargetName(request, payload);
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", request == null ? null : request.getId());
        view.put("companyId", request == null ? null : request.getCompanyId());
        view.put("module", request == null ? null : request.getModule());
        view.put("action", request == null ? null : request.getAction());
        view.put("status", request == null ? null : request.getStatus());
        view.put("statusLabel", request == null ? null : resolveStatusLabel(request.getStatus()));
        view.put("riskLevel", request == null ? null : request.getRiskLevel());
        view.put("requesterId", request == null ? null : request.getRequesterId());
        view.put("requesterName", resolveUserDisplayName(request == null ? null : request.getRequesterId()));
        view.put("requesterRoleCode", request == null ? null : request.getRequesterRoleCode());
        view.put("approverId", request == null ? null : request.getApproverId());
        view.put("approverName", resolveUserDisplayName(request == null ? null : request.getApproverId()));
        view.put("approverRoleCode", request == null ? null : request.getApproverRoleCode());
        view.put("currentApproverId", resolveCurrentApproverId(request, viewer, todoView));
        view.put("currentApproverName", resolveCurrentApproverName(request, viewer, todoView));
        view.put("approveNote", request == null ? null : request.getApproveNote());
        view.put("approvedAt", request == null ? null : request.getApprovedAt());
        view.put("createTime", request == null ? null : request.getCreateTime());
        view.put("updateTime", request == null ? null : request.getUpdateTime());
        view.put("targetId", request == null ? null : request.getTargetId());
        view.put("targetName", targetName);
        view.put("payloadJson", request == null ? null : request.getPayloadJson());
        view.put("title", resolveTitle(request, payload));
        view.put("requestType", resolveRequestType(request));
        view.put("requestTypeLabel", resolveRequestTypeLabel(request));
        view.put("reason", stringValue(payload.get("reason")));
        view.put("impact", stringValue(payload.get("impact")));
        view.put("payload", payload);
        return view;
    }

    private String resolveTargetName(GovernanceChangeRequest request, Map<String, Object> payload) {
        if (request == null) {
            return "-";
        }
        String module = String.valueOf(request.getModule() == null ? "" : request.getModule()).trim().toUpperCase(Locale.ROOT);
        Long requestCompanyId = request.getCompanyId();
        Long requestTargetId = request.getTargetId();

        if ("ROLE".equals(module)) {
            Role role = requestTargetId == null ? null : roleService.getById(requestTargetId);
            if (role != null && java.util.Objects.equals(role.getCompanyId(), requestCompanyId)) {
                return firstNonEmpty(role.getName(), role.getCode(), String.valueOf(role.getId()));
            }
            Long payloadRoleId = parseLong(payload == null ? null : payload.get("roleId"));
            if (payloadRoleId != null && payloadRoleId > 0L) {
                Role payloadRole = roleService.getById(payloadRoleId);
                if (payloadRole != null && java.util.Objects.equals(payloadRole.getCompanyId(), requestCompanyId)) {
                    return firstNonEmpty(payloadRole.getName(), payloadRole.getCode(), String.valueOf(payloadRole.getId()));
                }
            }
            return firstNonEmpty(
                stringValue(payload == null ? null : payload.get("name")),
                stringValue(payload == null ? null : payload.get("roleName")),
                stringValue(payload == null ? null : payload.get("code")),
                stringValue(payload == null ? null : payload.get("roleCode")),
                requestTargetId == null ? "" : "ID:" + requestTargetId
            );
        }

        if ("PERMISSION".equals(module)) {
            Permission permission = requestTargetId == null ? null : permissionService.getById(requestTargetId);
            if (permission != null && java.util.Objects.equals(permission.getCompanyId(), requestCompanyId)) {
                return firstNonEmpty(permission.getName(), permission.getCode(), String.valueOf(permission.getId()));
            }
            return firstNonEmpty(
                stringValue(payload == null ? null : payload.get("name")),
                stringValue(payload == null ? null : payload.get("code")),
                requestTargetId == null ? "" : "ID:" + requestTargetId
            );
        }

        if ("POLICY".equals(module)) {
            CompliancePolicy policy = requestTargetId == null ? null : compliancePolicyService.getById(requestTargetId);
            if (policy != null && java.util.Objects.equals(policy.getCompanyId(), requestCompanyId)) {
                return firstNonEmpty(policy.getName(), policy.getPolicyType(), String.valueOf(policy.getId()));
            }
            return firstNonEmpty(
                stringValue(payload == null ? null : payload.get("name")),
                stringValue(payload == null ? null : payload.get("policyType")),
                requestTargetId == null ? "" : "ID:" + requestTargetId
            );
        }

        if ("AI_WHITELIST".equals(module)) {
            return firstNonEmpty(
                stringValue(payload == null ? null : payload.get("name")),
                "公司AI白名单",
                requestCompanyId == null ? "" : "公司ID:" + requestCompanyId
            );
        }

        if ("USER".equals(module)) {
            User user = requestTargetId == null ? null : userService.getById(requestTargetId);
            if (user != null && java.util.Objects.equals(user.getCompanyId(), requestCompanyId)) {
                return firstNonEmpty(user.getRealName(), user.getNickname(), user.getUsername(), String.valueOf(user.getId()));
            }
            Long payloadUserId = parseLong(payload == null ? null : payload.get("id"));
            if (payloadUserId == null) {
                payloadUserId = parseLong(payload == null ? null : payload.get("userId"));
            }
            if (payloadUserId != null && payloadUserId > 0L) {
                User payloadUser = userService.getById(payloadUserId);
                if (payloadUser != null && java.util.Objects.equals(payloadUser.getCompanyId(), requestCompanyId)) {
                    return firstNonEmpty(payloadUser.getRealName(), payloadUser.getNickname(), payloadUser.getUsername(), String.valueOf(payloadUser.getId()));
                }
            }
            return firstNonEmpty(
                stringValue(payload == null ? null : payload.get("realName")),
                stringValue(payload == null ? null : payload.get("username")),
                requestTargetId == null ? "" : "ID:" + requestTargetId
            );
        }

        return firstNonEmpty(
            stringValue(payload == null ? null : payload.get("name")),
            stringValue(payload == null ? null : payload.get("code")),
            requestTargetId == null ? "" : "ID:" + requestTargetId,
            "-"
        );
    }

    private String firstNonEmpty(String... candidates) {
        if (candidates == null || candidates.length == 0) {
            return "-";
        }
        for (String candidate : candidates) {
            String value = String.valueOf(candidate == null ? "" : candidate).trim();
            if (StringUtils.hasText(value) && !"-".equals(value)) {
                return value;
            }
        }
        return "-";
    }

    private String normalizeListStatus(String rawStatus) {
        String value = String.valueOf(rawStatus == null ? "" : rawStatus).trim().toLowerCase(Locale.ROOT);
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return switch (value) {
            case "pending", "待审批", "审批中" -> STATUS_PENDING;
            case "approved", "通过", "已通过" -> STATUS_APPROVED;
            case "rejected", "reject", "拒绝", "已拒绝", "已驳回" -> STATUS_REJECTED;
            case "revoked", "撤回", "已撤回" -> "revoked";
            case "draft", "草稿" -> "draft";
            case "processed", "done", "handled", "已处理" -> "processed";
            case "all", "全部" -> "";
            default -> value;
        };
    }

    private String resolveRequestType(GovernanceChangeRequest request) {
        if (request == null) {
            return "";
        }
        String module = StringUtils.hasText(request.getModule()) ? request.getModule().trim().toUpperCase(Locale.ROOT) : "UNKNOWN";
        String action = StringUtils.hasText(request.getAction()) ? request.getAction().trim().toUpperCase(Locale.ROOT) : "UNKNOWN";
        return module + "_" + action;
    }

    private String resolveRequestTypeLabel(GovernanceChangeRequest request) {
        if (request == null) {
            return "-";
        }
        String module = switch (String.valueOf(request.getModule() == null ? "" : request.getModule()).trim().toUpperCase(Locale.ROOT)) {
            case "ROLE" -> "角色";
            case "PERMISSION" -> "权限";
            case "POLICY" -> "策略";
            case "AI_WHITELIST" -> "公司AI白名单";
            case "USER" -> "用户";
            default -> StringUtils.hasText(request.getModule()) ? request.getModule().trim() : "未知";
        };
        String action = switch (String.valueOf(request.getAction() == null ? "" : request.getAction()).trim().toUpperCase(Locale.ROOT)) {
            case "ADD" -> "新增";
            case "UPDATE" -> "修改";
            case "DELETE" -> "删除";
            default -> StringUtils.hasText(request.getAction()) ? request.getAction().trim() : "未知";
        };
        return module + " / " + action;
    }

    private String resolveStatusLabel(String status) {
        String original = String.valueOf(status == null ? "" : status).trim();
        String normalized = original.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case STATUS_PENDING -> "待审批";
            case STATUS_APPROVED -> "已通过";
            case STATUS_REJECTED -> "已驳回";
            case "revoked" -> "已撤回";
            case "draft", "草稿" -> "草稿";
            default -> StringUtils.hasText(original) ? original : "-";
        };
    }

    private String resolveTitle(GovernanceChangeRequest request, Map<String, Object> payload) {
        if (payload != null) {
            String payloadTitle = sanitizeApprovalTitle(stringValue(payload.get("title")));
            if (StringUtils.hasText(payloadTitle)) {
                return payloadTitle;
            }
        }
        String name = sanitizeApprovalTitle(payload == null ? "" : stringValue(payload.get("name")));
        if (!StringUtils.hasText(name)) {
            name = sanitizeApprovalTitle(payload == null ? "" : stringValue(payload.get("code")));
        }
        String module = request == null ? "GOVERNANCE" : sanitizeApprovalTitle(stringValue(request.getModule()));
        String action = request == null ? "SUBMIT" : sanitizeApprovalTitle(stringValue(request.getAction()));
        if (!StringUtils.hasText(module)) {
            module = "GOVERNANCE";
        }
        if (!StringUtils.hasText(action)) {
            action = "SUBMIT";
        }
        if (StringUtils.hasText(name)) {
            return module + " " + action + " - " + name;
        }
        String idText = request != null && request.getId() != null ? String.valueOf(request.getId()) : "NEW";
        return "Governance " + module + " " + action + " Request #" + idText;
    }

    private String sanitizeApprovalTitle(String value) {
        String text = String.valueOf(value == null ? "" : value).trim();
        if (!StringUtils.hasText(text)) {
            return "";
        }
        if (text.matches("^\\?{2,}$")) {
            return "";
        }
        text = text.replaceAll("\\?{2,}", " ").trim();
        return text;
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
        if (request == null) {
            return before;
        }
        String module = stringValue(request.getModule()).toUpperCase(Locale.ROOT);
        if ("AI_WHITELIST".equals(module)) {
            Long companyId = request.getCompanyId() == null ? companyScopeService.requireCompanyId() : request.getCompanyId();
            Map<String, Object> config = privacyShieldConfigService.getOrCreateConfig();
            before.put("companyId", companyId);
            before.put("whitelist", resolveCompanyWhitelist(config, companyId));
            before.put("catalog", toDistinctStringList(config.get("aiCatalog")));
            return before;
        }
        if (request.getTargetId() == null) {
            return before;
        }
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

    private User resolveDefaultApprover(User requester, Long companyId) {
        List<User> users = userService.lambdaQuery()
            .eq(User::getCompanyId, companyId)
            .eq(User::getAccountType, "real")
            .eq(User::getAccountStatus, "active")
            .list();
        if (users == null || users.isEmpty()) {
            return null;
        }
        return users.stream()
            .filter(item -> item != null && item.getId() != null)
            .filter(item -> requester == null || !java.util.Objects.equals(item.getId(), requester.getId()))
            .filter(item -> isReviewerCandidate(resolveRoleCode(item)))
            .sorted((a, b) -> Integer.compare(reviewerPriority(a), reviewerPriority(b)))
            .findFirst()
            .orElse(null);
    }

    private User resolveAiWhitelistReviewer(User requester, Long companyId) {
        List<User> users = userService.lambdaQuery()
            .eq(User::getCompanyId, companyId)
            .eq(User::getAccountType, "real")
            .eq(User::getAccountStatus, "active")
            .list();
        if (users == null || users.isEmpty()) {
            return null;
        }
        return users.stream()
            .filter(item -> item != null && item.getId() != null)
            .filter(item -> requester == null || !java.util.Objects.equals(item.getId(), requester.getId()))
            .filter(item -> "ADMIN_REVIEWER".equalsIgnoreCase(resolveRoleCode(item)))
            .findFirst()
            .orElse(null);
    }

    private boolean isAiWhitelistModule(String module) {
        return "AI_WHITELIST".equalsIgnoreCase(String.valueOf(module == null ? "" : module).trim());
    }

    private boolean isReviewerCandidate(String roleCode) {
        String normalized = normalize(roleCode);
        return "ADMIN_REVIEWER".equals(normalized)
            || "ADMIN".equals(normalized)
            || "SECOPS".equals(normalized)
            || "BUSINESS_OWNER_APPROVER".equals(normalized)
            || "BUSINESS_OWNER_REVIEWER".equals(normalized)
            || "BUSINESS_OWNER".equals(normalized);
    }

    private boolean canReviewAllPending(User reviewer) {
        if (reviewer == null) {
            return false;
        }
        String roleCode = resolveRoleCode(reviewer);
        return "ADMIN".equalsIgnoreCase(roleCode) || "ADMIN_REVIEWER".equalsIgnoreCase(roleCode);
    }

    private int reviewerPriority(User user) {
        String roleCode = resolveRoleCode(user);
        if ("ADMIN_REVIEWER".equalsIgnoreCase(roleCode)) {
            return 0;
        }
        if ("ADMIN".equalsIgnoreCase(roleCode)) {
            return 1;
        }
        if ("SECOPS".equalsIgnoreCase(roleCode)) {
            return 2;
        }
        if ("BUSINESS_OWNER".equalsIgnoreCase(roleCode)) {
            return 3;
        }
        return 99;
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

    private boolean isDraftStatus(String status) {
        String normalized = String.valueOf(status == null ? "" : status).trim().toLowerCase(Locale.ROOT);
        return "draft".equals(normalized) || "草稿".equals(normalized) || "2".equals(normalized);
    }

    private Long resolveCurrentApproverId(GovernanceChangeRequest request, User viewer, boolean todoView) {
        if (request == null) {
            return null;
        }
        if (todoView && viewer != null) {
            return viewer.getId();
        }
        return request.getApproverId();
    }

    private String resolveCurrentApproverName(GovernanceChangeRequest request, User viewer, boolean todoView) {
        if (request == null) {
            return "-";
        }
        if (todoView && viewer != null) {
            return resolveUserDisplayName(viewer.getId());
        }
        String approverName = resolveUserDisplayName(request.getApproverId());
        return StringUtils.hasText(approverName) ? approverName : "-";
    }

    private String resolveUserDisplayName(Long userId) {
        if (userId == null || userId <= 0L) {
            return "-";
        }
        User user = userService.getById(userId);
        if (user == null) {
            return String.valueOf(userId);
        }
        if (StringUtils.hasText(user.getRealName())) {
            return user.getRealName().trim();
        }
        if (StringUtils.hasText(user.getNickname())) {
            return user.getNickname().trim();
        }
        if (StringUtils.hasText(user.getUsername())) {
            return user.getUsername().trim();
        }
        return String.valueOf(userId);
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

    private List<String> toDistinctStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> result = new java.util.ArrayList<>();
        for (Object item : list) {
            String text = String.valueOf(item == null ? "" : item).trim();
            if (StringUtils.hasText(text) && !result.contains(text)) {
                result.add(text);
            }
        }
        return result;
    }

    private List<String> resolveCompanyWhitelist(Map<String, Object> config, Long companyId) {
        Object raw = config.get("aiWhitelistByCompany");
        if (raw instanceof Map<?, ?> map) {
            Object companyList = map.get(String.valueOf(companyId == null ? 0L : companyId));
            List<String> list = toDistinctStringList(companyList);
            if (!list.isEmpty()) {
                return list;
            }
        }
        return toDistinctStringList(config.get("aiWhitelist"));
    }

    private void setCompanyWhitelist(Map<String, Object> config, Long companyId, List<String> whitelist) {
        Map<String, Object> bucket;
        Object raw = config.get("aiWhitelistByCompany");
        if (raw instanceof Map<?, ?> map) {
            bucket = new LinkedHashMap<>();
            map.forEach((k, v) -> bucket.put(String.valueOf(k), v));
        } else {
            bucket = new LinkedHashMap<>();
        }
        bucket.put(String.valueOf(companyId == null ? 0L : companyId), whitelist == null ? List.of() : whitelist);
        config.put("aiWhitelistByCompany", bucket);
    }

    private Long extractFirstLong(Map<String, Object> payload, String... keys) {
        if (payload == null || keys == null) {
            return null;
        }
        for (String key : keys) {
            Long parsed = parseLong(payload.get(key));
            if (parsed != null && parsed > 0L) {
                return parsed;
            }
        }

        Map<String, Object> target = asMap(payload.get("target"));
        for (String key : keys) {
            Long parsed = parseLong(target.get(key));
            if (parsed != null && parsed > 0L) {
                return parsed;
            }
        }

        Map<String, Object> user = asMap(payload.get("user"));
        for (String key : keys) {
            Long parsed = parseLong(user.get(key));
            if (parsed != null && parsed > 0L) {
                return parsed;
            }
        }

        Map<String, Object> before = asMap(payload.get("before"));
        for (String key : keys) {
            Long parsed = parseLong(before.get(key));
            if (parsed != null && parsed > 0L) {
                return parsed;
            }
        }

        return null;
    }

    private String extractFirstString(Map<String, Object> payload, String... keys) {
        if (payload == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            String value = stringValue(payload.get(key)).trim();
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            raw.forEach((k, v) -> normalized.put(String.valueOf(k), v));
            return normalized;
        }
        return java.util.Collections.emptyMap();
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
