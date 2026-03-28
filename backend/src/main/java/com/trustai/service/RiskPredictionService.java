package com.trustai.service;

import java.util.List;

public interface RiskPredictionService {
    List<Double> forecastNext7Days();
}
