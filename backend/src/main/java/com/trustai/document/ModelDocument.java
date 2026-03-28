package com.trustai.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.time.LocalDateTime;

@Data
@Document(indexName = "ai_model")
public class ModelDocument {
    @Id
    private String id;
    private Long modelId;
    private String modelName;
    private String modelCode;
    private String provider;
    private String modelType;
    private String riskLevel;
    private String status;
    private String description;
    private LocalDateTime createTime;
}
