package com.trustai.controller;

import com.trustai.document.AuditLogDocument;
import com.trustai.exception.BizException;
import com.trustai.service.AuditLogService;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.UserService;
import com.trustai.entity.User;
import com.trustai.utils.R;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/audit-log")
@Validated
public class AuditLogController {
    @Autowired private AuditLogService auditLogService;
    @Autowired private CompanyScopeService companyScopeService;
    @Autowired private CurrentUserService currentUserService;
    @Autowired private UserService userService;

    @GetMapping("/search")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','AUDIT','BUSINESS_OWNER')")
    public R<List<AuditLogDocument>> search(@RequestParam(required = false) Long userId,
                                            @RequestParam(required = false) Long permissionId,
                                            @RequestParam(required = false) String operation,
                                            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date from,
                                            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date to) {
        currentUserService.requireAnyRole("ADMIN", "ADMIN_REVIEWER", "SECOPS", "AUDIT", "BUSINESS_OWNER");
        Set<Long> scopedUserIds = companyScopeService.companyUserIds().stream().collect(Collectors.toSet());
        User currentUser = currentUserService.requireCurrentUser();
        if (currentUserService.hasRole("BUSINESS_OWNER")) {
            scopedUserIds.retainAll(resolveBusinessOwnerAuditScope(currentUser));
        }
        List<AuditLogDocument> logs = auditLogService.search(userId, permissionId, operation, from, to)
            .stream()
            .filter(item -> item.getUserId() != null && scopedUserIds.contains(item.getUserId()))
            .peek(item -> {
                if ((item.getUserIdStr() == null || item.getUserIdStr().isBlank()) && item.getUserId() != null) {
                    item.setUserIdStr(String.valueOf(item.getUserId()));
                }
            })
            .collect(Collectors.toList());
        return R.ok(logs);
    }

    private Set<Long> resolveBusinessOwnerAuditScope(User currentUser) {
        Set<Long> allowed = new java.util.HashSet<>();
        if (currentUser == null || currentUser.getId() == null) {
            return allowed;
        }
        allowed.add(currentUser.getId());
        String department = currentUser.getDepartment();
        if (department == null || department.trim().isEmpty()) {
            return allowed;
        }
        Long companyId = currentUser.getCompanyId();
        if (companyId == null) {
            return allowed;
        }
        List<User> sameDepartmentUsers = userService.lambdaQuery()
            .eq(User::getCompanyId, companyId)
            .eq(User::getDepartment, department)
            .list();
        for (User item : sameDepartmentUsers) {
            if (item != null && item.getId() != null) {
                allowed.add(item.getId());
            }
        }
        return allowed;
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
