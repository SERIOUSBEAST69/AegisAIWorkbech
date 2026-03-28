package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("ai_model")
public class AiModel {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String modelName;
    private String modelCode;
    private String provider;
    private String apiUrl;
    /** 加密存储，真实值通过 AesEncryptor 解密 */
    private String apiKey;
    private String modelType; // chat/embedding/image
    private String riskLevel; // low/medium/high
    private String status;    // enabled/disabled
    private Integer callLimit; // 每日调用限额，0 表示不限
    @TableField("current_calls")
    private Integer currentCalls;
    private String description;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    @TableField(exist = false)
    private String name; // 兼容前端使用 name 字段的旧写法
}
