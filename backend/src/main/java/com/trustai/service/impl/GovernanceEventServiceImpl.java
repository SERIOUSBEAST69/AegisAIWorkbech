package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.GovernanceEvent;
import com.trustai.mapper.GovernanceEventMapper;
import com.trustai.service.GovernanceEventService;
import org.springframework.stereotype.Service;

@Service
public class GovernanceEventServiceImpl extends ServiceImpl<GovernanceEventMapper, GovernanceEvent>
    implements GovernanceEventService {
}
