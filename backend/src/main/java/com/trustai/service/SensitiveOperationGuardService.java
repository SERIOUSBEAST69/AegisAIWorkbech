package com.trustai.service;

import com.trustai.entity.AuditLog;
import com.trustai.entity.User;
import com.trustai.exception.BizException;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SensitiveOperationGuardService {

    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;
    private final UserService userService;

    private final Map<String, Deque<Long>> opWindows = new ConcurrentHashMap<>();

    @Value("${governance.sensitive-ops.max-per-minute:20}")
    private int maxPerMinute;

    public SensitiveOperationGuardService(CurrentUserService currentUserService,
                                         PasswordEncoder passwordEncoder,
                                         AuditLogService auditLogService,
                                         UserService userService) {
        this.currentUserService = currentUserService;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
        this.userService = userService;
    }

    public User requireConfirmedAdmin(String confirmPassword, String operation, String target) {
        currentUserService.requireAdmin();
        User operator = currentUserService.requireCurrentUser();
        return requireConfirmedOperator(operator, confirmPassword, operation, target);
    }

    public User requireConfirmedOperator(User operator, String confirmPassword, String operation, String target) {
        if (operator == null) {
            throw new BizException(40100, "未登录");
        }
        if (!StringUtils.hasText(confirmPassword)) {
            writeDeniedAudit(operator, operation, target, "password_empty");
            throw new BizException(40000, "敏感操作需要二次密码");
        }
        if (!passwordEncoder.matches(confirmPassword, operator.getPassword())) {
            writeDeniedAudit(operator, operation, target, "password_check_failed");
            throw new BizException(40000, "二次密码错误，请重新输入");
        }
        enforceRateLimit(operator, operation, target);
        return operator;
    }

    public User requireDualReviewedOperator(User operator,
                                            String confirmPassword,
                                            String reviewerUsername,
                                            String reviewerPassword,
                                            String operation,
                                            String target) {
        User confirmed = requireConfirmedOperator(operator, confirmPassword, operation, target);
        if (!StringUtils.hasText(reviewerUsername) || !StringUtils.hasText(reviewerPassword)) {
            writeDeniedAudit(confirmed, operation, target, "dual_review_missing");
            throw new BizException(40000, "敏感操作需要双人复核");
        }
        User reviewer = userService.lambdaQuery().eq(User::getUsername, reviewerUsername.trim()).one();
        if (reviewer == null) {
            writeDeniedAudit(confirmed, operation, target, "dual_review_user_not_found");
            throw new BizException(40000, "复核人不存在");
        }
        if (confirmed.getCompanyId() != null && !confirmed.getCompanyId().equals(reviewer.getCompanyId())) {
            writeDeniedAudit(confirmed, operation, target, "dual_review_cross_company");
            throw new BizException(40000, "复核人不在当前公司");
        }
        if (confirmed.getId() != null && confirmed.getId().equals(reviewer.getId())) {
            writeDeniedAudit(confirmed, operation, target, "dual_review_same_operator");
            throw new BizException(40000, "操作人与复核人不能为同一账号");
        }
        if (!passwordEncoder.matches(reviewerPassword, reviewer.getPassword())) {
            writeDeniedAudit(confirmed, operation, target, "dual_review_password_invalid");
            throw new BizException(40000, "复核人密码校验失败");
        }
        return confirmed;
    }

    private void enforceRateLimit(User operator, String operation, String target) {
        long now = System.currentTimeMillis();
        String key = operator.getId() + ":" + operation;
        Deque<Long> deque = opWindows.computeIfAbsent(key, unused -> new ArrayDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && now - deque.peekFirst() > 60_000L) {
                deque.pollFirst();
            }
            if (deque.size() >= Math.max(3, maxPerMinute)) {
                writeDeniedAudit(operator, operation, target, "rate_limited");
                throw new BizException(40000, "操作过于频繁，已触发治理熔断，请稍后再试");
            }
            deque.offerLast(now);
        }
    }

    private void writeDeniedAudit(User operator, String operation, String target, String reason) {
        try {
            AuditLog log = new AuditLog();
            log.setUserId(operator.getId());
            log.setOperation("sensitive_guard_" + operation);
            log.setOperationTime(new Date());
            log.setInputOverview("target=" + target + ", reason=" + reason);
            log.setOutputOverview("blocked");
            log.setResult("denied");
            log.setRiskLevel("HIGH");
            log.setCreateTime(new Date());
            auditLogService.saveAudit(log);
        } catch (Exception ignored) {
            // Non-blocking audit write.
        }
    }
}