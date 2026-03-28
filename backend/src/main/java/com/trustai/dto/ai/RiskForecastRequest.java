package com.trustai.dto.ai;

import java.util.List;
import lombok.Data;

@Data
public class RiskForecastRequest {
    private List<Double> series;
    private Integer horizon = 7;
}
