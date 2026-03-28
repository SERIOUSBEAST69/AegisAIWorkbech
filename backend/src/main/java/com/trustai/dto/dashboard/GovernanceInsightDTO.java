package com.trustai.dto.dashboard;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
public class GovernanceInsightDTO {
    private Integer postureScore;
    private Summary summary;
    private List<Highlight> highlights = new ArrayList<>();
    private List<Recommendation> recommendations = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Summary {
        private Long highSensitivityAssets;
        private Long openRiskEvents;
        private Long highRiskEvents;
        private Long highRiskModels;
        private Long pendingSubjectRequests;
        private Long todayAuditCount;
        private Long totalAiCalls;
        private Long totalCostCents;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Highlight {
        private String title;
        private String value;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Recommendation {
        private String code;
        private String priority;
        private String title;
        private String description;
        private String route;
        private String metric;
    }
}
