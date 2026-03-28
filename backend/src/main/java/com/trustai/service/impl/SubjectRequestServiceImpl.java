package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.SubjectRequest;
import com.trustai.mapper.SubjectRequestMapper;
import com.trustai.service.SubjectRequestService;
import org.springframework.stereotype.Service;

@Service
public class SubjectRequestServiceImpl extends ServiceImpl<SubjectRequestMapper, SubjectRequest> implements SubjectRequestService {
}
