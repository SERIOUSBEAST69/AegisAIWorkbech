package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 审计每次模型调用的概要信息（同步写库，异步可写 ES）。
 */
@Data
@TableName("ai_call_log")
public class AiCallLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long dataAssetId;
    private Long modelId;
    private String modelCode;
    private String provider;
    private String inputPreview;
    private String outputPreview;
    private String status; // success / fail
    private String errorMsg;
    private Long durationMs;
    private Integer tokenUsage;
    private String ip;
    private LocalDateTime createTime;
}
