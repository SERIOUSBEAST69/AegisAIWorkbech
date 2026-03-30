package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

@Data
@TableName("user_recycle_bin")
public class UserRecycleBin {
    @TableId
    private Long id;
    private Long companyId;
    private Long userId;
    private String username;
    private String snapshotJson;
    private Long deletedBy;
    private String deleteReason;
    private Date deletedAt;
    private String restoreStatus;
    private Long restoredBy;
    private Date restoredAt;
    private Date createTime;
    private Date updateTime;
}