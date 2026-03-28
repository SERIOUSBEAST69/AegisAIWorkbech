package com.trustai.controller;

import com.trustai.document.AuditLogDocument;
import com.trustai.service.AuditLogService;
import com.trustai.service.CompanyScopeService;
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

    @GetMapping("/search")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS')")
    public R<List<AuditLogDocument>> search(@RequestParam(required = false) Long userId,
                                            @RequestParam(required = false) String operation,
                                            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date from,
                                            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date to) {
        Set<Long> scopedUserIds = companyScopeService.companyUserIds().stream().collect(Collectors.toSet());
        List<AuditLogDocument> logs = auditLogService.search(userId, operation, from, to)
            .stream()
            .filter(item -> item.getUserId() != null && scopedUserIds.contains(item.getUserId()))
            .collect(Collectors.toList());
        return R.ok(logs);
    }

    @PostMapping("/delete")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> delete(@RequestBody @Validated IdReq req) {
        auditLogService.removeById(req.getId());
        return R.okMsg("删除成功");
    }

    public static class IdReq {
        @NotNull private Long id;
        public Long getId(){return id;}
        public void setId(Long id){this.id=id;}
    }
}
