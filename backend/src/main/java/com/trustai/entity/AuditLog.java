package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("audit_log")
public class AuditLog {
    @TableId
    private Long id;
    private Long userId;
    private Long assetId;
    private String operation;
    private Date operationTime;
    private String ip;
    private String device;
    private String inputOverview;
    private String outputOverview;
    private String result;
    private String riskLevel; // NORMAL/LOW/MEDIUM/HIGH
    private String hash;
    private Date createTime;
}
