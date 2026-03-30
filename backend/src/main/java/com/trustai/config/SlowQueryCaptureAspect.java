package com.trustai.config;

import com.trustai.entity.GovernanceEvent;
import com.trustai.service.GovernanceEventService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class SlowQueryCaptureAspect {

    private final JdbcTemplate jdbcTemplate;
    private final GovernanceEventService governanceEventService;

    @Value("${ops.slow-query-threshold-ms:250}")
    private long slowQueryThresholdMs;

    @Around("execution(* com.trustai.mapper..*(..))")
    public Object captureSlowQuery(ProceedingJoinPoint pjp) throws Throwable {
        long start = System.currentTimeMillis();
        try {
            return pjp.proceed();
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed >= slowQueryThresholdMs) {
                String method = pjp.getSignature().toShortString();
                String argsDigest = digestArgs(pjp.getArgs());
                Date now = new Date();

                try {
                    jdbcTemplate.update(
                        """
                        INSERT INTO slow_query_log
                        (company_id, mapper_method, elapsed_ms, args_digest, query_time, create_time)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                        1L,
                        method,
                        elapsed,
                        argsDigest,
                        now,
                        now
                    );
                } catch (Exception ex) {
                    log.debug("Failed to insert slow query log: {}", ex.getMessage());
                }

                if (elapsed >= slowQueryThresholdMs * 2) {
                    GovernanceEvent event = new GovernanceEvent();
                    event.setCompanyId(1L);
                    event.setEventType("SLOW_QUERY_ALERT");
                    event.setSourceModule("observability");
                    event.setSeverity(elapsed >= slowQueryThresholdMs * 4 ? "high" : "medium");
                    event.setStatus("pending");
                    event.setTitle("慢查询告警");
                    event.setDescription(method + " 耗时 " + elapsed + "ms");
                    event.setSourceEventId("slow-query:" + System.currentTimeMillis());
                    event.setAttackType("query_regression");
                    event.setPolicyVersion(1L);
                    event.setPayloadJson(String.valueOf(Map.of("elapsedMs", elapsed, "argsDigest", argsDigest)));
                    event.setEventTime(now);
                    event.setCreateTime(now);
                    event.setUpdateTime(now);
                    try {
                        governanceEventService.save(event);
                    } catch (Exception ex) {
                        log.debug("Failed to create slow query governance event: {}", ex.getMessage());
                    }
                }
            }
        }
    }

    private String digestArgs(Object[] args) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(String.valueOf(args == null ? "" : java.util.Arrays.deepToString(args))
                .getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "na";
        }
    }
}
