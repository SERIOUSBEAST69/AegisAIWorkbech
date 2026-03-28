package com.trustai.dto.ai;

import java.util.List;
import lombok.Data;

@Data
public class AiClassificationResult {
    private String label;
    private Double score;
    private List<LabelScore> labelScores;

    @Data
    public static class LabelScore {
        private String label;
        private Double score;
    }
}
