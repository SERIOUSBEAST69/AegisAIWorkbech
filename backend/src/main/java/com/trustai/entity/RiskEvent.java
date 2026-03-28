package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("risk_event")
public class RiskEvent {
    @TableId
    private Long id;
    private Long companyId;
    private String type;
    private String level;
    private Long relatedLogId;
    private String auditLogIds;
    private String status;
    private Long handlerId;
    private String processLog;
    private Date createTime;
    private Date updateTime;
}
