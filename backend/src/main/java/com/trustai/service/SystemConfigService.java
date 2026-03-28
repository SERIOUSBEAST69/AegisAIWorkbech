package com.trustai.service;

import com.trustai.dto.SystemConfigDTO;
import com.trustai.dto.SystemConfigRequest;
import java.util.List;

public interface SystemConfigService {
    List<SystemConfigDTO> findAll();

    SystemConfigDTO findByKey(String key);

    SystemConfigDTO create(SystemConfigRequest request);

    SystemConfigDTO update(String key, SystemConfigRequest request);
}
