package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.entity.SubjectRequest;
import com.trustai.entity.User;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.SubjectRequestService;
import com.trustai.service.UserService;
import com.trustai.utils.R;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/subject-request")
@Validated
public class SubjectRequestController {

    @Autowired
    private SubjectRequestService subjectRequestService;

    @Autowired
    private CurrentUserService currentUserService;

    @Autowired
    private CompanyScopeService companyScopeService;

    @Autowired
    private UserService userService;

    private static final Set<String> ALLOWED_STATUS = new HashSet<>(Arrays.asList("pending", "processing", "done", "rejected"));
    private static final Set<String> FINAL_STATUS = new HashSet<>(Arrays.asList("done", "rejected"));
    private static final Set<String> ALLOWED_TYPE = new HashSet<>(Arrays.asList("access", "export", "delete"));
    private static final Set<String> OPERATOR_ROLES = new HashSet<>(Arrays.asList(
        "ADMIN",
        "DATA_ADMIN",
        "BUSINESS_OWNER"
    ));

    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','DATA_ADMIN','BUSINESS_OWNER','EMPLOYEE')")
    public R<List<SubjectRequest>> list(@RequestParam(required = false) String status) {
        User currentUser = currentUserService.requireCurrentUser();
        String roleCode = currentUserService.currentRoleCode();
        QueryWrapper<SubjectRequest> qw = companyScopeService.withCompany(new QueryWrapper<>());
        if (status != null && !status.isEmpty() && ALLOWED_STATUS.contains(status)) {
            qw.eq("status", status);
        } else {
            qw.in("status", ALLOWED_STATUS);
        }
        if (!OPERATOR_ROLES.contains(roleCode == null ? "" : roleCode.toUpperCase())) {
            qw.eq("user_id", currentUser.getId());
        }
        qw.isNotNull("user_id").orderByDesc("create_time");
        return R.ok(subjectRequestService.list(qw));
    }

    @PostMapping("/create")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','EMPLOYEE')")
    public R<?> create(@RequestBody @Validated ApplyReq req) {
        String type = req.getType() == null ? "" : req.getType().trim().toLowerCase();
        if (!ALLOWED_TYPE.contains(type)) return R.error(40000, "不支持的类型");
        User currentUser = currentUserService.requireCurrentUser();
        Long companyId = companyScopeService.requireCompanyId();
        String roleCode = currentUserService.currentRoleCode();

        if (currentUserService.hasRole("EMPLOYEE")) {
            R<?> deny = validateEmployeeCreateDuty(currentUser, type);
            if (deny != null) {
                return deny;
            }
        }

        Long effectiveUserId = currentUser.getId();
        if (req.getUserId() != null) {
            if (!"ADMIN".equalsIgnoreCase(roleCode)) {
                return R.error(40300, "仅治理管理员可代他人创建主体请求");
            }
            User target = userService.lambdaQuery()
                .eq(User::getCompanyId, companyId)
                .eq(User::getId, req.getUserId())
                .one();
            if (target == null) {
                return R.error(40300, "目标用户不属于当前公司");
            }
            effectiveUserId = target.getId();
        }

        SubjectRequest entity = new SubjectRequest();
        entity.setCompanyId(companyId);
        entity.setUserId(effectiveUserId);
        entity.setType(type);
        entity.setComment(appendTraceComment(req.getComment(), currentUser, roleCode, companyId));
        entity.setStatus("pending");
        Date now = new Date();
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        subjectRequestService.save(entity);
        return R.ok(entity);
    }

    private R<?> validateEmployeeCreateDuty(User currentUser, String requestType) {
        if (currentUser == null || !currentUserService.hasRole("EMPLOYEE")) {
            return R.error(40300, "当前员工账号不可提交主体请求");
        }
        return null;
    }

    @PostMapping("/process")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> process(@RequestBody @Validated ProcessReq req) {
        SubjectRequest sr = subjectRequestService.getById(req.getId());
        if (sr == null || !java.util.Objects.equals(sr.getCompanyId(), companyScopeService.requireCompanyId())) {
            return R.error(40400, "工单不存在或不在当前公司");
        }
        String targetStatus = req.getStatus() == null ? "" : req.getStatus().trim().toLowerCase();
        if (!ALLOWED_STATUS.contains(targetStatus)) return R.error(40000, "不支持的状态");
        if (FINAL_STATUS.contains(sr.getStatus())) return R.error(40000, "已完结的工单不可再次处理");
        if (!canTransit(sr.getStatus(), targetStatus)) return R.error(40000, "状态流转不合法");
        if (req.getHandlerId() == null) return R.error(40000, "处理人不能为空");
        User currentUser = currentUserService.requireCurrentUser();
        if (!req.getHandlerId().equals(currentUser.getId())) {
            return R.error(40300, "处理人必须为当前登录账号");
        }
        sr.setStatus(targetStatus);
        sr.setHandlerId(currentUser.getId());
        sr.setResult(req.getResult());
        sr.setUpdateTime(new Date());
        subjectRequestService.updateById(sr);
        return R.okMsg("处理完成");
    }

    @PostMapping("/delete")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> delete(@RequestBody @Validated IdReq req) {
        SubjectRequest existing = subjectRequestService.getOne(
            companyScopeService.withCompany(new QueryWrapper<SubjectRequest>()).eq("id", req.getId())
        );
        if (existing == null) {
            return R.error(40000, "工单不存在或已删除");
        }
        boolean removed = subjectRequestService.removeById(req.getId());
        if (!removed) {
            return R.error(40000, "工单删除失败，请刷新后重试");
        }
        return R.okMsg("删除成功");
    }

    private boolean canTransit(String source, String target) {
        String from = source == null ? "pending" : source.trim().toLowerCase();
        String to = target == null ? "" : target.trim().toLowerCase();
        if (FINAL_STATUS.contains(from)) {
            return false;
        }
        if ("pending".equals(from)) {
            return "processing".equals(to) || "rejected".equals(to);
        }
        if ("processing".equals(from)) {
            return "done".equals(to) || "rejected".equals(to);
        }
        return false;
    }

    private String appendTraceComment(String comment, User currentUser, String roleCode, Long companyId) {
        String base = comment == null ? "" : comment.trim();
        if (currentUser == null) {
            return base;
        }
        String username = currentUser.getUsername() == null ? "-" : currentUser.getUsername();
        String department = currentUser.getDepartment() == null ? "-" : currentUser.getDepartment();
        String position = currentUser.getJobTitle() == null ? "-" : currentUser.getJobTitle();
        String deviceId = currentUser.getDeviceId() == null ? "-" : currentUser.getDeviceId();
        String snapshot = String.format(" [TRACE username=%s userId=%s role=%s department=%s position=%s companyId=%s device=%s]",
            username,
            currentUser.getId(),
            roleCode == null ? "-" : roleCode,
            department,
            position,
            companyId == null ? "-" : companyId,
            deviceId
        );
        return base + snapshot;
    }

    public static class ApplyReq {
        private Long userId;
        @NotBlank private String type;
        private String comment;
        public Long getUserId(){return userId;}
        public void setUserId(Long v){userId=v;}
        public String getType(){return type;}
        public void setType(String v){type=v;}
        public String getComment(){return comment;}
        public void setComment(String v){comment=v;}
    }

    public static class ProcessReq {
        @NotNull private Long id;
        @NotBlank private String status;
        @NotNull private Long handlerId;
        private String result;
        public Long getId(){return id;}
        public void setId(Long v){id=v;}
        public Long getHandlerId(){return handlerId;}
        public void setHandlerId(Long v){handlerId=v;}
        public String getStatus(){return status;}
        public void setStatus(String v){status=v;}
        public String getResult(){return result;}
        public void setResult(String v){result=v;}
    }

    public static class IdReq {
        @NotNull private Long id;
        public Long getId(){return id;}
        public void setId(Long v){id=v;}
    }
}
