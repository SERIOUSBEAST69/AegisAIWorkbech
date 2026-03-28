package com.trustai.config;

import com.trustai.entity.SystemConfig;
import com.trustai.repository.SystemConfigRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(8)
@RequiredArgsConstructor
public class SystemConfigInitializer implements CommandLineRunner {

    private final SystemConfigRepository repository;

    @Override
    public void run(String... args) {
        Map<String, String> defaults = new LinkedHashMap<>();
        defaults.put("basic.system.name", "Aegis Workbench");
        defaults.put("basic.api.url", "http://localhost:8080/api");
        defaults.put("basic.backup.frequency", "daily");
        defaults.put("security.password.policy", "medium");
        defaults.put("security.login.attempts", "5");
        defaults.put("security.session.timeout", "30");
        defaults.put("notification.email.enabled", "true");
        defaults.put("notification.sms.enabled", "false");
        defaults.put("notification.system.enabled", "true");

        defaults.forEach(this::ensureConfig);
    }

    private void ensureConfig(String key, String value) {
        if (repository.findByConfigKey(key).isPresent()) {
            return;
        }
        SystemConfig config = new SystemConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setDescription("系统初始化默认配置");
        config.setCreatedAt(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        repository.save(config);
    }
}
