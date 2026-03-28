package com.trustai.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.trustai.entity.SystemConfig;
import com.trustai.repository.SystemConfigRepository;
import com.trustai.service.PrivacyShieldConfigService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class PrivacyShieldConfigServiceImpl implements PrivacyShieldConfigService {

    private static final String CONFIG_KEY = "privacy.shield.config";

    private final SystemConfigRepository systemConfigRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public Map<String, Object> getOrCreateConfig() {
        return systemConfigRepository.findByConfigKey(CONFIG_KEY)
                .map(this::parseConfigSafely)
                .orElseGet(() -> {
                    Map<String, Object> defaults = defaultConfig();
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
        long currentVersion = toLong(current.get("configVersion"), 1L);
        long requestedVersion = toLong(merged.get("configVersion"), currentVersion);
        merged.put("configVersion", Math.max(currentVersion + 1, requestedVersion));
        merged.put("updatedAt", System.currentTimeMillis());
        saveConfig(merged);
        return merged;
    }

    @Override
    @Transactional(readOnly = true)
    public long getConfigVersion() {
        return toLong(getOrCreateConfig().get("configVersion"), 1L);
    }

    private void saveConfig(Map<String, Object> config) {
        SystemConfig entity = systemConfigRepository.findByConfigKey(CONFIG_KEY).orElseGet(SystemConfig::new);
        String serialized;
        try {
            serialized = objectMapper.writeValueAsString(config);
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
            merged.put("configVersion", toLong(merged.get("configVersion"), 1L));
            merged.putIfAbsent("updatedAt", System.currentTimeMillis());
            return merged;
        } catch (Exception ex) {
            return defaultConfig();
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

        List<Map<String, Object>> selectors = new ArrayList<>();
        selectors.add(selector("chatgpt", List.of("chat.openai.com", "chatgpt.com"),
                List.of("#prompt-textarea", "textarea[data-testid='prompt-textarea']", "textarea")));
        selectors.add(selector("doubao", List.of("doubao.com", "www.doubao.com"),
                List.of("textarea", "div[contenteditable='true']", "[data-testid='chat-input']")));
        selectors.add(selector("yiyan", List.of("yiyan.baidu.com"),
                List.of("textarea", "div[contenteditable='true']", "#chat-input")));
        root.put("siteSelectors", selectors);

        Map<String, Object> windowRules = new LinkedHashMap<>();
        windowRules.put("titleKeywords", List.of("ChatGPT", "豆包", "文心一言", "Kimi", "通义千问"));
        windowRules.put("processNames", List.of("chrome", "msedge", "firefox", "doubao", "qqbrowser"));
        root.put("aiWindowRules", windowRules);
        return root;
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
}
