package com.trustai.dto.ai;

import java.util.List;
import lombok.Data;

@Data
public class AiBatchClassificationResponse {
    private List<AiClassificationResult> results;
}
