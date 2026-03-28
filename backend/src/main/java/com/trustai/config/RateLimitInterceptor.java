package com.trustai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.service.RateLimiterService;
import com.trustai.utils.R;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 分布式 HTTP 限流拦截器（Redis + Lua 固定窗口算法）。
 *
 * <p>每个客户端 IP 在 {@value #WINDOW_SECONDS} 秒内最多允许 {@value #MAX_REQUESTS} 次请求。
 * 超限时返回 HTTP 429 并写入 JSON 错误体。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitInterceptor implements HandlerInterceptor {

    /** 固定窗口时长（秒） */
    private static final int WINDOW_SECONDS = 60;
    /** 窗口内最大请求数 */
    private static final int MAX_REQUESTS = 300;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RateLimiterService rateLimiterService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws IOException {
        String ip = resolveClientIp(request);
        String key = "api:ip:" + ip;

        if (!rateLimiterService.tryAcquire(key, MAX_REQUESTS, WINDOW_SECONDS)) {
            log.warn("Rate limit triggered for IP={}, uri={}", ip, request.getRequestURI());
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            String body = MAPPER.writeValueAsString(R.error(42900, "请求过于频繁，请稍后重试"));
            response.getWriter().write(body);
            return false;
        }
        return true;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理取第一个有效 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip == null ? "unknown" : ip;
    }
}
