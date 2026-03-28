package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("approval_request")
public class ApprovalRequest {
    @TableId
    private Long id;
    private Long companyId;
    private Long applicantId;
    private Long assetId;
    private String reason;
    private String status;
    private Long approverId;
    private String processInstanceId;
    private String taskId;
    private Date createTime;
    private Date updateTime;
}
