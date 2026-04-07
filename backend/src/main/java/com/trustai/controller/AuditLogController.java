package com.trustai.controller;

import com.trustai.document.AuditLogDocument;
import com.trustai.exception.BizException;
import com.trustai.service.AuditLogService;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CurrentUserService;
import com.trustai.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.stream.Collectors;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/audit-log")
@Validated
public class AuditLogController {
    @Autowired private AuditLogService auditLogService;
    @Autowired private CompanyScopeService companyScopeService;
    @Autowired private CurrentUserService currentUserService;

    @GetMapping("/search")
    @PreAuthorize("@currentUserService.hasPermission('audit:log:view')")
    public R<List<AuditLogDocument>> search(@RequestParam(required = false) Long userId,
                                            @RequestParam(required = false) Long permissionId,
                                            @RequestParam(required = false) String operation,
                                            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date from,
                                            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date to) {
        String roleCode = String.valueOf(currentUserService.currentRoleCode() == null ? "" : currentUserService.currentRoleCode())
            .trim().toUpperCase(Locale.ROOT);
        Long currentUserId = currentUserService.requireCurrentUser().getId();
        Long effectiveUserId = scopedUserId(roleCode, currentUserId, userId);

        Set<Long> scopedUserIds = companyScopeService.companyUserIds().stream().collect(Collectors.toSet());
        String effectiveOperation = scopedOperationKeyword(roleCode, operation);
        List<AuditLogDocument> logs = auditLogService.search(effectiveUserId, permissionId, effectiveOperation, from, to)
            .stream()
            .filter(item -> item.getUserId() != null && scopedUserIds.contains(item.getUserId()))
            .filter(item -> roleScopedFilter(roleCode, currentUserId, item))
            .collect(Collectors.toList());
        return R.ok(logs);
    }

    private Long scopedUserId(String roleCode, Long currentUserId, Long requestedUserId) {
        if (roleCode == null) {
            return requestedUserId;
        }
        if ("BUSINESS_OWNER".equals(roleCode) || "AUDIT".equals(roleCode)) {
            return currentUserId;
        }
        return requestedUserId;
    }

    private String scopedOperationKeyword(String roleCode, String operation) {
        String requested = operation == null ? "" : operation.trim();
        if ("SECOPS".equals(roleCode)) {
            return requested.isEmpty() ? "security" : requested;
        }
        if ("BUSINESS_OWNER".equals(roleCode)) {
            return requested.isEmpty() ? "approval" : requested;
        }
        if ("AUDIT".equals(roleCode)) {
            return requested.isEmpty() ? "audit" : requested;
        }
        return requested;
    }

    private boolean roleScopedFilter(String roleCode, Long currentUserId, AuditLogDocument log) {
        if (log == null) {
            return false;
        }
        String operation = String.valueOf(log.getOperation() == null ? "" : log.getOperation()).toLowerCase(Locale.ROOT);
        if ("SECOPS".equals(roleCode)) {
            return operation.contains("security") || operation.contains("threat") || operation.contains("anomaly") || operation.contains("shadow");
        }
        if ("BUSINESS_OWNER".equals(roleCode)) {
            return java.util.Objects.equals(currentUserId, log.getUserId())
                && (operation.contains("approval") || operation.contains("governance"));
        }
        if ("AUDIT".equals(roleCode)) {
            return operation.contains("audit") || operation.contains("governance") || operation.contains("risk");
        }
        if ("ADMIN_REVIEWER".equals(roleCode)) {
            return java.util.Objects.equals(currentUserId, log.getUserId());
        }
        return true;
    }

    @PostMapping("/delete")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> delete(@RequestBody @Validated IdReq req) {
        throw new BizException(40300, "审计日志不允许物理删除，请走审计归档流程");
    }

    public static class IdReq {
        @NotNull private Long id;
        public Long getId(){return id;}
        public void setId(Long id){this.id=id;}
    }
}
