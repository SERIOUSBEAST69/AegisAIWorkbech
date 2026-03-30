package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.SodConflictRule;
import com.trustai.mapper.SodConflictRuleMapper;
import com.trustai.service.SodConflictRuleService;
import org.springframework.stereotype.Service;

@Service
public class SodConflictRuleServiceImpl extends ServiceImpl<SodConflictRuleMapper, SodConflictRule>
    implements SodConflictRuleService {
}
