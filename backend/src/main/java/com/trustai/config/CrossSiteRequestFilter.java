package com.trustai.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.utils.R;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CrossSiteRequestFilter extends OncePerRequestFilter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CrossSiteGuardService crossSiteGuardService;

    public CrossSiteRequestFilter(CrossSiteGuardService crossSiteGuardService) {
        this.crossSiteGuardService = crossSiteGuardService;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (!crossSiteGuardService.isGuardEnabled()) {
            return true;
        }
        if (!StringUtils.hasText(uri) || !uri.startsWith("/api/")) {
            return true;
        }
        return "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!isBrowserLikeRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        String secFetchSite = trimToNull(request.getHeader("Sec-Fetch-Site"));
        String origin = trimToNull(request.getHeader("Origin"));
        String referer = trimToNull(request.getHeader("Referer"));
        String refererOrigin = extractOrigin(referer);

        if ("cross-site".equalsIgnoreCase(secFetchSite)) {
            deny(response, request, "SEC_FETCH_SITE_CROSS_SITE", origin, refererOrigin);
            return;
        }

        if (StringUtils.hasText(origin) && !crossSiteGuardService.isAllowedOrigin(origin)) {
            deny(response, request, "ORIGIN_NOT_ALLOWED", origin, refererOrigin);
            return;
        }

        if (!StringUtils.hasText(origin) && StringUtils.hasText(refererOrigin)
                && !crossSiteGuardService.isAllowedOrigin(refererOrigin)) {
            deny(response, request, "REFERER_ORIGIN_NOT_ALLOWED", origin, refererOrigin);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isBrowserLikeRequest(HttpServletRequest request) {
        return StringUtils.hasText(request.getHeader("Origin"))
                || StringUtils.hasText(request.getHeader("Referer"))
                || StringUtils.hasText(request.getHeader("Sec-Fetch-Site"));
    }

    private void deny(HttpServletResponse response,
                      HttpServletRequest request,
                      String reason,
                      String origin,
                      String refererOrigin) throws IOException {
        crossSiteGuardService.markBlocked();

        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("reason", reason);
        detail.put("method", request.getMethod());
        detail.put("path", request.getRequestURI());
        detail.put("origin", origin);
        detail.put("refererOrigin", refererOrigin);
        detail.put("allowedOrigins", crossSiteGuardService.getAllowedOrigins());

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(MAPPER.writeValueAsString(
                new R<>(40310, "跨站请求已被 AegisAI 防护拦截", detail, System.currentTimeMillis())
        ));
    }

    private String extractOrigin(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return null;
        }
        try {
            URI uri = URI.create(rawUrl);
            if (!StringUtils.hasText(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
                return null;
            }
            int port = uri.getPort();
            if (port <= 0) {
                return uri.getScheme() + "://" + uri.getHost();
            }
            return uri.getScheme() + "://" + uri.getHost() + ":" + port;
        } catch (Exception ignored) {
            return null;
        }
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
