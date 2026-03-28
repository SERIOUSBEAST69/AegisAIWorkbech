package com.trustai.config;

import com.trustai.service.ApiMetricService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ApiMetricsFilter extends OncePerRequestFilter {

    private final ApiMetricService apiMetricService;

    public ApiMetricsFilter(ApiMetricService apiMetricService) {
        this.apiMetricService = apiMetricService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (!uri.startsWith("/api/") || uri.startsWith("/api/ops-metrics")) {
            filterChain.doFilter(request, response);
            return;
        }
        long start = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = (System.nanoTime() - start) / 1_000_000;
            String key = request.getMethod() + " " + uri;
            apiMetricService.record(key, durationMs, response.getStatus());
        }
    }
}
