package com.trustai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesenseRecommendationDto {
    private Long ruleId;
    private String name;
    private String pattern;
    private String mask;
    private String strategy;
    private Integer priority;
    private String reason;
}
