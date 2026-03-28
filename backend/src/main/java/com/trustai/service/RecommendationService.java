package com.trustai.service;

import com.trustai.dto.DesenseRecommendationDto;
import java.util.List;

public interface RecommendationService {
    List<DesenseRecommendationDto> recommend(String dataCategory, String userRole, String sensitivityLevel);
}
