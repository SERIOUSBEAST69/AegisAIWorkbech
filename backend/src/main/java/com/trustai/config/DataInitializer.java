package com.trustai.config;

import com.trustai.entity.Role;
import com.trustai.entity.User;
import com.trustai.service.RoleService;
import com.trustai.service.UserService;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 简单的启动数据填充：若系统无用户与角色，初始化一个管理员账号。
 */
@Component
@Order(1)
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private UserService userService;
    @Autowired
    private RoleService roleService;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        DemoAccountCatalog.roleLabels().forEach((code, name) -> ensureRole(DemoAccountCatalog.DEMO_COMPANY_ID, code, name));

        for (DemoAccountCatalog.DemoAccountSeed seed : DemoAccountCatalog.demoAccountSeeds()) {
            ensureUser(seed);
        }

        cleanupDeprecatedSchoolIdentity();
    }

    private void ensureRole(Long companyId, String code, String name) {
        if (roleService.lambdaQuery()
            .eq(Role::getCompanyId, companyId)
            .eq(Role::getCode, code)
            .count() > 0) {
            return;
        }
        Role role = new Role();
        role.setCompanyId(companyId);
        role.setName(name);
        role.setCode(code);
        role.setDescription("系统默认角色: " + name);
        role.setCreateTime(new Date());
        role.setUpdateTime(new Date());
        roleService.save(role);
    }

    private void ensureUser(DemoAccountCatalog.DemoAccountSeed seed) {
        Role role = findRoleByCompanyAndCode(DemoAccountCatalog.DEMO_COMPANY_ID, seed.roleCode());
        User user = userService.lambdaQuery().eq(User::getUsername, seed.username()).one();
        boolean isNew = user == null;
        boolean shouldResetPassword = false;
        if (isNew) {
            user = new User();
            user.setUsername(seed.username());
            user.setCreateTime(new Date());
            shouldResetPassword = true;
        } else if (!isBcryptHash(user.getPassword()) || !passwordMatches(user.getPassword(), seed.password())) {
            shouldResetPassword = true;
        }

        if (shouldResetPassword) {
            user.setPassword(passwordEncoder.encode(seed.password()));
        }

        user.setRealName(seed.realName());
        user.setNickname(seed.realName());
        user.setRoleId(role == null ? null : role.getId());
        user.setCompanyId(DemoAccountCatalog.DEMO_COMPANY_ID);
        user.setDeviceId(seed.username() + "-device");
        user.setOrganizationType(seed.organizationType());
        user.setDepartment(seed.department());
        user.setPhone(seed.phone());
        user.setEmail(seed.email());
        user.setLoginType("password");
        user.setWechatOpenId(seed.wechatOpenId());
        user.setAccountType("demo");
        user.setAccountStatus("active");
        user.setRejectReason(null);
        user.setApprovedBy(resolveAdminId());
        user.setApprovedAt(new Date());
        user.setStatus(1);
        user.setUpdateTime(new Date());
        if (isNew) {
            userService.save(user);
        } else {
            if (!shouldResetPassword) {
                user.setPassword(null);
            }
            userService.updateById(user);
        }
    }

    private void cleanupDeprecatedSchoolIdentity() {
        userService.lambdaUpdate().eq(User::getUsername, "school.demo").remove();

        Role schoolRole = findRoleByCompanyAndCode(DemoAccountCatalog.DEMO_COMPANY_ID, "SCHOOL_ADMIN");
        if (schoolRole == null) {
            return;
        }

        Role fallbackRole = findRoleByCompanyAndCode(DemoAccountCatalog.DEMO_COMPANY_ID, "DATA_ADMIN");
        List<User> users = userService.lambdaQuery().eq(User::getRoleId, schoolRole.getId()).list();
        for (User user : users) {
            user.setRoleId(fallbackRole == null ? null : fallbackRole.getId());
            user.setUpdateTime(new Date());
            user.setPassword(null);
            userService.updateById(user);
        }

        roleService.removeById(schoolRole.getId());
    }

    private Role findRoleByCompanyAndCode(Long companyId, String roleCode) {
        return roleService.lambdaQuery()
            .eq(Role::getCompanyId, companyId)
            .eq(Role::getCode, roleCode)
            .list()
            .stream()
            .min(Comparator.comparing(Role::getId))
            .orElse(null);
    }

    private boolean isBcryptHash(String value) {
        return StringUtils.hasText(value) && value.startsWith("$2");
    }

    private boolean passwordMatches(String encoded, String raw) {
        try {
            return StringUtils.hasText(encoded) && passwordEncoder.matches(raw, encoded);
        } catch (Exception ex) {
            return false;
        }
    }

    private Long resolveAdminId() {
        User admin = userService.lambdaQuery().eq(User::getUsername, "admin").one();
        return admin == null ? null : admin.getId();
    }
}