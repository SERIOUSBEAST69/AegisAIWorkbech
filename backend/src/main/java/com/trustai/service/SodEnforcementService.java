package com.trustai.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.trustai.entity.SodConflictRule;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SodEnforcementService {

    private final CurrentUserService currentUserService;
    private final SodConflictRuleService sodConflictRuleService;

    public SodEnforcementService(CurrentUserService currentUserService,
                                 SodConflictRuleService sodConflictRuleService) {
        this.currentUserService = currentUserService;
        this.sodConflictRuleService = sodConflictRuleService;
    }

    public void enforceReviewerSeparation(User requester, User approver, String scenario) {
        if (requester == null || approver == null) {
            throw new BizException(40000, "复核上下文不完整");
        }
        if (requester.getId() != null && requester.getId().equals(approver.getId())) {
            throw new BizException(40000, "SoD冲突：申请人与复核人不能为同一人");
        }
        String requesterRole = currentRoleCode(requester);
        String approverRole = currentRoleCode(approver);
        if (!StringUtils.hasText(requesterRole) || !StringUtils.hasText(approverRole)) {
            throw new BizException(40000, "SoD冲突：无法识别复核角色");
        }
        if (isConflictRuleMatched(requester.getCompanyId(), scenario, requesterRole, approverRole)
            || isConflictRuleMatched(requester.getCompanyId(), scenario, approverRole, requesterRole)) {
            throw new BizException(40000, "SoD冲突：当前角色组合不允许执行该复核");
        }
    }

    private boolean isConflictRuleMatched(Long companyId, String scenario, String roleA, String roleB) {
        return sodConflictRuleService.count(new QueryWrapper<SodConflictRule>()
            .eq("enabled", 1)
            .eq(companyId != null, "company_id", companyId)
            .eq("scenario", normalize(scenario))
            .eq("role_code_a", normalize(roleA))
            .eq("role_code_b", normalize(roleB))) > 0;
    }

    private String currentRoleCode(User user) {
        String code = currentUserService.getCurrentRole(user) == null ? null : currentUserService.getCurrentRole(user).getCode();
        return normalize(code);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}