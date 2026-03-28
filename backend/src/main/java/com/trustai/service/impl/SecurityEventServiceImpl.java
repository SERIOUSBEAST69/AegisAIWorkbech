package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.SecurityEvent;
import com.trustai.mapper.SecurityEventMapper;
import com.trustai.service.SecurityEventService;
import org.springframework.stereotype.Service;

@Service
public class SecurityEventServiceImpl extends ServiceImpl<SecurityEventMapper, SecurityEvent> implements SecurityEventService {
}
