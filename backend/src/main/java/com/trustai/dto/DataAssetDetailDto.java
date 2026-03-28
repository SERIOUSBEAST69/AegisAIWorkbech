package com.trustai.dto;

import lombok.Data;

import java.util.List;

@Data
public class DataAssetDetailDto extends DataAssetDto {
    private List<AiCallBriefDto> calls;
}