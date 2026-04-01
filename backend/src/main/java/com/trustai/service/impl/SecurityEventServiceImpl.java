package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.SecurityEvent;
import com.trustai.exception.BizException;
import com.trustai.mapper.SecurityEventMapper;
import com.trustai.service.SecurityEventService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class SecurityEventServiceImpl extends ServiceImpl<SecurityEventMapper, SecurityEvent> implements SecurityEventService {

	private final JdbcTemplate jdbcTemplate;

	@Override
	public boolean save(SecurityEvent entity) {
		if (entity == null || entity.getCompanyId() == null || entity.getCompanyId() <= 0) {
			throw new BizException(40000, "安全事件缺少合法 company_id");
		}
		if (!StringUtils.hasText(entity.getEmployeeId())) {
			throw new BizException(40000, "安全事件必须绑定员工账号");
		}
		String candidate = entity.getEmployeeId().trim();
		String normalized = candidate.toLowerCase();
		if ("system".equals(normalized) || "anonymous".equals(normalized) || "匿名".equals(normalized)) {
			throw new BizException(40000, "禁止写入 system/匿名 安全事件");
		}

		String username = jdbcTemplate.query(
			"SELECT username FROM sys_user WHERE company_id = ? AND LOWER(username) = LOWER(?) LIMIT 1",
			ps -> {
				ps.setLong(1, entity.getCompanyId());
				ps.setString(2, candidate);
			},
			rs -> rs.next() ? rs.getString(1) : null
		);
		if (!StringUtils.hasText(username)) {
			throw new BizException(40000, "安全事件 employee_id 未绑定到公司账号");
		}
		entity.setEmployeeId(username);
		return super.save(entity);
	}
}
