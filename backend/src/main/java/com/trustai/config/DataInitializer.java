package com.trustai.config;

import com.trustai.entity.Role;
import com.trustai.entity.User;
import com.trustai.service.RoleService;
import com.trustai.service.UserService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 简单的启动数据填充：若系统无用户与角色，初始化一个管理员账号。
 */
@Component
@Order(1)
public class DataInitializer implements CommandLineRunner {

    private static final Long DEFAULT_COMPANY_ID = 1L;
    private static final String DEFAULT_PASSWORD = "Passw0rd!";

    private static final List<UserSeed> BASELINE_USERS = List.of(
        new UserSeed("admin", "治理管理员", "ADMIN", "治理中心", "13800138000", "admin@aegisai.com", "wx_admin", "admin"),
        new UserSeed("executive", "管理层", "EXECUTIVE", "经营管理部", "13800138001", "executive@aegisai.com", "wx_executive", DEFAULT_PASSWORD),
        new UserSeed("secops", "安全运维", "SECOPS", "安全运营中心", "13800138002", "secops@aegisai.com", "wx_secops", DEFAULT_PASSWORD),
        new UserSeed("dataadmin", "数据管理员", "DATA_ADMIN", "数据治理部", "13800138003", "dataadmin@aegisai.com", "wx_dataadmin", DEFAULT_PASSWORD),
        new UserSeed("aibuilder", "AI应用开发者", "AI_BUILDER", "模型平台组", "13800138004", "aibuilder@aegisai.com", "wx_aibuilder", DEFAULT_PASSWORD),
        new UserSeed("bizowner", "业务负责人", "BUSINESS_OWNER", "业务创新部", "13800138005", "bizowner@aegisai.com", "wx_bizowner", DEFAULT_PASSWORD),
        new UserSeed("employee", "普通员工", "EMPLOYEE", "业务一线", "13800138006", "employee@aegisai.com", "wx_employee", DEFAULT_PASSWORD)
    );

    @Autowired
    private UserService userService;
    @Autowired
    private RoleService roleService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        Map<String, Role> roleMap = ensureDefaultRoles(DEFAULT_COMPANY_ID);
        for (UserSeed seed : BASELINE_USERS) {
            ensureUser(DEFAULT_COMPANY_ID, seed, roleMap.get(seed.roleCode()));
        }
    }

    private Map<String, Role> ensureDefaultRoles(Long companyId) {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("ADMIN", "治理管理员");
        labels.put("EXECUTIVE", "管理层");
        labels.put("SECOPS", "安全运维");
        labels.put("DATA_ADMIN", "数据管理员");
        labels.put("AI_BUILDER", "AI应用开发者");
        labels.put("BUSINESS_OWNER", "业务负责人");
        labels.put("EMPLOYEE", "普通员工");

        Map<String, Role> result = new LinkedHashMap<>();
        labels.forEach((code, name) -> result.put(code, ensureRole(companyId, code, name)));
        return result;
    }

    private Role ensureRole(Long companyId, String code, String name) {
        Role existing = roleService.lambdaQuery()
            .eq(Role::getCompanyId, companyId)
            .eq(Role::getCode, code)
            .one();
        if (existing != null) {
            return existing;
        }
        Role role = new Role();
        role.setCompanyId(companyId);
        role.setName(name);
        role.setCode(code);
        role.setDescription("系统默认角色: " + name);
        role.setCreateTime(new Date());
        role.setUpdateTime(new Date());
        roleService.save(role);
        return role;
    }

    private void ensureUser(Long companyId, UserSeed seed, Role role) {
        User user = userService.lambdaQuery().eq(User::getUsername, seed.username()).one();
        boolean isNew = user == null;
        if (isNew) {
            user = new User();
            user.setUsername(seed.username());
            user.setCreateTime(new Date());
            user.setPassword(passwordEncoder.encode(seed.password()));
        }
        if (user == null) {
            return;
        }

        user.setRealName(seed.realName());
        user.setNickname(seed.realName());
        user.setRoleId(role == null ? null : role.getId());
        user.setCompanyId(companyId);
        user.setDeviceId(seed.username() + "-device");
        user.setOrganizationType("enterprise");
        user.setDepartment(seed.department());
        user.setPhone(seed.phone());
        user.setEmail(seed.email());
        user.setLoginType("password");
        user.setWechatOpenId(seed.wechatOpenId());
        user.setAccountType("real");
        user.setAccountStatus("active");
        user.setRejectReason(null);
        user.setApprovedBy(null);
        user.setApprovedAt(new Date());
        user.setStatus(1);
        user.setUpdateTime(new Date());
        if (isNew) {
            userService.save(user);
        } else {
            user.setPassword(null);
            userService.updateById(user);
        }
    }

    private record UserSeed(
        String username,
        String realName,
        String roleCode,
        String department,
        String phone,
        String email,
        String wechatOpenId,
        String password
    ) {}
}