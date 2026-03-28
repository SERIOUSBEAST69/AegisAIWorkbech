package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

@Data
@TableName("company")
public class Company {
    @TableId
    private Long id;
    private String companyCode;
    private String companyName;
    private Integer status;
    private Date createTime;
    private Date updateTime;
}
