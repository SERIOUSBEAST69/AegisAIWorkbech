package com.trustai.dto;

import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

@Data
public class UserUpdateDTO {
    private String nickname;
    private String realName;
    private String email;
    private String phone;
    private String department;
    private MultipartFile avatar;
}
