package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.AiCallLog;
import com.trustai.mapper.AiCallLogMapper;
import com.trustai.service.AiCallAuditService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiCallAuditServiceImpl extends ServiceImpl<AiCallLogMapper, AiCallLog> implements AiCallAuditService {

    private static final Logger log = LoggerFactory.getLogger(AiCallAuditServiceImpl.class);

    @Override
    @Async("aiAuditExecutor")
    public void recordAsync(AiCallLog logEntry) {
        try {
            save(logEntry);
        } catch (Exception e) {
            log.error("record ai call log failed", e);
        }
    }
}
