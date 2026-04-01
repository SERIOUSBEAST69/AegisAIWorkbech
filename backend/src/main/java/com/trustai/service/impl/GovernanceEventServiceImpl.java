package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.GovernanceEvent;
import com.trustai.exception.BizException;
import com.trustai.mapper.GovernanceEventMapper;
import com.trustai.service.GovernanceEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class GovernanceEventServiceImpl extends ServiceImpl<GovernanceEventMapper, GovernanceEvent>
    implements GovernanceEventService {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean save(GovernanceEvent entity) {
        if (entity == null || entity.getCompanyId() == null || entity.getCompanyId() <= 0) {
            throw new BizException(40000, "治理事件缺少合法 company_id");
        }

        Long userId = entity.getUserId();
        String username = entity.getUsername();
        if (isPseudoUser(username)) {
            throw new BizException(40000, "禁止写入 system/匿名 治理事件");
        }

        if (userId != null) {
            UserRow row = jdbcTemplate.query(
                "SELECT id, company_id, username FROM sys_user WHERE id = ? LIMIT 1",
                ps -> ps.setLong(1, userId),
                rs -> rs.next() ? new UserRow(rs.getLong(1), rs.getLong(2), rs.getString(3)) : null
            );
            if (row == null || !entity.getCompanyId().equals(row.companyId())) {
                throw new BizException(40000, "治理事件 user_id 非法或跨公司");
            }
            if (StringUtils.hasText(username) && !row.username().equalsIgnoreCase(username.trim())) {
                throw new BizException(40000, "治理事件 user_id 与 username 不一致");
            }
            entity.setUsername(row.username());
        } else {
            if (!StringUtils.hasText(username)) {
                throw new BizException(40000, "治理事件必须绑定已存在账号");
            }
            UserRow row = jdbcTemplate.query(
                "SELECT id, company_id, username FROM sys_user WHERE company_id = ? AND LOWER(username) = LOWER(?) LIMIT 1",
                ps -> {
                    ps.setLong(1, entity.getCompanyId());
                    ps.setString(2, username.trim());
                },
                rs -> rs.next() ? new UserRow(rs.getLong(1), rs.getLong(2), rs.getString(3)) : null
            );
            if (row == null) {
                throw new BizException(40000, "治理事件 username 未绑定到公司账号");
            }
            entity.setUserId(row.id());
            entity.setUsername(row.username());
        }

        return super.save(entity);
    }

    private boolean isPseudoUser(String username) {
        if (!StringUtils.hasText(username)) {
            return false;
        }
        String normalized = username.trim().toLowerCase();
        return "system".equals(normalized) || "anonymous".equals(normalized) || "匿名".equals(normalized);
    }

    private record UserRow(Long id, Long companyId, String username) {}
}
