package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.DesensitizeRule;
import com.trustai.mapper.DesensitizeRuleMapper;
import com.trustai.service.DesensitizeRuleService;
import org.springframework.stereotype.Service;

@Service
public class DesensitizeRuleServiceImpl extends ServiceImpl<DesensitizeRuleMapper, DesensitizeRule> implements DesensitizeRuleService {
}
