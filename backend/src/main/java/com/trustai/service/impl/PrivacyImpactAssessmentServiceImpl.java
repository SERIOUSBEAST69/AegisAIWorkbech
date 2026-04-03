package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.PrivacyImpactAssessment;
import com.trustai.mapper.PrivacyImpactAssessmentMapper;
import com.trustai.service.PrivacyImpactAssessmentService;
import org.springframework.stereotype.Service;

@Service
public class PrivacyImpactAssessmentServiceImpl extends ServiceImpl<PrivacyImpactAssessmentMapper, PrivacyImpactAssessment>
        implements PrivacyImpactAssessmentService {
}
