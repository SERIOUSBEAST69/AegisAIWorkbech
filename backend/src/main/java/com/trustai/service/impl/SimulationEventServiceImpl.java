package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.SimulationEvent;
import com.trustai.mapper.SimulationEventMapper;
import com.trustai.service.SimulationEventService;
import org.springframework.stereotype.Service;

@Service
public class SimulationEventServiceImpl extends ServiceImpl<SimulationEventMapper, SimulationEvent> implements SimulationEventService {
}
