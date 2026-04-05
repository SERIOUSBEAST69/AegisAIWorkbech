package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("compliance_policy")
public class CompliancePolicy {
    @TableId
    private Long id;
    private Long companyId;
    private String name;
    private String policyType;
    private Integer priority;
    private String ruleContent;
    private String scope;
    private String scopeDepartments;
    private String scopeUserGroups;
    private String scopeDataTypes;
    private Integer status;
    private String lastModifier;
    private Date lastModifiedAt;
    private Integer version;
    private Date createTime;
    private Date updateTime;
}
