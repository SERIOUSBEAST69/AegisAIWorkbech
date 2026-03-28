package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("desensitize_rule")
public class DesensitizeRule {
    @TableId
    private Long id;
    private String name;
    private String pattern;
    private String mask;
    private String example;
    private Date createTime;
    private Date updateTime;
}
