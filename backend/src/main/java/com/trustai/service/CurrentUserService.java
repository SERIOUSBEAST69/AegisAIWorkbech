package com.trustai.service;

import com.trustai.entity.Role;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import java.util.Objects;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class CurrentUserService {

    private static final String ADMIN_ROLE_CODE = "ADMIN";
    private static final String EMPLOYEE_ROLE_CODE = "EMPLOYEE";

    private final UserService userService;
    private final RoleService roleService;

    public User requireCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new BizException(40100, "未登录");
        }
        User user = userService.lambdaQuery().eq(User::getUsername, auth.getName()).one();
        if (user == null) {
            throw new BizException(40100, "用户不存在");
        }
        return user;
    }

    public void requireAdmin() {
        User user = requireCurrentUser();
        Role role = getCurrentRole(user);
        boolean isAdmin = "admin".equalsIgnoreCase(user.getUsername())
                || (role != null && ADMIN_ROLE_CODE.equalsIgnoreCase(role.getCode()));
        if (!isAdmin) {
            throw new BizException(40300, "仅管理员可操作");
        }
    }

    public void requireAnyRole(String... roleCodes) {
        User user = requireCurrentUser();
        Role role = getCurrentRole(user);
        String currentRoleCode = getCurrentRoleCode(user, role);
        if (!StringUtils.hasText(currentRoleCode)) {
            throw new BizException(40300, "当前账号未分配身份");
        }

        boolean allowed = Arrays.stream(roleCodes)
            .filter(StringUtils::hasText)
            .anyMatch(code -> code.equalsIgnoreCase(currentRoleCode));
        if (!allowed) {
            throw new BizException(40300, "当前身份无权执行该操作");
        }
    }

    public Role getCurrentRole(User user) {
        if (user.getRoleId() != null) {
            Role resolved = roleService.getById(user.getRoleId());
            if (resolved != null && Objects.equals(resolved.getCompanyId(), user.getCompanyId())) {
                return resolved;
            }
        }
        if ("admin".equalsIgnoreCase(user.getUsername())) {
            return roleService.lambdaQuery()
                .eq(Role::getCode, ADMIN_ROLE_CODE)
                .eq(user.getCompanyId() != null, Role::getCompanyId, user.getCompanyId())
                .one();
        }
        return null;
    }

    public String currentRoleCode() {
        User user = requireCurrentUser();
        return getCurrentRoleCode(user, getCurrentRole(user));
    }

    public boolean hasRole(String roleCode) {
        if (!StringUtils.hasText(roleCode)) {
            return false;
        }
        String current = safeCurrentRoleCode();
        return StringUtils.hasText(current) && roleCode.equalsIgnoreCase(current);
    }

    public boolean hasAnyRole(String... roleCodes) {
        String current = safeCurrentRoleCode();
        if (!StringUtils.hasText(current) || roleCodes == null || roleCodes.length == 0) {
            return false;
        }
        return Arrays.stream(roleCodes)
            .filter(StringUtils::hasText)
            .anyMatch(code -> code.equalsIgnoreCase(current));
    }

    public boolean isEmployeeUser() {
        return EMPLOYEE_ROLE_CODE.equalsIgnoreCase(currentRoleCode());
    }

    private String safeCurrentRoleCode() {
        try {
            User user = requireCurrentUser();
            return getCurrentRoleCode(user, getCurrentRole(user));
        } catch (Exception ex) {
            return null;
        }
    }

    private String getCurrentRoleCode(User user, Role role) {
        if (role != null && StringUtils.hasText(role.getCode())) {
            return role.getCode();
        }
        if ("admin".equalsIgnoreCase(user.getUsername())) {
            return ADMIN_ROLE_CODE;
        }
        return null;
    }
}
