package com.trustai.service.impl;

import com.trustai.service.RateLimiterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimiterServiceImpl implements RateLimiterService {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private final StringRedisTemplate redisTemplate;

    /**
     * Redis + Lua 原子限流脚本（固定窗口算法）。
     *
     * <p>KEYS[1]  : 限流键
     * <p>ARGV[1]  : 最大请求数
     * <p>ARGV[2]  : 窗口时长（秒）
     * <p>返回值   : 1 = 允许；0 = 拒绝
     */
    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local key = KEYS[1]\n" +
            "local limit = tonumber(ARGV[1])\n" +
            "local ttl = tonumber(ARGV[2])\n" +
            "local current = tonumber(redis.call('INCR', key))\n" +
            "if current == 1 then\n" +
            "    redis.call('EXPIRE', key, ttl)\n" +
            "end\n" +
            "if current > limit then\n" +
            "    return 0\n" +
            "end\n" +
            "return 1",
            Long.class
    );

    @Override
    public void checkQuota(String modelCode, int limit, LocalDate date) {
        if (limit <= 0) return; // 0 表示不限
        String key = modelKey(modelCode, date);
        try {
            String value = redisTemplate.opsForValue().get(key);
            long current = value == null ? 0L : Long.parseLong(value);
            if (current >= limit) {
                throw new IllegalStateException("模型" + modelCode + "今日调用已达上限" + limit);
            }
        } catch (Exception e) {
            if (e instanceof IllegalStateException) throw (IllegalStateException) e;
            log.warn("[RateLimiter] Redis 不可用，跳过配额检查 modelCode={}", modelCode, e);
        }
    }

    @Override
    public void increment(String modelCode, LocalDate date) {
        try {
            redisTemplate.opsForValue().increment(modelKey(modelCode, date), 1);
        } catch (Exception e) {
            log.warn("[RateLimiter] Redis 不可用，跳过计数递增 modelCode={}", modelCode, e);
        }
    }

    @Override
    public void reset(String modelCode, LocalDate date) {
        try {
            redisTemplate.delete(modelKey(modelCode, date));
        } catch (Exception e) {
            log.warn("[RateLimiter] Redis 不可用，跳过重置计数 modelCode={}", modelCode, e);
        }
    }

    /**
     * 通用分布式限流：原子性地检查并递增计数器。
     *
     * @return true 表示允许通过；false 表示触发限流
     */
    @Override
    public boolean tryAcquire(String key, int maxRequests, int windowSeconds) {
        try {
            List<String> keys = Collections.singletonList(key);
            Long result = redisTemplate.execute(
                    RATE_LIMIT_SCRIPT,
                    keys,
                    String.valueOf(maxRequests),
                    String.valueOf(windowSeconds)
            );
            return result != null && result == 1L;
        } catch (Exception e) {
            // Redis 不可用时降级放行，避免影响正常业务
            log.warn("Rate limit check failed for key={}, defaulting to allow", key, e);
            return true;
        }
    }

    private String modelKey(String modelCode, LocalDate date) {
        return "ai:call:" + modelCode + ":" + DATE.format(date);
    }
}
