package com.trustai.dto.dashboard;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TrustPulseDTO {
    private Integer score;
    private String pulseLevel;
    private String mission;
    private String innovationLabel;
    private List<Dimension> dimensions = new ArrayList<>();
    private List<Signal> signals = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Dimension {
        private String code;
        private String label;
        private Integer score;
        private String description;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Signal {
        private String title;
        private String value;
        private String tone;
        private String action;
    }
}