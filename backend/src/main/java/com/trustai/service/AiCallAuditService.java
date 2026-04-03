package com.trustai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trustai.entity.AiCallLog;
import java.util.Map;

public interface AiCallAuditService extends IService<AiCallLog> {
    void recordAsync(AiCallLog log);
    Map<String, Object> verifyHashChain(Long companyId);
    Map<String, Object> rebuildHashChain(Long companyId);
}
