package com.trustai.service.impl;

import com.trustai.dto.SystemConfigDTO;
import com.trustai.dto.SystemConfigRequest;
import com.trustai.entity.SystemConfig;
import com.trustai.exception.BizException;
import com.trustai.repository.SystemConfigRepository;
import com.trustai.service.SystemConfigService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemConfigServiceImpl implements SystemConfigService {

    private final SystemConfigRepository repository;

    @Override
    public List<SystemConfigDTO> findAll() {
        return repository.findAll().stream().map(this::toDto).collect(Collectors.toList());
    }

    @Override
    public SystemConfigDTO findByKey(String key) {
        SystemConfig entity = repository.findByConfigKey(key)
                .orElseThrow(() -> new BizException(40400, "配置不存在"));
        return toDto(entity);
    }

    @Override
    @Transactional
    public SystemConfigDTO create(SystemConfigRequest request) {
        repository.findByConfigKey(request.getConfigKey()).ifPresent(e -> {
            throw new BizException(40000, "配置键已存在");
        });
        SystemConfig entity = new SystemConfig();
        entity.setConfigKey(request.getConfigKey());
        entity.setConfigValue(request.getConfigValue());
        entity.setDescription(request.getDescription());
        LocalDateTime now = LocalDateTime.now();
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        repository.save(entity);
        return toDto(entity);
    }

    @Override
    @Transactional
    public SystemConfigDTO update(String key, SystemConfigRequest request) {
        SystemConfig entity = repository.findByConfigKey(key)
                .orElseThrow(() -> new BizException(40400, "配置不存在"));
        entity.setConfigValue(request.getConfigValue());
        entity.setDescription(request.getDescription());
        entity.setUpdatedAt(LocalDateTime.now());
        repository.save(entity);
        return toDto(entity);
    }

    private SystemConfigDTO toDto(SystemConfig entity) {
        return SystemConfigDTO.builder()
                .id(entity.getId())
                .configKey(entity.getConfigKey())
                .configValue(entity.getConfigValue())
                .description(entity.getDescription())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
