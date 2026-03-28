package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("role")
public class Role {
    @TableId
    private Long id;
    private Long companyId;
    private String name;
    private String code;
    private String description;
    private Date createTime;
    private Date updateTime;
}
