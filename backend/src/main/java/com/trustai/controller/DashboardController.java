package com.trustai.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.dto.dashboard.GovernanceInsightDTO;
import com.trustai.dto.dashboard.TrustPulseDTO;
import com.trustai.dto.dashboard.WorkbenchOverviewDTO;
import com.trustai.entity.AiModel;
import com.trustai.entity.AuditLog;
import com.trustai.entity.ApprovalRequest;
import com.trustai.entity.DataAsset;
import com.trustai.entity.ModelCallStat;
import com.trustai.entity.Role;
import com.trustai.entity.RiskEvent;
import com.trustai.entity.SubjectRequest;
import com.trustai.entity.User;
import com.trustai.service.*;
import com.trustai.utils.R;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/dashboard")
@PreAuthorize("isAuthenticated()")
public class DashboardController {
    @Autowired private DataAssetService dataAssetService;
    @Autowired private AiModelService aiModelService;
    @Autowired private UserService userService;
    @Autowired private RiskEventService riskEventService;
    @Autowired private RiskPredictionService riskPredictionService;
    @Autowired private SubjectRequestService subjectRequestService;
    @Autowired private AuditLogService auditLogService;
    @Autowired private ModelCallStatService modelCallStatService;
    @Autowired private ApprovalRequestService approvalRequestService;
    @Autowired private CurrentUserService currentUserService;
    @Autowired private CompanyScopeService companyScopeService;

    @GetMapping("/stats")
    public R<Map<String, Object>> stats() {
        User currentUser = currentUserService.requireCurrentUser();
        if (isDemoAccount(currentUser)) {
            return R.ok(buildDemoStats());
        }
        Long companyId = companyScopeService.requireCompanyId();
        Map<String, Object> map = new HashMap<>();
        map.put("dataAsset", dataAssetService.count(new QueryWrapper<DataAsset>().eq("company_id", companyId)));
        map.put("aiModel", aiModelService.count());
        map.put("user", userService.count(new QueryWrapper<User>().eq("company_id", companyId)));
        map.put("riskEvent", riskEventService.count(new QueryWrapper<RiskEvent>().eq("company_id", companyId)));
        return R.ok(map);
    }

    @GetMapping("/risk-forecast")
    public R<List<Double>> riskForecast() {
        return R.ok(riskPredictionService.forecastNext7Days());
    }

    @GetMapping("/workbench")
    public R<WorkbenchOverviewDTO> workbench() {
        User currentUser = currentUserService.requireCurrentUser();
        if (isDemoAccount(currentUser)) {
            return R.ok(buildDemoWorkbench(currentUser));
        }
        Role currentRole = currentUserService.getCurrentRole(currentUser);
        Long companyId = companyScopeService.requireCompanyId();
        List<Long> companyUserIds = companyScopeService.companyUserIds();
        ZoneId zoneId = ZoneId.systemDefault();
        LocalDate today = LocalDate.now();
        LocalDate last7Start = today.minusDays(6);
        LocalDate previous7Start = today.minusDays(13);

        Date last7Date = Date.from(last7Start.atStartOfDay(zoneId).toInstant());
        Date previous7Date = Date.from(previous7Start.atStartOfDay(zoneId).toInstant());
        Date todayDate = Date.from(today.atStartOfDay(zoneId).toInstant());
        Date yesterdayDate = Date.from(today.minusDays(1).atStartOfDay(zoneId).toInstant());

        List<RiskEvent> recentRiskEvents = riskEventService.list(
            new QueryWrapper<RiskEvent>()
                .select("id", "type", "level", "status", "process_log", "create_time")
                .eq("company_id", companyId)
                .ge("create_time", previous7Date)
                .orderByDesc("create_time")
                .last("LIMIT 5000")
        );
        QueryWrapper<AuditLog> auditQuery = new QueryWrapper<AuditLog>()
            .select("id", "user_id", "operation_time")
            .ge("operation_time", previous7Date)
            .last("LIMIT 10000");
        if (!companyUserIds.isEmpty()) {
            auditQuery.in("user_id", companyUserIds);
        }
        List<AuditLog> recentAuditLogs = auditLogService.list(auditQuery);
        QueryWrapper<ModelCallStat> modelStatQuery = new QueryWrapper<ModelCallStat>()
            .select("id", "user_id", "date", "call_count", "cost_cents")
            .ge("date", previous7Date)
            .last("LIMIT 10000");
        if (!companyUserIds.isEmpty()) {
            modelStatQuery.in("user_id", companyUserIds);
        }
        List<ModelCallStat> recentModelStats = modelCallStatService.list(modelStatQuery);
        QueryWrapper<SubjectRequest> pendingSubjectQuery = new QueryWrapper<SubjectRequest>()
            .in("status", Arrays.asList("pending", "processing"));
        if (!companyUserIds.isEmpty()) {
            pendingSubjectQuery.in("user_id", companyUserIds);
        }
        long pendingSubjectRequests = subjectRequestService.count(pendingSubjectQuery);
        QueryWrapper<SubjectRequest> subjectFeedQuery = new QueryWrapper<SubjectRequest>()
            .select("id", "type", "status", "comment", "create_time")
            .orderByDesc("create_time")
            .last("LIMIT 500");
        if (!companyUserIds.isEmpty()) {
            subjectFeedQuery.in("user_id", companyUserIds);
        }
        List<SubjectRequest> recentSubjectRequests = subjectRequestService.list(subjectFeedQuery);

        long highSensitivityAssets = dataAssetService.count(
            new QueryWrapper<DataAsset>()
                .eq("company_id", companyId)
                .in("sensitivity_level", Arrays.asList("high", "critical", "HIGH", "CRITICAL", "高", "高敏"))
        );
        long newHighSensitivityAssets = dataAssetService.count(
            new QueryWrapper<DataAsset>()
                .eq("company_id", companyId)
                .in("sensitivity_level", Arrays.asList("high", "critical", "HIGH", "CRITICAL", "高", "高敏"))
                .ge("create_time", last7Date)
        );
        long previousHighSensitivityAssets = dataAssetService.count(
            new QueryWrapper<DataAsset>()
                .eq("company_id", companyId)
                .in("sensitivity_level", Arrays.asList("high", "critical", "HIGH", "CRITICAL", "高", "高敏"))
                .between("create_time", previous7Date, last7Date)
        );
        long openAlerts = recentRiskEvents.stream()
            .filter(item -> Arrays.asList("open", "processing").contains(normalizeLower(item.getStatus())))
            .count();
        long recentOpenAlerts = recentRiskEvents.stream()
            .filter(item -> Arrays.asList("open", "processing").contains(normalizeLower(item.getStatus())))
            .filter(item -> item.getCreateTime() != null && !item.getCreateTime().before(last7Date))
            .count();
        long previousOpenAlerts = recentRiskEvents.stream()
            .filter(item -> Arrays.asList("open", "processing").contains(normalizeLower(item.getStatus())))
            .filter(item -> item.getCreateTime() != null && !item.getCreateTime().before(previous7Date) && item.getCreateTime().before(last7Date))
            .count();
        long aiCallsLast7 = recentModelStats.stream()
            .filter(item -> !toLocalDate(item.getDate()).isBefore(last7Start))
            .mapToLong(item -> item.getCallCount() == null ? 0L : item.getCallCount())
            .sum();
        long aiCallsPrevious7 = recentModelStats.stream()
            .filter(item -> {
                LocalDate date = toLocalDate(item.getDate());
                return !date.isBefore(previous7Start) && date.isBefore(last7Start);
            })
            .mapToLong(item -> item.getCallCount() == null ? 0L : item.getCallCount())
            .sum();
        long todayAuditCount = recentAuditLogs.stream()
            .filter(item -> item.getOperationTime() != null && !item.getOperationTime().before(todayDate))
            .count();
        long yesterdayAuditCount = recentAuditLogs.stream()
            .filter(item -> item.getOperationTime() != null && !item.getOperationTime().before(yesterdayDate) && item.getOperationTime().before(todayDate))
            .count();
        long highRiskEvents = recentRiskEvents.stream()
            .filter(item -> Arrays.asList("high", "critical", "高").contains(normalizeLower(item.getLevel())))
            .count();
        long enabledModels = aiModelService.count(new QueryWrapper<AiModel>().eq("status", "enabled"));

        WorkbenchOverviewDTO dto = new WorkbenchOverviewDTO();
        dto.setOperator(new WorkbenchOverviewDTO.Operator(
            safeUserName(currentUser),
            currentRole == null ? "未分配身份" : currentRole.getName(),
            currentUser.getDepartment() == null ? "可信AI治理中心" : currentUser.getDepartment(),
            currentUser.getAvatar()
        ));
        dto.setHeadline("可信AI数据治理与隐私合规工作台");
        dto.setSubheadline(String.format(
            "%s视角下，平台正在对 %d 项高敏资产、%d 个启用模型、%d 条待闭环风险与 %d 个主体工单进行统一编排。",
            currentRole == null ? "运营" : currentRole.getName(),
            highSensitivityAssets,
            enabledModels,
            highRiskEvents,
            pendingSubjectRequests
        ));
        dto.setSceneTags(Arrays.asList("审计证据链", "高敏资产纳管", "模型分级准入", "主体权利履约"));
        dto.setMetrics(buildMetrics(highSensitivityAssets, newHighSensitivityAssets, previousHighSensitivityAssets, openAlerts, recentOpenAlerts, previousOpenAlerts, aiCallsLast7, aiCallsPrevious7, todayAuditCount, yesterdayAuditCount));
        dto.setTrend(buildTrend(last7Start, recentRiskEvents, recentAuditLogs, recentModelStats));
        dto.setRiskDistribution(buildRiskDistribution(recentRiskEvents));
        dto.setTodos(buildTodos(highRiskEvents, openAlerts, pendingSubjectRequests, enabledModels));
        dto.setFeeds(buildFeeds(recentRiskEvents, recentSubjectRequests));
        return R.ok(dto);
    }

        @GetMapping("/insights")
        public R<GovernanceInsightDTO> insights() {
        User currentUser = currentUserService.requireCurrentUser();
        if (isDemoAccount(currentUser)) {
            return R.ok(buildDemoInsights());
        }
        long highSensitivityAssets = dataAssetService.count(
            new QueryWrapper<DataAsset>()
                .eq("company_id", companyScopeService.requireCompanyId())
                .in("sensitivity_level", Arrays.asList("high", "critical", "HIGH", "CRITICAL", "高", "高敏"))
        );
        List<Long> companyUserIds = companyScopeService.companyUserIds();
        long openRiskEvents = riskEventService.count(
            new QueryWrapper<RiskEvent>().eq("company_id", companyScopeService.requireCompanyId()).eq("status", "open")
        );
        long highRiskEvents = riskEventService.count(
            new QueryWrapper<RiskEvent>().eq("company_id", companyScopeService.requireCompanyId()).eq("status", "open").in("level", Arrays.asList("high", "HIGH", "critical", "CRITICAL", "高"))
        );
        long highRiskModels = aiModelService.count(
            new QueryWrapper<AiModel>().eq("status", "enabled").in("risk_level", Arrays.asList("high", "HIGH", "高"))
        );
        long pendingSubjectRequests = subjectRequestService.count(
            new QueryWrapper<SubjectRequest>().in("status", Arrays.asList("pending", "processing")).in(!companyUserIds.isEmpty(), "user_id", companyUserIds)
        );

        LocalDate today = LocalDate.now();
        long todayAuditCount = auditLogService.count(
            new QueryWrapper<AuditLog>()
                .ge("operation_time", java.util.Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant()))
                .in(!companyUserIds.isEmpty(), "user_id", companyUserIds)
        );

        Date last30DaysDate = Date.from(today.minusDays(29).atStartOfDay(ZoneId.systemDefault()).toInstant());
        List<ModelCallStat> modelStats = modelCallStatService.list(
            new QueryWrapper<ModelCallStat>()
                .select("id", "user_id", "date", "call_count", "cost_cents")
                .ge("date", last30DaysDate)
                .in(!companyUserIds.isEmpty(), "user_id", companyUserIds)
                .last("LIMIT 20000")
        );
        long totalAiCalls = modelStats.stream().mapToLong(item -> item.getCallCount() == null ? 0L : item.getCallCount()).sum();
        long totalCostCents = modelStats.stream().mapToLong(item -> item.getCostCents() == null ? 0L : item.getCostCents()).sum();

        GovernanceInsightDTO dto = new GovernanceInsightDTO();
        dto.setPostureScore(calculatePostureScore(highSensitivityAssets, openRiskEvents, highRiskEvents, pendingSubjectRequests, todayAuditCount));
        dto.setSummary(new GovernanceInsightDTO.Summary(
            highSensitivityAssets,
            openRiskEvents,
            highRiskEvents,
            highRiskModels,
            pendingSubjectRequests,
            todayAuditCount,
            totalAiCalls,
            totalCostCents
        ));
        dto.setHighlights(buildHighlights(highSensitivityAssets, highRiskModels, todayAuditCount, totalAiCalls, totalCostCents));
        dto.setRecommendations(buildRecommendations(highSensitivityAssets, openRiskEvents, highRiskEvents, pendingSubjectRequests, todayAuditCount, highRiskModels, totalCostCents));
        return R.ok(dto);
        }

        @GetMapping("/trust-pulse")
        public R<TrustPulseDTO> trustPulse() {
        User currentUser = currentUserService.requireCurrentUser();
        if (isDemoAccount(currentUser)) {
            return R.ok(buildDemoTrustPulse(currentUser));
        }
        Role currentRole = currentUserService.getCurrentRole(currentUser);
            Long companyId = companyScopeService.requireCompanyId();
            List<Long> companyUserIds = companyScopeService.companyUserIds();
        long highSensitivityAssets = dataAssetService.count(
                new QueryWrapper<DataAsset>().eq("company_id", companyId).in("sensitivity_level", Arrays.asList("high", "critical", "HIGH", "CRITICAL", "高", "高敏"))
        );
            long openRiskEvents = riskEventService.count(new QueryWrapper<RiskEvent>().eq("company_id", companyId).eq("status", "open"));
        long highRiskEvents = riskEventService.count(
                new QueryWrapper<RiskEvent>().eq("company_id", companyId).eq("status", "open").in("level", Arrays.asList("high", "HIGH", "critical", "CRITICAL", "高"))
        );
        long enabledModels = aiModelService.count(new QueryWrapper<AiModel>().eq("status", "enabled"));
        long highRiskModels = aiModelService.count(
            new QueryWrapper<AiModel>().eq("status", "enabled").in("risk_level", Arrays.asList("high", "HIGH", "高"))
        );
        long pendingSubjectRequests = subjectRequestService.count(
            new QueryWrapper<SubjectRequest>().in("status", Arrays.asList("pending", "processing")).in(!companyUserIds.isEmpty(), "user_id", companyUserIds)
        );
        long pendingApprovals = countPendingApprovals(companyUserIds);
        LocalDate today = LocalDate.now();
        long todayAuditCount = auditLogService.count(
            new QueryWrapper<AuditLog>()
            .ge("operation_time", java.util.Date.from(today.atStartOfDay(ZoneId.systemDefault()).toInstant()))
                .in(!companyUserIds.isEmpty(), "user_id", companyUserIds)
        );
        TrustPulseDTO dto = new TrustPulseDTO();
        int dataScore = clampScore(100 - (int) Math.min(48, highSensitivityAssets * 5 + openRiskEvents * 3));
        int modelScore = clampScore(100 - (int) Math.min(52, highRiskModels * 10 + Math.max(0, enabledModels - 10) * 2));
        int processScore = clampScore(100 - (int) Math.min(46, pendingSubjectRequests * 8 + pendingApprovals * 6));
        int auditScore = clampScore(68 + (int) Math.min(24, todayAuditCount));
        int totalScore = Math.round((dataScore + modelScore + processScore + auditScore) / 4f);

        dto.setScore(totalScore);
        dto.setPulseLevel(resolvePulseLevel(totalScore));
        dto.setMission(buildPulseMission(currentRole == null ? null : currentRole.getCode(), totalScore, highRiskEvents, pendingSubjectRequests));
        dto.setInnovationLabel("治理脉冲引擎 · Governance Pulse Engine");
        dto.setDimensions(Arrays.asList(
            new TrustPulseDTO.Dimension("data", "数据边界", dataScore, highSensitivityAssets + " 项高敏资产正在纳管，持续映射共享与脱敏压力。"),
            new TrustPulseDTO.Dimension("model", "模型可信", modelScore, highRiskModels + " 个高风险模型处于受控接入，跟踪额度与状态门禁。"),
            new TrustPulseDTO.Dimension("process", "流程闭环", processScore, pendingApprovals + " 个审批节点与 " + pendingSubjectRequests + " 个主体工单共同决定履约效率。"),
            new TrustPulseDTO.Dimension("audit", "审计准备度", auditScore, todayAuditCount + " 条当日留痕决定监管抽查时的可追溯性。")
        ));
        dto.setSignals(Arrays.asList(
            new TrustPulseDTO.Signal("高危风险", String.valueOf(highRiskEvents), highRiskEvents > 0 ? "danger" : "safe", "优先压降高危事件"),
            new TrustPulseDTO.Signal("模型接入", enabledModels + " / " + highRiskModels, highRiskModels > 0 ? "warning" : "safe", "检查模型额度与准入条件"),
            new TrustPulseDTO.Signal("流程积压", String.valueOf(pendingApprovals + pendingSubjectRequests), pendingApprovals + pendingSubjectRequests > 0 ? "warning" : "safe", "缩短共享审批与主体履约时延")
        ));
        return R.ok(dto);
        }

        private int calculatePostureScore(long highSensitivityAssets,
                          long openRiskEvents,
                          long highRiskEvents,
                          long pendingSubjectRequests,
                          long todayAuditCount) {
        int score = 100;
        score -= Math.min(28, (int) highSensitivityAssets * 6);
        score -= Math.min(30, (int) highRiskEvents * 8 + Math.max(0, (int) (openRiskEvents - highRiskEvents)) * 3);
        score -= Math.min(16, (int) pendingSubjectRequests * 4);
        if (todayAuditCount == 0) {
            score -= 10;
        }
        return Math.max(18, score);
        }

        private int clampScore(int score) {
        return Math.max(18, Math.min(100, score));
        }

        private long countPendingApprovals(List<Long> companyUserIds) {
        return approvalRequestService.count(
            new QueryWrapper<ApprovalRequest>()
                .in("status", Arrays.asList("待审批", "pending", "processing"))
                .in(!companyUserIds.isEmpty(), "applicant_id", companyUserIds)
        );
        }

        private String resolvePulseLevel(int score) {
        if (score >= 85) {
            return "稳态领航";
        }
        if (score >= 70) {
            return "可控推进";
        }
        if (score >= 55) {
            return "高压编排";
        }
        return "紧急干预";
        }

        private String buildPulseMission(String roleCode, int score, long highRiskEvents, long pendingSubjectRequests) {
        String role = roleCode == null ? "治理角色" : roleCode;
        if (score < 60) {
            return role + " 当前应进入高压指挥模式，优先处理 " + highRiskEvents + " 个高危风险和 " + pendingSubjectRequests + " 个履约阻塞。";
        }
        if (score < 80) {
            return role + " 当前处于可控推进区间，建议把资源集中在风险压降和流程提速。";
        }
        return role + " 当前进入稳态领航区间，可以把重点转向自动化编排和跨组织治理协同。";
        }

        private List<GovernanceInsightDTO.Highlight> buildHighlights(long highSensitivityAssets,
                                     long highRiskModels,
                                     long todayAuditCount,
                                     long totalAiCalls,
                                     long totalCostCents) {
        List<GovernanceInsightDTO.Highlight> highlights = new ArrayList<>();
        highlights.add(new GovernanceInsightDTO.Highlight(
            "高敏资产纳管",
            highSensitivityAssets + " 项",
            "直接反映高风险数据资产纳入平台治理的密度，区别于传统平台只做库表登记。"
        ));
        highlights.add(new GovernanceInsightDTO.Highlight(
            "AI 模型治理",
            highRiskModels + " 个高风险模型",
            "模型被纳入风险等级、调用额度和状态治理，而不是作为平台外部黑盒。"
        ));
        highlights.add(new GovernanceInsightDTO.Highlight(
            "今日审计留痕",
            todayAuditCount + " 条",
            "审计从日志存储升级为可检索证据链，支持追溯与监管抽查。"
        ));
        highlights.add(new GovernanceInsightDTO.Highlight(
            "AI 调用与成本",
            totalAiCalls + " 次 / ¥" + String.format("%.2f", totalCostCents / 100.0),
            "平台具备 AI 使用量与成本的治理视角，传统规则平台通常没有该成本面板。"
        ));
        return highlights;
        }

        private List<GovernanceInsightDTO.Recommendation> buildRecommendations(long highSensitivityAssets,
                                           long openRiskEvents,
                                           long highRiskEvents,
                                           long pendingSubjectRequests,
                                           long todayAuditCount,
                                           long highRiskModels,
                                           long totalCostCents) {
        List<GovernanceInsightDTO.Recommendation> items = new ArrayList<>();
        if (highRiskEvents > 0) {
            items.add(new GovernanceInsightDTO.Recommendation(
                "open-high-risk-events",
                "P0",
                "优先闭环高风险事件",
                "当前存在高风险未闭环事件，建议先处理高危告警，避免治理平台只告警不闭环。",
                "/risk-event-manage",
                highRiskEvents + " 个高风险事件"
            ));
        }
        if (highSensitivityAssets > 0) {
            items.add(new GovernanceInsightDTO.Recommendation(
                "high-sensitivity-assets",
                "P0",
                "补齐高敏资产治理策略",
                "高敏资产越多，越需要配套脱敏、共享审批和审计规则，区别于传统台账式治理。",
                "/data-asset",
                highSensitivityAssets + " 个高敏资产"
            ));
        }
        if (pendingSubjectRequests > 0) {
            items.add(new GovernanceInsightDTO.Recommendation(
                "pending-subject-requests",
                "P1",
                "压降主体权利工单积压",
                "主体权利请求仍在处理中，平台已具备工单化闭环能力，应继续提升履约时效。",
                "/subject-request",
                pendingSubjectRequests + " 个待处理工单"
            ));
        }
        if (highRiskModels > 0) {
            items.add(new GovernanceInsightDTO.Recommendation(
                "high-risk-models",
                "P1",
                "强化高风险模型接入治理",
                "高风险模型已纳入平台，但仍需持续校验额度、状态和审计策略，避免模型成为监管盲区。",
                "/ai/risk-rating",
                highRiskModels + " 个高风险模型"
            ));
        }
        if (todayAuditCount == 0) {
            items.add(new GovernanceInsightDTO.Recommendation(
                "missing-audit-activity",
                "P1",
                "校验审计链路是否正常入库",
                "当日无审计留痕，说明系统可能未产生操作，也可能存在留痕链路缺口。",
                "/audit-log",
                "今日 0 条审计记录"
            ));
        }
        if (totalCostCents > 50000) {
            items.add(new GovernanceInsightDTO.Recommendation(
                "ai-cost-observe",
                "P2",
                "关注 AI 成本波动",
                "调用成本已明显增长，平台已具备成本治理基础，可进一步细化模型与业务维度归因。",
                "/operations-command",
                "累计 ¥" + String.format("%.2f", totalCostCents / 100.0)
            ));
        }
        if (items.isEmpty()) {
            items.add(new GovernanceInsightDTO.Recommendation(
                "steady-state",
                "P1",
                "治理态势稳定",
                "当前核心指标平稳，可继续扩展自动化策略编排与跨系统联动。",
                "/settings",
                "无明显阻塞项"
            ));
        }
        return items.stream()
            .sorted((left, right) -> left.getPriority().compareTo(right.getPriority()))
            .collect(Collectors.toList());
        }

        private List<WorkbenchOverviewDTO.Metric> buildMetrics(long highSensitivityAssets,
                                               long newHighSensitivityAssets,
                                               long previousHighSensitivityAssets,
                                               long openAlerts,
                                               long recentOpenAlerts,
                                               long previousOpenAlerts,
                                               long aiCallsLast7,
                                               long aiCallsPrevious7,
                                               long todayAuditCount,
                                               long yesterdayAuditCount) {
        List<WorkbenchOverviewDTO.Metric> items = new ArrayList<>();
        items.add(new WorkbenchOverviewDTO.Metric(
            "assets",
            "高敏资产纳管",
            highSensitivityAssets,
            "项",
            calcDelta(newHighSensitivityAssets, previousHighSensitivityAssets),
            "近7日新增高敏资产纳管规模"
        ));
        items.add(new WorkbenchOverviewDTO.Metric(
            "alerts",
            "待闭环告警",
            openAlerts,
            "条",
            calcDelta(recentOpenAlerts, previousOpenAlerts),
            "仍需人工处置的风险与告警压力"
        ));
        items.add(new WorkbenchOverviewDTO.Metric(
            "aiCalls",
            "7日AI调用",
            aiCallsLast7,
            "次",
            calcDelta(aiCallsLast7, aiCallsPrevious7),
            "模型真实调用量与治理压力"
        ));
        items.add(new WorkbenchOverviewDTO.Metric(
            "audits",
            "今日审计留痕",
            todayAuditCount,
            "条",
            calcDelta(todayAuditCount, yesterdayAuditCount),
            "面向监管抽查的证据链产出"
        ));
        return items;
        }

        private WorkbenchOverviewDTO.Trend buildTrend(LocalDate start,
                                      List<RiskEvent> riskEvents,
                                      List<AuditLog> auditLogs,
                                      List<ModelCallStat> modelStats) {
        WorkbenchOverviewDTO.Trend trend = new WorkbenchOverviewDTO.Trend();
        Map<LocalDate, Long> riskByDay = riskEvents.stream()
            .filter(item -> item.getCreateTime() != null)
            .collect(Collectors.groupingBy(item -> toLocalDate(item.getCreateTime()), Collectors.counting()));
        Map<LocalDate, Long> auditByDay = auditLogs.stream()
            .filter(item -> item.getOperationTime() != null)
            .collect(Collectors.groupingBy(item -> toLocalDate(item.getOperationTime()), Collectors.counting()));
        Map<LocalDate, Long> aiCallsByDay = modelStats.stream()
            .collect(Collectors.groupingBy(item -> toLocalDate(item.getDate()), Collectors.summingLong(item -> item.getCallCount() == null ? 0L : item.getCallCount())));
        Map<LocalDate, Long> costByDay = modelStats.stream()
            .collect(Collectors.groupingBy(item -> toLocalDate(item.getDate()), Collectors.summingLong(item -> item.getCostCents() == null ? 0L : item.getCostCents())));

        List<Long> riskSeries = new ArrayList<>();
        for (int index = 0; index < 7; index++) {
            LocalDate date = start.plusDays(index);
            trend.getLabels().add(date.getMonthValue() + "/" + date.getDayOfMonth());
            long risk = riskByDay.getOrDefault(date, 0L);
            long audit = auditByDay.getOrDefault(date, 0L);
            long aiCalls = aiCallsByDay.getOrDefault(date, 0L);
            long cost = costByDay.getOrDefault(date, 0L);
            riskSeries.add(risk);
            trend.getRiskSeries().add(risk);
            trend.getAuditSeries().add(audit);
            trend.getAiCallSeries().add(aiCalls);
            trend.getCostSeries().add(cost);
        }
        trend.setForecastNextDay(forecastNextDay(riskSeries));
        return trend;
        }

        private List<WorkbenchOverviewDTO.RiskBucket> buildRiskDistribution(List<RiskEvent> riskEvents) {
        Map<String, Long> grouped = new LinkedHashMap<>();
        grouped.put("高危", 0L);
        grouped.put("中危", 0L);
        grouped.put("低危", 0L);
        grouped.put("待研判", 0L);
        riskEvents.forEach(item -> {
            String level = normalizeLower(item.getLevel());
            if (Arrays.asList("high", "critical", "高").contains(level)) {
                grouped.computeIfPresent("高危", (key, value) -> value + 1);
            } else if (Arrays.asList("medium", "中").contains(level)) {
                grouped.computeIfPresent("中危", (key, value) -> value + 1);
            } else if (Arrays.asList("low", "低").contains(level)) {
                grouped.computeIfPresent("低危", (key, value) -> value + 1);
            } else {
                grouped.computeIfPresent("待研判", (key, value) -> value + 1);
            }
        });
        return grouped.entrySet().stream()
            .map(entry -> new WorkbenchOverviewDTO.RiskBucket(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
        }

        private List<WorkbenchOverviewDTO.TodoItem> buildTodos(long highRiskEvents,
                                               long openAlerts,
                                               long pendingSubjectRequests,
                                               long enabledModels) {
        List<WorkbenchOverviewDTO.TodoItem> todos = new ArrayList<>();
        if (highRiskEvents > 0) {
            todos.add(new WorkbenchOverviewDTO.TodoItem("P0", "闭环高风险事件", "优先压降高风险事件，避免平台只监测不处置。", "/risk-event-manage", highRiskEvents + " 个高危事件"));
        }
        if (openAlerts > 0) {
            todos.add(new WorkbenchOverviewDTO.TodoItem("P0", "处理待处置风险", "仍有高优先级风险待跟进，需进入风险编排视图。", "/risk-event-manage", openAlerts + " 条待处置风险"));
        }
        if (pendingSubjectRequests > 0) {
            todos.add(new WorkbenchOverviewDTO.TodoItem("P1", "履约主体权利请求", "访问、删除、导出类工单仍在队列中，影响隐私履约体验。", "/subject-request", pendingSubjectRequests + " 个待处理工单"));
        }
        todos.add(new WorkbenchOverviewDTO.TodoItem("P1", "巡检启用AI能力", "核验高风险模型额度、状态与绑定资产是否仍符合最新策略。", "/ai/risk-rating", enabledModels + " 个启用模型"));
        return todos.stream().limit(4).collect(Collectors.toList());
        }

        private List<WorkbenchOverviewDTO.ActivityFeed> buildFeeds(List<RiskEvent> riskEvents,
                               List<SubjectRequest> subjectRequests) {
        List<WorkbenchOverviewDTO.ActivityFeed> feeds = new ArrayList<>();
        riskEvents.stream()
            .filter(item -> item.getCreateTime() != null)
            .sorted(Comparator.comparing(RiskEvent::getCreateTime).reversed())
            .limit(3)
            .forEach(item -> feeds.add(new WorkbenchOverviewDTO.ActivityFeed(
                normalizeLower(item.getLevel()),
                "风险事件 · " + defaultText(item.getType(), "待识别类型"),
                "状态：" + defaultText(item.getStatus(), "open") + "，处置日志：" + defaultText(item.getProcessLog(), "待跟进"),
                "/risk-event-manage",
                formatTime(item.getCreateTime())
            )));
        subjectRequests.stream()
            .filter(item -> item.getCreateTime() != null)
            .sorted(Comparator.comparing(SubjectRequest::getCreateTime).reversed())
            .limit(2)
            .forEach(item -> feeds.add(new WorkbenchOverviewDTO.ActivityFeed(
                normalizeLower(item.getStatus()),
                "主体权利 · " + defaultText(item.getType(), "access"),
                defaultText(item.getComment(), "等待处理") + "，当前状态：" + defaultText(item.getStatus(), "pending"),
                "/subject-request",
                formatTime(item.getCreateTime())
            )));
        return feeds.stream()
            .sorted(Comparator.comparing(WorkbenchOverviewDTO.ActivityFeed::getTimeLabel).reversed())
            .limit(6)
            .collect(Collectors.toList());
        }

        private boolean isDemoAccount(User user) {
        return user != null && "demo".equalsIgnoreCase(user.getAccountType());
        }

        private Map<String, Object> buildDemoStats() {
        Map<String, Object> map = new HashMap<>();
        map.put("dataAsset", 5L);
        map.put("aiModel", 9L);
        map.put("user", 14L);
        map.put("riskEvent", 3L);
        return map;
        }

        private WorkbenchOverviewDTO buildDemoWorkbench(User user) {
        WorkbenchOverviewDTO dto = new WorkbenchOverviewDTO();
        Role role = currentUserService.getCurrentRole(user);
        dto.setOperator(new WorkbenchOverviewDTO.Operator(
            safeUserName(user),
            role == null ? "演示身份" : role.getName(),
            user.getDepartment() == null ? "演示组织" : user.getDepartment(),
            user.getAvatar()
        ));
        dto.setHeadline("可信AI数据治理与隐私合规工作台（演示数据）");
        dto.setSubheadline("当前账号为演示账号，展示的是 Faker 模拟指标，不会影响真实企业数据。");
        dto.setSceneTags(Arrays.asList("演示态势", "模拟风险", "模拟工单", "模拟成本"));
        dto.setMetrics(Arrays.asList(
            new WorkbenchOverviewDTO.Metric("assets", "高敏资产纳管", 12L, "项", 18, "演示数据：近7日新增2项"),
            new WorkbenchOverviewDTO.Metric("alerts", "待闭环告警", 4L, "条", -22, "演示数据：告警持续下降"),
            new WorkbenchOverviewDTO.Metric("aiCalls", "7日AI调用", 1386L, "次", 35, "演示数据：业务接入增长"),
            new WorkbenchOverviewDTO.Metric("audits", "今日审计留痕", 87L, "条", 12, "演示数据：证据链正常")
        ));
        WorkbenchOverviewDTO.Trend trend = new WorkbenchOverviewDTO.Trend();
        trend.setLabels(Arrays.asList("03/11", "03/12", "03/13", "03/14", "03/15", "03/16", "03/17"));
        trend.setRiskSeries(Arrays.asList(7L, 6L, 6L, 5L, 4L, 4L, 3L));
        trend.setAuditSeries(Arrays.asList(60L, 72L, 70L, 79L, 82L, 85L, 87L));
        trend.setAiCallSeries(Arrays.asList(160L, 180L, 192L, 205L, 220L, 210L, 219L));
        trend.setCostSeries(Arrays.asList(1220L, 1300L, 1420L, 1480L, 1550L, 1520L, 1588L));
        trend.setForecastNextDay(3L);
        dto.setTrend(trend);
        dto.setRiskDistribution(Arrays.asList(
            new WorkbenchOverviewDTO.RiskBucket("高危", 1L),
            new WorkbenchOverviewDTO.RiskBucket("中危", 2L),
            new WorkbenchOverviewDTO.RiskBucket("低危", 6L),
            new WorkbenchOverviewDTO.RiskBucket("待研判", 1L)
        ));
        dto.setTodos(Arrays.asList(
            new WorkbenchOverviewDTO.TodoItem("P0", "回放高危外发演练", "验证阻断链路是否可复现", "/threat-monitor", "1 条模拟事件"),
            new WorkbenchOverviewDTO.TodoItem("P1", "复核脱敏策略命中", "检查手机号和邮箱规则命中率", "/desense-preview", "命中率 93%"),
            new WorkbenchOverviewDTO.TodoItem("P1", "处理模拟审批工单", "演示共享申请流程闭环", "/approval-manage", "2 个待处理工单")
        ));
        dto.setFeeds(Arrays.asList(
            new WorkbenchOverviewDTO.ActivityFeed("warning", "模拟风险事件 · 非法外传尝试", "系统已阻断并生成审计证据", "/threat-monitor", "03-17 10:26"),
            new WorkbenchOverviewDTO.ActivityFeed("safe", "模拟主体请求 · access", "工单已进入处理队列", "/subject-request", "03-17 09:48")
        ));
        dto.set_dataSource("mock");
        return dto;
        }

        private GovernanceInsightDTO buildDemoInsights() {
        GovernanceInsightDTO dto = new GovernanceInsightDTO();
        dto.setPostureScore(86);
        dto.setSummary(new GovernanceInsightDTO.Summary(12L, 3L, 1L, 2L, 2L, 87L, 1386L, 15880L));
        dto.setHighlights(Arrays.asList(
            new GovernanceInsightDTO.Highlight("高敏资产纳管", "12 项", "演示资产覆盖客户、订单、员工和营销线索场景"),
            new GovernanceInsightDTO.Highlight("今日审计留痕", "87 条", "演示环境持续写入审计记录，便于讲解追溯链路"),
            new GovernanceInsightDTO.Highlight("AI 调用成本", "¥158.80", "演示成本曲线用于展示配额和预算治理")
        ));
        dto.setRecommendations(Arrays.asList(
            new GovernanceInsightDTO.Recommendation("demo-risk", "P0", "演示高危事件闭环", "建议演示一次事件签收与处置全过程", "/risk-event-manage", "1 条高危事件"),
            new GovernanceInsightDTO.Recommendation("demo-desense", "P1", "演示一键脱敏", "展示预览与执行接口联动，强化合规可视化", "/desense-preview", "建议演示手机号/邮箱样例")
        ));
        return dto;
        }

        private TrustPulseDTO buildDemoTrustPulse(User user) {
        Role role = currentUserService.getCurrentRole(user);
        TrustPulseDTO dto = new TrustPulseDTO();
        dto.setScore(84);
        dto.setPulseLevel("可控推进");
        dto.setMission((role == null ? "演示角色" : role.getCode()) + " 当前处于演示治理脉冲，适合展示跨模块闭环能力。");
        dto.setInnovationLabel("治理脉冲引擎 · Demo Mode");
        dto.setDimensions(Arrays.asList(
            new TrustPulseDTO.Dimension("data", "数据边界", 82, "演示资产和脱敏策略形成可观察闭环"),
            new TrustPulseDTO.Dimension("model", "模型可信", 86, "演示模型风险等级与配额控制正常"),
            new TrustPulseDTO.Dimension("process", "流程闭环", 80, "演示审批和主体工单可完整流转"),
            new TrustPulseDTO.Dimension("audit", "审计准备度", 88, "演示审计链路持续写入，支持回放")
        ));
        dto.setSignals(Arrays.asList(
            new TrustPulseDTO.Signal("演示高危风险", "1", "warning", "进入风险事件页执行闭环"),
            new TrustPulseDTO.Signal("演示审批积压", "2", "warning", "在审批管理中完成处理"),
            new TrustPulseDTO.Signal("审计留痕", "87", "safe", "可导出并复盘证据链")
        ));
        return dto;
        }

        private int calcDelta(long current, long previous) {
        if (previous <= 0) {
            return current > 0 ? 100 : 0;
        }
        return (int) Math.round(((double) current - previous) / previous * 100);
        }

        private LocalDate toLocalDate(Date value) {
        return value.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }

        private long forecastNextDay(List<Long> series) {
        double average = series.stream().mapToLong(Long::longValue).average().orElse(0);
        long last = series.isEmpty() ? 0L : series.get(series.size() - 1);
        return Math.max(0L, Math.round((average * 0.55) + (last * 0.45) + Math.sqrt(Math.max(average, 0))));
        }

        private String safeUserName(User user) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        if (user.getRealName() != null && !user.getRealName().isBlank()) {
            return user.getRealName();
        }
        return defaultText(user.getUsername(), "访客");
        }

        private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
        }

        private String normalizeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase();
        }

        private String formatTime(Date value) {
        LocalDateTime time = value.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
        return String.format("%02d-%02d %02d:%02d", time.getMonthValue(), time.getDayOfMonth(), time.getHour(), time.getMinute());
        }
}
