package com.trustai.config;

import com.trustai.entity.Role;
import com.trustai.entity.User;
import com.trustai.service.RoleService;
import com.trustai.service.UserService;
import java.util.Date;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Random;
import org.springframework.jdbc.core.JdbcTemplate;
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
        new UserSeed("admin_reviewer", "治理复核员A", "ADMIN", "治理中心", "13800138070", "admin_reviewer@aegisai.com", "wx_admin_reviewer", "admin"),
        new UserSeed("admin_ops", "治理复核员B", "ADMIN", "治理中心", "13800138071", "admin_ops@aegisai.com", "wx_admin_ops", "admin"),
        new UserSeed("executive", "管理层", "EXECUTIVE", "经营管理部", "13800138001", "executive@aegisai.com", "wx_executive", DEFAULT_PASSWORD),
        new UserSeed("executive_2", "管理层二号", "EXECUTIVE", "经营管理部", "13800138011", "executive2@aegisai.com", "wx_executive2", DEFAULT_PASSWORD),
        new UserSeed("executive_3", "管理层三号", "EXECUTIVE", "经营管理部", "13800138012", "executive3@aegisai.com", "wx_executive3", DEFAULT_PASSWORD),
        new UserSeed("secops", "安全运维", "SECOPS", "安全运营中心", "13800138002", "secops@aegisai.com", "wx_secops", DEFAULT_PASSWORD),
        new UserSeed("secops_2", "安全运维二号", "SECOPS", "安全运营中心", "13800138022", "secops2@aegisai.com", "wx_secops2", DEFAULT_PASSWORD),
        new UserSeed("secops_3", "安全运维三号", "SECOPS", "安全运营中心", "13800138023", "secops3@aegisai.com", "wx_secops3", DEFAULT_PASSWORD),
        new UserSeed("dataadmin", "数据管理员", "DATA_ADMIN", "数据治理部", "13800138003", "dataadmin@aegisai.com", "wx_dataadmin", DEFAULT_PASSWORD),
        new UserSeed("dataadmin_2", "数据管理员二号", "DATA_ADMIN", "数据治理部", "13800138033", "dataadmin2@aegisai.com", "wx_dataadmin2", DEFAULT_PASSWORD),
        new UserSeed("dataadmin_3", "数据管理员三号", "DATA_ADMIN", "数据治理部", "13800138034", "dataadmin3@aegisai.com", "wx_dataadmin3", DEFAULT_PASSWORD),
        new UserSeed("aibuilder", "AI应用开发者", "AI_BUILDER", "模型平台组", "13800138004", "aibuilder@aegisai.com", "wx_aibuilder", DEFAULT_PASSWORD),
        new UserSeed("aibuilder_2", "AI开发者二号", "AI_BUILDER", "模型平台组", "13800138044", "aibuilder2@aegisai.com", "wx_aibuilder2", DEFAULT_PASSWORD),
        new UserSeed("aibuilder_3", "AI开发者三号", "AI_BUILDER", "模型平台组", "13800138045", "aibuilder3@aegisai.com", "wx_aibuilder3", DEFAULT_PASSWORD),
        new UserSeed("bizowner", "业务负责人", "BUSINESS_OWNER", "业务创新部", "13800138005", "bizowner@aegisai.com", "wx_bizowner", DEFAULT_PASSWORD),
        new UserSeed("bizowner_2", "业务负责人二号", "BUSINESS_OWNER", "业务创新部", "13800138055", "bizowner2@aegisai.com", "wx_bizowner2", DEFAULT_PASSWORD),
        new UserSeed("bizowner_3", "业务负责人三号", "BUSINESS_OWNER", "业务创新部", "13800138056", "bizowner3@aegisai.com", "wx_bizowner3", DEFAULT_PASSWORD),
        new UserSeed("employee", "普通员工", "EMPLOYEE", "业务一线", "13800138006", "employee@aegisai.com", "wx_employee", DEFAULT_PASSWORD),
        new UserSeed("employee_2", "普通员工二号", "EMPLOYEE", "业务一线", "13800138066", "employee2@aegisai.com", "wx_employee2", DEFAULT_PASSWORD),
        new UserSeed("employee_3", "普通员工三号", "EMPLOYEE", "业务一线", "13800138067", "employee3@aegisai.com", "wx_employee3", DEFAULT_PASSWORD)
    );

    @Autowired
    private UserService userService;
    @Autowired
    private RoleService roleService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Override
    public void run(String... args) {
        Map<String, Role> roleMap = ensureDefaultRoles(DEFAULT_COMPANY_ID);
        for (UserSeed seed : BASELINE_USERS) {
            ensureUser(DEFAULT_COMPANY_ID, seed, roleMap.get(seed.roleCode()));
        }
        seedGovernanceAdminDemoData(DEFAULT_COMPANY_ID, roleMap);
    }

    private void seedGovernanceAdminDemoData(Long companyId, Map<String, Role> roleMap) {
        seedPermissionsAndRoleBindings(companyId, roleMap);
        seedApprovalAndAuditData(companyId);
        seedRiskAndGovernanceData(companyId);
        seedSubjectRequestAndPolicyData(companyId);
    }

    private void seedPermissionsAndRoleBindings(Long companyId, Map<String, Role> roleMap) {
        if (!tableExists("permission") || !tableExists("role_permission")) {
            return;
        }
        List<PermissionSeed> permissionSeeds = List.of(
            new PermissionSeed("用户读取", "USER_VIEW", "menu"),
            new PermissionSeed("用户管理", "USER_MANAGE", "button"),
            new PermissionSeed("角色管理", "ROLE_MANAGE", "menu"),
            new PermissionSeed("权限管理", "PERMISSION_MANAGE", "menu"),
            new PermissionSeed("审批读取", "APPROVAL_VIEW", "menu"),
            new PermissionSeed("审批处理", "APPROVAL_OPERATE", "button"),
            new PermissionSeed("审计读取", "AUDIT_VIEW", "menu"),
            new PermissionSeed("审计导出", "AUDIT_EXPORT", "button"),
            new PermissionSeed("风险事件处理", "RISK_EVENT_HANDLE", "button"),
            new PermissionSeed("策略管理", "POLICY_MANAGE", "menu"),
            new PermissionSeed("主体权利处理", "SUBJECT_REQUEST_HANDLE", "button"),
            new PermissionSeed("模型风险总览", "MODEL_RISK_VIEW", "menu")
        );

        Map<String, Long> permissionIdByCode = new LinkedHashMap<>();
        for (PermissionSeed seed : permissionSeeds) {
            Long existingId = querySingleLong(
                "SELECT id FROM permission WHERE company_id = ? AND code = ? ORDER BY id ASC LIMIT 1",
                companyId,
                seed.code()
            );
            if (existingId == null) {
                Date now = new Date();
                jdbcTemplate.update(
                    "INSERT INTO permission(company_id, name, code, type, parent_id, create_time, update_time) VALUES(?, ?, ?, ?, NULL, ?, ?)",
                    companyId,
                    seed.name(),
                    seed.code(),
                    seed.type(),
                    now,
                    now
                );
                existingId = querySingleLong(
                    "SELECT id FROM permission WHERE company_id = ? AND code = ? ORDER BY id DESC LIMIT 1",
                    companyId,
                    seed.code()
                );
            }
            if (existingId != null) {
                permissionIdByCode.put(seed.code(), existingId);
            }
        }

        bindPermissions(roleMap.get("ADMIN"), permissionIdByCode.keySet());
        bindPermissions(roleMap.get("SECOPS"), Arrays.asList("AUDIT_VIEW", "AUDIT_EXPORT", "RISK_EVENT_HANDLE", "APPROVAL_VIEW", "MODEL_RISK_VIEW"));
        bindPermissions(roleMap.get("DATA_ADMIN"), Arrays.asList("APPROVAL_VIEW", "APPROVAL_OPERATE", "POLICY_MANAGE"));
        bindPermissions(roleMap.get("BUSINESS_OWNER"), Arrays.asList("APPROVAL_VIEW", "APPROVAL_OPERATE", "MODEL_RISK_VIEW"));
    }

    private void bindPermissions(Role role, Iterable<String> permissionCodes) {
        if (role == null || role.getId() == null) {
            return;
        }
        for (String code : permissionCodes) {
            Long permissionId = querySingleLong(
                "SELECT id FROM permission WHERE company_id = ? AND code = ? ORDER BY id ASC LIMIT 1",
                role.getCompanyId(),
                code
            );
            if (permissionId == null) {
                continue;
            }
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM role_permission WHERE role_id = ? AND permission_id = ?",
                Integer.class,
                role.getId(),
                permissionId
            );
            if (exists == null || exists == 0) {
                jdbcTemplate.update("INSERT INTO role_permission(role_id, permission_id) VALUES(?, ?)", role.getId(), permissionId);
            }
        }
    }

    private void seedApprovalAndAuditData(Long companyId) {
        if (!tableExists("approval_request") || !tableExists("audit_log")) {
            return;
        }
        List<User> users = userService.lambdaQuery().eq(User::getCompanyId, companyId).list();
        if (users.isEmpty()) {
            return;
        }
        Map<String, User> userByName = new LinkedHashMap<>();
        for (User user : users) {
            userByName.put(String.valueOf(user.getUsername()).toLowerCase(Locale.ROOT), user);
        }

        User admin = userByName.get("admin");
        User dataAdmin = userByName.get("dataadmin");
        User bizOwner = userByName.get("bizowner");
        List<User> applicants = new ArrayList<>();
        for (String key : List.of("employee", "aibuilder", "dataadmin", "bizowner")) {
            User user = userByName.get(key);
            if (user != null) {
                applicants.add(user);
            }
        }
        if (applicants.isEmpty()) {
            applicants.addAll(users);
        }

        Integer approvalCount = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM approval_request WHERE company_id = ?", Integer.class, companyId);
        int targetApprovals = 96;
        int existingApprovals = approvalCount == null ? 0 : approvalCount;
        Random random = new Random(20260330L);
        Date now = new Date();

        for (int i = existingApprovals; i < targetApprovals; i++) {
            User applicant = applicants.get(i % applicants.size());
            String prefix = i % 3 == 0 ? "[DATA]" : (i % 3 == 1 ? "[BUSINESS]" : "[PERSONAL]");
            String status = i % 5 == 0 ? "拒绝" : (i % 2 == 0 ? "通过" : "待审批");
            Long approverId;
            if ("待审批".equals(status)) {
                approverId = null;
            } else if ("[BUSINESS]".equals(prefix) && bizOwner != null) {
                approverId = bizOwner.getId();
            } else if ("[DATA]".equals(prefix) && dataAdmin != null) {
                approverId = dataAdmin.getId();
            } else {
                approverId = admin == null ? applicant.getId() : admin.getId();
            }

            String reason = prefix + " 申请访问资产 " + (1000 + i) + " 用于跨部门治理协同验证";
            Date createTime = new Date(now.getTime() - (long) (targetApprovals - i) * 3600_000L);
            Date updateTime = new Date(createTime.getTime() + random.nextInt(1800) * 1000L);
            jdbcTemplate.update(
                "INSERT INTO approval_request(company_id, applicant_id, asset_id, reason, status, approver_id, process_instance_id, task_id, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                companyId,
                applicant.getId(),
                1000L + i,
                reason,
                status,
                approverId,
                "PROC-" + i,
                "TASK-" + i,
                createTime,
                updateTime
            );
        }

        Integer auditCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM audit_log WHERE user_id IN (SELECT id FROM sys_user WHERE company_id = ?)",
            Integer.class,
            companyId
        );
        int targetAudit = 420;
        int existingAudit = auditCount == null ? 0 : auditCount;
        List<Long> approvalIds = jdbcTemplate.query(
            "SELECT id FROM approval_request WHERE company_id = ? ORDER BY id ASC",
            (rs, rowNum) -> rs.getLong(1),
            companyId
        );
        List<User> actors = users;

        for (int i = existingAudit; i < targetAudit; i++) {
            User actor = actors.get(i % actors.size());
            String operation;
            if (i % 7 == 0) {
                operation = "approval_approve";
            } else if (i % 7 == 1) {
                operation = "approval_reject";
            } else if (i % 7 == 2) {
                operation = "permission_update";
            } else if (i % 7 == 3) {
                operation = "role_update";
            } else if (i % 7 == 4) {
                operation = "subject_request_handle";
            } else {
                operation = "policy_publish";
            }
            Long linkedApprovalId = approvalIds.isEmpty() ? null : approvalIds.get(i % approvalIds.size());
            Date opTime = new Date(now.getTime() - (long) (targetAudit - i) * 900_000L);
            String inputOverview = linkedApprovalId == null
                ? "companyId=" + companyId + ", actor=" + actor.getUsername()
                : "eventId=" + linkedApprovalId + ", companyId=" + companyId + ", actor=" + actor.getUsername();
            String outputOverview = "result=success, trace=" + operation + ", idx=" + i;
            String risk = i % 9 == 0 ? "HIGH" : (i % 3 == 0 ? "MEDIUM" : "LOW");

            jdbcTemplate.update(
                "INSERT INTO audit_log(user_id, asset_id, operation, operation_time, ip, device, input_overview, output_overview, result, risk_level, hash, create_time) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                actor.getId(),
                linkedApprovalId == null ? 0L : linkedApprovalId,
                operation,
                opTime,
                "10.10." + (i % 20) + "." + (i % 255),
                "governance-console",
                inputOverview,
                outputOverview,
                "success",
                risk,
                Integer.toHexString((inputOverview + outputOverview + opTime.getTime()).hashCode()),
                opTime
            );
        }
    }

    private void seedRiskAndGovernanceData(Long companyId) {
        if (!tableExists("risk_event")) {
            return;
        }
        Integer riskCount = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM risk_event WHERE company_id = ?", Integer.class, companyId);
        int targetRisk = 72;
        int existingRisk = riskCount == null ? 0 : riskCount;
        List<Long> auditIds = jdbcTemplate.query(
            "SELECT id FROM audit_log WHERE user_id IN (SELECT id FROM sys_user WHERE company_id = ?) ORDER BY id DESC LIMIT 500",
            (rs, rowNum) -> rs.getLong(1),
            companyId
        );
        Date now = new Date();
        for (int i = existingRisk; i < targetRisk; i++) {
            Long relatedAudit = auditIds.isEmpty() ? null : auditIds.get(i % auditIds.size());
            String type = i % 3 == 0 ? "DATA_LEAKAGE" : (i % 3 == 1 ? "ABNORMAL_ACCESS" : "PRIVACY_VIOLATION");
            String level = i % 5 == 0 ? "HIGH" : (i % 2 == 0 ? "MEDIUM" : "LOW");
            String status = i % 4 == 0 ? "已处理" : "待处理";
            Date createTime = new Date(now.getTime() - (long) (targetRisk - i) * 5400_000L);
            jdbcTemplate.update(
                "INSERT INTO risk_event(company_id, type, level, related_log_id, audit_log_ids, status, handler_id, process_log, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                companyId,
                type,
                level,
                relatedAudit,
                relatedAudit == null ? "" : String.valueOf(relatedAudit),
                status,
                null,
                "自动生成治理演示风险事件 #" + i,
                createTime,
                createTime
            );
        }

        try {
            Integer govCount = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM governance_event WHERE company_id = ?", Integer.class, companyId);
            int targetGov = 120;
            int existingGov = govCount == null ? 0 : govCount;
            for (int i = existingGov; i < targetGov; i++) {
                String severity = i % 10 == 0 ? "high" : (i % 3 == 0 ? "medium" : "low");
                String status = i % 4 == 0 ? "blocked" : (i % 4 == 1 ? "resolved" : "pending");
                String eventType = i % 2 == 0 ? "MODEL_RISK_ALERT" : "POLICY_BREACH";
                Date eventTime = new Date(now.getTime() - (long) (targetGov - i) * 1800_000L);
                jdbcTemplate.update(
                    "INSERT INTO governance_event(company_id, user_id, username, event_type, source_module, severity, status, title, description, source_event_id, attack_type, policy_version, payload_json, handler_id, dispose_note, event_time, disposed_at, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    companyId,
                    null,
                    "system",
                    eventType,
                    "governance-center",
                    severity,
                    status,
                    "治理事件 " + i,
                    "用于治理管理员演示的跨模块关联治理事件",
                    "SRC-" + i,
                    i % 2 == 0 ? "prompt_injection" : "data_exfil_steg",
                    1L,
                    "{\"eventIndex\":" + i + "}",
                    null,
                    "",
                    eventTime,
                    "pending".equals(status) ? null : new Date(eventTime.getTime() + 1200_000L),
                    eventTime,
                    eventTime
                );
            }
        } catch (Exception ignored) {
            // governance_event table may be unavailable before optional schema initializer.
        }
    }

    private void seedSubjectRequestAndPolicyData(Long companyId) {
        if (!tableExists("subject_request") || !tableExists("compliance_policy")) {
            return;
        }
        Integer subjectCount = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM subject_request WHERE company_id = ?", Integer.class, companyId);
        int targetSubject = 48;
        int existingSubject = subjectCount == null ? 0 : subjectCount;
        List<User> users = userService.lambdaQuery().eq(User::getCompanyId, companyId).list();
        if (!users.isEmpty()) {
            Date now = new Date();
            for (int i = existingSubject; i < targetSubject; i++) {
                User user = users.get(i % users.size());
                String type = i % 3 == 0 ? "access" : (i % 3 == 1 ? "export" : "delete");
                String status = i % 4 == 0 ? "done" : (i % 4 == 1 ? "processing" : "pending");
                Date createTime = new Date(now.getTime() - (long) (targetSubject - i) * 7200_000L);
                jdbcTemplate.update(
                    "INSERT INTO subject_request(company_id, user_id, type, status, comment, handler_id, result, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    companyId,
                    user.getId(),
                    type,
                    status,
                    "主体请求演示工单 #" + i,
                    null,
                    "",
                    createTime,
                    createTime
                );
            }
        }

        Integer policyCount = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM compliance_policy WHERE company_id = ?", Integer.class, companyId);
        int targetPolicy = 24;
        int existingPolicy = policyCount == null ? 0 : policyCount;
        Date now = new Date();
        for (int i = existingPolicy; i < targetPolicy; i++) {
            Date createTime = new Date(now.getTime() - (long) (targetPolicy - i) * 86400_000L);
            jdbcTemplate.update(
                "INSERT INTO compliance_policy(company_id, name, rule_content, scope, status, version, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, ?, ?)",
                companyId,
                "治理策略-" + (i + 1),
                "{\"rule\":\"deny high risk export\",\"index\":" + i + "}",
                i % 2 == 0 ? "全局" : "指定资产",
                1,
                1 + (i % 3),
                createTime,
                createTime
            );
        }
    }

    private Long querySingleLong(String sql, Object... args) {
        List<Long> rows = jdbcTemplate.query(sql, (rs, rowNum) -> rs.getLong(1), args);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private boolean tableExists(String tableName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.tables WHERE lower(table_name) = lower(?)",
                Integer.class,
                tableName
            );
            return count != null && count > 0;
        } catch (Exception ex) {
            return false;
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
        List<Role> existingRoles = roleService.lambdaQuery()
            .eq(Role::getCompanyId, companyId)
            .eq(Role::getCode, code)
            .list();
        Role existing = pickRole(existingRoles);
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
        List<User> existingUsers = userService.lambdaQuery().eq(User::getUsername, seed.username()).list();
        User user = pickUser(existingUsers);
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

    private Role pickRole(List<Role> roles) {
        return roles.stream()
            .min(Comparator.comparing(role -> role.getId() == null ? Long.MAX_VALUE : role.getId()))
            .orElse(null);
    }

    private User pickUser(List<User> users) {
        return users.stream()
            .min(Comparator.comparing(user -> user.getId() == null ? Long.MAX_VALUE : user.getId()))
            .orElse(null);
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

    private record PermissionSeed(
        String name,
        String code,
        String type
    ) {}
}