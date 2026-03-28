package com.trustai.dto;

import lombok.Data;

import java.util.Date;

@Data
public class DataAssetDto {
    private Long id;
    private String name;
    private String type;
    private String sensitivityLevel;
    private String location;
    private Date discoveryTime;
    private Long ownerId;
    private String lineage;
    private String description;
    private Date createTime;
    private Date updateTime;
}