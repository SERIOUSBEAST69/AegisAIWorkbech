package com.trustai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trustai.entity.AiCallLog;

public interface AiCallAuditService extends IService<AiCallLog> {
    void recordAsync(AiCallLog log);
}
