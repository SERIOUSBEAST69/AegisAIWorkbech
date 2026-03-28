package com.trustai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.trustai.dto.SystemConfigDTO;
import com.trustai.entity.AiCallLog;
import com.trustai.entity.AuditLog;
import com.trustai.entity.DataAsset;
import com.trustai.entity.RiskEvent;
import com.trustai.mapper.AiCallLogMapper;
import com.trustai.mapper.AuditLogMapper;
import com.trustai.mapper.DataAssetMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RiskDetectionService {

    private final AuditLogMapper auditLogMapper;
    private final DataAssetMapper dataAssetMapper;
    private final AiCallLogMapper aiCallLogMapper;
    private final RiskEventService riskEventService;
    private final SystemConfigService systemConfigService;

    private static final String LEVEL_NORMAL = "NORMAL";
    private static final String LEVEL_LOW = "LOW";
    private static final String LEVEL_MEDIUM = "MEDIUM";
    private static final String LEVEL_HIGH = "HIGH";

    @Scheduled(cron = "0 */5 * * * ?")
    public void detect() {
        Instant now = Instant.now();
        Date hourAgo = Date.from(now.minus(1, ChronoUnit.HOURS));
        List<AuditLog> recentLogs = auditLogMapper.selectList(new QueryWrapper<AuditLog>().ge("operation_time", hourAgo));

        detectLoginFailures(now, recentLogs);
        detectSensitiveAccess(now, recentLogs);
        detectModelFailures(now);
    }

    private void detectLoginFailures(Instant now, List<AuditLog> logs) {
        int threshold = intConfig("risk.login.fail.threshold", 5);
        int windowMinutes = intConfig("risk.login.fail.window", 10);
        Instant windowStart = now.minus(windowMinutes, ChronoUnit.MINUTES);

        List<AuditLog> windowLogs = logs.stream()
                .filter(l -> l.getOperation() != null && containsLogin(l.getOperation()))
            .filter(l -> isFail(l.getResult()))
                .filter(l -> l.getOperationTime() != null && l.getOperationTime().toInstant().isAfter(windowStart))
                .toList();

        Map<Long, List<AuditLog>> byUser = windowLogs.stream()
                .filter(l -> l.getUserId() != null)
                .collect(Collectors.groupingBy(AuditLog::getUserId));

        byUser.forEach((userId, userLogs) -> {
            if (userLogs.size() > threshold && !recentEventExists("LOGIN_FAIL_SPIKE", "user:" + userId, windowStart)) {
                RiskEvent event = baseEvent("LOGIN_FAIL_SPIKE", LEVEL_MEDIUM, userLogs, windowMinutes + "分钟内登录失败" + userLogs.size() + "次", "user:" + userId);
                riskEventService.save(event);
                updateRiskLevel(userLogs, LEVEL_MEDIUM);
                log.info("Generated login failure risk for user {} count {}", userId, userLogs.size());
            }
        });
    }

    private void detectSensitiveAccess(Instant now, List<AuditLog> logs) {
        int threshold = intConfig("risk.sensitive.access.threshold", 20);
        Instant windowStart = now.minus(1, ChronoUnit.HOURS);

        Set<Long> assetIds = logs.stream()
                .filter(l -> l.getAssetId() != null)
                .map(AuditLog::getAssetId)
                .collect(Collectors.toSet());
        Map<Long, DataAsset> assetMap = assetIds.isEmpty() ? Map.of() :
                dataAssetMapper.selectBatchIds(assetIds).stream().collect(Collectors.toMap(DataAsset::getId, a -> a));

        List<AuditLog> filtered = logs.stream()
                .filter(l -> l.getOperationTime() != null && l.getOperationTime().toInstant().isAfter(windowStart))
                .filter(l -> isQuery(l.getOperation()))
                .filter(l -> isHighAsset(assetMap.get(l.getAssetId())))
                .toList();

        Map<Long, List<AuditLog>> byUser = filtered.stream()
                .filter(l -> l.getUserId() != null)
                .collect(Collectors.groupingBy(AuditLog::getUserId));

        byUser.forEach((userId, userLogs) -> {
            if (userLogs.size() > threshold && !recentEventExists("SENSITIVE_ACCESS_SPIKE", "user:" + userId, windowStart)) {
                RiskEvent event = baseEvent("SENSITIVE_ACCESS_SPIKE", LEVEL_HIGH, userLogs, "高敏资产查询次数" + userLogs.size(), "user:" + userId);
                riskEventService.save(event);
                updateRiskLevel(userLogs, LEVEL_HIGH);
                log.info("Generated sensitive access risk for user {} count {}", userId, userLogs.size());
            }
        });
    }

    private void detectModelFailures(Instant now) {
        double rateThreshold = doubleConfig("risk.model.fail.rate", 0.3d);
        Instant windowStart = now.minus(1, ChronoUnit.HOURS);
        List<AiCallLog> callLogs = aiCallLogMapper.selectList(new QueryWrapper<AiCallLog>().ge("create_time", Date.from(windowStart)));

        Map<String, List<AiCallLog>> byModel = callLogs.stream()
                .filter(l -> l.getModelCode() != null)
                .collect(Collectors.groupingBy(AiCallLog::getModelCode));

        byModel.forEach((modelCode, logs) -> {
            long fails = logs.stream().filter(l -> isFail(l.getStatus())).count();
            double rate = logs.isEmpty() ? 0d : (double) fails / logs.size();
            if (rate > rateThreshold && logs.size() >= 3 && !recentEventExists("MODEL_FAIL_RATE", "model:" + modelCode, windowStart)) {
                RiskEvent event = new RiskEvent();
                event.setType("MODEL_FAIL_RATE");
                event.setLevel(LEVEL_LOW);
                event.setRelatedLogId(logs.get(0).getId());
                event.setAuditLogIds(null);
                event.setStatus("open");
                event.setProcessLog("model:" + modelCode + " 失败率" + String.format("%.2f", rate * 100) + "% (" + fails + "/" + logs.size() + ")");
                event.setCreateTime(new Date());
                event.setUpdateTime(new Date());
                riskEventService.save(event);
                log.info("Generated model fail risk for {} rate {}", modelCode, rate);
            }
        });
    }

    private RiskEvent baseEvent(String type, String level, List<AuditLog> logs, String desc, String dimensionKey) {
        RiskEvent event = new RiskEvent();
        event.setType(type);
        event.setLevel(level);
        event.setRelatedLogId(logs.isEmpty() ? null : logs.get(0).getId());
        event.setAuditLogIds(joinIds(logs));
        event.setStatus("open");
        event.setProcessLog(dimensionKey + " | " + desc);
        event.setCreateTime(new Date());
        event.setUpdateTime(new Date());
        return event;
    }

    private boolean recentEventExists(String type, String dimensionKey, Instant since) {
        return riskEventService.lambdaQuery()
                .eq(RiskEvent::getType, type)
                .like(RiskEvent::getProcessLog, dimensionKey)
                .ge(RiskEvent::getCreateTime, Date.from(since))
                .count() > 0;
    }

    private void updateRiskLevel(List<AuditLog> logs, String level) {
        if (logs.isEmpty()) return;
        List<Long> ids = logs.stream().map(AuditLog::getId).filter(Objects::nonNull).toList();
        if (ids.isEmpty()) return;
        UpdateWrapper<AuditLog> uw = new UpdateWrapper<>();
        uw.in("id", ids).set("risk_level", level);
        auditLogMapper.update(null, uw);
    }

    private String joinIds(List<AuditLog> logs) {
        return logs.stream()
                .map(AuditLog::getId)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    private boolean isFail(String result) {
        if (result == null) return true;
        String r = result.toLowerCase(Locale.ROOT);
        return !("success".equals(r) || "ok".equals(r) || "成功".equals(result));
    }

    private boolean containsLogin(String op) {
        String o = op.toLowerCase(Locale.ROOT);
        return o.contains("login") || o.contains("登录");
    }

    private boolean isQuery(String op) {
        if (op == null) return false;
        String o = op.toLowerCase(Locale.ROOT);
        return o.contains("query") || o.contains("查询");
    }

    private boolean isHighAsset(DataAsset asset) {
        if (asset == null || asset.getSensitivityLevel() == null) return false;
        String lvl = asset.getSensitivityLevel().toLowerCase(Locale.ROOT);
        return lvl.contains("high") || lvl.contains("高");
    }

    private int intConfig(String key, int defVal) {
        try {
            SystemConfigDTO dto = systemConfigService.findByKey(key);
            return Integer.parseInt(dto.getConfigValue());
        } catch (Exception e) {
            return defVal;
        }
    }

    private double doubleConfig(String key, double defVal) {
        try {
            SystemConfigDTO dto = systemConfigService.findByKey(key);
            return Double.parseDouble(dto.getConfigValue());
        } catch (Exception e) {
            return defVal;
        }
    }
}