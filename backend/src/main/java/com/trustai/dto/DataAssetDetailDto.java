package com.trustai.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class DataAssetDetailDto extends DataAssetDto {
    private List<AiCallBriefDto> calls;
}