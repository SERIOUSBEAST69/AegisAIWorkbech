package com.trustai.controller;

import com.trustai.entity.Permission;
import com.trustai.entity.User;
import com.trustai.service.CurrentUserService;
import com.trustai.service.PermissionService;
import com.trustai.utils.R;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/permissions")
public class PermissionTreeController {

    private final PermissionService permissionService;
    private final CurrentUserService currentUserService;

    public PermissionTreeController(PermissionService permissionService, CurrentUserService currentUserService) {
        this.permissionService = permissionService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/tree")
    public R<List<Map<String, Object>>> tree() {
        currentUserService.requireAnyPermission("permission:manage", "role:manage", "role:permission:assign");
        User currentUser = currentUserService.requireCurrentUser();
        List<Permission> permissions = permissionService.lambdaQuery()
            .eq(Permission::getCompanyId, currentUser.getCompanyId())
            .orderByAsc(Permission::getId)
            .list();

        Map<Long, Map<String, Object>> idToNode = new LinkedHashMap<>();
        List<Map<String, Object>> roots = new ArrayList<>();

        for (Permission permission : permissions) {
            Map<String, Object> node = new LinkedHashMap<>();
            node.put("id", permission.getId());
            node.put("name", permission.getName());
            node.put("code", permission.getCode());
            node.put("type", permission.getType());
            node.put("status", permission.getStatus());
            node.put("children", new ArrayList<Map<String, Object>>());
            idToNode.put(permission.getId(), node);
        }

        for (Permission permission : permissions) {
            Map<String, Object> node = idToNode.get(permission.getId());
            Long parentId = permission.getParentId();
            if (parentId == null || !idToNode.containsKey(parentId)) {
                roots.add(node);
                continue;
            }
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) idToNode.get(parentId).get("children");
            children.add(node);
        }

        for (Map<String, Object> node : idToNode.values()) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> children = (List<Map<String, Object>>) node.get("children");
            children.removeIf(child -> Objects.equals(child.get("id"), node.get("id")));
        }
        return R.ok(roots);
    }
}
