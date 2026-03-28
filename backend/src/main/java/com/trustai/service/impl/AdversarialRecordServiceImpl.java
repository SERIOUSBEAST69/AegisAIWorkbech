package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.AdversarialRecord;
import com.trustai.mapper.AdversarialRecordMapper;
import com.trustai.service.AdversarialRecordService;
import org.springframework.stereotype.Service;

@Service
public class AdversarialRecordServiceImpl extends ServiceImpl<AdversarialRecordMapper, AdversarialRecord>
    implements AdversarialRecordService {
}
