package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.entity.ApprovalRequest;
import com.trustai.entity.Role;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import com.trustai.service.ApprovalRequestService;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CurrentUserService;
import com.trustai.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/approval")
@Validated
public class ApprovalController {
    @Autowired private ApprovalRequestService approvalRequestService;
    @Autowired private CurrentUserService currentUserService;
    @Autowired private CompanyScopeService companyScopeService;

    private static final Set<String> APPROVE_STATUS = new HashSet<>(Arrays.asList("通过", "拒绝"));
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_DATA_ADMIN = "DATA_ADMIN";
    private static final String ROLE_BUSINESS_OWNER = "BUSINESS_OWNER";
    private static final String ROLE_EMPLOYEE = "EMPLOYEE";
    private static final String TYPE_DATA = "DATA";
    private static final String TYPE_BUSINESS = "BUSINESS";
    private static final String TYPE_PERSONAL = "PERSONAL";

    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN','BUSINESS_OWNER','EMPLOYEE')")
    public R<List<ApprovalRequest>> list(@RequestParam(required = false) Long applicantId,
                                         @RequestParam(required = false) Long assetId) {
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

    @PostMapping("/apply")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN','BUSINESS_OWNER','EMPLOYEE')")
    public R<?> apply(@RequestBody ApprovalRequest req) {
        User currentUser = currentUserService.requireCurrentUser();
        String roleCode = currentUserService.currentRoleCode();
        req.setCompanyId(companyScopeService.requireCompanyId());
        req.setApplicantId(currentUser.getId());
        req.setReason(normalizeApplyReason(req.getReason(), roleCode));
        req.setApproverId(null);
        req.setStatus(null);
        req.setTaskId(null);
        req.setProcessInstanceId(null);
        approvalRequestService.startApproval(req);
        return R.okMsg("提交成功");
    }

    @PostMapping("/reject")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN','BUSINESS_OWNER')")
    public R<?> reject(@RequestBody ApproveReq req) {
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
        // 驳回并回退
        approvalRequestService.approve(req.getRequestId(), currentUser.getId(), "拒绝");
        ApprovalRequest after = approvalRequestService.getById(req.getRequestId());
        java.util.Map<String, Object> detail = new java.util.HashMap<>();
        detail.put("requestId", req.getRequestId());
        detail.put("previousStatus", prevStatus);
        detail.put("currentStatus", after == null ? "驳回" : after.getStatus());
        detail.put("approverId", currentUser.getId());
        detail.put("message", "审批已驳回，状态已从「" + prevStatus + "」回退至「驳回」。");
        return R.ok(detail);
    }

    @PostMapping("/approve")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN','BUSINESS_OWNER')")
    public R<?> approve(@RequestBody ApproveReq req) {
        if (!APPROVE_STATUS.contains(req.getStatus())) return R.error(40000, "不支持的状态");
        User currentUser = currentUserService.requireCurrentUser();
        String roleCode = currentUserService.currentRoleCode();
        ApprovalRequest request = approvalRequestService.getOne(
            companyScopeService.withCompany(new QueryWrapper<ApprovalRequest>()).eq("id", req.getRequestId())
        );
        if (request == null) return R.error(40000, "申请不存在");
        if (!canOperateRequest(roleCode, request, currentUser)) {
            throw new BizException(40300, "当前身份无权审批该类型申请");
        }
        approvalRequestService.approve(req.getRequestId(), currentUser.getId(), req.getStatus());
        return R.okMsg("审批完成");
    }

    @GetMapping("/todo")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN','BUSINESS_OWNER')")
    public R<List<ApprovalRequest>> todo() {
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
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN','BUSINESS_OWNER','EMPLOYEE')")
    public R<?> delete(@RequestBody @Validated IdReq req) {
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
        return R.okMsg("删除成功");
    }

    private boolean isApprovalOperator(User user) {
        Role role = currentUserService.getCurrentRole(user);
        if (role == null || role.getCode() == null) {
            return false;
        }
        return Arrays.asList(ROLE_ADMIN, ROLE_DATA_ADMIN, ROLE_BUSINESS_OWNER).contains(role.getCode().toUpperCase());
    }

    private List<ApprovalRequest> filterByOperatorScope(List<ApprovalRequest> list, String roleCode, User currentUser) {
        if (list == null) {
            return List.of();
        }
        if (ROLE_ADMIN.equalsIgnoreCase(roleCode)) {
            return list;
        }
        if (ROLE_DATA_ADMIN.equalsIgnoreCase(roleCode)) {
            return list.stream().filter(item -> TYPE_DATA.equals(resolveRequestType(item))).toList();
        }
        if (ROLE_BUSINESS_OWNER.equalsIgnoreCase(roleCode)) {
            return list.stream().filter(item -> TYPE_BUSINESS.equals(resolveRequestType(item))).toList();
        }
        return list.stream()
            .filter(item -> currentUser.getId() != null && currentUser.getId().equals(item.getApplicantId()))
            .toList();
    }

    private boolean canOperateRequest(String roleCode, ApprovalRequest request, User currentUser) {
        if (ROLE_ADMIN.equalsIgnoreCase(roleCode)) {
            return true;
        }
        String type = resolveRequestType(request);
        if (ROLE_DATA_ADMIN.equalsIgnoreCase(roleCode)) {
            return TYPE_DATA.equals(type);
        }
        if (ROLE_BUSINESS_OWNER.equalsIgnoreCase(roleCode)) {
            return TYPE_BUSINESS.equals(type);
        }
        return currentUser.getId() != null && currentUser.getId().equals(request.getApplicantId());
    }

    private boolean canDeleteRequest(String roleCode, ApprovalRequest request, User currentUser) {
        if (ROLE_ADMIN.equalsIgnoreCase(roleCode)) {
            return true;
        }
        if (ROLE_EMPLOYEE.equalsIgnoreCase(roleCode)) {
            return currentUser.getId() != null && currentUser.getId().equals(request.getApplicantId());
        }
        return canOperateRequest(roleCode, request, currentUser);
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

    private String normalizeApplyReason(String reason, String roleCode) {
        String base = reason == null ? "" : reason.trim();
        String upper = base.toUpperCase();
        if (upper.startsWith("[DATA]") || upper.startsWith("[BUSINESS]") || upper.startsWith("[PERSONAL]")) {
            return base;
        }
        if (ROLE_EMPLOYEE.equalsIgnoreCase(roleCode)) {
            return "[PERSONAL] " + base;
        }
        if (ROLE_BUSINESS_OWNER.equalsIgnoreCase(roleCode)) {
            return "[BUSINESS] " + base;
        }
        if (ROLE_DATA_ADMIN.equalsIgnoreCase(roleCode)) {
            return "[DATA] " + base;
        }
        return "[DATA] " + base;
    }

    public static class ApproveReq { public Long getRequestId(){return requestId;} public void setRequestId(Long id){this.requestId=id;} public String getStatus(){return status;} public void setStatus(String s){this.status=s;} private Long requestId; private String status; }

    public static class IdReq { @NotNull private Long id; public Long getId(){return id;} public void setId(Long id){this.id=id;} }
}
