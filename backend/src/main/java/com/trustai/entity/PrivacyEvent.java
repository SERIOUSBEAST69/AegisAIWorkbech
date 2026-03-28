package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

@Data
@TableName("privacy_event")
public class PrivacyEvent {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long companyId;
    private String userId;
    private String eventType;
    private String contentMasked;
    private String source;
    private String action;
    private String severity;
    private String deviceId;
    private String hostname;
    private String windowTitle;
    private String matchedTypes;
    private Long policyVersion;
    private Date eventTime;
    private Date createTime;
    private Date updateTime;
}
