package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("sensitive_scan_task")
public class SensitiveScanTask {
    @TableId
    private Long id;
    private Long assetId;
    private String sourceType; // file/db
    private String sourcePath;
    private String status; // pending/running/done/failed
    private Double sensitiveRatio;
    private String reportPath;
    private String reportData;
    private Date createTime;
    private Date updateTime;
}
