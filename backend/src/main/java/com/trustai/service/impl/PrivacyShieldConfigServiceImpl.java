package com.trustai.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.entity.SystemConfig;
import com.trustai.repository.SystemConfigRepository;
import com.trustai.service.PrivacyShieldConfigService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PrivacyShieldConfigServiceImpl implements PrivacyShieldConfigService {

    private static final String CONFIG_KEY = "privacy.shield.config";
    private static final List<String> COMPETITION_AI_CATALOG = List.of(
        "阿里通义系列",
        "百度文心系列",
        "DeepSeek",
        "豆包 AI",
        "腾讯混元系列"
    );
    private static final List<String> DEFAULT_AI_WHITELIST = List.of(
        "阿里通义系列",
        "百度文心系列",
        "DeepSeek",
        "豆包 AI",
        "腾讯混元系列"
    );

    private final SystemConfigRepository systemConfigRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public Map<String, Object> getOrCreateConfig() {
        return systemConfigRepository.findByConfigKey(CONFIG_KEY)
                .map(this::parseConfigSafely)
                .orElseGet(() -> {
                    Map<String, Object> defaults = stampConfig(defaultConfig());
                    saveConfig(defaults);
                    return defaults;
                });
    }

    @Override
    @Transactional
    public Map<String, Object> updateConfig(Map<String, Object> newConfig) {
        Map<String, Object> current = getOrCreateConfig();
        Map<String, Object> merged = new LinkedHashMap<>(defaultConfig());
        merged.putAll(current);
        if (newConfig != null) {
            merged.putAll(newConfig);
        }
        merged = sanitizeCompetitionConfig(merged);
        long currentVersion = toLong(current.get("configVersion"), 1L);
        long requestedVersion = toLong(merged.get("configVersion"), currentVersion);
        merged.put("configVersion", Math.max(currentVersion + 1, requestedVersion));
        merged.put("updatedAt", System.currentTimeMillis());
        merged = stampConfig(merged);
        saveConfig(merged);
        return merged;
    }

    @Override
    @Transactional(readOnly = true)
    public long getConfigVersion() {
        return toLong(getOrCreateConfig().get("configVersion"), 1L);
    }

    @Override
    @Transactional(readOnly = true)
    public String getConfigChecksum() {
        return String.valueOf(getOrCreateConfig().getOrDefault("configChecksum", ""));
    }

    private void saveConfig(Map<String, Object> config) {
        Map<String, Object> normalized = stampConfig(config);
        SystemConfig entity = systemConfigRepository.findByConfigKey(CONFIG_KEY).orElseGet(SystemConfig::new);
        String serialized;
        try {
            serialized = objectMapper.writeValueAsString(normalized);
        } catch (Exception ex) {
            serialized = "{}";
        }

        LocalDateTime now = LocalDateTime.now();
        if (entity.getId() == null) {
            entity.setConfigKey(CONFIG_KEY);
            entity.setCreatedAt(now);
        }
        entity.setConfigValue(serialized);
        entity.setDescription("隐私盾配置");
        entity.setUpdatedAt(now);
        systemConfigRepository.save(entity);
    }

    private Map<String, Object> parseConfigSafely(SystemConfig config) {
        if (config == null || !StringUtils.hasText(config.getConfigValue())) {
            return defaultConfig();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(config.getConfigValue(), new TypeReference<>() {
            });
            Map<String, Object> merged = new LinkedHashMap<>(defaultConfig());
            if (parsed != null) {
                merged.putAll(parsed);
            }
            merged = sanitizeCompetitionConfig(merged);
            merged.put("configVersion", toLong(merged.get("configVersion"), 1L));
            merged.putIfAbsent("updatedAt", System.currentTimeMillis());
            return stampConfig(merged);
        } catch (Exception ex) {
            return stampConfig(defaultConfig());
        }
    }

    private Map<String, Object> defaultConfig() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("monitorEnabled", true);
        root.put("predictEnabled", true);
        root.put("predictEndpoint", "http://localhost:5000/predict");
        root.put("dedupeSeconds", 60);
        root.put("configVersion", 1L);
        root.put("updatedAt", System.currentTimeMillis());
        root.put("syncIntervalSec", 60);
        root.put("sensitiveKeywords", List.of("身份证", "银行卡", "手机号", "公司代码"));
        root.put("aiCatalog", COMPETITION_AI_CATALOG);
        root.put("aiWhitelist", DEFAULT_AI_WHITELIST);
        root.put("desenseGlobalEnabled", true);
        root.put("desenseRules", Map.of(
            "phone", Map.of("enabled", true, "pattern", "(1\\\\d{2})\\\\d{4}(\\\\d{4})", "format", "$1****$2"),
            "idCard", Map.of("enabled", true, "pattern", "(\\\\d{6})\\\\d{8}(\\\\d{3}[\\\\dXx])", "format", "$1********$2"),
            "email", Map.of("enabled", true, "pattern", "([A-Za-z0-9._%+-]{2})([A-Za-z0-9._%+-]*)([A-Za-z0-9._%+-]{2})@([A-Za-z0-9.-]+\\\\.[A-Za-z]{2,})", "format", "$1****$3@$4")
        ));

        List<Map<String, Object>> selectors = new ArrayList<>();
        selectors.add(selector("tongyi", List.of("qianwen.aliyun.com", "tongyi.aliyun.com", "dashscope.aliyuncs.com"),
                List.of("textarea", "div[contenteditable='true']")));
        selectors.add(selector("doubao", List.of("doubao.com", "www.doubao.com"),
                List.of("textarea", "div[contenteditable='true']", "[data-testid='chat-input']")));
        selectors.add(selector("yiyan", List.of("yiyan.baidu.com"),
                List.of("textarea", "div[contenteditable='true']", "#chat-input")));
        root.put("siteSelectors", selectors);

        Map<String, Object> windowRules = new LinkedHashMap<>();
        windowRules.put("titleKeywords", List.of("通义", "文心", "DeepSeek", "豆包", "混元"));
        windowRules.put("processNames", List.of("chrome", "msedge", "firefox", "doubao", "qqbrowser"));
        root.put("aiWindowRules", windowRules);
        return root;
    }

    private Map<String, Object> sanitizeCompetitionConfig(Map<String, Object> raw) {
        Map<String, Object> sanitized = new LinkedHashMap<>(raw == null ? Map.of() : raw);
        sanitized.put("aiCatalog", COMPETITION_AI_CATALOG);

        Set<String> catalog = new LinkedHashSet<>(COMPETITION_AI_CATALOG);
        List<String> rawWhitelist = toStringList(sanitized.get("aiWhitelist"));
        List<String> whitelist = new ArrayList<>();
        for (String item : rawWhitelist) {
            if (catalog.contains(item) && !whitelist.contains(item)) {
                whitelist.add(item);
            }
        }
        if (whitelist.isEmpty()) {
            for (String candidate : DEFAULT_AI_WHITELIST) {
                if (!whitelist.contains(candidate) && catalog.contains(candidate)) {
                    whitelist.add(candidate);
                }
            }
        }
        sanitized.put("aiWhitelist", whitelist);
        return sanitized;
    }

    private List<String> toStringList(Object value) {
        if (value instanceof List<?> list) {
            List<String> out = new ArrayList<>();
            for (Object item : list) {
                String normalized = String.valueOf(item == null ? "" : item).trim();
                if (StringUtils.hasText(normalized)) {
                    out.add(normalized);
                }
            }
            return out;
        }
        return List.of();
    }

    private Map<String, Object> selector(String siteId, List<String> hosts, List<String> inputs) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("siteId", siteId);
        item.put("hosts", hosts);
        item.put("inputSelectors", inputs);
        return item;
    }

    private long toLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private Map<String, Object> stampConfig(Map<String, Object> config) {
        Map<String, Object> normalized = new LinkedHashMap<>(config == null ? Map.of() : config);
        normalized.remove("changed");
        normalized.remove("configChecksum");
        normalized.put("configChecksum", checksumOf(normalized));
        return normalized;
    }

    private String checksumOf(Map<String, Object> config) {
        try {
            String serialized = objectMapper.writeValueAsString(config == null ? Map.of() : config);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(serialized.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hashed.length * 2);
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception ex) {
            return "";
        }
    }
}
