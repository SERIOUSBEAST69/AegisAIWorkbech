package com.trustai.service;

import com.trustai.config.jwt.JwtPrincipal;
import com.trustai.entity.Permission;
import com.trustai.entity.Role;
import com.trustai.entity.RolePermission;
import com.trustai.entity.User;
import com.trustai.entity.UserRole;
import com.trustai.exception.BizException;
import java.util.Objects;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
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
    private final RolePermissionService rolePermissionService;
    private final PermissionService permissionService;
    private final UserRoleService userRoleService;

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
        for (Long roleId : currentRoleIds(user)) {
            Role resolved = roleService.getById(roleId);
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

    public boolean hasPermission(String permissionCode) {
        if (!StringUtils.hasText(permissionCode)) {
            return false;
        }
        try {
            User user = requireCurrentUser();
            Role role = getCurrentRole(user);
            if (role == null && currentRoleIds(user).isEmpty()) {
                return false;
            }
            Set<String> codes = permissionCodesOfUser(user);
            return codes.contains(permissionCode.trim().toLowerCase());
        } catch (Exception ex) {
            return false;
        }
    }

    public boolean hasAnyPermission(String... permissionCodes) {
        if (permissionCodes == null || permissionCodes.length == 0) {
            return false;
        }
        try {
            User user = requireCurrentUser();
            Role role = getCurrentRole(user);
            if (role == null && currentRoleIds(user).isEmpty()) {
                return false;
            }
            Set<String> codes = permissionCodesOfUser(user);
            return Arrays.stream(permissionCodes)
                .filter(StringUtils::hasText)
                .map(code -> code.trim().toLowerCase())
                .anyMatch(codes::contains);
        } catch (Exception ex) {
            return false;
        }
    }

    public void requirePermission(String permissionCode) {
        if (!hasPermission(permissionCode)) {
            throw new AccessDeniedException("当前身份无权执行该操作");
        }
    }

    public void requireAnyPermission(String... permissionCodes) {
        if (!hasAnyPermission(permissionCodes)) {
            throw new AccessDeniedException("当前身份无权执行该操作");
        }
    }

    public boolean isEmployeeUser() {
        return EMPLOYEE_ROLE_CODE.equalsIgnoreCase(currentRoleCode());
    }

    public Set<String> permissionCodesOfCurrentUser() {
        return permissionCodesOfUser(requireCurrentUser());
    }

    public Set<String> permissionCodesOfUser(User user) {
        if (user == null) {
            return Set.of();
        }
        Set<Long> roleIds = currentRoleIds(user);
        if (roleIds.isEmpty()) {
            return Set.of();
        }
        Set<String> codes = new HashSet<>();
        for (Long roleId : roleIds) {
            codes.addAll(currentPermissionCodes(roleId, user.getCompanyId()));
        }
        return codes;
    }

    public Set<Long> currentRoleIds(User user) {
        Set<Long> roleIds = new HashSet<>();
        if (user == null) {
            return roleIds;
        }
        if (user.getRoleId() != null) {
            roleIds.add(user.getRoleId());
        }
        if (user.getId() != null) {
            try {
                for (UserRole userRole : userRoleService.lambdaQuery().eq(UserRole::getUserId, user.getId()).list()) {
                    if (userRole.getRoleId() != null) {
                        roleIds.add(userRole.getRoleId());
                    }
                }
            } catch (Exception ignored) {
                // user_role may not exist in some legacy environments.
            }
        }
        return roleIds;
    }

    private Set<String> currentPermissionCodes(Long roleId, Long companyId) {
        if (roleId == null) {
            return Set.of();
        }
        Set<Long> permissionIds = rolePermissionService.lambdaQuery()
            .eq(RolePermission::getRoleId, roleId)
            .list()
            .stream()
            .map(RolePermission::getPermissionId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (permissionIds.isEmpty()) {
            return Set.of();
        }

        Set<String> codes = new HashSet<>();
        for (Permission permission : permissionService.lambdaQuery()
            .in(Permission::getId, permissionIds)
            .eq(companyId != null, Permission::getCompanyId, companyId)
            .list()) {
            if (StringUtils.hasText(permission.getCode())) {
                codes.add(permission.getCode().trim().toLowerCase());
            }
        }
        return codes;
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
