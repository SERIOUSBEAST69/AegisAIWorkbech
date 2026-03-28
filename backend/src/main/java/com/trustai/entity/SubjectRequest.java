package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("subject_request")
public class SubjectRequest {
    @TableId
    private Long id;
    private Long companyId;
    private Long userId;
    private String type; // access/export/delete
    private String status; // pending/processing/done/rejected
    private String comment;
    private Long handlerId;
    private String result;
    private Date createTime;
    private Date updateTime;
}
