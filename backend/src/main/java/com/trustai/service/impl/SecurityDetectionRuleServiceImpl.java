package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.SecurityDetectionRule;
import com.trustai.mapper.SecurityDetectionRuleMapper;
import com.trustai.service.SecurityDetectionRuleService;
import org.springframework.stereotype.Service;

@Service
public class SecurityDetectionRuleServiceImpl extends ServiceImpl<SecurityDetectionRuleMapper, SecurityDetectionRule> implements SecurityDetectionRuleService {
}
