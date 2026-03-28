package com.trustai.config;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CrossSiteGuardService {

    @Value("${security.cross-site.guard-enabled:true}")
    private boolean guardEnabled;

    @Value("${security.cross-site.allowed-origins:http://localhost:5173,http://127.0.0.1:5173}")
    private String allowedOriginsRaw;

    private final AtomicLong blockedCount = new AtomicLong(0);
    private volatile Long lastBlockedAt = null;

    public boolean isGuardEnabled() {
        return guardEnabled;
    }

    public List<String> getAllowedOrigins() {
        return Arrays.stream(allowedOriginsRaw.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    public boolean isAllowedOrigin(String origin) {
        if (!guardEnabled) {
            return true;
        }
        if (!StringUtils.hasText(origin)) {
            return false;
        }
        return getAllowedOrigins().stream().anyMatch(allow -> allow.equalsIgnoreCase(origin.trim()));
    }

    public void markBlocked() {
        blockedCount.incrementAndGet();
        lastBlockedAt = System.currentTimeMillis();
    }

    public Map<String, Object> snapshot() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("enabled", guardEnabled);
        map.put("mode", guardEnabled ? "enforce" : "disabled");
        map.put("allowedOrigins", getAllowedOrigins());
        map.put("blockedCount", blockedCount.get());
        map.put("lastBlockedAt", lastBlockedAt);
        return map;
    }
}
