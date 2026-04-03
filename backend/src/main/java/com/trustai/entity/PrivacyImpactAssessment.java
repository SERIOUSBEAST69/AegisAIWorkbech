package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

@Data
@TableName("privacy_impact_assessment")
public class PrivacyImpactAssessment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long companyId;
    private Long assetId;
    private String framework;
    private Integer impactScore;
    private String riskLevel;
    private String riskFactorsJson;
    private Long assessedBy;
    private Date createTime;
    private Date updateTime;
}
