package com.trustai.config;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.entity.AdversarialRecord;
import com.trustai.entity.ApprovalRequest;
import com.trustai.entity.AuditLog;
import com.trustai.entity.ClientReport;
import com.trustai.entity.DataAsset;
import com.trustai.entity.PrivacyEvent;
import com.trustai.entity.Role;
import com.trustai.entity.RiskEvent;
import com.trustai.entity.SecurityEvent;
import com.trustai.entity.User;
import com.trustai.service.AdversarialRecordService;
import com.trustai.service.ApprovalRequestService;
import com.trustai.service.AuditLogService;
import com.trustai.service.ClientReportService;
import com.trustai.service.DataAssetService;
import com.trustai.service.EventHubService;
import com.trustai.service.PrivacyEventService;
import com.trustai.service.RiskEventService;
import com.trustai.service.RoleService;
import com.trustai.service.SecurityEventService;
import com.trustai.service.UserService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 演示公司(company_id=1)数据初始化。
 *
 * <p>该初始化器采用幂等“补齐”策略：
 * 若已存在同版本 demo 标记数据，则只补齐缺失数量，不重复插入。
 * 真实公司导入时请使用新的 company_id，并复用本类的组装逻辑或对应 Service 扩展点。
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class DemoDatasetInitializer implements CommandLineRunner {

    private static final Long DEMO_COMPANY_ID = DemoAccountCatalog.DEMO_COMPANY_ID;
    private static final String DEMO_SEED_TAG = "DEMO-SEED-2026-03-18";
    private static final String PRIVACY_SOURCE_TAG = "demo-seed";
    private static final String CLIENT_VERSION_TAG = "demo-seed-v20260318";
    private static final String SECURITY_SOURCE_TAG = "demo-seed-security";

    private static final int TARGET_PRIVACY_EVENTS = 30;
    private static final int TARGET_ANOMALY_EVENTS = 30;
    private static final int TARGET_ADVERSARIAL_REPORTS = 10;
    private static final int TARGET_SHADOW_DISCOVERIES = 10;
    private static final int TARGET_APPROVAL_REQUESTS = 10;
    private static final int TARGET_AUDIT_LOGS = 10;
    private static final int TARGET_SECURITY_EVENTS = 12;
    private static final int TARGET_DATA_ASSETS = 8;

    private final UserService userService;
    private final RoleService roleService;
    private final PrivacyEventService privacyEventService;
    private final RiskEventService riskEventService;
    private final AdversarialRecordService adversarialRecordService;
    private final ClientReportService clientReportService;
    private final ApprovalRequestService approvalRequestService;
    private final AuditLogService auditLogService;
    private final SecurityEventService securityEventService;
    private final DataAssetService dataAssetService;
    private final EventHubService eventHubService;
    private final ObjectMapper objectMapper;

    @Override
    public void run(String... args) {
        Map<String, User> demoUsers = loadDemoUsers();
        if (demoUsers.isEmpty()) {
            log.warn("Skip demo dataset seeding: no demo users for company {}", DEMO_COMPANY_ID);
            return;
        }

        Map<Long, String> roleCodeById = loadRoleCodeById();
        List<User> allUsers = demoUsers.values().stream()
            .sorted(Comparator.comparing(User::getUsername))
            .toList();
        List<User> employeeUsers = allUsers.stream()
            .filter(user -> "EMPLOYEE".equalsIgnoreCase(roleCodeById.get(user.getRoleId())))
            .toList();

        User admin = demoUsers.getOrDefault("admin", allUsers.get(0));
        User bizApprover = demoUsers.getOrDefault("biz.demo", admin);
        User dataApprover = demoUsers.getOrDefault("data.demo", admin);

        seedDataAssets(allUsers);
        seedPrivacyEvents(allUsers);
        seedAnomalyEvents(allUsers);
        seedAdversarialReports(allUsers, admin);
        seedShadowAiDiscoveries(employeeUsers.isEmpty() ? allUsers : employeeUsers);
        seedApprovalRequests(employeeUsers.isEmpty() ? allUsers : employeeUsers, bizApprover, dataApprover);
        seedAuditLogs(allUsers);
        seedSecurityEvents(allUsers);

        log.info("Demo dataset seed complete for company {} ({})", DEMO_COMPANY_ID, DEMO_SEED_TAG);
    }

    private Map<String, User> loadDemoUsers() {
        List<String> usernames = DemoAccountCatalog.demoAccountSeeds().stream()
            .map(DemoAccountCatalog.DemoAccountSeed::username)
            .toList();
        if (usernames.isEmpty()) {
            return Map.of();
        }
        return userService.lambdaQuery()
            .eq(User::getCompanyId, DEMO_COMPANY_ID)
            .in(User::getUsername, usernames)
            .list()
            .stream()
            .collect(Collectors.toMap(User::getUsername, user -> user, (left, right) -> left, LinkedHashMap::new));
    }

    private Map<Long, String> loadRoleCodeById() {
        return roleService.lambdaQuery()
            .eq(Role::getCompanyId, DEMO_COMPANY_ID)
            .list()
            .stream()
            .filter(role -> role.getId() != null)
            .collect(Collectors.toMap(Role::getId, Role::getCode, (left, right) -> left));
    }

    private void seedDataAssets(List<User> users) {
        int existing = Math.toIntExact(dataAssetService.count(new QueryWrapper<DataAsset>()
            .eq("company_id", DEMO_COMPANY_ID)
            .like("description", DEMO_SEED_TAG)));
        int missing = TARGET_DATA_ASSETS - existing;
        if (missing <= 0) {
            return;
        }

        List<String> names = List.of(
            "客户主数据主表", "研发源代码仓库镜像", "市场用户行为日志", "模型训练样本库",
            "财务结算流水", "合同文档档案", "客服会话记录", "员工组织架构快照"
        );
        List<String> types = List.of("database", "file", "database", "dataset", "database", "document", "log", "api");
        List<String> levels = List.of("high", "high", "medium", "high", "medium", "medium", "low", "low");

        for (int i = 0; i < missing; i++) {
            int offset = existing + i;
            User owner = users.get(offset % users.size());
            DataAsset asset = new DataAsset();
            asset.setCompanyId(DEMO_COMPANY_ID);
            asset.setName(names.get(offset % names.size()) + "-" + (offset + 1));
            asset.setType(types.get(offset % types.size()));
            asset.setSensitivityLevel(levels.get(offset % levels.size()));
            asset.setLocation("demo://asset/" + (offset + 1));
            Date now = minutesAgo(720L + offset * 13L);
            asset.setDiscoveryTime(now);
            asset.setOwnerId(owner.getId());
            asset.setLineage("demo-ingest -> governance -> audit");
            asset.setDescription("[" + DEMO_SEED_TAG + "] 演示资产，用于权限与治理流程联调");
            asset.setCreateTime(now);
            asset.setUpdateTime(now);
            dataAssetService.save(asset);
        }
    }

    private void seedPrivacyEvents(List<User> users) {
        int existing = Math.toIntExact(privacyEventService.count(new QueryWrapper<PrivacyEvent>()
            .eq("company_id", DEMO_COMPANY_ID)
            .eq("source", PRIVACY_SOURCE_TAG)));
        int missing = TARGET_PRIVACY_EVENTS - existing;
        if (missing <= 0) {
            return;
        }

        List<String> eventTypes = List.of("SENSITIVE_TEXT", "CLIPBOARD_COPY", "FILE_UPLOAD");
        List<String> actions = List.of("detect", "desensitize", "ignore");
        List<String> severityLevels = List.of("high", "medium", "low");

        for (int i = 0; i < missing; i++) {
            int offset = existing + i;
            User actor = users.get(offset % users.size());
            String severity = severityLevels.get(offset % severityLevels.size());
            String matchedTypes = switch (severity) {
                case "high" -> "id_card,bank_card";
                case "medium" -> "phone,email";
                default -> "employee_id";
            };

            PrivacyEvent event = new PrivacyEvent();
            event.setCompanyId(DEMO_COMPANY_ID);
            event.setUserId(String.valueOf(actor.getId()));
            event.setEventType(eventTypes.get(offset % eventTypes.size()));
            event.setContentMasked("[" + DEMO_SEED_TAG + "] " + actor.getUsername() + " 触发隐私检测样例");
            event.setSource(PRIVACY_SOURCE_TAG);
            event.setAction(actions.get(offset % actions.size()));
            event.setDeviceId(actor.getUsername() + "-device");
            event.setHostname("demo-host-" + ((offset % 6) + 1));
            event.setWindowTitle("AI Assistant - Demo Session");
            event.setMatchedTypes(matchedTypes);
            event.setSeverity(severity);
            event.setPolicyVersion(1L);
            Date eventTime = minutesAgo(30L + offset * 11L);
            event.setEventTime(eventTime);
            event.setCreateTime(eventTime);
            event.setUpdateTime(eventTime);
            privacyEventService.save(event);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("seedTag", DEMO_SEED_TAG);
            payload.put("severity", severity);
            payload.put("matchedTypes", matchedTypes);
            payload.put("source", event.getSource());
            payload.put("action", event.getAction());
            eventHubService.ingestPrivacyEvent(event, actor, payload);
        }
    }

    private void seedAnomalyEvents(List<User> users) {
        int existing = Math.toIntExact(riskEventService.count(new QueryWrapper<RiskEvent>()
            .eq("company_id", DEMO_COMPANY_ID)
            .likeRight("type", "demo_behavior_")));
        int missing = TARGET_ANOMALY_EVENTS - existing;
        if (missing <= 0) {
            return;
        }

        List<String> anomalyTypes = List.of(
            "late_night_access",
            "frequent_code_push",
            "unknown_ai_access",
            "cross_department_data_pull",
            "mass_prompt_export",
            "abnormal_token_spike"
        );
        List<String> levels = List.of("high", "medium", "low");

        for (int i = 0; i < missing; i++) {
            int offset = existing + i;
            User actor = users.get(offset % users.size());
            String anomalyType = anomalyTypes.get(offset % anomalyTypes.size());
            String level = levels.get(offset % levels.size());
            String status = offset % 5 == 0 ? "processing" : (offset % 7 == 0 ? "resolved" : "open");
            Date eventTime = minutesAgo(45L + offset * 9L);

            RiskEvent riskEvent = new RiskEvent();
            riskEvent.setCompanyId(DEMO_COMPANY_ID);
            riskEvent.setType("demo_behavior_" + anomalyType);
            riskEvent.setLevel(level);
            riskEvent.setStatus(status);
            riskEvent.setHandlerId(actor.getId());
            riskEvent.setProcessLog("[" + DEMO_SEED_TAG + "] " + actor.getUsername() + " 命中异常行为: " + anomalyType);
            riskEvent.setCreateTime(eventTime);
            riskEvent.setUpdateTime(eventTime);
            riskEventService.save(riskEvent);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("event_id", "demo-anomaly-" + (riskEvent.getId() == null ? (offset + 1) : riskEvent.getId()));
            payload.put("risk_level", level);
            payload.put("description", "异常类型: " + anomalyType + "，账号: " + actor.getUsername());
            payload.put("riskEventId", riskEvent.getId() == null ? "" : String.valueOf(riskEvent.getId()));
            payload.put("seedTag", DEMO_SEED_TAG);
            eventHubService.ingestAnomalyEvent(DEMO_COMPANY_ID, actor, payload);
        }
    }

    private void seedAdversarialReports(List<User> users, User admin) {
        int existing = Math.toIntExact(adversarialRecordService.count(new QueryWrapper<AdversarialRecord>()
            .eq("company_id", DEMO_COMPANY_ID)
            .likeRight("scenario", "demo_model_battle_")));
        int missing = TARGET_ADVERSARIAL_REPORTS - existing;
        if (missing <= 0) {
            return;
        }

        List<String> scenarios = List.of("prompt_injection", "data_exfil", "shadow_deploy", "decision_drift");
        for (int i = 0; i < missing; i++) {
            int offset = existing + i;
            int seed = 20260318 + offset * 37;
            Random rng = new Random(seed);
            User operator = users.get(offset % users.size());
            Map<String, Object> battle = buildBattleReportMap(scenarios.get(offset % scenarios.size()), rng, seed);

            AdversarialRecord record = new AdversarialRecord();
            record.setCompanyId(DEMO_COMPANY_ID);
            record.setUserId(operator.getId() == null ? admin.getId() : operator.getId());
            record.setUsername(operator.getUsername());
            record.setGovernanceEventId(null);
            record.setScenario("demo_model_battle_" + scenarios.get(offset % scenarios.size()));
            record.setPolicyVersion(1L);
            record.setResultJson(toJson(Map.of(
                "ok", true,
                "engine", "trained-model-simulator",
                "battle", battle,
                "seed", seed,
                "seedTag", DEMO_SEED_TAG
            )));
            record.setEffectivenessAnalysis("基于训练后的攻防模型生成历史战报，体现不同策略在多轮对弈中的防守有效性差异。");
            record.setSuggestionsJson(toJson(List.of(
                "提高高危策略的二次确认门槛",
                "对影子AI发现后联动开启流量熔断",
                "每周回放一次随机攻防样本用于策略校准"
            )));
            Date createdAt = minutesAgo(120L + offset * 17L);
            record.setCreateTime(createdAt);
            record.setUpdateTime(createdAt);
            adversarialRecordService.save(record);
        }
    }

    private void seedShadowAiDiscoveries(List<User> users) {
        int existing = Math.toIntExact(clientReportService.count(new QueryWrapper<ClientReport>()
            .eq("company_id", DEMO_COMPANY_ID)
            .eq("client_version", CLIENT_VERSION_TAG)));
        int missing = TARGET_SHADOW_DISCOVERIES - existing;
        if (missing <= 0) {
            return;
        }

        List<String> domains = List.of(
            "chatgpt.com", "claude.ai", "gemini.google.com", "kimi.moonshot.cn", "poe.com", "perplexity.ai"
        );
        List<String> riskLevels = List.of("high", "medium", "low");

        for (int i = 0; i < missing; i++) {
            int offset = existing + i;
            User actor = users.get(offset % users.size());
            int count = 1 + (offset % 3);
            List<Map<String, Object>> discovered = new ArrayList<>();
            for (int j = 0; j < count; j++) {
                String domain = domains.get((offset + j) % domains.size());
                discovered.add(Map.of(
                    "name", domain,
                    "domain", domain,
                    "category", "chat",
                    "source", j % 2 == 0 ? "browser_history" : "network",
                    "riskLevel", riskLevels.get((offset + j) % riskLevels.size()),
                    "firstSeen", LocalDateTime.now().minusDays(2 + j).toString(),
                    "lastSeen", LocalDateTime.now().minusHours(3 + j).toString()
                ));
            }

            ClientReport report = new ClientReport();
            report.setCompanyId(DEMO_COMPANY_ID);
            report.setClientId("demo-client-" + (offset + 1));
            report.setHostname("DEMO-WORKSTATION-" + ((offset % 12) + 1));
            report.setOsUsername(actor.getUsername());
            report.setOsType(offset % 2 == 0 ? "Windows" : "macOS");
            report.setClientVersion(CLIENT_VERSION_TAG);
            report.setDiscoveredServices(toJson(discovered));
            report.setShadowAiCount(count);
            report.setRiskLevel(riskLevels.get(offset % riskLevels.size()));
            LocalDateTime scanTime = LocalDateTime.now().minusHours(2L + offset);
            report.setScanTime(scanTime);
            report.setCreateTime(scanTime);
            report.setUpdateTime(scanTime);
            clientReportService.save(report);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("seedTag", DEMO_SEED_TAG);
            payload.put("serviceCount", count);
            payload.put("services", discovered.stream().map(item -> item.get("domain")).toList());
            eventHubService.ingestShadowAiEvent(report, actor, payload);
        }
    }

    private void seedApprovalRequests(List<User> applicants, User bizApprover, User dataApprover) {
        int existing = Math.toIntExact(approvalRequestService.count(new QueryWrapper<ApprovalRequest>()
            .eq("company_id", DEMO_COMPANY_ID)
            .like("reason", DEMO_SEED_TAG)));
        int missing = TARGET_APPROVAL_REQUESTS - existing;
        if (missing <= 0) {
            return;
        }

        List<Long> assetIds = dataAssetService.lambdaQuery()
            .eq(DataAsset::getCompanyId, DEMO_COMPANY_ID)
            .list()
            .stream()
            .map(DataAsset::getId)
            .filter(id -> id != null)
            .toList();
        List<String> statuses = List.of("待审批", "合规审批通过", "驳回");

        for (int i = 0; i < missing; i++) {
            int offset = existing + i;
            User applicant = applicants.get(offset % applicants.size());
            String status = statuses.get(offset % statuses.size());
            String typePrefix = offset % 3 == 0 ? "[DATA]" : (offset % 3 == 1 ? "[BUSINESS]" : "[PERSONAL]");
            Date createdAt = minutesAgo(90L + offset * 15L);

            ApprovalRequest request = new ApprovalRequest();
            request.setCompanyId(DEMO_COMPANY_ID);
            request.setApplicantId(applicant.getId());
            request.setAssetId(assetIds.isEmpty() ? null : assetIds.get(offset % assetIds.size()));
            request.setReason(typePrefix + " [" + DEMO_SEED_TAG + "] 演示审批工单 " + (offset + 1));
            request.setStatus(status);
            if ("待审批".equals(status)) {
                request.setApproverId(null);
                request.setTaskId(null);
                request.setProcessInstanceId(null);
            } else if ("合规审批通过".equals(status)) {
                request.setApproverId(dataApprover.getId());
                request.setTaskId("demo-task-pass-" + (offset + 1));
                request.setProcessInstanceId("demo-pi-pass-" + (offset + 1));
            } else {
                request.setApproverId(bizApprover.getId());
                request.setTaskId("demo-task-reject-" + (offset + 1));
                request.setProcessInstanceId("demo-pi-reject-" + (offset + 1));
            }
            request.setCreateTime(createdAt);
            request.setUpdateTime(createdAt);
            approvalRequestService.save(request);
        }
    }

    private void seedAuditLogs(List<User> users) {
        int existing = Math.toIntExact(auditLogService.count(new QueryWrapper<AuditLog>()
            .likeRight("operation", "demo_seed_")));
        int missing = TARGET_AUDIT_LOGS - existing;
        if (missing <= 0) {
            return;
        }

        List<String> operations = List.of(
            "demo_seed_login",
            "demo_seed_alert_dispose",
            "demo_seed_policy_update",
            "demo_seed_approval_operate",
            "demo_seed_shadow_ai_review"
        );
        List<String> riskLevels = List.of("LOW", "MEDIUM", "HIGH");

        for (int i = 0; i < missing; i++) {
            int offset = existing + i;
            User actor = users.get(offset % users.size());
            Date opTime = minutesAgo(20L + offset * 8L);

            AuditLog logItem = new AuditLog();
            logItem.setUserId(actor.getId());
            logItem.setAssetId(null);
            logItem.setOperation(operations.get(offset % operations.size()));
            logItem.setOperationTime(opTime);
            logItem.setIp("10.10.10." + ((offset % 50) + 10));
            logItem.setDevice("demo-device-" + ((offset % 8) + 1));
            logItem.setInputOverview("seedInput#" + (offset + 1));
            logItem.setOutputOverview("seedOutput#" + (offset + 1));
            logItem.setResult("success");
            logItem.setRiskLevel(riskLevels.get(offset % riskLevels.size()));
            logItem.setHash("seed-hash-" + DEMO_SEED_TAG + "-" + (offset + 1));
            logItem.setCreateTime(opTime);
            auditLogService.save(logItem);
        }
    }

    private void seedSecurityEvents(List<User> users) {
        int existing = Math.toIntExact(securityEventService.count(new QueryWrapper<SecurityEvent>()
            .eq("company_id", DEMO_COMPANY_ID)
            .eq("source", SECURITY_SOURCE_TAG)));
        int missing = TARGET_SECURITY_EVENTS - existing;
        if (missing <= 0) {
            return;
        }

        List<String> eventTypes = List.of("FILE_STEAL", "SUSPICIOUS_UPLOAD", "EXFILTRATION", "CREDENTIAL_DUMP");
        List<String> severity = List.of("critical", "high", "medium", "low");
        List<String> status = List.of("pending", "reviewing", "blocked", "ignored");

        for (int i = 0; i < missing; i++) {
            int offset = existing + i;
            User actor = users.get(offset % users.size());
            Date eventTime = minutesAgo(35L + offset * 7L);

            SecurityEvent event = new SecurityEvent();
            event.setCompanyId(DEMO_COMPANY_ID);
            event.setEventType(eventTypes.get(offset % eventTypes.size()));
            event.setFilePath("/demo/data/file_" + (offset + 1) + ".txt");
            event.setTargetAddr("https://suspicious-demo-target-" + ((offset % 5) + 1) + ".example.com/upload");
            event.setEmployeeId(actor.getUsername());
            event.setHostname("demo-host-" + ((offset % 12) + 1));
            event.setFileSize(1024L * (offset + 1));
            event.setSeverity(severity.get(offset % severity.size()));
            event.setStatus(status.get(offset % status.size()));
            event.setSource(SECURITY_SOURCE_TAG);
            event.setPolicyVersion(1L);
            event.setOperatorId(null);
            event.setEventTime(eventTime);
            event.setCreateTime(eventTime);
            event.setUpdateTime(eventTime);
            securityEventService.save(event);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("seedTag", DEMO_SEED_TAG);
            payload.put("filePath", event.getFilePath());
            payload.put("targetAddr", event.getTargetAddr());
            payload.put("eventType", event.getEventType());
            eventHubService.ingestSecurityEvent(event, actor, payload);
        }
    }

    private Map<String, Object> buildBattleReportMap(String scenario, Random rng, int seed) {
        int rounds = 8 + rng.nextInt(8);
        List<Map<String, Object>> roundRecords = new ArrayList<>();
        int attackerScore = 0;
        int defenderScore = 0;
        int successCount = 0;

        for (int round = 1; round <= rounds; round++) {
            boolean success = rng.nextDouble() < 0.38 + (rng.nextDouble() * 0.25);
            if (success) {
                attackerScore += 8 + rng.nextInt(6);
                defenderScore -= 2;
                successCount++;
            } else {
                defenderScore += 7 + rng.nextInt(5);
                attackerScore -= 1;
            }
            Map<String, Object> roundPayload = new LinkedHashMap<>();
            roundPayload.put("round_num", round);
            roundPayload.put("attack_strategy", scenario + "_attack");
            roundPayload.put("defense_strategy", "adaptive_defense_" + ((round % 3) + 1));
            roundPayload.put("attack_success", success);
            roundPayload.put("final_effectiveness", Math.round((0.25 + rng.nextDouble() * 0.6) * 1000.0) / 1000.0);
            roundPayload.put("narrative", success ? "攻击方突破临界防线" : "防御方触发拦截策略");
            roundRecords.add(roundPayload);
        }

        String winner = attackerScore > defenderScore ? "攻击方 (OpenClaw-v2)" : "防御方 (AegisAI-Guard)";
        Map<String, Object> battle = new LinkedHashMap<>();
        battle.put("scenario", scenario);
        battle.put("seed", seed);
        battle.put("total_rounds", rounds);
        battle.put("attacker_final_score", attackerScore);
        battle.put("defender_final_score", defenderScore);
        battle.put("winner", winner);
        battle.put("attack_success_rate", rounds == 0 ? 0.0 : (double) successCount / (double) rounds);
        battle.put("rounds", roundRecords);
        battle.put("recommendations", List.of(
            "收紧高危模型调用额度并启用双人复核",
            "针对外部未授权域名建立持续阻断策略",
            "按周回放随机样本并更新防护阈值"
        ));
        battle.put("battle_start", LocalDateTime.now().minusMinutes(15).toString());
        battle.put("battle_end", LocalDateTime.now().toString());
        return battle;
    }

    private Date minutesAgo(long minutes) {
        Instant instant = Instant.now().minusSeconds(Math.max(0L, minutes) * 60L);
        return Date.from(instant);
    }

    private String toJson(Object value) {
        try {
            if (value == null) {
                return "{}";
            }
            if (value instanceof Map<?, ?> || value instanceof List<?>) {
                return objectMapper.writeValueAsString(value);
            }
            return objectMapper.writeValueAsString(Map.of("value", value));
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
