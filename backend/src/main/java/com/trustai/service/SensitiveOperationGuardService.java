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

    private final Map<String, Deque<Long>> opWindows = new ConcurrentHashMap<>();

    @Value("${governance.sensitive-ops.max-per-minute:20}")
    private int maxPerMinute;

    public SensitiveOperationGuardService(CurrentUserService currentUserService,
                                         PasswordEncoder passwordEncoder,
                                         AuditLogService auditLogService) {
        this.currentUserService = currentUserService;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
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
        if (!StringUtils.hasText(confirmPassword) || !passwordEncoder.matches(confirmPassword, operator.getPassword())) {
            writeDeniedAudit(operator, operation, target, "password_check_failed");
            throw new BizException(40000, "敏感操作需要二次密码确认");
        }
        enforceRateLimit(operator, operation, target);
        return operator;
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