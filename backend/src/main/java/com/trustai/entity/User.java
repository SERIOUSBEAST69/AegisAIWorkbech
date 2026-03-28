package com.trustai.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.util.Date;

@Data
@TableName("sys_user")
public class User {
    @TableId
    private Long id;
    private Long companyId;
    private String accountType;
    private String accountStatus;
    private String username;
    private String password;
    private String realName;
    private String nickname;
    private String avatar;
    private Long roleId;
    private String deviceId;
    private String department;
    private String organizationType;
    private String loginType;
    private String wechatOpenId;
    private String phone;
    private String email;
    private Integer status;
    private Long approvedBy;
    private String rejectReason;
    private Date approvedAt;
    private Date lastPolicyPullTime;
    private Date createTime;
    private Date updateTime;
}
