package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.AiCallLog;
import com.trustai.exception.BizException;
import com.trustai.mapper.AiCallLogMapper;
import com.trustai.service.AiCallAuditService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AiCallAuditServiceImpl extends ServiceImpl<AiCallLogMapper, AiCallLog> implements AiCallAuditService {

    private static final Logger log = LoggerFactory.getLogger(AiCallAuditServiceImpl.class);
    private final JdbcTemplate jdbcTemplate;

    @Override
    public boolean save(AiCallLog entity) {
        if (entity == null || entity.getCompanyId() == null || entity.getCompanyId() <= 0) {
            throw new BizException(40000, "AI调用日志缺少合法 company_id");
        }
        if (entity.getUserId() == null || entity.getUserId() <= 0) {
            throw new BizException(40000, "AI调用日志缺少合法 user_id");
        }

        UserRow user = jdbcTemplate.query(
            "SELECT id, company_id, username FROM sys_user WHERE id = ? LIMIT 1",
            ps -> ps.setLong(1, entity.getUserId()),
            rs -> rs.next() ? new UserRow(rs.getLong(1), rs.getLong(2), rs.getString(3)) : null
        );
        if (user == null || !entity.getCompanyId().equals(user.companyId())) {
            throw new BizException(40000, "AI调用日志 user_id 非法或跨公司");
        }
        if (StringUtils.hasText(entity.getUsername())) {
            String normalized = entity.getUsername().trim();
            if (!user.username().equalsIgnoreCase(normalized)) {
                throw new BizException(40000, "AI调用日志 user_id 与 username 不一致");
            }
        }
        entity.setUsername(user.username());
        boolean ok = super.save(entity);
        appendAiCallHashChain(entity);
        return ok;
    }

    @Override
    @Async("aiAuditExecutor")
    public void recordAsync(AiCallLog logEntry) {
        try {
            save(logEntry);
        } catch (Exception e) {
            log.error("record ai call log failed", e);
        }
    }

    @Override
    public Map<String, Object> verifyHashChain(Long companyId) {
        Map<String, Object> result = new LinkedHashMap<>();
        long checked = 0L;
        long violated = 0L;
        String expectedPrev = null;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
            """
                SELECT c.id, c.company_id, c.ai_call_log_id, c.prev_hash, c.current_hash,
                       l.user_id, l.username, l.data_asset_id, l.model_code, l.provider,
                       l.status, l.duration_ms, l.token_usage, l.create_time
                FROM ai_call_hash_chain c
                JOIN ai_call_log l ON l.id = c.ai_call_log_id
                WHERE c.company_id = ?
                ORDER BY c.id ASC
            """,
            companyId
        );

        for (Map<String, Object> row : rows) {
            checked++;
            String prevHash = row.get("prev_hash") == null ? null : String.valueOf(row.get("prev_hash"));
            if (expectedPrev != null && !equalsIgnoreCase(expectedPrev, prevHash)) {
                violated++;
            }
            String payload = String.join("|",
                String.valueOf(row.get("company_id")),
                String.valueOf(row.get("ai_call_log_id")),
                safe(row.get("user_id")),
                safe(row.get("username")),
                safe(row.get("data_asset_id")),
                safe(row.get("model_code")),
                safe(row.get("provider")),
                safe(row.get("status")),
                safe(row.get("duration_ms")),
                safe(row.get("token_usage")),
                normalizeDateTime(row.get("create_time")),
                safe(prevHash)
            );
            String recomputed = sha256(payload);
            String currentHash = row.get("current_hash") == null ? "" : String.valueOf(row.get("current_hash"));
            if (!equalsIgnoreCase(recomputed, currentHash)) {
                violated++;
            }
            expectedPrev = currentHash;
        }

        result.put("companyId", companyId);
        result.put("checkedRows", checked);
        result.put("violationCount", violated);
        result.put("passed", violated == 0);
        result.put("verifiedAt", LocalDateTime.now().toString());
        return result;
    }

    @Override
    public Map<String, Object> rebuildHashChain(Long companyId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (companyId == null || companyId <= 0) {
            result.put("companyId", companyId);
            result.put("rebuiltCount", 0);
            result.put("deletedCount", 0);
            return result;
        }

        int deleted = jdbcTemplate.update("DELETE FROM ai_call_hash_chain WHERE company_id = ?", companyId);
        List<Map<String, Object>> logs = jdbcTemplate.queryForList(
            """
                SELECT id, company_id, user_id, username, data_asset_id, model_code, provider,
                       status, duration_ms, token_usage, create_time
                FROM ai_call_log
                WHERE company_id = ?
                ORDER BY id ASC
            """,
            companyId
        );

        String prevHash = null;
        int rebuilt = 0;
        for (Map<String, Object> row : logs) {
            String payload = String.join("|",
                safe(row.get("company_id")),
                safe(row.get("id")),
                safe(row.get("user_id")),
                safe(row.get("username")),
                safe(row.get("data_asset_id")),
                safe(row.get("model_code")),
                safe(row.get("provider")),
                safe(row.get("status")),
                safe(row.get("duration_ms")),
                safe(row.get("token_usage")),
                normalizeDateTime(row.get("create_time")),
                safe(prevHash)
            );
            String currentHash = sha256(payload);
            jdbcTemplate.update(
                "INSERT INTO ai_call_hash_chain(company_id, ai_call_log_id, prev_hash, current_hash, create_time) VALUES(?, ?, ?, ?, CURRENT_TIMESTAMP)",
                companyId,
                row.get("id"),
                prevHash,
                currentHash
            );
            prevHash = currentHash;
            rebuilt++;
        }

        result.put("companyId", companyId);
        result.put("deletedCount", deleted);
        result.put("rebuiltCount", rebuilt);
        result.put("tailHash", prevHash);
        return result;
    }

    private void appendAiCallHashChain(AiCallLog logEntity) {
        if (logEntity == null || logEntity.getId() == null || logEntity.getCompanyId() == null) {
            return;
        }
        try {
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM ai_call_hash_chain WHERE ai_call_log_id = ?",
                Integer.class,
                logEntity.getId()
            );
            if (exists != null && exists > 0) {
                return;
            }

            String prevHash = jdbcTemplate.query(
                "SELECT current_hash FROM ai_call_hash_chain WHERE company_id = ? ORDER BY id DESC LIMIT 1",
                ps -> ps.setLong(1, logEntity.getCompanyId()),
                rs -> rs.next() ? rs.getString(1) : null
            );

            String payload = String.join("|",
                String.valueOf(logEntity.getCompanyId()),
                String.valueOf(logEntity.getId()),
                safe(logEntity.getUserId()),
                safe(logEntity.getUsername()),
                safe(logEntity.getDataAssetId()),
                safe(logEntity.getModelCode()),
                safe(logEntity.getProvider()),
                safe(logEntity.getStatus()),
                safe(logEntity.getDurationMs()),
                safe(logEntity.getTokenUsage()),
                normalizeDateTime(logEntity.getCreateTime()),
                safe(prevHash)
            );
            String currentHash = sha256(payload);

            jdbcTemplate.update(
                "INSERT INTO ai_call_hash_chain(company_id, ai_call_log_id, prev_hash, current_hash, create_time) VALUES(?, ?, ?, ?, CURRENT_TIMESTAMP)",
                logEntity.getCompanyId(),
                logEntity.getId(),
                prevHash,
                currentHash
            );
        } catch (Exception ex) {
            log.warn("append ai_call hash chain failed for logId={}", logEntity.getId(), ex);
        }
    }

    private String normalizeDateTime(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equalsIgnoreCase(right);
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : bytes) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("sha256 compute failed", ex);
        }
    }

    private record UserRow(Long id, Long companyId, String username) {}
}
