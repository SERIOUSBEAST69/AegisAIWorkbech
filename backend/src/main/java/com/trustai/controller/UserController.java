package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.dto.ChangePasswordDTO;
import com.trustai.dto.UserProfileDTO;
import com.trustai.dto.UserUpdateDTO;
import com.trustai.entity.AuditLog;
import com.trustai.entity.Company;
import com.trustai.entity.Role;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import com.trustai.service.AuditLogService;
import com.trustai.service.CompanyService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.RoleService;
import com.trustai.service.UserService;
import com.trustai.utils.R;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/user")
@Validated
public class UserController {
    private static final String ACCOUNT_STATUS_PENDING = "pending";
    private static final String ACCOUNT_STATUS_ACTIVE = "active";
    private static final String ACCOUNT_STATUS_REJECTED = "rejected";
    private static final long MAX_AVATAR_SIZE_BYTES = 2L * 1024 * 1024;
    private static final Set<String> ALLOWED_AVATAR_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".gif", ".webp");
    private static final Set<String> ALLOWED_AVATAR_CONTENT_TYPES = Set.of("image/png", "image/jpeg", "image/gif", "image/webp");

    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CurrentUserService currentUserService;
    @Autowired private CompanyService companyService;
    @Autowired private AuditLogService auditLogService;
    @Autowired private RoleService roleService;

    @GetMapping("/list")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<List<User>> list(@RequestParam(required = false) String username,
                              @RequestParam(required = false) String accountStatus,
                              @RequestParam(required = false) String accountType) {
        currentUserService.requireAdmin();
        Long companyId = currentUserService.requireCurrentUser().getCompanyId();
        QueryWrapper<User> qw = new QueryWrapper<>();
        if (companyId != null) {
            qw.eq("company_id", companyId);
        }
        if (username != null && !username.isEmpty()) qw.like("username", username);
        if (accountStatus != null && !accountStatus.isEmpty()) qw.eq("account_status", accountStatus);
        if (accountType != null && !accountType.isEmpty()) qw.eq("account_type", accountType);
        List<User> list = userService.list(qw);
        list.forEach(u -> u.setPassword(null));
        return R.ok(list);
    }

    @GetMapping("/pending")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<List<User>> pendingList() {
        currentUserService.requireAdmin();
        Long companyId = currentUserService.requireCurrentUser().getCompanyId();
        QueryWrapper<User> qw = new QueryWrapper<User>()
            .eq("account_type", "real")
            .eq("account_status", ACCOUNT_STATUS_PENDING);
        if (companyId != null) {
            qw.eq("company_id", companyId);
        }
        List<User> list = userService.list(qw);
        list.forEach(u -> u.setPassword(null));
        return R.ok(list);
    }

    @PostMapping("/approve")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> approve(@Valid @RequestBody ApproveReq req) {
        currentUserService.requireAdmin();
        User admin = currentUserService.requireCurrentUser();
        User user = requireCompanyUser(req.getId(), admin.getCompanyId());
        user.setAccountStatus(ACCOUNT_STATUS_ACTIVE);
        user.setRejectReason(null);
        user.setApprovedBy(admin.getId());
        user.setApprovedAt(new Date());
        user.setStatus(1);
        user.setUpdateTime(new Date());
        userService.updateById(user);
        writeApprovalAudit(admin, user, "approve", "账号审批通过");
        return R.okMsg("审批通过");
    }

    @PostMapping("/reject")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> reject(@Valid @RequestBody RejectReq req) {
        currentUserService.requireAdmin();
        User admin = currentUserService.requireCurrentUser();
        User user = requireCompanyUser(req.getId(), admin.getCompanyId());
        user.setAccountStatus(ACCOUNT_STATUS_REJECTED);
        user.setRejectReason(req.getReason());
        user.setApprovedBy(admin.getId());
        user.setApprovedAt(new Date());
        user.setUpdateTime(new Date());
        userService.updateById(user);
        writeApprovalAudit(admin, user, "reject", StringUtils.hasText(req.getReason()) ? req.getReason() : "账号审批拒绝");
        return R.okMsg("审批已拒绝");
    }

    @PostMapping("/register")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> register(@Valid @RequestBody User user) {
        currentUserService.requireAdmin();
        User currentUser = currentUserService.requireCurrentUser();
        user.setCompanyId(currentUser.getCompanyId());
        ensureRoleInCompany(user.getRoleId(), currentUser.getCompanyId());
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        if (!StringUtils.hasText(user.getAccountType())) {
            user.setAccountType("real");
        }
        if (!StringUtils.hasText(user.getAccountStatus())) {
            user.setAccountStatus(ACCOUNT_STATUS_ACTIVE);
        }
        user.setApprovedBy(currentUserService.requireCurrentUser().getId());
        user.setApprovedAt(new Date());
        user.setRejectReason(null);
        user.setCreateTime(new Date());
        user.setUpdateTime(new Date());
        userService.save(user);
        return R.okMsg("注册成功");
    }

    @PostMapping("/update")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> update(@Valid @RequestBody User user) {
        currentUserService.requireAdmin();
        User existing = userService.getById(user.getId());
        if (existing == null || !java.util.Objects.equals(existing.getCompanyId(), currentUserService.requireCurrentUser().getCompanyId())) {
            throw new BizException(40400, "用户不存在或不在当前公司");
        }
        if (user.getRoleId() != null) {
            ensureRoleInCompany(user.getRoleId(), existing.getCompanyId());
        }
        if (user.getPassword() != null && !user.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(user.getPassword()));
        }
        user.setCompanyId(existing.getCompanyId());
        user.setUpdateTime(new Date());
        userService.updateById(user);
        return R.okMsg("更新成功");
    }

    @PostMapping("/delete")
    @PreAuthorize("@currentUserService.hasRole('ADMIN')")
    public R<?> delete(@Valid @RequestBody IdReq req) {
        currentUserService.requireAdmin();
        User existing = userService.getById(req.getId());
        if (existing == null || !java.util.Objects.equals(existing.getCompanyId(), currentUserService.requireCurrentUser().getCompanyId())) {
            throw new BizException(40400, "用户不存在或不在当前公司");
        }
        userService.removeById(req.getId());
        return R.okMsg("删除成功");
    }

    public static class IdReq {
        @NotNull(message = "用户ID不能为空")
        private Long id;
        public Long getId(){return id;}
        public void setId(Long id){this.id=id;}
    }

    public static class ApproveReq {
        @NotNull(message = "用户ID不能为空")
        private Long id;
        public Long getId(){return id;}
        public void setId(Long id){this.id=id;}
    }

    public static class RejectReq {
        @NotNull(message = "用户ID不能为空")
        private Long id;
        @Size(max = 200, message = "拒绝原因不能超过200字符")
        private String reason;
        public Long getId(){return id;}
        public void setId(Long id){this.id=id;}
        public String getReason(){return reason;}
        public void setReason(String reason){this.reason=reason;}
    }

    @GetMapping("/profile")
    @PreAuthorize("isAuthenticated()")
    public R<UserProfileDTO> profile() {
        User user = currentUserService.requireCurrentUser();
        return R.ok(toProfile(user));
    }

    @PutMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("isAuthenticated()")
    public R<UserProfileDTO> updateProfile(@ModelAttribute UserUpdateDTO req) {
        User user = currentUserService.requireCurrentUser();
        if (req.getNickname() != null) user.setNickname(req.getNickname());
        if (req.getRealName() != null) user.setRealName(req.getRealName());
        if (req.getEmail() != null) user.setEmail(req.getEmail());
        if (req.getPhone() != null) user.setPhone(req.getPhone());
        if (req.getDepartment() != null) user.setDepartment(req.getDepartment());
        MultipartFile avatar = req.getAvatar();
        if (avatar != null && !avatar.isEmpty()) {
            user.setAvatar(storeAvatar(avatar));
        }
        user.setUpdateTime(new Date());
        userService.updateById(user);
        return R.ok(toProfile(user));
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public R<?> changePassword(@Validated @RequestBody ChangePasswordDTO req) {
        User user = currentUserService.requireCurrentUser();
        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new BizException(40000, "旧密码不正确");
        }
        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        user.setUpdateTime(new Date());
        userService.updateById(user);
        return R.okMsg("密码已更新");
    }

    private UserProfileDTO toProfile(User user) {
        Role role = currentUserService.getCurrentRole(user);
        Company company = user.getCompanyId() == null ? null : companyService.getById(user.getCompanyId());
        return UserProfileDTO.builder()
            .id(user.getId())
            .companyId(user.getCompanyId())
            .companyName(company == null ? null : company.getCompanyName())
            .accountType(user.getAccountType())
            .accountStatus(resolveAccountStatus(user))
            .username(user.getUsername())
            .avatar(user.getAvatar())
            .nickname(user.getNickname())
            .realName(user.getRealName())
            .email(user.getEmail())
            .phone(user.getPhone())
            .department(user.getDepartment())
            .roleName(role == null ? null : role.getName())
            .roleCode(role == null ? null : role.getCode())
            .lastActiveAt(user.getUpdateTime() == null ? null : user.getUpdateTime().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime())
            .build();
    }

    private String resolveAccountStatus(User user) {
        if (StringUtils.hasText(user.getAccountStatus())) {
            return user.getAccountStatus();
        }
        return user.getStatus() != null && user.getStatus() == 0 ? "disabled" : ACCOUNT_STATUS_ACTIVE;
    }

    private User requireCompanyUser(Long id, Long companyId) {
        User existing = userService.getById(id);
        if (existing == null || !Objects.equals(existing.getCompanyId(), companyId)) {
            throw new BizException(40400, "用户不存在或不在当前公司");
        }
        return existing;
    }

    private void writeApprovalAudit(User admin, User target, String action, String detail) {
        AuditLog log = new AuditLog();
        log.setUserId(admin.getId());
        log.setOperation("user_registration_" + action);
        log.setOperationTime(new Date());
        log.setDevice(admin.getDeviceId());
        log.setInputOverview("targetUser=" + target.getUsername());
        log.setOutputOverview(detail);
        log.setResult("success");
        log.setRiskLevel("NORMAL");
        log.setCreateTime(new Date());
        auditLogService.saveAudit(log);
    }

    private void ensureRoleInCompany(Long roleId, Long companyId) {
        if (roleId == null) {
            throw new BizException(40000, "角色不能为空");
        }
        Role role = roleService.getById(roleId);
        if (role == null || !Objects.equals(role.getCompanyId(), companyId)) {
            throw new BizException(40000, "角色不存在或不属于当前公司");
        }
    }

    private String storeAvatar(MultipartFile file) {
        try {
            if (file.getSize() > MAX_AVATAR_SIZE_BYTES) {
                throw new BizException(40000, "头像大小不能超过 2MB");
            }
            String original = file.getOriginalFilename();
            if (!StringUtils.hasText(original) || !original.contains(".")) {
                throw new BizException(40000, "头像文件名非法");
            }
            String ext = original.substring(original.lastIndexOf('.')).toLowerCase(Locale.ROOT);
            if (!ALLOWED_AVATAR_EXTENSIONS.contains(ext)) {
                throw new BizException(40000, "仅支持 png/jpg/jpeg/gif/webp 格式头像");
            }
            String contentType = StringUtils.hasText(file.getContentType())
                    ? file.getContentType().toLowerCase(Locale.ROOT)
                    : "";
            if (!ALLOWED_AVATAR_CONTENT_TYPES.contains(contentType)) {
                throw new BizException(40000, "头像 MIME 类型不合法");
            }
            String filename = UUID.randomUUID() + ext;
            Path dir = Paths.get("uploads");
            Files.createDirectories(dir);
            Path target = dir.resolve(filename);
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
            return dir.resolve(filename).toString().replace('\\', '/');
        } catch (IOException e) {
            throw new BizException(50000, "头像上传失败: " + e.getMessage());
        }
    }
}
