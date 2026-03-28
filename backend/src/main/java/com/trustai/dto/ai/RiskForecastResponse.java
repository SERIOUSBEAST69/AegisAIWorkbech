package com.trustai.dto.ai;

import java.util.List;
import lombok.Data;

@Data
public class RiskForecastResponse {
    private List<Double> forecast;
}
