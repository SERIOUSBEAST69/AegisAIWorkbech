package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("permission")
public class Permission {
    @TableId
    private Long id;
    private String name;
    private String code;
    private String type;
    private Long parentId;
    private Date createTime;
    private Date updateTime;
}
