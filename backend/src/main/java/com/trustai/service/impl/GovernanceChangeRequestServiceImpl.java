package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.GovernanceChangeRequest;
import com.trustai.mapper.GovernanceChangeRequestMapper;
import com.trustai.service.GovernanceChangeRequestService;
import org.springframework.stereotype.Service;

@Service
public class GovernanceChangeRequestServiceImpl extends ServiceImpl<GovernanceChangeRequestMapper, GovernanceChangeRequest>
    implements GovernanceChangeRequestService {
}
