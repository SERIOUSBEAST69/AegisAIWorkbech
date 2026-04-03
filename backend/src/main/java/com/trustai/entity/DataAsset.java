package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
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

    @TableField(exist = false)
    private Integer diaScore;
    @TableField(exist = false)
    private String diaRiskLevel;
    @TableField(exist = false)
    private String diaFramework;
    @TableField(exist = false)
    private Date diaUpdatedAt;
}
