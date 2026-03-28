-- ai_model：统一模型配置
CREATE TABLE IF NOT EXISTS ai_model (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    model_name VARCHAR(100) NOT NULL COMMENT '模型显示名称',
    model_code VARCHAR(100) NOT NULL UNIQUE COMMENT '模型代码',
    provider VARCHAR(50) NOT NULL COMMENT '供应商，如 deepseek/qwen/wenxin',
    api_url VARCHAR(255) NOT NULL COMMENT 'API 地址',
    api_key TEXT NOT NULL COMMENT '加密存储的 API 密钥',
    model_type VARCHAR(30) NOT NULL COMMENT 'chat/embedding/image',
    risk_level VARCHAR(20) DEFAULT 'low' COMMENT '风险等级',
    status VARCHAR(20) DEFAULT 'enabled' COMMENT 'enabled/disabled',
    call_limit INT DEFAULT 0 COMMENT '每日调用限额，0 表示不限',
    current_calls INT DEFAULT 0 COMMENT '当日已调用次数',
    description VARCHAR(512) COMMENT '描述',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_model_code (model_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 模型配置';

-- ai_call_log：调用审计
CREATE TABLE IF NOT EXISTS ai_call_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id BIGINT COMMENT '调用用户',
    data_asset_id BIGINT DEFAULT NULL COMMENT '关联数据资产ID',
    model_id BIGINT COMMENT '模型ID',
    model_code VARCHAR(100) COMMENT '模型代码',
    provider VARCHAR(50) COMMENT '供应商',
    input_preview VARCHAR(200) COMMENT '输入预览（已脱敏）',
    output_preview VARCHAR(200) COMMENT '输出预览（已脱敏）',
    status VARCHAR(20) COMMENT 'success/fail',
    error_msg VARCHAR(500) COMMENT '失败原因',
    duration_ms BIGINT COMMENT '耗时毫秒',
    token_usage INT COMMENT 'token 用量',
    ip VARCHAR(64) COMMENT '调用者IP',
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_model_code_date (model_code, create_time),
    INDEX idx_user_date (user_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI 调用审计日志';
