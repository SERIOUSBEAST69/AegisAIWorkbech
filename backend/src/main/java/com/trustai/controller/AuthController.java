package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.config.jwt.JwtUtil;
import com.trustai.dto.UserProfileDTO;
import com.trustai.entity.Company;
import com.trustai.entity.Role;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import com.trustai.service.AuthVerificationService;
import com.trustai.service.CompanyService;
import com.trustai.service.CurrentUserService;
import com.trustai.service.RoleService;
import com.trustai.service.UserService;
import com.trustai.utils.R;
import java.util.Date;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Validated
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);
    private static final String ACCOUNT_TYPE_REAL = "real";
    private static final String ACCOUNT_STATUS_PENDING = "pending";
    private static final String ACCOUNT_STATUS_ACTIVE = "active";
    private static final String ACCOUNT_STATUS_REJECTED = "rejected";
    private static final String ACCOUNT_STATUS_DISABLED = "disabled";
    private static final Map<String, String> ROLE_LABELS = Map.of(
        "ADMIN", "治理管理员",
        "EXECUTIVE", "管理层",
        "SECOPS", "安全运维",
        "DATA_ADMIN", "数据管理员",
        "AI_BUILDER", "AI应用开发者",
        "BUSINESS_OWNER", "业务负责人",
        "EMPLOYEE", "普通员工"
    );
    private static final Map<String, List<Map<String, String>>> DEMO_ACCOUNTS = Map.of(
        "ADMIN", List.of(
            demoAccount("主治理管理员", "admin", "admin"),
            demoAccount("治理复核员A", "admin_reviewer", "admin"),
            demoAccount("治理复核员B", "admin_ops", "admin")
        ),
        "EXECUTIVE", List.of(
            demoAccount("管理层账号A", "executive", "Passw0rd!"),
            demoAccount("管理层账号B", "executive_2", "Passw0rd!"),
            demoAccount("管理层账号C", "executive_3", "Passw0rd!")
        ),
        "SECOPS", List.of(
            demoAccount("安全运维A", "secops", "Passw0rd!"),
            demoAccount("安全运维B", "secops_2", "Passw0rd!"),
            demoAccount("安全运维C", "secops_3", "Passw0rd!")
        ),
        "DATA_ADMIN", List.of(
            demoAccount("数据管理员A", "dataadmin", "Passw0rd!"),
            demoAccount("数据管理员B", "dataadmin_2", "Passw0rd!"),
            demoAccount("数据管理员C", "dataadmin_3", "Passw0rd!")
        ),
        "AI_BUILDER", List.of(
            demoAccount("AI开发者A", "aibuilder", "Passw0rd!"),
            demoAccount("AI开发者B", "aibuilder_2", "Passw0rd!"),
            demoAccount("AI开发者C", "aibuilder_3", "Passw0rd!")
        ),
        "BUSINESS_OWNER", List.of(
            demoAccount("业务负责人A", "bizowner", "Passw0rd!"),
            demoAccount("业务负责人B", "bizowner_2", "Passw0rd!"),
            demoAccount("业务负责人C", "bizowner_3", "Passw0rd!")
        ),
        "EMPLOYEE", List.of(
            demoAccount("员工账号A", "employee1", "Passw0rd!"),
            demoAccount("员工账号B", "employee2", "Passw0rd!"),
            demoAccount("员工账号C", "employee3", "Passw0rd!")
        )
    );

    @Autowired private UserService userService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private CurrentUserService currentUserService;
    @Autowired private RoleService roleService;
    @Autowired private AuthVerificationService authVerificationService;
    @Autowired private CompanyService companyService;

    @PostMapping("/login")
    public R<?> login(@Valid @RequestBody LoginReq req) {
        User user = findUserByUsername(req.getUsername());
        log.info("login pick user id={}", user != null ? user.getId() : null);
        assertLoginAllowed(user, "用户不存在");
        if (user == null) {
            throw new BizException(40100, "用户不存在");
        }
        if (!verifyPasswordAndUpgradeIfNeeded(user, req.getPassword())) {
            throw new BizException(40100, "用户名或密码错误");
        }
        user.setLoginType("password");
        user.setUpdateTime(new Date());
        userService.updateById(user);
        return R.ok(buildSession(user, true));
    }

    @PostMapping("/login-phone")
    public R<?> loginByPhone(@Valid @RequestBody PhoneLoginReq req) {
        authVerificationService.verifyPhoneCode(req.getPhone(), req.getCode());
        User user = findUserByPhone(req.getPhone());
        assertLoginAllowed(user, "手机号未注册");
        user.setLoginType("phone");
        user.setUpdateTime(new Date());
        userService.updateById(user);
        return R.ok(buildSession(user, true));
    }

    @PostMapping("/login-wechat")
    public R<?> loginByWechat(@Valid @RequestBody WechatLoginReq req) {
        String openId = normalizeWechatOpenId(req.getWechatOpenId(), req.getNickname());
        User user = findUserByWechat(openId);
        if (user == null) {
            throw new BizException(40100, "微信身份未注册，请先完成账号注册");
        }
        assertLoginAllowed(user, "用户不存在");
        user.setLoginType("wechat");
        user.setUpdateTime(new Date());
        userService.updateById(user);
        return R.ok(buildSession(user, true));
    }

    @PostMapping("/register")
    public R<?> register(@Valid @RequestBody RegisterReq req) {
        User user = createUser(req, StringUtils.hasText(req.getLoginType()) ? req.getLoginType() : inferLoginType(req));
        String accountStatus = normalizeAccountStatus(user);
        if (ACCOUNT_STATUS_ACTIVE.equals(accountStatus)) {
            return R.ok(buildSession(user, true));
        }
        return R.ok(new RegisterResp(false, true, accountStatus, "注册申请已提交，等待管理员审批", toProfile(user)));
    }

    @PostMapping("/phone-code")
    public R<?> sendPhoneCode(@Valid @RequestBody PhoneCodeReq req) {
        Map<String, String> payload = new LinkedHashMap<>();
        AuthVerificationService.PhoneCodePayload issued = authVerificationService.issuePhoneCode(req.getPhone());
        payload.put("phone", issued.phone());
        payload.put("expiresAt", String.valueOf(issued.expiresAt()));
        payload.put("codeHint", issued.developmentMode() ? issued.code() : "");
        payload.put("message", issued.developmentMode() ? "验证码已生成，可用于本地联调" : "验证码已发送");
        return R.ok(payload);
    }

    @GetMapping("/registration-options")
    public R<?> registrationOptions(@RequestParam(required = false) Long companyId) {
        List<Map<String, Object>> identities = resolveSelfRegisterIdentities(companyId);
        List<Map<String, String>> organizations = List.of(
            option("enterprise", "企业"),
            option("school", "学校"),
            option("ai-team", "AI应用团队"),
            option("public-sector", "政企/公共机构")
        );
        List<Map<String, Object>> demoAccounts = new ArrayList<>();
        for (Map.Entry<String, String> role : ROLE_LABELS.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("roleCode", role.getKey());
            row.put("roleLabel", role.getValue());
            row.put("accounts", DEMO_ACCOUNTS.getOrDefault(role.getKey(), List.of()));
            demoAccounts.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("identities", identities);
        result.put("organizations", organizations);
        result.put("demoAccounts", demoAccounts);
        return R.ok(result);
    }

    @GetMapping("/me")
    public R<?> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new BizException(40100, "未登录或令牌失效");
        }
        User user = currentUserService.requireCurrentUser();
        return R.ok(buildSession(user, false));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public R<?> logout() {
        return R.okMsg("退出成功");
    }

    private SessionResp buildSession(User user, boolean includeToken) {
        Set<String> permissions = currentUserService.permissionCodesOfUser(user);
        List<String> permissionCodes = permissions.stream().sorted().toList();
        String token = includeToken ? jwtUtil.generateToken(user.getUsername(), user.getId(), user.getCompanyId(), permissionCodes) : null;
        user.setPassword(null);
        return new SessionResp(token, toProfile(user, permissionCodes), true, System.currentTimeMillis());
    }

    private User findUserByUsername(String username) {
        List<User> users = userService.list(new QueryWrapper<User>().eq("username", username));
        return pickLatestUser(users);
    }

    private User findUserByPhone(String phone) {
        List<User> users = userService.list(new QueryWrapper<User>().eq("phone", phone));
        return pickLatestUser(users);
    }

    private User findUserByWechat(String wechatOpenId) {
        List<User> users = userService.list(new QueryWrapper<User>().eq("wechat_open_id", wechatOpenId));
        return pickLatestUser(users);
    }

    private User pickLatestUser(List<User> users) {
        User user = users.stream()
            .filter(candidate -> candidate.getPassword() != null && candidate.getPassword().startsWith("$2"))
            .findFirst()
            .orElseGet(() -> users.stream()
                .findFirst()
                .orElse(null));
        if (user == null) {
            return null;
        }
        return user;
    }

    private boolean verifyPasswordAndUpgradeIfNeeded(User user, String rawPassword) {
        String storedPassword = user.getPassword();
        if (!StringUtils.hasText(storedPassword) || !StringUtils.hasText(rawPassword)) {
            return false;
        }

        if (isBcryptPassword(storedPassword)) {
            try {
                return passwordEncoder.matches(rawPassword, storedPassword);
            } catch (IllegalArgumentException ex) {
                log.warn("invalid bcrypt hash for user id={}, fallback to plain comparison", user.getId());
                return rawPassword.equals(storedPassword);
            }
        }

        if (!rawPassword.equals(storedPassword)) {
            return false;
        }

        user.setPassword(passwordEncoder.encode(rawPassword));
        return true;
    }

    private boolean isBcryptPassword(String password) {
        return password.startsWith("$2a$") || password.startsWith("$2b$") || password.startsWith("$2y$");
    }

    private Role resolveOrCreateRoleForCompany(String roleCode, Long companyId) {
        Role role = roleService.lambdaQuery()
            .eq(Role::getCode, roleCode)
            .eq(companyId != null, Role::getCompanyId, companyId)
            .list()
            .stream()
            .min(Comparator.comparing(Role::getId))
            .orElse(null);
        if (role != null) {
            return role;
        }
        String roleName = ROLE_LABELS.get(roleCode);
        if (!StringUtils.hasText(roleName)) {
            return null;
        }
        Role created = new Role();
        created.setCompanyId(companyId);
        created.setCode(roleCode);
        created.setName(roleName);
        created.setDescription("系统默认角色: " + roleName);
        created.setCreateTime(new Date());
        created.setUpdateTime(new Date());
        roleService.save(created);
        return created;
    }

    private User createUser(RegisterReq req, String loginType) {
        if (!StringUtils.hasText(req.getRoleCode())) {
            if (req.getRoleId() == null) {
                throw new BizException(40000, "请选择身份");
            }
        }

        String accountType = resolveAccountType(req);
        boolean realAccount = ACCOUNT_TYPE_REAL.equals(accountType);
        Long companyId = resolveCompanyId(req, accountType);
        Role role = resolveRegisterRole(req, companyId);
        if (role == null) {
            throw new BizException(40000, "身份不存在，请联系管理员");
        }
        if (!Boolean.TRUE.equals(role.getAllowSelfRegister())) {
            throw new BizException(40000, "该身份不允许自助注册");
        }

        String username = resolveUsername(req, loginType);
        if (userService.lambdaQuery().eq(User::getUsername, username).count() > 0) {
            throw new BizException(40000, "用户名已存在");
        }
        if (StringUtils.hasText(req.getPhone()) && userService.lambdaQuery().eq(User::getPhone, req.getPhone()).count() > 0) {
            throw new BizException(40000, "手机号已注册");
        }

        String wechatOpenId = StringUtils.hasText(req.getWechatOpenId()) ? normalizeWechatOpenId(req.getWechatOpenId(), req.getNickname()) : null;
        if (StringUtils.hasText(wechatOpenId) && userService.lambdaQuery().eq(User::getWechatOpenId, wechatOpenId).count() > 0) {
            throw new BizException(40000, "微信身份已绑定");
        }

        if ("phone".equals(loginType)) {
            authVerificationService.verifyPhoneCode(req.getPhone(), req.getPhoneCode());
        }

        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(resolvePassword(req, loginType)));
        user.setRealName(StringUtils.hasText(req.getRealName()) ? req.getRealName() : req.getNickname());
        user.setNickname(StringUtils.hasText(req.getNickname()) ? req.getNickname() : req.getRealName());
        user.setRoleId(role.getId());
        user.setCompanyId(companyId);
        user.setDeviceId(username + "-device");
        user.setDepartment(req.getDepartment());
        user.setOrganizationType(req.getOrganizationType());
        user.setPhone(req.getPhone());
        user.setEmail(req.getEmail());
        user.setLoginType(loginType);
        user.setWechatOpenId(wechatOpenId);
        user.setAccountType(accountType);
        user.setAccountStatus(realAccount ? ACCOUNT_STATUS_PENDING : ACCOUNT_STATUS_ACTIVE);
        user.setApprovedBy(realAccount ? null : 1L);
        user.setApprovedAt(realAccount ? null : new Date());
        user.setRejectReason(null);
        user.setStatus(1);
        user.setCreateTime(new Date());
        user.setUpdateTime(new Date());
        userService.save(user);
        return user;
    }

    private Role resolveRegisterRole(RegisterReq req, Long companyId) {
        if (req.getRoleId() != null) {
            Role role = roleService.getById(req.getRoleId());
            if (role != null && Objects.equals(role.getCompanyId(), companyId)) {
                return role;
            }
            return null;
        }
        return resolveOrCreateRoleForCompany(req.getRoleCode(), companyId);
    }

    private List<Map<String, Object>> resolveSelfRegisterIdentities(Long companyId) {
        if (companyId == null || companyId <= 0L) {
            companyId = 1L;
        }
        List<Map<String, Object>> dynamic = roleService.lambdaQuery()
            .eq(Role::getCompanyId, companyId)
            .eq(Role::getAllowSelfRegister, true)
            .orderByAsc(Role::getId)
            .list()
            .stream()
            .map(this::roleOption)
            .toList();
        if (!dynamic.isEmpty()) {
            return dynamic;
        }
        final Long fallbackCompanyId = companyId;
        return ROLE_LABELS.entrySet().stream()
            .filter(entry -> isDefaultSelfRegisterRole(entry.getKey()))
            .map(entry -> defaultRoleOption(entry.getKey(), entry.getValue(), fallbackCompanyId))
            .toList();
    }

    private boolean isDefaultSelfRegisterRole(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return false;
        }
        String code = roleCode.trim().toUpperCase();
        return "EMPLOYEE".equals(code) || "AI_BUILDER".equals(code) || "BUSINESS_OWNER".equals(code);
    }

    private Map<String, Object> roleOption(Role role) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", role.getId());
        item.put("code", role.getCode());
        item.put("label", role.getName());
        return item;
    }

    private Map<String, Object> defaultRoleOption(String code, String label, Long companyId) {
        Map<String, Object> item = new LinkedHashMap<>();
        Role role = roleService.lambdaQuery()
            .eq(Role::getCompanyId, companyId)
            .eq(Role::getCode, code)
            .orderByAsc(Role::getId)
            .last("LIMIT 1")
            .one();
        item.put("id", role == null ? null : role.getId());
        item.put("code", code);
        item.put("label", label);
        return item;
    }

    private String resolveAccountType(RegisterReq req) {
        if (!StringUtils.hasText(req.getAccountType())) {
            return ACCOUNT_TYPE_REAL;
        }
        String normalized = req.getAccountType().trim().toLowerCase();
        if (!ACCOUNT_TYPE_REAL.equals(normalized)) {
            throw new BizException(40000, "仅支持真实账号注册");
        }
        return normalized;
    }

    private Long resolveCompanyId(RegisterReq req, String accountType) {
        if (!StringUtils.hasText(req.getCompanyName())) {
            throw new BizException(40000, "真实账号注册必须填写公司名称");
        }
        String companyName = req.getCompanyName().trim();
        Company existing = companyService.lambdaQuery()
            .eq(Company::getCompanyName, companyName)
            .list()
            .stream()
            .min(Comparator.comparing(Company::getId))
            .orElse(null);
        if (existing != null) {
            return existing.getId();
        }
        Company company = new Company();
        company.setCompanyName(companyName);
        company.setCompanyCode(buildCompanyCode(companyName));
        company.setStatus(1);
        company.setCreateTime(new Date());
        company.setUpdateTime(new Date());
        companyService.save(company);
        return company.getId();
    }

    private String buildCompanyCode(String companyName) {
        String base = companyName.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (!StringUtils.hasText(base)) {
            base = "tenant";
        }
        String candidate = base;
        int suffix = 1;
        while (companyService.count(new QueryWrapper<Company>().eq("company_code", candidate)) > 0) {
            candidate = base + "-" + suffix;
            suffix++;
        }
        return candidate;
    }

    private String resolveUsername(RegisterReq req, String loginType) {
        if (StringUtils.hasText(req.getUsername())) {
            return req.getUsername().trim();
        }
        if ("phone".equals(loginType) && StringUtils.hasText(req.getPhone())) {
            return "phone_" + req.getPhone();
        }
        if ("wechat".equals(loginType)) {
            return "wx_" + normalizeWechatOpenId(req.getWechatOpenId(), req.getNickname()).replaceAll("[^a-zA-Z0-9_]", "_");
        }
        if (StringUtils.hasText(req.getPhone())) {
            return "user_" + req.getPhone();
        }
        return "user_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
    }

    private String resolvePassword(RegisterReq req, String loginType) {
        if ("password".equals(loginType) && !StringUtils.hasText(req.getConfirmPassword())) {
            throw new BizException(40000, "请确认注册密码");
        }
        if (StringUtils.hasText(req.getPassword())) {
            if ("password".equals(loginType)
                && StringUtils.hasText(req.getConfirmPassword())
                && !req.getPassword().equals(req.getConfirmPassword())) {
                throw new BizException(40000, "两次输入的密码不一致");
            }
            return req.getPassword();
        }
        if ("phone".equals(loginType) || "wechat".equals(loginType)) {
            return UUID.randomUUID().toString();
        }
        throw new BizException(40000, "请输入密码");
    }

    private String normalizeWechatOpenId(String wechatOpenId, String nickname) {
        if (StringUtils.hasText(wechatOpenId)) {
            return wechatOpenId.trim();
        }
        if (StringUtils.hasText(nickname)) {
            return "wx_" + nickname.trim().replaceAll("\\s+", "_").toLowerCase();
        }
        throw new BizException(40000, "请提供微信身份标识");
    }

    private String inferLoginType(RegisterReq req) {
        if (StringUtils.hasText(req.getWechatOpenId())) {
            return "wechat";
        }
        if (StringUtils.hasText(req.getPhone())) {
            return "phone";
        }
        return "password";
    }

    private Map<String, String> option(String code, String label) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("code", code);
        item.put("label", label);
        return item;
    }

    private static Map<String, String> demoAccount(String label, String username, String password) {
        Map<String, String> item = new LinkedHashMap<>();
        item.put("label", label);
        item.put("username", username);
        item.put("password", password);
        return item;
    }

    private void assertLoginAllowed(User user, String notFoundMessage) {
        if (user == null) {
            throw new BizException(40100, notFoundMessage);
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new BizException(40100, "账号已禁用，请联系管理员");
        }
        String status = normalizeAccountStatus(user);
        if (ACCOUNT_STATUS_PENDING.equals(status)) {
            throw new BizException(40100, "账号待审批，请联系管理员审核后再登录");
        }
        if (ACCOUNT_STATUS_REJECTED.equals(status)) {
            String reason = StringUtils.hasText(user.getRejectReason()) ? ("，原因：" + user.getRejectReason()) : "";
            throw new BizException(40100, "账号审批未通过" + reason);
        }
        if (ACCOUNT_STATUS_DISABLED.equals(status)) {
            throw new BizException(40100, "账号已禁用，请联系管理员");
        }
    }

    private String normalizeAccountStatus(User user) {
        if (StringUtils.hasText(user.getAccountStatus())) {
            return user.getAccountStatus().trim().toLowerCase();
        }
        if (user.getStatus() != null && user.getStatus() == 0) {
            return ACCOUNT_STATUS_DISABLED;
        }
        return ACCOUNT_STATUS_ACTIVE;
    }

    private String resolveCompanyName(Long companyId) {
        if (companyId == null) {
            return null;
        }
        Company company = companyService.getById(companyId);
        return company == null ? null : company.getCompanyName();
    }

    private UserProfileDTO toProfile(User user) {
        return toProfile(user, currentUserService.permissionCodesOfUser(user).stream().sorted().toList());
    }

    private UserProfileDTO toProfile(User user, List<String> permissionCodes) {
        Role role = currentUserService.getCurrentRole(user);
        String resolvedRoleCode = role == null ? null : role.getCode();
        String resolvedRoleName = role == null ? null : role.getName();
        if (!StringUtils.hasText(resolvedRoleCode) && "admin".equalsIgnoreCase(user.getUsername())) {
            resolvedRoleCode = "ADMIN";
            resolvedRoleName = ROLE_LABELS.get("ADMIN");
        }
        return UserProfileDTO.builder()
            .id(user.getId())
            .companyId(user.getCompanyId())
            .companyName(resolveCompanyName(user.getCompanyId()))
            .accountType(user.getAccountType())
            .accountStatus(normalizeAccountStatus(user))
            .username(user.getUsername())
            .avatar(user.getAvatar())
            .nickname(user.getNickname())
            .realName(user.getRealName())
            .email(user.getEmail())
            .phone(user.getPhone())
            .department(user.getDepartment())
            .organizationType(user.getOrganizationType())
            .loginType(user.getLoginType())
            .roleName(resolvedRoleName)
            .roleCode(resolvedRoleCode)
            .permissionCodes(permissionCodes)
            .roleIds(currentUserService.currentRoleIds(user).stream().sorted().toList())
            .deviceId(user.getDeviceId())
            .lastActiveAt(user.getUpdateTime() == null ? null : user.getUpdateTime().toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime())
            .build();
    }

    @Data
    public static class LoginReq {
        @NotBlank(message = "用户名不能为空")
        private String username;
        @NotBlank(message = "密码不能为空")
        private String password;
    }

    @Data
    public static class PhoneLoginReq {
        @NotBlank(message = "手机号不能为空")
        private String phone;
        @NotBlank(message = "验证码不能为空")
        private String code;
    }

    @Data
    public static class WechatLoginReq {
        private String nickname;
        private String phone;
        private String wechatOpenId;
        private String roleCode;
        private String organizationType;
        private String department;
    }

    @Data
    public static class PhoneCodeReq {
        @NotBlank(message = "手机号不能为空")
        private String phone;
    }

    @Data
    public static class RegisterReq {
        private String username;
        private String password;
        private String confirmPassword;
        private String realName;
        private String nickname;
        private String companyName;
        private String accountType;
        private String roleCode;
        private Long roleId;
        private String organizationType;
        private String department;
        private String phone;
        private String phoneCode;
        private String email;
        private String loginType;
        private String wechatOpenId;
    }

    @Data
    public static class RegisterResp {
        private final boolean authenticated;
        private final boolean pendingApproval;
        private final String accountStatus;
        private final String message;
        private final UserProfileDTO user;
    }

    @Data
    public static class SessionResp {
        private final String token;
        private final UserProfileDTO user;
        private final boolean authenticated;
        private final long serverTime;
    }

}
