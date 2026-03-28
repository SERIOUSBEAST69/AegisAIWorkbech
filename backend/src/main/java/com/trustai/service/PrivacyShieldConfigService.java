package com.trustai.service;

import java.util.Map;

public interface PrivacyShieldConfigService {

    Map<String, Object> getOrCreateConfig();

    Map<String, Object> updateConfig(Map<String, Object> newConfig);

    long getConfigVersion();
}
