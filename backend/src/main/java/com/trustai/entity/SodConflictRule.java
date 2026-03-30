package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

@Data
@TableName("sod_conflict_rule")
public class SodConflictRule {
    @TableId
    private Long id;
    private Long companyId;
    private String scenario;
    private String roleCodeA;
    private String roleCodeB;
    private Integer enabled;
    private String description;
    private Date createTime;
    private Date updateTime;
}
