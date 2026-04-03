package com.trustai.config;

import com.trustai.entity.Role;
import com.trustai.entity.User;
import com.trustai.entity.AuditLog;
import com.trustai.service.AuditLogService;
import com.trustai.service.RoleService;
import com.trustai.service.UserService;
import java.util.Calendar;
import java.util.Date;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import org.springframework.beans.factory.annotation.Value;
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
        new UserSeed("employee1", "普通员工一号", "EMPLOYEE", "业务一线", "13800138006", "employee1@aegisai.com", "wx_employee1", DEFAULT_PASSWORD),
        new UserSeed("employee2", "普通员工二号", "EMPLOYEE", "业务一线", "13800138066", "employee2@aegisai.com", "wx_employee2", DEFAULT_PASSWORD),
        new UserSeed("employee3", "普通员工三号", "EMPLOYEE", "业务一线", "13800138067", "employee3@aegisai.com", "wx_employee3", DEFAULT_PASSWORD)
    );

    @Autowired
    private UserService userService;
    @Autowired
    private RoleService roleService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private AuditLogService auditLogService;

    @Value("${app.seed.demo-data:false}")
    private boolean seedDemoData;

    @Override
    public void run(String... args) {
        enforceTrustedAiModelBaseline();
        Map<String, Role> roleMap = ensureDefaultRoles(DEFAULT_COMPANY_ID);
        cleanupDuplicateDefaultRoles(DEFAULT_COMPANY_ID);
        roleMap = ensureDefaultRoles(DEFAULT_COMPANY_ID);
        seedPermissionsAndRoleBindings(DEFAULT_COMPANY_ID, roleMap);
        for (UserSeed seed : BASELINE_USERS) {
            ensureUser(DEFAULT_COMPANY_ID, seed, roleMap.get(seed.roleCode()));
        }
        seedEnterpriseDataAssets(DEFAULT_COMPANY_ID);
        seedTraceableObservabilityBaseline(DEFAULT_COMPANY_ID);
        seedShadowAiClientBaseline(DEFAULT_COMPANY_ID);
        repairHistoricalTraceability(DEFAULT_COMPANY_ID);
        seedSensitiveScanTraceabilityBaseline(DEFAULT_COMPANY_ID);
        if (seedDemoData) {
            seedGovernanceAdminDemoData(DEFAULT_COMPANY_ID, roleMap);
        }
    }

    private void seedSensitiveScanTraceabilityBaseline(Long companyId) {
        if (!tableExists("sensitive_scan_task")) {
            return;
        }
        if (!columnExists("sensitive_scan_task", "company_id") || !columnExists("sensitive_scan_task", "user_id") || !columnExists("sensitive_scan_task", "trace_json")) {
            return;
        }
        Integer companyTaskCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(1) FROM sensitive_scan_task WHERE company_id = ?",
            Integer.class,
            companyId
        );
        if (companyTaskCount != null && companyTaskCount > 0) {
            return;
        }
        boolean hasReportDataColumn = columnExists("sensitive_scan_task", "report_data");

        Long adminId = querySingleLong(
            "SELECT id FROM sys_user WHERE company_id = ? AND username = ? ORDER BY id ASC LIMIT 1",
            companyId,
            "admin"
        );
        Long dataAdminId = querySingleLong(
            "SELECT id FROM sys_user WHERE company_id = ? AND username = ? ORDER BY id ASC LIMIT 1",
            companyId,
            "dataadmin"
        );

        List<Long> seeds = new ArrayList<>();
        if (adminId != null) {
            seeds.add(adminId);
        }
        if (dataAdminId != null && !Objects.equals(dataAdminId, adminId)) {
            seeds.add(dataAdminId);
        }
        if (seeds.isEmpty()) {
            Long fallback = querySingleLong(
                "SELECT id FROM sys_user WHERE company_id = ? ORDER BY id ASC LIMIT 1",
                companyId
            );
            if (fallback != null) {
                seeds.add(fallback);
            }
        }

        Date now = new Date();
        int idx = 0;
        for (Long userId : seeds) {
            User actor = userService.getById(userId);
            if (actor == null) {
                continue;
            }
            Date taskTime = new Date(now.getTime() - idx * 3600_000L);
            String trace = buildSensitiveScanTrace(actor, companyId);
            if (hasReportDataColumn) {
                jdbcTemplate.update(
                    "INSERT INTO sensitive_scan_task(company_id, user_id, source_type, source_path, trace_json, status, sensitive_ratio, report_path, report_data, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    companyId,
                    actor.getId(),
                    "file",
                    "/data/demo/traceable-scan-" + actor.getUsername() + ".txt",
                    trace,
                    idx == 0 ? "done" : "pending",
                    idx == 0 ? 36.5 : null,
                    idx == 0 ? "/reports/task-trace-baseline-" + actor.getId() + ".json" : null,
                    idx == 0 ? "{\"summary\":{\"total\":1,\"sensitiveFields\":[\"id_card\",\"phone\"],\"ratio\":36.5},\"results\":[{\"text\":\"[seed] 可追溯扫描样本\",\"label\":\"id_card\",\"score\":0.97}]}" : null,
                    taskTime,
                    taskTime
                );
            } else {
                jdbcTemplate.update(
                    "INSERT INTO sensitive_scan_task(company_id, user_id, source_type, source_path, trace_json, status, sensitive_ratio, report_path, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    companyId,
                    actor.getId(),
                    "file",
                    "/data/demo/traceable-scan-" + actor.getUsername() + ".txt",
                    trace,
                    idx == 0 ? "done" : "pending",
                    idx == 0 ? 36.5 : null,
                    idx == 0 ? "/reports/task-trace-baseline-" + actor.getId() + ".json" : null,
                    taskTime,
                    taskTime
                );
            }
            idx++;
        }
    }

    private String buildSensitiveScanTrace(User user, Long companyId) {
        Map<String, Object> trace = new LinkedHashMap<>();
        trace.put("username", user == null ? "-" : user.getUsername());
        trace.put("userId", user == null ? "-" : user.getId());
        trace.put("role", user == null ? "-" : resolveRoleCode(user.getRoleId()));
        trace.put("department", user == null ? "-" : user.getDepartment());
        trace.put("position", user == null ? "-" : user.getJobTitle());
        trace.put("companyId", companyId == null ? "-" : companyId);
        trace.put("device", user == null ? "-" : user.getDeviceId());
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(trace);
        } catch (Exception ignored) {
            return "{}";
        }
    }

    private void seedTraceableObservabilityBaseline(Long companyId) {
        if (!tableExists("model_call_stat") || !tableExists("audit_log") || !tableExists("risk_event")) {
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
        List<User> actors = users;

        Date now = new Date();
        boolean aiModelHasCompanyColumn = columnExists("ai_model", "company_id");
        Long aiModelId = aiModelHasCompanyColumn
            ? querySingleLong(
                "SELECT id FROM ai_model WHERE company_id = ? AND LOWER(provider) IN ('qwen','wenxin','deepseek','doubao','hunyuan','kimi','spark','zhipu','modelwhale') ORDER BY id ASC LIMIT 1",
                companyId
            )
            : querySingleLong(
                "SELECT id FROM ai_model WHERE LOWER(provider) IN ('qwen','wenxin','deepseek','doubao','hunyuan','kimi','spark','zhipu','modelwhale') ORDER BY id ASC LIMIT 1"
            );
        for (int dayOffset = 13; dayOffset >= 0; dayOffset--) {
            Date day = new Date(now.getTime() - dayOffset * 24L * 3600_000L);
            Date dayStart = atStartOfDay(day);
            Date dayEnd = new Date(dayStart.getTime() + 24L * 3600_000L);
            User actor = actors.get(dayOffset % actors.size());

            Integer dailyAuditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM audit_log WHERE user_id = ? AND operation_time >= ? AND operation_time < ?",
                Integer.class,
                actor.getId(),
                dayStart,
                dayEnd
            );
            int auditBurst = 3 + (dayOffset % 4);
            if (dailyAuditCount == null || dailyAuditCount < auditBurst) {
                int deficit = auditBurst - (dailyAuditCount == null ? 0 : dailyAuditCount);
                for (int i = 0; i < deficit; i++) {
                    Date auditTime = new Date(dayStart.getTime() + (2L + i * 3L) * 3600_000L);
                    AuditLog log = new AuditLog();
                    log.setUserId(actor.getId());
                    log.setOperation("demo_observability_baseline");
                    log.setOperationTime(auditTime);
                    log.setInputOverview("companyId=" + companyId + ", actor=" + actor.getUsername() + ", burst=" + i);
                    log.setOutputOverview("seed=trend-baseline, traceUser=" + actor.getUsername());
                    log.setResult("success");
                    log.setRiskLevel((i + dayOffset) % 4 == 0 ? "MEDIUM" : "LOW");
                    log.setCreateTime(auditTime);
                    auditLogService.saveAudit(log);
                }
            }

            Integer dailyModelStats = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM model_call_stat WHERE user_id = ? AND date >= ? AND date < ?",
                Integer.class,
                actor.getId(),
                dayStart,
                dayEnd
            );
            if (dailyModelStats == null || dailyModelStats == 0) {
                jdbcTemplate.update(
                    "INSERT INTO model_call_stat(model_id, user_id, date, call_count, total_latency_ms, cost_cents) VALUES(?, ?, ?, ?, ?, ?)",
                    aiModelId,
                    actor.getId(),
                    day,
                    6 + (dayOffset % 5),
                    6000L + dayOffset * 200L,
                    80 + dayOffset * 5
                );
            }

            if (tableExists("ai_call_log")) {
                Integer dailyAiCalls = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM ai_call_log WHERE company_id = ? AND user_id = ? AND create_time >= ? AND create_time < ?",
                    Integer.class,
                    companyId,
                    actor.getId(),
                    dayStart,
                    dayEnd
                );
                int callBurst = 2 + (dayOffset % 3);
                if (dailyAiCalls == null || dailyAiCalls < callBurst) {
                    int deficit = callBurst - (dailyAiCalls == null ? 0 : dailyAiCalls);
                    for (int i = 0; i < deficit; i++) {
                        Date callTime = new Date(dayStart.getTime() + (3L + i * 2L) * 3600_000L);
                        String provider = switch ((dayOffset + i) % 5) {
                            case 0 -> "qwen";
                            case 1 -> "wenxin";
                            case 2 -> "deepseek";
                            case 3 -> "doubao";
                            default -> "hunyuan";
                        };
                        String modelCode = switch (provider) {
                            case "qwen" -> "qwen-max";
                            case "wenxin" -> "ernie-4";
                            case "deepseek" -> "deepseek-chat";
                            case "doubao" -> "doubao-pro";
                            default -> "hunyuan-standard";
                        };
                        jdbcTemplate.update(
                            "INSERT INTO ai_call_log(user_id, company_id, username, model_code, provider, input_preview, output_preview, status, duration_ms, token_usage, ip, create_time) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            actor.getId(),
                            companyId,
                            actor.getUsername(),
                            modelCode,
                            provider,
                            "[seed] observability call by " + actor.getUsername(),
                            "[seed] response ok",
                            (i + dayOffset) % 6 == 0 ? "fail" : "success",
                            700 + (dayOffset * 40L) + i * 60L,
                            220 + dayOffset * 10 + i * 20,
                            "10.20." + ((dayOffset % 5) + 1) + "." + (20 + i),
                            callTime
                        );
                    }
                }
            }

            if (tableExists("governance_event")) {
                Integer dailyGovEvents = jdbcTemplate.queryForObject(
                    "SELECT COUNT(1) FROM governance_event WHERE company_id = ? AND user_id = ? AND event_time >= ? AND event_time < ?",
                    Integer.class,
                    companyId,
                    actor.getId(),
                    dayStart,
                    dayEnd
                );
                if (dailyGovEvents == null || dailyGovEvents == 0) {
                    String eventType = dayOffset % 2 == 0 ? "PRIVACY_ALERT" : "ANOMALY_ALERT";
                    String severity = dayOffset % 5 == 0 ? "high" : "medium";
                    Date eventTime = new Date(dayStart.getTime() + 8L * 3600_000L);
                    String payload = "{\"trace\":{\"username\":\"" + actor.getUsername() + "\",\"userId\":" + actor.getId() + ",\"role\":\"" + resolveRoleCode(actor.getRoleId()) + "\",\"department\":\"" + String.valueOf(actor.getDepartment()) + "\",\"position\":\"" + String.valueOf(actor.getJobTitle()) + "\",\"companyId\":" + companyId + ",\"device\":\"" + String.valueOf(actor.getDeviceId()) + "\"}}";
                    jdbcTemplate.update(
                        "INSERT INTO governance_event(company_id, user_id, username, event_type, source_module, severity, status, title, description, source_event_id, attack_type, policy_version, payload_json, event_time, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        companyId,
                        actor.getId(),
                        actor.getUsername(),
                        eventType,
                        "behavior-monitor",
                        severity,
                        "pending",
                        "[seed] 可追溯观测事件",
                        "用于运维观测与员工行为监控联动展示",
                        "SEED-OBS-" + actor.getId() + "-" + dayOffset,
                        eventType.equals("PRIVACY_ALERT") ? "data_exfil_plain" : "abnormal_access",
                        1L,
                        payload,
                        eventTime,
                        eventTime,
                        eventTime
                    );
                }
            }

            Integer dailyRisk = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM risk_event WHERE company_id = ? AND create_time >= ? AND create_time < ?",
                Integer.class,
                companyId,
                dayStart,
                dayEnd
            );
            if (dailyRisk == null || dailyRisk == 0) {
                Long relatedAudit = querySingleLong(
                    "SELECT a.id FROM audit_log a JOIN sys_user u ON a.user_id = u.id WHERE u.company_id = ? ORDER BY a.id DESC LIMIT 1",
                    companyId
                );
                jdbcTemplate.update(
                    "INSERT INTO risk_event(company_id, type, level, related_log_id, audit_log_ids, status, handler_id, process_log, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    companyId,
                    dayOffset % 2 == 0 ? "ABNORMAL_ACCESS" : "PRIVACY_VIOLATION",
                    dayOffset % 5 == 0 ? "HIGH" : "MEDIUM",
                    relatedAudit,
                    relatedAudit == null ? "" : String.valueOf(relatedAudit),
                    dayOffset % 4 == 0 ? "已处理" : "待处理",
                    actor.getId(),
                    "观测基线事件，主体=" + actor.getUsername(),
                    day,
                    day
                );
            }
        }
    }

    private void seedShadowAiClientBaseline(Long companyId) {
        if (!tableExists("client_report")) {
            return;
        }
        List<User> users = userService.lambdaQuery().eq(User::getCompanyId, companyId).list();
        if (users.isEmpty()) {
            return;
        }
        Date now = new Date();
        int idx = 0;
        for (User user : users) {
            String username = String.valueOf(user.getUsername()).toLowerCase(Locale.ROOT);
            if (username.startsWith("walkthrough_")) {
                continue;
            }
            String clientId = "seed-client-" + username;
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM client_report WHERE company_id = ? AND client_id = ?",
                Integer.class,
                companyId,
                clientId
            );
            if (exists != null && exists > 0) {
                idx++;
                continue;
            }
            String osType = idx % 2 == 0 ? "Windows" : "macOS";
            String riskLevel = idx % 4 == 0 ? "high" : (idx % 3 == 0 ? "medium" : "low");
            int shadowCount = String.valueOf(user.getUsername()).toLowerCase(Locale.ROOT).startsWith("employee") ? 1 : 2;
            String services = "[{\"name\":\"ChatGPT\",\"domain\":\"chat.openai.com\",\"riskLevel\":\"" + riskLevel + "\"},{\"name\":\"Doubao\",\"domain\":\"www.doubao.com\",\"riskLevel\":\"medium\"}]";
            Date scanTime = new Date(now.getTime() - (long) (idx + 1) * 1800_000L);
            jdbcTemplate.update(
                "INSERT INTO client_report(company_id, client_id, hostname, ip_address, os_username, os_type, client_version, discovered_services, shadow_ai_count, risk_level, scan_time, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                companyId,
                clientId,
                "WS-" + username.toUpperCase(Locale.ROOT).replace('_', '-'),
                "10.30." + ((idx % 10) + 1) + "." + (20 + idx),
                user.getUsername(),
                osType,
                "1.0.3",
                services,
                shadowCount,
                riskLevel,
                scanTime,
                scanTime,
                scanTime
            );
            idx++;
        }
    }

    private Date atStartOfDay(Date value) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(value);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTime();
    }

    private void repairHistoricalTraceability(Long companyId) {
        if (!tableExists("governance_event")) {
            return;
        }
        List<User> users = userService.lambdaQuery().eq(User::getCompanyId, companyId).list();
        if (users.isEmpty()) {
            return;
        }
        Map<String, User> byName = new LinkedHashMap<>();
        for (User user : users) {
            byName.put(String.valueOf(user.getUsername()).toLowerCase(Locale.ROOT), user);
        }
        User admin = byName.getOrDefault("admin", users.get(0));
        User secops = byName.getOrDefault("secops", admin);

        List<Map<String, Object>> brokenEvents = jdbcTemplate.queryForList(
            "SELECT id, source_module FROM governance_event WHERE company_id = ? AND (user_id IS NULL OR username IS NULL OR LOWER(username) IN ('system','anonymous','匿名'))",
            companyId
        );
        for (Map<String, Object> row : brokenEvents) {
            Long id = ((Number) row.get("id")).longValue();
            String module = String.valueOf(row.getOrDefault("source_module", ""));
            User target = (module.contains("security") || module.contains("shadow") || module.contains("observability")) ? secops : admin;

            jdbcTemplate.update(
                "UPDATE governance_event SET user_id = ?, username = ?, update_time = CURRENT_TIMESTAMP WHERE id = ?",
                target.getId(),
                target.getUsername(),
                id
            );

            AuditLog trace = new AuditLog();
            trace.setUserId(admin.getId());
            trace.setOperation("traceability_repair");
            trace.setOperationTime(new Date());
            trace.setInputOverview("governanceEventId=" + id + ", module=" + module);
            trace.setOutputOverview("bindUser=" + target.getUsername());
            trace.setResult("success");
            trace.setRiskLevel("MEDIUM");
            trace.setCreateTime(new Date());
            auditLogService.saveAudit(trace);
        }
    }

    private void seedEnterpriseDataAssets(Long companyId) {
        if (!tableExists("data_asset")) {
            return;
        }
        Long dataAdminId = querySingleLong(
            "SELECT id FROM sys_user WHERE company_id = ? AND username = ? ORDER BY id ASC LIMIT 1",
            companyId,
            "dataadmin"
        );
        Long adminId = querySingleLong(
            "SELECT id FROM sys_user WHERE company_id = ? AND username = ? ORDER BY id ASC LIMIT 1",
            companyId,
            "admin"
        );
        Long defaultOwnerId = dataAdminId != null ? dataAdminId : adminId;

        List<EnterpriseAssetSeed> seeds = List.of(
            new EnterpriseAssetSeed("客户主数据平台", "table", "critical", "mysql://dwh/customer_master", "已纳管", "{\"upstream\":[\"crm_user\",\"mdm_profile\"]}", 21),
            new EnterpriseAssetSeed("研发代码仓库镜像", "file", "high", "git://code.internal/core-services", "已纳管", "{\"upstream\":[\"gitlab\",\"ci_artifacts\"]}", 20),
            new EnterpriseAssetSeed("用户行为事件日志", "table", "high", "hdfs://lake/ods/user_behavior", "扫描中", "{\"upstream\":[\"app_sdk\",\"web_tracker\"]}", 18),
            new EnterpriseAssetSeed("模型训练样本集", "file", "high", "s3://ml-platform/training/samples", "已纳管", "{\"upstream\":[\"label_platform\",\"feature_store\"]}", 17),
            new EnterpriseAssetSeed("财务流水总账", "table", "critical", "oracle://finance/gl_transaction", "已纳管", "{\"upstream\":[\"erp\",\"settlement\"]}", 15),
            new EnterpriseAssetSeed("电子合同归档库", "document", "critical", "oss://legal/contracts/archives", "扫描中", "{\"upstream\":[\"contract_center\"]}", 14),
            new EnterpriseAssetSeed("客服会话记录", "document", "high", "es://service/chat_sessions", "已纳管", "{\"upstream\":[\"service_desk\",\"voice2text\"]}", 13),
            new EnterpriseAssetSeed("组织架构主档", "table", "medium", "mysql://hr/org_structure", "已纳管", "{\"upstream\":[\"hr_core\"]}", 12),
            new EnterpriseAssetSeed("供应链采购台账", "table", "medium", "postgres://scm/procurement_ledger", "已纳管", "{\"upstream\":[\"supplier_portal\"]}", 11),
            new EnterpriseAssetSeed("风控规则版本库", "api", "high", "api://risk-engine/rule-repo", "扫描中", "{\"upstream\":[\"risk_console\",\"decision_engine\"]}", 10),
            new EnterpriseAssetSeed("经营分析指标集", "table", "low", "clickhouse://bi/ops_kpi", "已纳管", "{\"upstream\":[\"bi_etl\",\"ops_report\"]}", 9),
            new EnterpriseAssetSeed("终端威胁检测记录", "table", "high", "mysql://secops/endpoint_threat", "已纳管", "{\"upstream\":[\"agent_collector\",\"threat_center\"]}", 8)
        );

        Date now = new Date();
        for (EnterpriseAssetSeed seed : seeds) {
            Long exists = querySingleLong(
                "SELECT id FROM data_asset WHERE company_id = ? AND name = ? ORDER BY id ASC LIMIT 1",
                companyId,
                seed.name()
            );
            if (exists != null) {
                continue;
            }

            Date createTime = new Date(now.getTime() - seed.daysAgo() * 24L * 3600_000L);
            jdbcTemplate.update(
                "INSERT INTO data_asset(company_id, name, type, sensitivity_level, location, discovery_time, owner_id, lineage, description, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                companyId,
                seed.name(),
                seed.type(),
                seed.sensitivityLevel(),
                seed.location(),
                createTime,
                defaultOwnerId,
                seed.lineage(),
                "治理状态：" + seed.governanceStatus() + "；来源系统已完成资产指纹采集。",
                createTime,
                createTime
            );
        }
    }

    private void enforceTrustedAiModelBaseline() {
        if (!tableExists("ai_model")) {
            return;
        }
        Long companyId = DEFAULT_COMPANY_ID;
        boolean hasIsolationLevelColumn = columnExists("ai_model", "isolation_level");
        boolean hasCompanyIdColumn = columnExists("ai_model", "company_id");

        List<String> trustedProviders = List.of("qwen", "wenxin", "deepseek", "doubao", "hunyuan", "kimi", "spark", "zhipu", "modelwhale");
        List<Object[]> modelSeeds = List.of(
            new Object[] {"通义千问", "qwen-max", "qwen", "chat", "low", "L1", "enabled", "官方可信白名单模型"},
            new Object[] {"文心一言", "ernie-4", "wenxin", "chat", "low", "L1", "enabled", "官方可信白名单模型"},
            new Object[] {"DeepSeek", "deepseek-chat", "deepseek", "chat", "medium", "L2", "enabled", "官方可信白名单模型"},
            new Object[] {"豆包", "doubao-pro", "doubao", "chat", "medium", "L2", "enabled", "官方可信白名单模型"},
            new Object[] {"混元", "hunyuan-standard", "hunyuan", "chat", "medium", "L2", "enabled", "官方可信白名单模型"},
            new Object[] {"Kimi", "kimi-k2", "kimi", "chat", "medium", "L2", "enabled", "官方可信白名单模型"},
            new Object[] {"讯飞星火", "spark-max", "spark", "chat", "medium", "L2", "enabled", "官方可信白名单模型"},
            new Object[] {"智谱GLM", "glm-4-flash", "zhipu", "chat", "medium", "L2", "enabled", "官方可信白名单模型"},
            new Object[] {"和鲸", "modelwhale-chat", "modelwhale", "chat", "medium", "L2", "enabled", "官方可信白名单模型"}
        );

        for (Object[] seed : modelSeeds) {
            String modelCode = String.valueOf(seed[1]);
            Long exists = hasCompanyIdColumn
                ? querySingleLong("SELECT id FROM ai_model WHERE company_id = ? AND LOWER(COALESCE(model_code,'')) = ? ORDER BY id ASC LIMIT 1", companyId, modelCode.toLowerCase(Locale.ROOT))
                : querySingleLong("SELECT id FROM ai_model WHERE LOWER(COALESCE(model_code,'')) = ? ORDER BY id ASC LIMIT 1", modelCode.toLowerCase(Locale.ROOT));
            if (exists != null) {
                if (hasIsolationLevelColumn) {
                    jdbcTemplate.update(
                        "UPDATE ai_model SET status = 'enabled', provider = ?, model_name = COALESCE(NULLIF(model_name,''), ?), model_type = COALESCE(NULLIF(model_type,''), ?), risk_level = COALESCE(NULLIF(risk_level,''), ?), isolation_level = COALESCE(NULLIF(isolation_level,''), ?), description = COALESCE(NULLIF(description,''), ?), update_time = CURRENT_TIMESTAMP WHERE id = ?",
                        seed[2], seed[0], seed[3], seed[4], seed[5], seed[7], exists
                    );
                } else {
                    jdbcTemplate.update(
                        "UPDATE ai_model SET status = 'enabled', provider = ?, model_name = COALESCE(NULLIF(model_name,''), ?), model_type = COALESCE(NULLIF(model_type,''), ?), risk_level = COALESCE(NULLIF(risk_level,''), ?), description = COALESCE(NULLIF(description,''), ?), update_time = CURRENT_TIMESTAMP WHERE id = ?",
                        seed[2], seed[0], seed[3], seed[4], seed[7], exists
                    );
                }
                continue;
            }
            if (hasIsolationLevelColumn) {
                if (hasCompanyIdColumn) {
                    jdbcTemplate.update(
                        "INSERT INTO ai_model(company_id, model_name, model_code, provider, model_type, risk_level, isolation_level, status, description, call_limit, current_calls, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                        companyId, seed[0], seed[1], seed[2], seed[3], seed[4], seed[5], seed[6], seed[7]
                    );
                } else {
                    jdbcTemplate.update(
                        "INSERT INTO ai_model(model_name, model_code, provider, model_type, risk_level, isolation_level, status, description, call_limit, current_calls, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, ?, ?, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                        seed[0], seed[1], seed[2], seed[3], seed[4], seed[5], seed[6], seed[7]
                    );
                }
            } else {
                if (hasCompanyIdColumn) {
                    jdbcTemplate.update(
                        "INSERT INTO ai_model(company_id, model_name, model_code, provider, model_type, risk_level, status, description, call_limit, current_calls, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, ?, ?, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                        companyId, seed[0], seed[1], seed[2], seed[3], seed[4], seed[6], seed[7]
                    );
                } else {
                    jdbcTemplate.update(
                        "INSERT INTO ai_model(model_name, model_code, provider, model_type, risk_level, status, description, call_limit, current_calls, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, ?, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                        seed[0], seed[1], seed[2], seed[3], seed[4], seed[6], seed[7]
                    );
                }
            }
        }

        String inClause = trustedProviders.stream().map(v -> "'" + v + "'").collect(java.util.stream.Collectors.joining(","));
        if (hasCompanyIdColumn) {
            jdbcTemplate.update(
                "UPDATE ai_model SET status = 'disabled' WHERE company_id = ? AND LOWER(COALESCE(provider,'')) NOT IN (" + inClause + ")",
                companyId
            );
            jdbcTemplate.update(
                "UPDATE ai_model SET status = 'enabled' WHERE company_id = ? AND LOWER(COALESCE(provider,'')) IN (" + inClause + ")",
                companyId
            );
        } else {
            jdbcTemplate.update(
                "UPDATE ai_model SET status = 'disabled' WHERE LOWER(COALESCE(provider,'')) NOT IN (" + inClause + ")"
            );
            jdbcTemplate.update(
                "UPDATE ai_model SET status = 'enabled' WHERE LOWER(COALESCE(provider,'')) IN (" + inClause + ")"
            );
        }
    }

    private void seedGovernanceAdminDemoData(Long companyId, Map<String, Role> roleMap) {
        seedApprovalAndAuditData(companyId);
        seedRiskAndGovernanceData(companyId);
        seedSubjectRequestAndPolicyData(companyId);
    }

    private void seedPermissionsAndRoleBindings(Long companyId, Map<String, Role> roleMap) {
        if (!tableExists("permission") || !tableExists("role_permission")) {
            return;
        }
        List<PermissionSeed> permissionSeeds = List.of(
            new PermissionSeed("数据资产菜单", "menu:data_asset", "menu"),
            new PermissionSeed("数据资产上传", "data_asset:upload", "button"),
            new PermissionSeed("数据资产删除", "data_asset:delete", "button"),
            new PermissionSeed("用户管理菜单", "menu:user_manage", "menu"),
            new PermissionSeed("角色管理菜单", "menu:role_manage", "menu"),
            new PermissionSeed("权限管理菜单", "menu:permission_manage", "menu"),
            new PermissionSeed("用户管理", "user:manage", "button"),
            new PermissionSeed("角色管理", "role:manage", "button"),
            new PermissionSeed("权限管理", "permission:manage", "button"),
            new PermissionSeed("权限矩阵查看", "permission:matrix:view", "menu"),
            new PermissionSeed("SoD规则查看", "sod:rule:view", "menu"),
            new PermissionSeed("SoD规则编辑", "sod:rule:edit", "button"),
            new PermissionSeed("治理变更发起", "govern:change:create", "button"),
            new PermissionSeed("治理变更查看", "govern:change:view", "menu"),
            new PermissionSeed("治理变更复核", "govern:change:review", "button"),
            new PermissionSeed("审批查看", "approval:view", "menu"),
            new PermissionSeed("审批处理", "approval:operate", "button"),
            new PermissionSeed("风险事件查看", "risk:event:view", "menu"),
            new PermissionSeed("风险事件处置", "risk:event:handle", "button"),
            new PermissionSeed("安全事件查看", "security:event:view", "menu"),
            new PermissionSeed("安全事件处置", "security:event:handle", "button"),
            new PermissionSeed("安全规则管理", "security:rule:manage", "button"),
            new PermissionSeed("策略查看", "policy:view", "menu"),
            new PermissionSeed("策略结构管理", "policy:structure:manage", "button"),
            new PermissionSeed("策略状态切换", "policy:status:toggle", "button"),
            new PermissionSeed("审计日志检索", "audit:log:view", "menu"),
            new PermissionSeed("审计报告查看", "audit:report:view", "menu"),
            new PermissionSeed("审计报告生成", "audit:report:generate", "button"),
            new PermissionSeed("运维指标查看", "ops:metrics:view", "menu")
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

        bindPermissions(roleMap.get("ADMIN"), Arrays.asList(
            "menu:data_asset",
            "data_asset:upload",
            "data_asset:delete",
            "menu:user_manage",
            "menu:role_manage",
            "menu:permission_manage",
            "user:manage",
            "role:manage",
            "permission:manage",
            "permission:matrix:view",
            "sod:rule:view",
            "sod:rule:edit",
            "govern:change:create",
            "govern:change:view",
            "govern:change:review",
            "approval:view",
            "approval:operate",
            "risk:event:view",
            "security:event:view",
            "policy:view",
            "policy:structure:manage",
            "policy:status:toggle",
            "audit:log:view",
            "audit:report:view",
            "audit:report:generate",
            "ops:metrics:view"
        ));
        bindPermissions(roleMap.get("SECOPS"), Arrays.asList(
            "govern:change:view",
            "govern:change:review",
            "risk:event:view",
            "risk:event:handle",
            "security:event:view",
            "security:event:handle",
            "security:rule:manage",
            "policy:view",
            "policy:status:toggle",
            "audit:log:view",
            "audit:report:view",
            "ops:metrics:view"
        ));
        bindPermissions(roleMap.get("EXECUTIVE"), Arrays.asList(
            "permission:matrix:view",
            "govern:change:view",
            "approval:view",
            "risk:event:view",
            "security:event:view",
            "policy:view",
            "audit:log:view",
            "audit:report:view",
            "ops:metrics:view"
        ));
        bindPermissions(roleMap.get("DATA_ADMIN"), Arrays.asList(
            "menu:data_asset",
            "data_asset:upload",
            "data_asset:delete",
            "approval:view",
            "approval:operate",
            "risk:event:view",
            "security:event:view",
            "policy:view",
            "audit:log:view",
            "ops:metrics:view"
        ));
        bindPermissions(roleMap.get("BUSINESS_OWNER"), Arrays.asList(
            "menu:data_asset",
            "approval:view",
            "approval:operate",
            "govern:change:view",
            "risk:event:view",
            "security:event:view",
            "policy:view"
        ));
        bindPermissions(roleMap.get("AI_BUILDER"), Arrays.asList(
            "menu:data_asset",
            "approval:view",
            "govern:change:view",
            "risk:event:view",
            "security:event:view",
            "policy:view"
        ));
        bindPermissions(roleMap.get("EMPLOYEE"), Arrays.asList(
            "approval:view",
            "risk:event:view",
            "security:event:view"
        ));
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
        List<User> demoUsers = users.stream()
            .filter(user -> !"employee1".equalsIgnoreCase(user.getUsername()))
            .toList();
        Map<String, User> userByName = new LinkedHashMap<>();
        for (User user : demoUsers) {
            userByName.put(String.valueOf(user.getUsername()).toLowerCase(Locale.ROOT), user);
        }

        User admin = userByName.get("admin");
        User dataAdmin = userByName.get("dataadmin");
        User bizOwner = userByName.get("bizowner");
        List<User> applicants = new ArrayList<>();
        for (String key : List.of("employee1", "aibuilder", "dataadmin", "bizowner")) {
            User user = userByName.get(key);
            if (user != null) {
                applicants.add(user);
            }
        }
        if (applicants.isEmpty()) {
            applicants.addAll(demoUsers);
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
        List<User> actors = demoUsers.isEmpty() ? users : demoUsers;

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
            Long adminId = querySingleLong(
                "SELECT id FROM sys_user WHERE company_id = ? AND username = 'admin' ORDER BY id ASC LIMIT 1",
                companyId
            );
            Long secopsId = querySingleLong(
                "SELECT id FROM sys_user WHERE company_id = ? AND username = 'secops' ORDER BY id ASC LIMIT 1",
                companyId
            );
            Long fallbackId = secopsId != null ? secopsId : adminId;
            String fallbackName = secopsId != null ? "secops" : "admin";
            for (int i = existingGov; i < targetGov; i++) {
                String severity = i % 10 == 0 ? "high" : (i % 3 == 0 ? "medium" : "low");
                String status = i % 4 == 0 ? "blocked" : (i % 4 == 1 ? "resolved" : "pending");
                String eventType = i % 2 == 0 ? "MODEL_RISK_ALERT" : "POLICY_BREACH";
                Date eventTime = new Date(now.getTime() - (long) (targetGov - i) * 1800_000L);
                jdbcTemplate.update(
                    "INSERT INTO governance_event(company_id, user_id, username, event_type, source_module, severity, status, title, description, source_event_id, attack_type, policy_version, payload_json, handler_id, dispose_note, event_time, disposed_at, create_time, update_time) VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    companyId,
                    fallbackId,
                    fallbackName,
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
        List<User> demoUsers = users.stream()
            .filter(user -> !"employee1".equalsIgnoreCase(user.getUsername()))
            .toList();
        if (!demoUsers.isEmpty()) {
            Date now = new Date();
            for (int i = existingSubject; i < targetSubject; i++) {
                User user = demoUsers.get(i % demoUsers.size());
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

    private String resolveRoleCode(Long roleId) {
        if (roleId == null) {
            return "-";
        }
        List<String> rows = jdbcTemplate.query(
            "SELECT code FROM role WHERE id = ? LIMIT 1",
            (rs, rowNum) -> rs.getString(1),
            roleId
        );
        return rows.isEmpty() ? "-" : String.valueOf(rows.get(0));
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

    private boolean columnExists(String tableName, String columnName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM information_schema.columns WHERE lower(table_name) = lower(?) AND lower(column_name) = lower(?)",
                Integer.class,
                tableName,
                columnName
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

    private void cleanupDuplicateDefaultRoles(Long companyId) {
        List<String> defaultCodes = Arrays.asList(
            "ADMIN",
            "EXECUTIVE",
            "SECOPS",
            "DATA_ADMIN",
            "AI_BUILDER",
            "BUSINESS_OWNER",
            "EMPLOYEE"
        );
        boolean rolePermissionReady = tableExists("role_permission");
        boolean userRoleReady = tableExists("user_role");
        boolean userRoleIdColumnReady = tableExists("user") && columnExists("user", "role_id");
        boolean userCompanyColumnReady = userRoleIdColumnReady && columnExists("user", "company_id");
        for (String code : defaultCodes) {
            List<Role> sameCodeRoles = roleService.lambdaQuery()
                .eq(Role::getCompanyId, companyId)
                .eq(Role::getCode, code)
                .orderByAsc(Role::getId)
                .list();
            if (sameCodeRoles.size() <= 1) {
                continue;
            }
            sameCodeRoles.sort((a, b) -> {
                boolean aSystem = Boolean.TRUE.equals(a.getIsSystem());
                boolean bSystem = Boolean.TRUE.equals(b.getIsSystem());
                if (aSystem != bSystem) {
                    return bSystem ? 1 : -1;
                }
                long aId = a.getId() == null ? Long.MAX_VALUE : a.getId();
                long bId = b.getId() == null ? Long.MAX_VALUE : b.getId();
                return Long.compare(aId, bId);
            });

            Role canonical = sameCodeRoles.get(0);
            if (canonical.getId() == null) {
                continue;
            }
            canonical.setIsSystem(true);
            canonical.setAllowSelfRegister(allowSelfRegister(code));
            canonical.setUpdateTime(new Date());
            roleService.updateById(canonical);

            for (int i = 1; i < sameCodeRoles.size(); i++) {
                Role duplicate = sameCodeRoles.get(i);
                if (duplicate.getId() == null || Objects.equals(duplicate.getId(), canonical.getId())) {
                    continue;
                }
                if (userRoleIdColumnReady) {
                    try {
                        if (userCompanyColumnReady) {
                            jdbcTemplate.update(
                                "UPDATE user SET role_id = ? WHERE company_id = ? AND role_id = ?",
                                canonical.getId(),
                                companyId,
                                duplicate.getId()
                            );
                        } else {
                            jdbcTemplate.update(
                                "UPDATE user SET role_id = ? WHERE role_id = ?",
                                canonical.getId(),
                                duplicate.getId()
                            );
                        }
                    } catch (Exception ignored) {
                        jdbcTemplate.update(
                            "UPDATE user SET role_id = ? WHERE role_id = ?",
                            canonical.getId(),
                            duplicate.getId()
                        );
                    }
                }
                if (rolePermissionReady) {
                    jdbcTemplate.update(
                        "INSERT INTO role_permission(role_id, permission_id) " +
                            "SELECT ?, rp.permission_id FROM role_permission rp " +
                            "WHERE rp.role_id = ? AND NOT EXISTS (" +
                            "SELECT 1 FROM role_permission x WHERE x.role_id = ? AND x.permission_id = rp.permission_id)",
                        canonical.getId(),
                        duplicate.getId(),
                        canonical.getId()
                    );
                    jdbcTemplate.update("DELETE FROM role_permission WHERE role_id = ?", duplicate.getId());
                }
                if (userRoleReady) {
                    jdbcTemplate.update(
                        "INSERT INTO user_role(user_id, role_id, create_time, update_time) " +
                            "SELECT ur.user_id, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP FROM user_role ur " +
                            "WHERE ur.role_id = ? AND NOT EXISTS (" +
                            "SELECT 1 FROM user_role x WHERE x.user_id = ur.user_id AND x.role_id = ?)",
                        canonical.getId(),
                        duplicate.getId(),
                        canonical.getId()
                    );
                    jdbcTemplate.update("DELETE FROM user_role WHERE role_id = ?", duplicate.getId());
                }
                roleService.removeById(duplicate.getId());
            }
        }
    }

    private Role ensureRole(Long companyId, String code, String name) {
        List<Role> existingRoles = roleService.lambdaQuery()
            .eq(Role::getCompanyId, companyId)
            .eq(Role::getCode, code)
            .list();
        Role existing = pickRole(existingRoles);
        boolean allowSelfRegister = allowSelfRegister(code);
        if (existing != null) {
            existing.setIsSystem(true);
            existing.setAllowSelfRegister(allowSelfRegister);
            existing.setUpdateTime(new Date());
            roleService.updateById(existing);
            return existing;
        }
        Role role = new Role();
        role.setCompanyId(companyId);
        role.setName(name);
        role.setCode(code);
        role.setDescription("系统默认角色: " + name);
        role.setIsSystem(true);
        role.setAllowSelfRegister(allowSelfRegister);
        role.setCreateTime(new Date());
        role.setUpdateTime(new Date());
        roleService.save(role);
        return role;
    }

    private boolean allowSelfRegister(String roleCode) {
        String code = String.valueOf(roleCode == null ? "" : roleCode).toUpperCase(Locale.ROOT);
        return "EMPLOYEE".equals(code) || "AI_BUILDER".equals(code) || "BUSINESS_OWNER".equals(code);
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
        user.setJobTitle(resolveJobTitle(seed.roleCode(), seed.username()));
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

    private String resolveJobTitle(String roleCode, String username) {
        String role = String.valueOf(roleCode == null ? "" : roleCode).toUpperCase(Locale.ROOT);
        return switch (role) {
            case "ADMIN" -> username.contains("reviewer") ? "治理复核专员" : (username.contains("ops") ? "治理运营专员" : "治理管理员");
            case "SECOPS" -> username.endsWith("_2") ? "威胁处置工程师" : (username.endsWith("_3") ? "安全审计复核员" : "终端告警运营");
            case "DATA_ADMIN" -> username.endsWith("_2") ? "数据分级专员" : (username.endsWith("_3") ? "数据血缘管理员" : "数据治理管理员");
            case "AI_BUILDER" -> username.endsWith("_2") ? "模型联调工程师" : (username.endsWith("_3") ? "提示工程工程师" : "AI应用开发工程师");
            case "BUSINESS_OWNER" -> username.endsWith("_2") ? "业务风险协同负责人" : (username.endsWith("_3") ? "业务流程负责人" : "业务线负责人");
            case "EXECUTIVE" -> username.endsWith("_2") ? "经营分析管理层" : (username.endsWith("_3") ? "合规治理管理层" : "企业管理层");
            case "EMPLOYEE" -> username.endsWith("1") ? "业务执行专员（真实上报测试）" : "业务执行专员";
            default -> "平台成员";
        };
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

    private record EnterpriseAssetSeed(
        String name,
        String type,
        String sensitivityLevel,
        String location,
        String governanceStatus,
        String lineage,
        long daysAgo
    ) {}
}