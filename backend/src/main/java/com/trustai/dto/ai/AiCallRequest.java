package com.trustai.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class AiCallRequest {
    @NotBlank
    private String modelCode;
    private String prompt;
    private Long assetId;
    private String accessReason;
    @NotEmpty
    private List<AiMessage> messages;
}
