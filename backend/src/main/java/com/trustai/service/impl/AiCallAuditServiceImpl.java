package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.AiCallLog;
import com.trustai.exception.BizException;
import com.trustai.mapper.AiCallLogMapper;
import com.trustai.service.AiCallAuditService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AiCallAuditServiceImpl extends ServiceImpl<AiCallLogMapper, AiCallLog> implements AiCallAuditService {

    private static final Logger log = LoggerFactory.getLogger(AiCallAuditServiceImpl.class);
    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean save(AiCallLog entity) {
        if (entity == null || entity.getCompanyId() == null || entity.getCompanyId() <= 0) {
            throw new BizException(40000, "AI调用日志缺少合法 company_id");
        }
        if (entity.getUserId() == null || entity.getUserId() <= 0) {
            throw new BizException(40000, "AI调用日志缺少合法 user_id");
        }

        UserRow user = jdbcTemplate.query(
            "SELECT id, company_id, username FROM sys_user WHERE id = ? LIMIT 1",
            ps -> ps.setLong(1, entity.getUserId()),
            rs -> rs.next() ? new UserRow(rs.getLong(1), rs.getLong(2), rs.getString(3)) : null
        );
        if (user == null || !entity.getCompanyId().equals(user.companyId())) {
            throw new BizException(40000, "AI调用日志 user_id 非法或跨公司");
        }
        if (StringUtils.hasText(entity.getUsername())) {
            String normalized = entity.getUsername().trim();
            if (!user.username().equalsIgnoreCase(normalized)) {
                throw new BizException(40000, "AI调用日志 user_id 与 username 不一致");
            }
        }
        entity.setUsername(user.username());
        return super.save(entity);
    }

    @Override
    @Async("aiAuditExecutor")
    public void recordAsync(AiCallLog logEntry) {
        try {
            save(logEntry);
        } catch (Exception e) {
            log.error("record ai call log failed", e);
        }
    }

    private record UserRow(Long id, Long companyId, String username) {}
}
