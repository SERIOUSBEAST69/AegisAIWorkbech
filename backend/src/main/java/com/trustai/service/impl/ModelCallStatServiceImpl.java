package com.trustai.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.trustai.entity.ModelCallStat;
import com.trustai.mapper.ModelCallStatMapper;
import com.trustai.service.ModelCallStatService;
import org.springframework.stereotype.Service;

@Service
public class ModelCallStatServiceImpl extends ServiceImpl<ModelCallStatMapper, ModelCallStat> implements ModelCallStatService {
}
