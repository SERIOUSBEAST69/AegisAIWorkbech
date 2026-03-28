package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.CompliancePolicy;
import com.trustai.mapper.CompliancePolicyMapper;
import com.trustai.service.CompliancePolicyService;
import org.springframework.stereotype.Service;

@Service
public class CompliancePolicyServiceImpl extends ServiceImpl<CompliancePolicyMapper, CompliancePolicy> implements CompliancePolicyService {
}
