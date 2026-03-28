package com.trustai.config;

import com.trustai.entity.DesenseRecommendRule;
import com.trustai.entity.DesensitizeRule;
import com.trustai.service.DesenseRecommendRuleService;
import com.trustai.service.DesensitizeRuleService;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(5)
@RequiredArgsConstructor
public class DesenseRuleInitializer implements CommandLineRunner {

    private final DesensitizeRuleService desensitizeRuleService;
    private final DesenseRecommendRuleService recommendRuleService;

    @Override
    public void run(String... args) {
        seedRules();
        seedRecommendRules();
    }

    private void seedRules() {
        if (desensitizeRuleService.count() > 0) {
            return;
        }
        List<DesensitizeRule> defaults = Arrays.asList(
                buildRule("手机号掩码", "1[3-9]\\d{9}", "*", "138****8899"),
                buildRule("身份证掩码", "[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]", "*", "4201**********1234"),
                buildRule("银行卡掩码", "\\d{12,19}", "*", "6222 **** **** 5566"),
                buildRule("邮箱脱敏", "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "*", "u***@example.com")
        );
        desensitizeRuleService.saveBatch(defaults);
    }

    private void seedRecommendRules() {
        if (recommendRuleService.count() > 0) {
            return;
        }
        List<DesenseRecommendRule> rules = Arrays.asList(
                buildRec("id_card", "analyst", "mask", 2L, 1),
                buildRec("bank_card", "analyst", "mask", 3L, 1),
                buildRec("phone", "auditor", "hash", 1L, 2),
                buildRec("email", "auditor", "mask", 4L, 3)
        );
        recommendRuleService.saveBatch(rules);
    }

    private DesensitizeRule buildRule(String name, String pattern, String mask, String example) {
        DesensitizeRule rule = new DesensitizeRule();
        rule.setName(name);
        rule.setPattern(pattern);
        rule.setMask(mask);
        rule.setExample(example);
        rule.setCreateTime(new Date());
        rule.setUpdateTime(new Date());
        return rule;
    }

    private DesenseRecommendRule buildRec(String category, String role, String strategy, Long ruleId, int priority) {
        DesenseRecommendRule r = new DesenseRecommendRule();
        r.setDataCategory(category);
        r.setUserRole(role);
        r.setStrategy(strategy);
        r.setRuleId(ruleId);
        r.setPriority(priority);
        r.setCreateTime(new Date());
        r.setUpdateTime(new Date());
        return r;
    }
}
