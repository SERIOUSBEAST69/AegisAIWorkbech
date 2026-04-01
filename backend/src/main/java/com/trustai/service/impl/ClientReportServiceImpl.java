package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.ClientReport;
import com.trustai.exception.BizException;
import com.trustai.mapper.ClientReportMapper;
import com.trustai.service.ClientReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ClientReportServiceImpl extends ServiceImpl<ClientReportMapper, ClientReport>
        implements ClientReportService {

        private final JdbcTemplate jdbcTemplate;

        @Override
        public boolean save(ClientReport entity) {
                if (entity == null || entity.getCompanyId() == null || entity.getCompanyId() <= 0) {
                        throw new BizException(40000, "客户端报告缺少合法 company_id");
                }
                if (!StringUtils.hasText(entity.getOsUsername())) {
                        throw new BizException(40000, "客户端报告必须绑定员工账号");
                }
                String normalized = entity.getOsUsername().trim().toLowerCase();
                if ("system".equals(normalized) || "anonymous".equals(normalized) || "匿名".equals(normalized)) {
                        throw new BizException(40000, "禁止写入 system/匿名 客户端报告");
                }

                String username = jdbcTemplate.query(
                        "SELECT username FROM sys_user WHERE company_id = ? AND LOWER(username) = LOWER(?) LIMIT 1",
                        ps -> {
                                ps.setLong(1, entity.getCompanyId());
                                ps.setString(2, entity.getOsUsername().trim());
                        },
                        rs -> rs.next() ? rs.getString(1) : null
                );
                if (!StringUtils.hasText(username)) {
                        throw new BizException(40000, "客户端报告 os_username 未绑定到公司账号");
                }
                entity.setOsUsername(username);
                return super.save(entity);
        }
}
