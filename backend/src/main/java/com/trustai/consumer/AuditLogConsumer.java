package com.trustai.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.config.RabbitConfig;
import com.trustai.document.AuditLogDocument;
import com.trustai.entity.AuditLog;
import com.trustai.mapper.AuditLogMapper;
import com.trustai.repository.AuditLogEsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuditLogConsumer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AuditLogMapper auditLogMapper;
    private final ObjectProvider<AuditLogEsRepository> auditLogEsRepositoryProvider;

    @RabbitListener(queues = RabbitConfig.AUDIT_LOG_QUEUE)
    public void onMessage(String payload) {
        try {
            AuditLog messageLog = MAPPER.readValue(payload, AuditLog.class);
            AuditLog persisted = resolvePersistedAuditLog(messageLog);
            if (persisted == null || persisted.getId() == null) {
                return;
            }

            AuditLogDocument doc = new AuditLogDocument();
            doc.setId(String.valueOf(persisted.getId()));
            doc.setLogId(persisted.getId());
            doc.setUserId(persisted.getUserId());
            doc.setAssetId(persisted.getAssetId());
            doc.setPermissionId(persisted.getPermissionId());
            doc.setPermissionName(persisted.getPermissionName());
            doc.setOperation(persisted.getOperation());
            doc.setOperationTime(persisted.getOperationTime());
            doc.setIp(persisted.getIp());
            doc.setDevice(persisted.getDevice());
            doc.setInputOverview(persisted.getInputOverview());
            doc.setOutputOverview(persisted.getOutputOverview());
            doc.setResult(persisted.getResult());
            doc.setRiskLevel(persisted.getRiskLevel());
            doc.setHash(persisted.getHash());
            doc.setCreateTime(persisted.getCreateTime());

            AuditLogEsRepository esRepository = auditLogEsRepositoryProvider.getIfAvailable();
            if (esRepository != null) {
                esRepository.save(doc);
            }
        } catch (Exception e) {
            log.error("Consume audit log failed", e);
        }
    }

    private AuditLog resolvePersistedAuditLog(AuditLog messageLog) {
        if (messageLog == null) {
            return null;
        }
        if (messageLog.getId() != null) {
            AuditLog existing = auditLogMapper.selectById(messageLog.getId());
            if (existing != null) {
                return existing;
            }
        }
        auditLogMapper.insert(messageLog);
        return messageLog;
    }
}
