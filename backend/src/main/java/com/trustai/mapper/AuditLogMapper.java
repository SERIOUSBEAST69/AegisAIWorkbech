package com.trustai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.trustai.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
}
