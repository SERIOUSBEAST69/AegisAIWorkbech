package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

@Data
@TableName("tenant_health_report")
public class TenantHealthReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long companyId;
    private Date checkAt;
    private String permissionGapsJson;
    private Double auditCoverage;
    private Integer privacyDebtScore;
    private String riskMetricsJson;
    private String status;
    private Long createdBy;
    private Date createTime;
    private Date updateTime;
}
