package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

@Data
@TableName("desense_recommend_rule")
public class DesenseRecommendRule {
    @TableId
    private Long id;
    private String dataCategory;
    private String userRole;
    private String strategy;
    private Long ruleId;
    private Integer priority;
    private Date createTime;
    private Date updateTime;
}
