package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

@Data
@TableName("governance_event")
public class GovernanceEvent {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long companyId;
    private Long userId;
    private String username;

    private String eventType;
    private String sourceModule;
    private String severity;
    private String status;

    private String title;
    private String description;
    private String sourceEventId;
    private String attackType;
    private Long policyVersion;

    private String payloadJson;

    private Long handlerId;
    private String disposeNote;
    private Date eventTime;
    private Date disposedAt;

    private Date createTime;
    private Date updateTime;
}
