package com.trustai.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AiCallResponse {
    private String content;
    private Integer tokenUsage;
    private Long durationMs;
    private String provider;
    private String modelName;
}
