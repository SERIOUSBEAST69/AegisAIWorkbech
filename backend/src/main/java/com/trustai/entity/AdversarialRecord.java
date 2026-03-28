package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

@Data
@TableName("adversarial_record")
public class AdversarialRecord {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long companyId;
    private Long userId;
    private String username;

    private Long governanceEventId;
    private String scenario;
    private Long policyVersion;

    private String resultJson;
    private String effectivenessAnalysis;
    private String suggestionsJson;

    private Date createTime;
    private Date updateTime;
}
