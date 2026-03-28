package com.trustai.config;

import com.trustai.utils.R;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Enumeration;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class ThreatInputFilter extends OncePerRequestFilter {

    private static final Pattern THREAT_PATTERN = Pattern.compile(
        "(?i)(\\bunion\\b.+\\bselect\\b|\\bdrop\\b\\s+\\btable\\b|\\bor\\b\\s+1=1|<\\s*script|javascript:|onerror\\s*=|onload\\s*=)"
    );

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        if (looksMalicious(request)) {
            response.setStatus(400);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write(objectMapper.writeValueAsString(R.error(40010, "检测到潜在攻击载荷，请检查输入内容")));
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean looksMalicious(HttpServletRequest request) {
        if (matches(request.getRequestURI())) {
            return true;
        }
        String query = request.getQueryString();
        if (matches(query)) {
            return true;
        }
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            if ("authorization".equalsIgnoreCase(name)) {
                continue;
            }
            if (matches(request.getHeader(name))) {
                return true;
            }
        }
        for (String[] values : request.getParameterMap().values()) {
            if (values == null) continue;
            for (String value : values) {
                if (matches(value)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matches(String value) {
        return value != null && THREAT_PATTERN.matcher(value).find();
    }
}
