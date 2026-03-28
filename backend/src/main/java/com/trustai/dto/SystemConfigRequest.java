package com.trustai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SystemConfigRequest {
    @NotBlank
    private String configKey;

    @NotBlank
    private String configValue;

    private String description;
}
