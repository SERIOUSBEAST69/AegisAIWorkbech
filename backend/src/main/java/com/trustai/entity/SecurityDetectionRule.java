package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("security_detection_rule")
public class SecurityDetectionRule {
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 规则名称 */
    private String name;

    /** 敏感文件类型（逗号分隔，如 .docx,.pdf,.xlsx） */
    private String sensitiveExtensions;

    /** 敏感目录（逗号分隔） */
    private String sensitivePaths;

    /** 告警阈值（单次传输超过该字节数触发告警） */
    private Long alertThresholdBytes;

    /** 是否启用 */
    private Boolean enabled;

    /** 描述 */
    private String description;

    private Date createTime;
    private Date updateTime;
}
