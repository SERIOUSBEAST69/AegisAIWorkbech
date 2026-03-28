package com.trustai.service;

import java.time.LocalDate;

public interface RateLimiterService {
    void checkQuota(String modelCode, int limit, LocalDate date);
    void increment(String modelCode, LocalDate date);
    void reset(String modelCode, LocalDate date);

    /**
     * 通用分布式限流：使用 Redis + Lua 脚本原子性地检查并递增计数器。
     *
     * @param key           限流键（建议格式：业务前缀 + 标识，如 "api:ip:192.168.1.1"）
     * @param maxRequests   窗口内最大允许请求数
     * @param windowSeconds 窗口时长（秒）
     * @return true 表示允许通过；false 表示已触发限流
     */
    boolean tryAcquire(String key, int maxRequests, int windowSeconds);
}
