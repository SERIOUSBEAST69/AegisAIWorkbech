package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.TenantHealthReport;
import com.trustai.mapper.TenantHealthReportMapper;
import com.trustai.service.TenantHealthReportService;
import org.springframework.stereotype.Service;

@Service
public class TenantHealthReportServiceImpl extends ServiceImpl<TenantHealthReportMapper, TenantHealthReport>
        implements TenantHealthReportService {
}
