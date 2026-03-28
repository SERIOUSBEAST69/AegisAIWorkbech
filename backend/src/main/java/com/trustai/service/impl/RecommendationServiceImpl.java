package com.trustai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.trustai.dto.DesenseRecommendationDto;
import com.trustai.entity.DesenseRecommendRule;
import com.trustai.entity.DesensitizeRule;
import com.trustai.service.DesenseRecommendRuleService;
import com.trustai.service.DesensitizeRuleService;
import com.trustai.service.RecommendationService;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private final DesenseRecommendRuleService recommendRuleService;
    private final DesensitizeRuleService desensitizeRuleService;

    @Override
    public List<DesenseRecommendationDto> recommend(String dataCategory, String userRole, String sensitivityLevel) {
        LambdaQueryWrapper<DesenseRecommendRule> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(dataCategory)) {
            qw.eq(DesenseRecommendRule::getDataCategory, dataCategory);
        }
        if (StringUtils.hasText(userRole)) {
            qw.eq(DesenseRecommendRule::getUserRole, userRole);
        }
        qw.orderByAsc(DesenseRecommendRule::getPriority);
        List<DesenseRecommendRule> rules = recommendRuleService.list(qw);
        if (rules.isEmpty()) {
            rules = recommendRuleService.list(new LambdaQueryWrapper<DesenseRecommendRule>().orderByAsc(DesenseRecommendRule::getPriority));
        }
        if (rules.isEmpty()) {
            return Collections.emptyList();
        }
        Set<Long> ruleIds = rules.stream()
                .map(DesenseRecommendRule::getRuleId)
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toSet());
        Map<Long, DesensitizeRule> ruleMap = desensitizeRuleService.listByIds(ruleIds).stream()
                .collect(Collectors.toMap(DesensitizeRule::getId, r -> r));

        return rules.stream().map(r -> {
            DesensitizeRule dr = ruleMap.get(r.getRuleId());
            DesenseRecommendationDto dto = new DesenseRecommendationDto();
            dto.setRuleId(r.getRuleId());
            dto.setStrategy(r.getStrategy());
            dto.setPriority(r.getPriority());
            if (dr != null) {
                dto.setName(dr.getName());
                dto.setPattern(dr.getPattern());
                dto.setMask(dr.getMask());
            }
            dto.setReason(buildReason(r, sensitivityLevel));
            return dto;
        }).collect(Collectors.toList());
    }

    private String buildReason(DesenseRecommendRule rule, String sensitivityLevel) {
        StringBuilder sb = new StringBuilder("匹配规则");
        if (StringUtils.hasText(rule.getDataCategory())) {
            sb.append(" 类别:").append(rule.getDataCategory());
        }
        if (StringUtils.hasText(rule.getUserRole())) {
            sb.append(" 角色:").append(rule.getUserRole());
        }
        if (StringUtils.hasText(sensitivityLevel)) {
            sb.append(" 敏感级别:").append(sensitivityLevel);
        }
        return sb.toString();
    }
}
