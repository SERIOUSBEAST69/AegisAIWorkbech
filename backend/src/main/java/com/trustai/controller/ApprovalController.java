package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.trustai.entity.AuditLog;
import com.trustai.entity.ApprovalRequest;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import com.trustai.service.AuditLogService;
import com.trustai.service.ApprovalRequestService;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.KeyTaskMetricService;
import com.trustai.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Date;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotNull;
import org.springframework.util.StringUtils;

@RestController
@RequestMapping("/api/approval")
@Validated
public class ApprovalController {
    @Autowired private ApprovalRequestService approvalRequestService;
    @Autowired private CurrentUserService currentUserService;
    @Autowired private CompanyScopeService companyScopeService;
    @Autowired private KeyTaskMetricService keyTaskMetricService;
    @Autowired private AuditLogService auditLogService;

    private static final String STATUS_APPROVE = "\u901A\u8FC7";
    private static final String STATUS_REJECT = "\u62D2\u7EDD";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_DATA_ADMIN = "DATA_ADMIN";
    private static final String ROLE_BUSINESS_OWNER = "BUSINESS_OWNER";
    private static final String ROLE_EMPLOYEE = "EMPLOYEE";
    private static final String PERM_APPROVAL_VIEW = "approval:view";
    private static final String PERM_APPROVAL_OPERATE = "approval:operate";
    private static final String PERM_APPROVAL_OPERATE_DATA = "approval:operate:data";
    private static final String PERM_APPROVAL_OPERATE_GOVERNANCE = "approval:operate:governance";
    private static final String PERM_APPROVAL_OPERATE_BUSINESS = "approval:operate:business";
    private static final String TYPE_DATA = "DATA";
    private static final String TYPE_GOVERNANCE = "GOVERNANCE";
    private static final String TYPE_BUSINESS = "BUSINESS";
    private static final String TYPE_PERSONAL = "PERSONAL";

    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasPermission('approval:view')")
    public R<List<ApprovalRequest>> list(@RequestParam(required = false) Long applicantId,
                                         @RequestParam(required = false) Long assetId) {
        currentUserService.requirePermission(PERM_APPROVAL_VIEW);
        User currentUser = currentUserService.requireCurrentUser();
        String roleCode = currentUserService.currentRoleCode();
        QueryWrapper<ApprovalRequest> qw = companyScopeService.withCompany(new QueryWrapper<>());
        if (isApprovalOperator(currentUser)) {
            if (applicantId != null) qw.eq("applicant_id", applicantId);
        } else {
            qw.eq("applicant_id", currentUser.getId());
        }
        if (assetId != null) qw.eq("asset_id", assetId);
        List<ApprovalRequest> list = approvalRequestService.list(qw);
        return R.ok(filterByOperatorScope(list, roleCode, currentUser));
    }

    @GetMapping("/page")
    @PreAuthorize("@currentUserService.hasPermission('approval:view')")
    public R<Map<String, Object>> page(@RequestParam(defaultValue = "1") int page,
                                       @RequestParam(defaultValue = "10") int pageSize,
                                       @RequestParam(required = false) Long applicantId,
                                       @RequestParam(required = false) Long assetId,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String keyword) {
        currentUserService.requirePermission(PERM_APPROVAL_VIEW);
        User currentUser = currentUserService.requireCurrentUser();
        String roleCode = currentUserService.currentRoleCode();
        QueryWrapper<ApprovalRequest> qw = companyScopeService.withCompany(new QueryWrapper<>());
        if (isApprovalOperator(currentUser)) {
            if (applicantId != null) {
                qw.eq("applicant_id", applicantId);
            }
        } else {
            qw.eq("applicant_id", currentUser.getId());
        }
        if (assetId != null) {
            qw.eq("asset_id", assetId);
        }
        if (StringUtils.hasText(status)) {
            qw.like("status", status.trim());
        }
        if (StringUtils.hasText(keyword)) {
            qw.and(w -> w.like("reason", keyword).or().like("status", keyword));
        }
        qw.orderByDesc("update_time");

        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(100, pageSize));
        Page<ApprovalRequest> result = approvalRequestService.page(new Page<>(safePage, safePageSize), qw);
        List<ApprovalRequest> scoped = filterByOperatorScope(result.getRecords(), roleCode, currentUser);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("current", result.getCurrent());
        payload.put("pages", result.getPages());
        payload.put("total", result.getTotal());
        payload.put("list", scoped);
        return R.ok(payload);
    }

    @PostMapping("/apply")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN','BUSINESS_OWNER')")
    public R<?> apply(@RequestBody ApprovalRequest req) {
        try {
            User currentUser = currentUserService.requireCurrentUser();
            String roleCode = currentUserService.currentRoleCode();
            req.setCompanyId(companyScopeService.requireCompanyId());
            req.setApplicantId(currentUser.getId());
            req.setReason(normalizeApplyReason(req.getReason(), roleCode, currentUser));
            req.setApproverId(null);
            req.setStatus(null);
            req.setTaskId(null);
            req.setProcessInstanceId(null);
            ApprovalRequest created = approvalRequestService.startApproval(req);
            writeApprovalAudit(currentUser, "approval_apply", "requestId=" + created.getId() + ", applicantId=" + currentUser.getId());
            keyTaskMetricService.record("approval.flow", true);
            return R.okMsg("提交成功");
        } catch (RuntimeException ex) {
            keyTaskMetricService.record("approval.flow", false);
            throw ex;
        }
    }

    @PostMapping("/reject")
    @PreAuthorize("@currentUserService.hasAnyPermission('approval:operate','approval:operate:data','approval:operate:governance','approval:operate:business')")
    public R<?> reject(@RequestBody ApproveReq req) {
        try {
            User currentUser = currentUserService.requireCurrentUser();
            String roleCode = currentUserService.currentRoleCode();
            ApprovalRequest before = approvalRequestService.getOne(
                companyScopeService.withCompany(new QueryWrapper<ApprovalRequest>()).eq("id", req.getRequestId())
            );
            if (before == null) return R.error(40000, "申请不存在");
            if (!canOperateRequest(roleCode, before, currentUser)) {
                throw new BizException(40300, "当前身份无权审批该类型申请");
            }
            String prevStatus = before.getStatus();
            approvalRequestService.approve(req.getRequestId(), currentUser.getId(), STATUS_REJECT);
            ApprovalRequest after = approvalRequestService.getById(req.getRequestId());
            java.util.Map<String, Object> detail = new java.util.HashMap<>();
            detail.put("requestId", req.getRequestId());
            detail.put("previousStatus", prevStatus);
            detail.put("currentStatus", after == null ? "驳回" : after.getStatus());
            detail.put("approverId", currentUser.getId());
            detail.put("message", "审批已驳回，状态已从「" + prevStatus + "」回退至「驳回」。");
            writeApprovalAudit(currentUser, "approval_reject", "requestId=" + req.getRequestId() + ", from=" + prevStatus + ", to=驳回");
            keyTaskMetricService.record("approval.flow", true);
            return R.ok(detail);
        } catch (RuntimeException ex) {
            keyTaskMetricService.record("approval.flow", false);
            throw ex;
        }
    }

    @PostMapping("/approve")
    @PreAuthorize("@currentUserService.hasAnyPermission('approval:operate','approval:operate:data','approval:operate:governance','approval:operate:business')")
    public R<?> approve(@RequestBody ApproveReq req) {
        try {
            String targetStatus = normalizeDecisionStatus(req.getStatus());
            if (targetStatus == null) return R.error(40000, "不支持的状态");
            User currentUser = currentUserService.requireCurrentUser();
            String roleCode = currentUserService.currentRoleCode();
            ApprovalRequest request = approvalRequestService.getOne(
                companyScopeService.withCompany(new QueryWrapper<ApprovalRequest>()).eq("id", req.getRequestId())
            );
            if (request == null) return R.error(40000, "申请不存在");
            if (!canOperateRequest(roleCode, request, currentUser)) {
                throw new BizException(40300, "当前身份无权审批该类型申请");
            }
            ApprovalRequest after = approvalRequestService.approve(req.getRequestId(), currentUser.getId(), targetStatus);
            writeApprovalAudit(currentUser, "approval_approve", "requestId=" + req.getRequestId() + ", to=" + (after == null ? req.getStatus() : after.getStatus()));
            keyTaskMetricService.record("approval.flow", true);
            return R.okMsg("审批完成");
        } catch (RuntimeException ex) {
            keyTaskMetricService.record("approval.flow", false);
            throw ex;
        }
    }

    @GetMapping("/todo")
    @PreAuthorize("@currentUserService.hasAnyPermission('approval:operate','approval:operate:data','approval:operate:governance','approval:operate:business')")
    public R<List<ApprovalRequest>> todo() {
        currentUserService.requireAnyPermission(
            PERM_APPROVAL_OPERATE,
            PERM_APPROVAL_OPERATE_DATA,
            PERM_APPROVAL_OPERATE_GOVERNANCE,
            PERM_APPROVAL_OPERATE_BUSINESS
        );
        User currentUser = currentUserService.requireCurrentUser();
        String roleCode = currentUserService.currentRoleCode();
        List<ApprovalRequest> todos = approvalRequestService.todo(currentUser.getId());
        Long companyId = companyScopeService.requireCompanyId();
        todos = todos.stream()
            .filter(item -> java.util.Objects.equals(item.getCompanyId(), companyId))
            .toList();
        return R.ok(filterByOperatorScope(todos, roleCode, currentUser));
    }

    @PostMapping("/delete")
    @PreAuthorize("@currentUserService.hasPermission('approval:view')")
    public R<?> delete(@RequestBody @Validated IdReq req) {
        currentUserService.requirePermission(PERM_APPROVAL_VIEW);
        User currentUser = currentUserService.requireCurrentUser();
        String roleCode = currentUserService.currentRoleCode();
        ApprovalRequest approval = approvalRequestService.getOne(
            companyScopeService.withCompany(new QueryWrapper<ApprovalRequest>()).eq("id", req.getId())
        );
        if (approval == null) {
            throw new BizException(40000, "申请不存在");
        }
        if (!canDeleteRequest(roleCode, approval, currentUser)) {
            throw new BizException(40300, "仅可删除本人申请");
        }
        approvalRequestService.removeById(req.getId());
        writeApprovalAudit(currentUser, "approval_delete", "requestId=" + approval.getId());
        return R.okMsg("删除成功");
    }

    private void writeApprovalAudit(User operator, String operation, String detail) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(operator.getId());
            log.setOperation(operation);
            log.setOperationTime(new Date());
            log.setInputOverview(detail);
            log.setOutputOverview("approval_flow");
            log.setResult("success");
            log.setRiskLevel("MEDIUM");
            log.setCreateTime(new Date());
            auditLogService.saveAudit(log);
        } catch (Exception ignored) {
            // Non-blocking audit write.
        }
    }

    private boolean isApprovalOperator(User user) {
        if (user == null) {
            return false;
        }
        return hasGlobalOperatePermission()
            || currentUserService.hasPermission(PERM_APPROVAL_OPERATE_DATA)
            || currentUserService.hasPermission(PERM_APPROVAL_OPERATE_GOVERNANCE)
            || currentUserService.hasPermission(PERM_APPROVAL_OPERATE_BUSINESS);
    }

    private List<ApprovalRequest> filterByOperatorScope(List<ApprovalRequest> list, String roleCode, User currentUser) {
        if (list == null) {
            return List.of();
        }
        if (hasGlobalOperatePermission() || isAdminRole(roleCode)) {
            return list;
        }
        boolean canData = currentUserService.hasPermission(PERM_APPROVAL_OPERATE_DATA);
        boolean canGovernance = currentUserService.hasPermission(PERM_APPROVAL_OPERATE_GOVERNANCE);
        boolean canBusiness = currentUserService.hasPermission(PERM_APPROVAL_OPERATE_BUSINESS);
        if (canData || canGovernance || canBusiness) {
            return list.stream().filter(item -> {
                String type = resolveRequestType(item);
                return (canData && TYPE_DATA.equals(type))
                    || (canGovernance && TYPE_GOVERNANCE.equals(type))
                    || (canBusiness && TYPE_BUSINESS.equals(type));
            }).toList();
        }
        return list.stream()
            .filter(item -> currentUser.getId() != null && currentUser.getId().equals(item.getApplicantId()))
            .toList();
    }

    private boolean canOperateRequest(String roleCode, ApprovalRequest request, User currentUser) {
        if (hasGlobalOperatePermission() || isAdminRole(roleCode)) {
            return true;
        }
        String type = resolveRequestType(request);
        if (TYPE_DATA.equals(type)) {
            return currentUserService.hasPermission(PERM_APPROVAL_OPERATE_DATA);
        }
        if (TYPE_GOVERNANCE.equals(type)) {
            return currentUserService.hasPermission(PERM_APPROVAL_OPERATE_GOVERNANCE);
        }
        if (TYPE_BUSINESS.equals(type)) {
            return currentUserService.hasPermission(PERM_APPROVAL_OPERATE_BUSINESS);
        }
        return currentUser.getId() != null && currentUser.getId().equals(request.getApplicantId());
    }

    private boolean canDeleteRequest(String roleCode, ApprovalRequest request, User currentUser) {
        if (isAdminRole(roleCode)) {
            return true;
        }
        if (isEmployeeRole(roleCode)) {
            return currentUser.getId() != null && currentUser.getId().equals(request.getApplicantId());
        }
        return canOperateRequest(roleCode, request, currentUser);
    }

    private boolean hasGlobalOperatePermission() {
        return currentUserService.hasPermission(PERM_APPROVAL_OPERATE);
    }

    private String resolveRequestType(ApprovalRequest request) {
        if (request == null) {
            return TYPE_DATA;
        }
        String reason = request.getReason();
        if (reason == null) {
            return TYPE_DATA;
        }
        String normalized = reason.trim().toUpperCase();
        if (normalized.startsWith("[GOVERNANCE]")) {
            return TYPE_GOVERNANCE;
        }
        if (normalized.startsWith("[BUSINESS]")) {
            return TYPE_BUSINESS;
        }
        if (normalized.startsWith("[PERSONAL]")) {
            return TYPE_PERSONAL;
        }
        if (normalized.startsWith("[DATA]")) {
            return TYPE_DATA;
        }
        return request.getAssetId() == null ? TYPE_BUSINESS : TYPE_DATA;
    }

    private String normalizeApplyReason(String reason, String roleCode, User currentUser) {
        String base = reason == null ? "" : reason.trim();
        String upper = base.toUpperCase();
        String normalized = base;
        if (!upper.startsWith("[DATA]") && !upper.startsWith("[BUSINESS]") && !upper.startsWith("[GOVERNANCE]") && !upper.startsWith("[PERSONAL]")) {
            if (isEmployeeRole(roleCode)) {
                normalized = "[PERSONAL] " + base;
            } else if (isBusinessOwnerRole(roleCode)) {
                normalized = "[BUSINESS] " + base;
            } else if (isDataAdminRole(roleCode)) {
                normalized = "[DATA] " + base;
            } else {
                normalized = "[DATA] " + base;
            }
        }
        return normalized + buildTraceSnapshot(currentUser, roleCode);
    }

    private boolean isAdminRole(String roleCode) {
        return ROLE_ADMIN.equalsIgnoreCase(roleCode);
    }

    private boolean isDataAdminRole(String roleCode) {
        return ROLE_DATA_ADMIN.equalsIgnoreCase(roleCode);
    }

    private boolean isBusinessOwnerRole(String roleCode) {
        return ROLE_BUSINESS_OWNER.equalsIgnoreCase(roleCode);
    }

    private boolean isEmployeeRole(String roleCode) {
        return ROLE_EMPLOYEE.equalsIgnoreCase(roleCode);
    }

    private String normalizeDecisionStatus(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.trim().toLowerCase();
        if (STATUS_APPROVE.equals(raw.trim()) || "approve".equals(normalized) || "approved".equals(normalized) || "pass".equals(normalized)) {
            return STATUS_APPROVE;
        }
        if (STATUS_REJECT.equals(raw.trim()) || "reject".equals(normalized) || "rejected".equals(normalized) || "deny".equals(normalized)) {
            return STATUS_REJECT;
        }
        return null;
    }

    private String buildTraceSnapshot(User currentUser, String roleCode) {
        if (currentUser == null) {
            return "";
        }
        String username = currentUser.getUsername() == null ? "-" : currentUser.getUsername();
        String department = currentUser.getDepartment() == null ? "-" : currentUser.getDepartment();
        String position = currentUser.getJobTitle() == null ? "-" : currentUser.getJobTitle();
        String deviceId = currentUser.getDeviceId() == null ? "-" : currentUser.getDeviceId();
        Long companyId = currentUser.getCompanyId();
        return String.format(" [TRACE username=%s userId=%s role=%s department=%s position=%s companyId=%s device=%s]",
            username,
            currentUser.getId(),
            roleCode == null ? "-" : roleCode,
            department,
            position,
            companyId == null ? "-" : companyId,
            deviceId
        );
    }

    public static class ApproveReq { public Long getRequestId(){return requestId;} public void setRequestId(Long id){this.requestId=id;} public String getStatus(){return status;} public void setStatus(String s){this.status=s;} private Long requestId; private String status; }

    public static class IdReq { @NotNull private Long id; public Long getId(){return id;} public void setId(Long id){this.id=id;} }
}
