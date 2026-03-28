package com.trustai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.entity.ClientReport;
import com.trustai.entity.CompliancePolicy;
import com.trustai.entity.GovernanceEvent;
import com.trustai.entity.PrivacyEvent;
import com.trustai.entity.SecurityEvent;
import com.trustai.entity.User;
import com.trustai.service.CompliancePolicyService;
import com.trustai.service.EventHubService;
import com.trustai.service.GovernanceEventService;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class EventHubServiceImpl implements EventHubService {

    private final GovernanceEventService governanceEventService;
    private final CompliancePolicyService compliancePolicyService;
    private final ObjectMapper objectMapper;

    @Override
    public long resolvePolicyVersion(Long companyId) {
        if (companyId == null) {
            return 1L;
        }
        List<CompliancePolicy> policies = compliancePolicyService.list(new QueryWrapper<CompliancePolicy>()
            .eq("company_id", companyId)
            .eq("status", 1)
            .orderByDesc("update_time")
            .last("limit 1"));
        if (policies.isEmpty() || policies.get(0).getUpdateTime() == null) {
            return 1L;
        }
        return Math.max(1L, policies.get(0).getUpdateTime().getTime());
    }

    @Override
    public GovernanceEvent ingestPrivacyEvent(PrivacyEvent event, User user, Map<String, Object> payload) {
        Map<String, Object> safePayload = payload == null ? Map.of() : payload;
        Long userId = user != null ? user.getId() : null;
        String username = user != null ? user.getUsername() : null;
        String severity = "medium";
        String matched = String.valueOf(event.getMatchedTypes() == null ? "" : event.getMatchedTypes()).toLowerCase(Locale.ROOT);
        if (matched.contains("id_card") || matched.contains("bank_card")) {
            severity = "high";
        }

        return persist(createBaseEvent(
            event.getCompanyId(),
            userId,
            username,
            "PRIVACY_ALERT",
            "privacy",
            severity,
            "pending",
            "隐私盾告警",
            StringUtils.hasText(event.getWindowTitle()) ? event.getWindowTitle() : "检测到敏感内容",
            event.getId() == null ? null : String.valueOf(event.getId()),
            "data_exfil_steg",
            event.getPolicyVersion(),
            event.getEventTime(),
            safePayload
        ));
    }

    @Override
    public GovernanceEvent ingestSecurityEvent(SecurityEvent event, User user, Map<String, Object> payload) {
        String eventType = String.valueOf(event.getEventType() == null ? "" : event.getEventType()).toUpperCase(Locale.ROOT);
        String attackType = switch (eventType) {
            case "CREDENTIAL_DUMP" -> "credential_harvest";
            case "EXFILTRATION", "FILE_STEAL", "DATA_SCRAPE" -> "data_exfil_steg";
            case "SUSPICIOUS_UPLOAD" -> "shadow_deployment";
            default -> "prompt_injection";
        };

        return persist(createBaseEvent(
            event.getCompanyId(),
            user != null ? user.getId() : null,
            user != null ? user.getUsername() : event.getEmployeeId(),
            "SECURITY_ALERT",
            "security",
            StringUtils.hasText(event.getSeverity()) ? event.getSeverity() : "medium",
            StringUtils.hasText(event.getStatus()) ? event.getStatus() : "pending",
            "安全事件告警",
            StringUtils.hasText(event.getFilePath()) ? event.getFilePath() : eventType,
            event.getId() == null ? null : String.valueOf(event.getId()),
            attackType,
            event.getPolicyVersion(),
            event.getEventTime(),
            payload
        ));
    }

    @Override
    public GovernanceEvent ingestAnomalyEvent(Long companyId, User user, Map<String, Object> anomalyPayload) {
        Map<String, Object> payload = anomalyPayload == null ? Map.of() : anomalyPayload;
        String riskLevel = String.valueOf(payload.getOrDefault("risk_level", payload.getOrDefault("riskLevel", "medium")));
        String severity = normalizeSeverity(riskLevel);
        String description = String.valueOf(payload.getOrDefault("description", "异常行为检测命中"));

        return persist(createBaseEvent(
            companyId,
            user != null ? user.getId() : null,
            user != null ? user.getUsername() : null,
            "ANOMALY_ALERT",
            "anomaly",
            severity,
            "pending",
            "异常行为告警",
            description,
            String.valueOf(payload.getOrDefault("event_id", "anomaly:" + System.currentTimeMillis())),
            "decision_drift",
            resolvePolicyVersion(companyId),
            new Date(),
            payload
        ));
    }

    @Override
    public GovernanceEvent ingestShadowAiEvent(ClientReport report, User user, Map<String, Object> payload) {
        String risk = normalizeSeverity(report.getRiskLevel());
        return persist(createBaseEvent(
            report.getCompanyId(),
            user != null ? user.getId() : null,
            user != null ? user.getUsername() : report.getOsUsername(),
            "SHADOW_AI_ALERT",
            "shadow_ai",
            risk,
            "pending",
            "影子AI告警",
            StringUtils.hasText(report.getHostname()) ? report.getHostname() : report.getClientId(),
            report.getId() == null ? null : String.valueOf(report.getId()),
            "shadow_deployment",
            resolvePolicyVersion(report.getCompanyId()),
            report.getScanTime() == null ? new Date() : java.sql.Timestamp.valueOf(report.getScanTime()),
            payload
        ));
    }

    @Override
    public void syncGovernanceStatus(String sourceModule, Long sourceEventId, String status, Long handlerId, String note) {
        if (!StringUtils.hasText(sourceModule) || sourceEventId == null) {
            return;
        }
        List<GovernanceEvent> events = governanceEventService.list(new QueryWrapper<GovernanceEvent>()
            .eq("source_module", sourceModule)
            .eq("source_event_id", String.valueOf(sourceEventId)));
        if (events.isEmpty()) {
            return;
        }
        Date now = new Date();
        for (GovernanceEvent event : events) {
            event.setStatus(status);
            event.setHandlerId(handlerId);
            event.setDisposeNote(note);
            event.setDisposedAt(now);
            event.setUpdateTime(now);
            governanceEventService.updateById(event);
        }
    }

    private GovernanceEvent createBaseEvent(
        Long companyId,
        Long userId,
        String username,
        String eventType,
        String sourceModule,
        String severity,
        String status,
        String title,
        String description,
        String sourceEventId,
        String attackType,
        Long policyVersion,
        Date eventTime,
        Map<String, Object> payload
    ) {
        GovernanceEvent event = new GovernanceEvent();
        event.setCompanyId(companyId == null ? 1L : companyId);
        event.setUserId(userId);
        event.setUsername(username);
        event.setEventType(eventType);
        event.setSourceModule(sourceModule);
        event.setSeverity(normalizeSeverity(severity));
        event.setStatus(StringUtils.hasText(status) ? status : "pending");
        event.setTitle(title);
        event.setDescription(description);
        event.setSourceEventId(sourceEventId);
        event.setAttackType(attackType);
        event.setPolicyVersion(policyVersion == null ? resolvePolicyVersion(event.getCompanyId()) : policyVersion);
        event.setPayloadJson(toJson(payload));
        event.setEventTime(eventTime == null ? new Date() : eventTime);
        Date now = new Date();
        event.setCreateTime(now);
        event.setUpdateTime(now);
        return event;
    }

    private GovernanceEvent persist(GovernanceEvent event) {
        governanceEventService.save(event);
        return event;
    }

    private String normalizeSeverity(String value) {
        String normalized = String.valueOf(value == null ? "" : value).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "critical", "high", "medium", "low" -> normalized;
            case "高" -> "high";
            case "中" -> "medium";
            case "低" -> "low";
            default -> "medium";
        };
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? new LinkedHashMap<>() : payload);
        } catch (JsonProcessingException ex) {
            return "{}";
        }
    }
}
