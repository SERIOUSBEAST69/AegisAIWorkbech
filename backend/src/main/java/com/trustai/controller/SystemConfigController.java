package com.trustai.controller;

import com.trustai.dto.SystemConfigDTO;
import com.trustai.dto.SystemConfigRequest;
import com.trustai.service.CurrentUserService;
import com.trustai.service.SystemConfigService;
import com.trustai.utils.R;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/config")
@RequiredArgsConstructor
public class SystemConfigController {

    private final SystemConfigService systemConfigService;
    private final CurrentUserService currentUserService;

    @GetMapping
    public R<List<SystemConfigDTO>> list() {
        return R.ok(systemConfigService.findAll());
    }

    @GetMapping("/{key}")
    public R<SystemConfigDTO> get(@PathVariable String key) {
        return R.ok(systemConfigService.findByKey(key));
    }

    @PostMapping
    public R<SystemConfigDTO> create(@Valid @RequestBody SystemConfigRequest request) {
        currentUserService.requireAdmin();
        return R.ok(systemConfigService.create(request));
    }

    @PutMapping("/{key}")
    public R<SystemConfigDTO> update(@PathVariable String key, @Valid @RequestBody SystemConfigRequest request) {
        currentUserService.requireAdmin();
        return R.ok(systemConfigService.update(key, request));
    }
}
