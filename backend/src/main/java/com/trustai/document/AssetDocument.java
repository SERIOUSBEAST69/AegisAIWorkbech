package com.trustai.document;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.Date;

@Data
@Document(indexName = "data_asset")
public class AssetDocument {
    @Id
    private String id;
    private Long assetId;
    private String name;
    private String type;
    private String sensitivityLevel;
    private String location;
    private String description;
    private Date createTime;
}
