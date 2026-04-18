package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.entity.SubjectRequest;
import com.trustai.entity.User;
import com.trustai.service.CompanyScopeService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.SubjectRequestService;
import com.trustai.service.UserService;
import com.trustai.utils.R;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.SecureRandom;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

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
    private static final Set<String> ALLOWED_SOURCE = new HashSet<>(Arrays.asList("web", "app", "api", "offline"));
    private static final DateTimeFormatter REQUEST_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> OPERATOR_ROLES = new HashSet<>(Arrays.asList(
        "ADMIN",
        "SECOPS",
        "ADMIN_REVIEWER"
    ));

    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS','BUSINESS_OWNER','AUDIT')")
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
        List<SubjectRequest> scoped = subjectRequestService.list(qw);
        return R.ok(filterWalkthroughRows(scoped));
    }

    private List<SubjectRequest> filterWalkthroughRows(List<SubjectRequest> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        Set<Long> ids = new HashSet<>();
        for (SubjectRequest item : rows) {
            if (item == null) {
                continue;
            }
            if (item.getUserId() != null) {
                ids.add(item.getUserId());
            }
            if (item.getHandlerId() != null) {
                ids.add(item.getHandlerId());
            }
        }
        Map<Long, String> usernameById = new HashMap<>();
        if (!ids.isEmpty()) {
            List<User> users = userService.lambdaQuery().in(User::getId, ids).list();
            for (User user : users) {
                if (user != null && user.getId() != null) {
                    usernameById.put(user.getId(), String.valueOf(user.getUsername() == null ? "" : user.getUsername()));
                }
            }
        }
        return rows.stream()
            .filter(item -> {
                String applicant = usernameById.getOrDefault(item.getUserId(), "");
                String handler = usernameById.getOrDefault(item.getHandlerId(), "");
                String comment = String.valueOf(item.getComment() == null ? "" : item.getComment());
                String result = String.valueOf(item.getResult() == null ? "" : item.getResult());
                return !isWalkthrough(applicant)
                    && !isWalkthrough(handler)
                    && !containsWalkthrough(comment)
                    && !containsWalkthrough(result);
            })
            .collect(Collectors.toList());
    }

    private boolean isWalkthrough(String value) {
        return String.valueOf(value == null ? "" : value).trim().toLowerCase().contains("walkthrough");
    }

    private boolean containsWalkthrough(String value) {
        return String.valueOf(value == null ? "" : value).toLowerCase().contains("walkthrough");
    }

    @PostMapping("/create")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','BUSINESS_OWNER')")
    public R<?> create(@RequestBody @Validated ApplyReq req, HttpServletRequest httpRequest) {
        String type = req.getType() == null ? "" : req.getType().trim().toLowerCase();
        if (!ALLOWED_TYPE.contains(type)) return R.error(40000, "不支持的类型");
        User currentUser = currentUserService.requireCurrentUser();
        Long companyId = companyScopeService.requireCompanyId();
        String roleCode = currentUserService.currentRoleCode();

        if (currentUserService.hasRole("BUSINESS_OWNER")) {
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
        entity.setRequestNo(generateRequestNo());
        entity.setCompanyId(companyId);
        entity.setUserId(effectiveUserId);
        entity.setRequestSource(normalizeSource(req.getRequestSource()));
        entity.setType(type);
        entity.setComment(appendTraceComment(req.getComment(), currentUser, roleCode, companyId));
        entity.setStatus("pending");
        Date now = new Date();
        entity.setDeadlineAt(addBusinessDays(now, 15));
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        subjectRequestService.save(entity);
        return R.ok(entity);
    }

    private R<?> validateEmployeeCreateDuty(User currentUser, String requestType) {
        if (currentUser == null || !currentUserService.hasRole("BUSINESS_OWNER")) {
            return R.error(40300, "当前账号不可提交主体请求");
        }
        return null;
    }

    @PostMapping("/process")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','ADMIN_REVIEWER','SECOPS')")
    public R<?> process(@RequestBody @Validated ProcessReq req, HttpServletRequest httpRequest) {
        SubjectRequest sr = subjectRequestService.getById(req.getId());
        if (sr == null || !java.util.Objects.equals(sr.getCompanyId(), companyScopeService.requireCompanyId())) {
            return R.error(40400, "工单不存在或不在当前公司");
        }
        String targetStatus = req.getStatus() == null ? "" : req.getStatus().trim().toLowerCase();
        if (!ALLOWED_STATUS.contains(targetStatus)) return R.error(40000, "不支持的状态");
        if (FINAL_STATUS.contains(sr.getStatus())) return R.error(40000, "已完结的工单不可再次处理");
        if (!canTransit(sr.getStatus(), targetStatus)) return R.error(40000, "状态流转不合法");
        User currentUser = currentUserService.requireCurrentUser();
        String currentRoleCode = currentUserService.currentRoleCode();
        String roleUpper = currentRoleCode == null ? "" : currentRoleCode.trim().toUpperCase();
        if ("pending".equals(sr.getStatus()) && !"ADMIN".equals(roleUpper)) {
            return R.error(40300, "仅治理管理员可处理待处理工单");
        }
        if (sr.getUserId() != null && sr.getUserId().equals(currentUser.getId())) {
            return R.error(40300, "申请人不能处理自己的工单");
        }
        if ("processing".equals(sr.getStatus()) && sr.getHandlerId() != null && !currentUser.getId().equals(sr.getHandlerId())) {
            return R.error(40300, "处理中工单仅处理人可操作");
        }
        if ("processing".equals(sr.getStatus()) && "rejected".equals(targetStatus)) {
            return R.error(40000, "处理中工单仅允许完成，不能驳回");
        }
        sr.setStatus(targetStatus);
        sr.setHandlerId(currentUser.getId());
        sr.setResult(appendProcessTrace(req.getResult(), currentUser, targetStatus, httpRequest));
        sr.setUpdateTime(new Date());
        subjectRequestService.updateById(sr);
        return R.okMsg("处理完成");
    }

    @PostMapping("/delete")
    @PreAuthorize("@currentUserService.hasAnyRole('ADMIN','SECOPS','BUSINESS_OWNER')")
    public R<?> delete(@RequestBody @Validated IdReq req) {
        return R.error(40300, "主体权利工单为合规留痕数据，不可删除");
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
            return "done".equals(to);
        }
        return false;
    }

    private String normalizeSource(String source) {
        String value = String.valueOf(source == null ? "" : source).trim().toLowerCase();
        if (ALLOWED_SOURCE.contains(value)) {
            return value;
        }
        return "web";
    }

    private Date addBusinessDays(Date base, int businessDays) {
        LocalDateTime cursor = LocalDateTime.ofInstant(base.toInstant(), ZONE);
        int added = 0;
        while (added < businessDays) {
            cursor = cursor.plusDays(1);
            DayOfWeek day = cursor.getDayOfWeek();
            if (day != DayOfWeek.SATURDAY && day != DayOfWeek.SUNDAY) {
                added++;
            }
        }
        return Date.from(cursor.atZone(ZONE).toInstant());
    }

    private String generateRequestNo() {
        for (int i = 0; i < 5; i++) {
            String timePart = REQUEST_NO_TIME.format(LocalDateTime.now());
            String randomPart = String.format("%06d", RANDOM.nextInt(1_000_000));
            String requestNo = timePart + randomPart;
            long exists = subjectRequestService.count(new QueryWrapper<SubjectRequest>().eq("request_no", requestNo));
            if (exists == 0) {
                return requestNo;
            }
        }
        return REQUEST_NO_TIME.format(LocalDateTime.now()) + System.nanoTime() % 1_000_000;
    }

    private String appendProcessTrace(String result, User operator, String targetStatus, HttpServletRequest request) {
        String base = String.valueOf(result == null ? "" : result).trim();
        String ip = request == null ? "-" : String.valueOf(request.getRemoteAddr() == null ? "-" : request.getRemoteAddr());
        String trace = String.format("[FLOW action=%s operator=%s operatorId=%s ip=%s at=%d]",
            targetStatus,
            operator == null || operator.getUsername() == null ? "-" : operator.getUsername(),
            operator == null || operator.getId() == null ? "-" : operator.getId(),
            ip,
            System.currentTimeMillis()
        );
        if (base.isEmpty()) {
            return trace;
        }
        return base + " " + trace;
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
        private String requestSource;
        private String comment;
        public Long getUserId(){return userId;}
        public void setUserId(Long v){userId=v;}
        public String getType(){return type;}
        public void setType(String v){type=v;}
        public String getRequestSource(){return requestSource;}
        public void setRequestSource(String v){requestSource=v;}
        public String getComment(){return comment;}
        public void setComment(String v){comment=v;}
    }

    public static class ProcessReq {
        @NotNull private Long id;
        @NotBlank private String status;
        private String result;
        public Long getId(){return id;}
        public void setId(Long v){id=v;}
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
