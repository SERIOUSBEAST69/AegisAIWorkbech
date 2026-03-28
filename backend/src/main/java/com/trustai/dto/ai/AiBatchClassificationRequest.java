package com.trustai.dto.ai;

import java.util.List;
import lombok.Data;

@Data
public class AiBatchClassificationRequest {
    private List<String> texts;
}
