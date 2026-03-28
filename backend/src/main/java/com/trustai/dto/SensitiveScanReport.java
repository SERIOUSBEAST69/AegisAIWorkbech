package com.trustai.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SensitiveScanReport {
    private Summary summary;
    private List<Result> results;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private List<String> sensitiveFields;
        private Double ratio;
        private Integer totalSamples;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Result {
        private String text;
        private String label;
        private Double score;
    }
}
