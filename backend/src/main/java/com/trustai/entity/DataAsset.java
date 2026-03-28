package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("data_asset")
public class DataAsset {
    @TableId
    private Long id;
    private Long companyId;
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
