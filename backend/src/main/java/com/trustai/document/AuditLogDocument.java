package com.trustai.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.util.Date;

@Data
@Document(indexName = "audit_log")
public class AuditLogDocument {
    @Id
    private String id;
    private Long logId;
    private Long userId;
    private Long assetId;
    private String operation;
    private Date operationTime;
    private String ip;
    private String device;
    private String inputOverview;
    private String outputOverview;
    private String result;
    private String riskLevel;
    private String hash;
    @Field(type = FieldType.Date)
    private Date createTime;
}
