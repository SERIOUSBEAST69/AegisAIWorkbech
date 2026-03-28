package com.trustai.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.trustai.entity.AuditLog;
import com.trustai.document.AuditLogDocument;
import java.util.Date;
import java.util.List;

public interface AuditLogService extends IService<AuditLog> {

	boolean saveAudit(AuditLog log);

	List<AuditLogDocument> search(Long userId, String operation, Date from, Date to);
}
