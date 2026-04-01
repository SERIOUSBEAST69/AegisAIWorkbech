package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

@Data
@TableName("user_role")
public class UserRole {
    @TableId
    private Long id;
    private Long userId;
    private Long roleId;
    private Date createTime;
    private Date updateTime;
}
