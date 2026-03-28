package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.RiskEvent;
import com.trustai.mapper.RiskEventMapper;
import com.trustai.service.RiskEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class RiskEventServiceImpl extends ServiceImpl<RiskEventMapper, RiskEvent> implements RiskEventService {

    @Override
    public boolean save(RiskEvent entity) {
        return super.save(entity);
    }
}

