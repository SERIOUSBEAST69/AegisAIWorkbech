package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.AuditLog;
import com.trustai.entity.RiskEvent;
import com.trustai.exception.BizException;
import com.trustai.mapper.RiskEventMapper;
import com.trustai.service.AuditLogService;
import com.trustai.service.RiskEventService;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskEventServiceImpl extends ServiceImpl<RiskEventMapper, RiskEvent> implements RiskEventService {

    private final JdbcTemplate jdbcTemplate;
    private final AuditLogService auditLogService;

    @Override
    public boolean save(RiskEvent entity) {
        if (entity == null || entity.getCompanyId() == null || entity.getCompanyId() <= 0) {
            throw new BizException(40000, "风险事件缺少合法 company_id");
        }

        Long relatedAuditLogId = entity.getRelatedLogId();
        if (relatedAuditLogId != null && relatedAuditLogId > 0) {
            Integer valid = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM audit_log a JOIN sys_user u ON a.user_id = u.id WHERE a.id = ? AND u.company_id = ?",
                Integer.class,
                relatedAuditLogId,
                entity.getCompanyId()
            );
            if (valid == null || valid == 0) {
                throw new BizException(40000, "风险事件 related_log_id 未绑定公司内合法账号审计");
            }
        } else {
            Long actorId = resolveActorId(entity.getCompanyId(), entity.getHandlerId());
            AuditLog log = new AuditLog();
            log.setUserId(actorId);
            log.setOperation("risk_event_trace_bind");
            log.setOperationTime(new Date());
            log.setInputOverview("companyId=" + entity.getCompanyId() + ", type=" + safe(entity.getType()));
            log.setOutputOverview("riskLevel=" + safe(entity.getLevel()) + ", status=" + safe(entity.getStatus()));
            log.setResult("success");
            log.setRiskLevel("MEDIUM");
            log.setCreateTime(new Date());
            auditLogService.saveAudit(log);

            entity.setRelatedLogId(log.getId());
            if (!StringUtils.hasText(entity.getAuditLogIds())) {
                entity.setAuditLogIds(String.valueOf(log.getId()));
            }
            if (entity.getHandlerId() == null) {
                entity.setHandlerId(actorId);
            }
        }

        return super.save(entity);
    }

    private Long resolveActorId(Long companyId, Long preferredUserId) {
        if (preferredUserId != null) {
            Integer valid = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sys_user WHERE id = ? AND company_id = ?",
                Integer.class,
                preferredUserId,
                companyId
            );
            if (valid != null && valid > 0) {
                return preferredUserId;
            }
        }

        Long fallback = jdbcTemplate.query(
            "SELECT id FROM sys_user WHERE company_id = ? AND username IN ('secops','admin','employee1') ORDER BY CASE username WHEN 'secops' THEN 1 WHEN 'admin' THEN 2 WHEN 'employee1' THEN 3 ELSE 9 END LIMIT 1",
            ps -> ps.setLong(1, companyId),
            rs -> rs.next() ? rs.getLong(1) : null
        );
        if (fallback == null) {
            throw new BizException(40000, "风险事件无法绑定合法账号");
        }
        return fallback;
    }

    private String safe(String text) {
        return text == null ? "" : text;
    }
}

