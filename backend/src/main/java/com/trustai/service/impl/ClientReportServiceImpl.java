package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.ClientReport;
import com.trustai.mapper.ClientReportMapper;
import com.trustai.service.ClientReportService;
import org.springframework.stereotype.Service;

@Service
public class ClientReportServiceImpl extends ServiceImpl<ClientReportMapper, ClientReport>
        implements ClientReportService {
}
