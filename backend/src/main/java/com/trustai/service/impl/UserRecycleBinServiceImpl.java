package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.UserRecycleBin;
import com.trustai.mapper.UserRecycleBinMapper;
import com.trustai.service.UserRecycleBinService;
import org.springframework.stereotype.Service;

@Service
public class UserRecycleBinServiceImpl extends ServiceImpl<UserRecycleBinMapper, UserRecycleBin> implements UserRecycleBinService {
}
