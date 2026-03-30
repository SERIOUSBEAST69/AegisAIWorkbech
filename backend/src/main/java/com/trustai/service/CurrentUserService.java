package com.trustai.service;

import com.trustai.config.jwt.JwtPrincipal;
import com.trustai.entity.Role;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import java.util.Objects;
import java.util.Arrays;
import org.springframework.security.access.AccessDeniedException;
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
        User user = null;
        String jwtUsername = null;
        Object principal = auth.getPrincipal();
        if (principal instanceof JwtPrincipal jwtPrincipal && jwtPrincipal.userId() != null) {
            jwtUsername = jwtPrincipal.username();
            user = userService.getById(jwtPrincipal.userId());
            if (user != null && StringUtils.hasText(jwtPrincipal.username())
                && !jwtPrincipal.username().equalsIgnoreCase(user.getUsername())) {
                throw new BizException(40100, "令牌用户信息不一致");
            }
        } else if (principal instanceof JwtPrincipal jwtPrincipal) {
            jwtUsername = jwtPrincipal.username();
        }
        if (user == null) {
            String fallbackUsername = StringUtils.hasText(jwtUsername) ? jwtUsername : auth.getName();
            if (StringUtils.hasText(fallbackUsername)) {
                user = userService.lambdaQuery().eq(User::getUsername, fallbackUsername).one();
            }
        }
        if (user == null) {
            throw new BizException(40100, "用户不存在");
        }
        return user;
    }

    public void requireAdmin() {
        User user = requireCurrentUser();
        String roleCode = getCurrentRoleCode(user, getCurrentRole(user));
        boolean isAdmin = ADMIN_ROLE_CODE.equalsIgnoreCase(roleCode);
        if (!isAdmin) {
            throw new AccessDeniedException("仅管理员可操作");
        }
    }

    public void requireAnyRole(String... roleCodes) {
        User user = requireCurrentUser();
        Role role = getCurrentRole(user);
        String currentRoleCode = getCurrentRoleCode(user, role);
        if (!StringUtils.hasText(currentRoleCode)) {
            throw new AccessDeniedException("当前账号未分配身份");
        }

        boolean allowed = Arrays.stream(roleCodes)
            .filter(StringUtils::hasText)
            .anyMatch(code -> code.equalsIgnoreCase(currentRoleCode));
        if (!allowed) {
            throw new AccessDeniedException("当前身份无权执行该操作");
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
