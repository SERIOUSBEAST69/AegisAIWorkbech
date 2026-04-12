package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

@Data
@TableName("simulation_events")
public class SimulationEvent {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long companyId;
    private String eventType;
    private String targetKey;
    private String severity;
    private String status;
    private String source;
    private String triggerUser;
    private String payloadJson;
    private String processedBy;
    private Date processedAt;
    private Date createTime;
    private Date updateTime;
}
