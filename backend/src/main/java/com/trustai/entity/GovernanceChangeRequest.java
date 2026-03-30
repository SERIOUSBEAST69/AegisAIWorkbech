package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

@Data
@TableName("governance_change_request")
public class GovernanceChangeRequest {
    @TableId
    private Long id;
    private Long companyId;
    private String module;
    private String action;
    private Long targetId;
    private String payloadJson;
    private String status;
    private String riskLevel;
    private Long requesterId;
    private String requesterRoleCode;
    private Long approverId;
    private String approverRoleCode;
    private String approveNote;
    private Date approvedAt;
    private Date createTime;
    private Date updateTime;
}
