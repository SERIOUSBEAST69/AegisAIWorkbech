package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("model_call_stat")
public class ModelCallStat {
    @TableId
    private Long id;
    private Long modelId;
    private Long userId;
    private Date date;
    private Integer callCount;
    private Long totalLatencyMs;
    private Integer costCents;
}
