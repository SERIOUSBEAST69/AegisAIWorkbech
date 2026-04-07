package com.trustai.consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.document.AuditLogDocument;
import com.trustai.entity.AuditLog;
import com.trustai.mapper.AuditLogMapper;
import com.trustai.repository.AuditLogEsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class AuditLogConsumerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private AuditLogMapper auditLogMapper;

    @Mock
    private ObjectProvider<AuditLogEsRepository> auditLogEsRepositoryProvider;

    @Mock
    private AuditLogEsRepository auditLogEsRepository;

    private AuditLogConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new AuditLogConsumer(auditLogMapper, auditLogEsRepositoryProvider);
        when(auditLogEsRepositoryProvider.getIfAvailable()).thenReturn(auditLogEsRepository);
    }

    @Test
    void shouldNotInsertAgainWhenAuditAlreadyExists() throws Exception {
        AuditLog payload = new AuditLog();
        payload.setId(1001L);
        payload.setUserId(7L);
        payload.setOperation("user:update");

        AuditLog persisted = new AuditLog();
        persisted.setId(1001L);
        persisted.setUserId(7L);
        persisted.setOperation("user:update");

        when(auditLogMapper.selectById(1001L)).thenReturn(persisted);

        consumer.onMessage(MAPPER.writeValueAsString(payload));

        verify(auditLogMapper, never()).insert(any(AuditLog.class));
        verify(auditLogEsRepository).save(any(AuditLogDocument.class));
    }

    @Test
    void shouldInsertWhenAuditDoesNotExist() throws Exception {
        AuditLog payload = new AuditLog();
        payload.setId(2002L);
        payload.setUserId(9L);
        payload.setOperation("role:grant");

        when(auditLogMapper.selectById(2002L)).thenReturn(null);

        consumer.onMessage(MAPPER.writeValueAsString(payload));

        verify(auditLogMapper).insert(any(AuditLog.class));
        verify(auditLogEsRepository).save(any(AuditLogDocument.class));
    }
}
