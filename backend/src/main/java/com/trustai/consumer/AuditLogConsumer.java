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
            AuditLog log = MAPPER.readValue(payload, AuditLog.class);
            auditLogMapper.insert(log);
            AuditLogDocument doc = new AuditLogDocument();
            doc.setId(String.valueOf(log.getId()));
            doc.setLogId(log.getId());
            doc.setUserId(log.getUserId());
            doc.setAssetId(log.getAssetId());
            doc.setOperation(log.getOperation());
            doc.setOperationTime(log.getOperationTime());
            doc.setIp(log.getIp());
            doc.setDevice(log.getDevice());
            doc.setInputOverview(log.getInputOverview());
            doc.setOutputOverview(log.getOutputOverview());
            doc.setResult(log.getResult());
            doc.setRiskLevel(log.getRiskLevel());
            doc.setHash(log.getHash());
            doc.setCreateTime(log.getCreateTime());

            AuditLogEsRepository esRepository = auditLogEsRepositoryProvider.getIfAvailable();
            if (esRepository != null) {
                esRepository.save(doc);
            }
        } catch (Exception e) {
            log.error("Consume audit log failed", e);
        }
    }
}
