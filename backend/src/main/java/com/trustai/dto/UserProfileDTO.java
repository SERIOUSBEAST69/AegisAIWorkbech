package com.trustai.dto;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserProfileDTO {
    private Long id;
    private Long companyId;
    private String companyName;
    private String accountType;
    private String accountStatus;
    private String username;
    private String avatar;
    private String nickname;
    private String realName;
    private String email;
    private String phone;
    private String department;
    private String organizationType;
    private String loginType;
    private String roleName;
    private String roleCode;
    private List<Long> roleIds;
    private List<String> permissionCodes;
    private String deviceId;
    private LocalDateTime lastActiveAt;
}
