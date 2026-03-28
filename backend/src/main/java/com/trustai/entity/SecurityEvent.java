package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("security_event")
public class SecurityEvent {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long companyId;

    /** 事件类型：FILE_STEAL / SUSPICIOUS_UPLOAD / BATCH_COPY / EXFILTRATION */
    private String eventType;

    /** 涉及文件路径 */
    private String filePath;

    /** 目标地址（模拟上传到的远端地址） */
    private String targetAddr;

    /** 员工标识（设备ID或用户名） */
    private String employeeId;

    /** 主机名 */
    private String hostname;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 风险等级：critical / high / medium / low */
    private String severity;

    /** 状态：pending / blocked / ignored / reviewing */
    private String status;

    /** 上报来源（如 openclaw-sim / agent / manual） */
    private String source;

    /** 告警触发时使用的策略版本 */
    private Long policyVersion;

    /** 操作者 ID（阻拦/忽略时记录） */
    private Long operatorId;

    /** 事件发生时间 */
    private Date eventTime;

    private Date createTime;
    private Date updateTime;
}
