package com.trustai.service;

import com.trustai.entity.ClientReport;
import com.trustai.entity.GovernanceEvent;
import com.trustai.entity.PrivacyEvent;
import com.trustai.entity.SecurityEvent;
import com.trustai.entity.User;
import java.util.Map;

public interface EventHubService {

    long resolvePolicyVersion(Long companyId);

    GovernanceEvent ingestPrivacyEvent(PrivacyEvent event, User user, Map<String, Object> payload);

    GovernanceEvent ingestSecurityEvent(SecurityEvent event, User user, Map<String, Object> payload);

    GovernanceEvent ingestAnomalyEvent(Long companyId, User user, Map<String, Object> anomalyPayload);

    GovernanceEvent ingestShadowAiEvent(ClientReport report, User user, Map<String, Object> payload);

    void syncGovernanceStatus(String sourceModule, Long sourceEventId, String status, Long handlerId, String note);
}
