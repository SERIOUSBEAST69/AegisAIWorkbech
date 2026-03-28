-- TrustAI 数据治理平台核心表 DDL

CREATE TABLE `sys_user` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
  `company_id` BIGINT COMMENT '公司ID',
  `account_type` VARCHAR(20) DEFAULT 'demo' COMMENT '账号类型 demo/real',
  `account_status` VARCHAR(20) DEFAULT 'active' COMMENT '账号状态 pending/active/rejected/disabled',
  `username` VARCHAR(50) NOT NULL COMMENT '用户名',
  `password` VARCHAR(100) NOT NULL COMMENT '密码（加密存储）',
  `real_name` VARCHAR(50) COMMENT '真实姓名',
  `nickname` VARCHAR(50) COMMENT '昵称',
  `avatar` VARCHAR(255) COMMENT '头像地址',
  `role_id` BIGINT COMMENT '角色ID',
  `device_id` VARCHAR(128) COMMENT '设备ID',
  `department` VARCHAR(50) COMMENT '部门',
  `phone` VARCHAR(20) COMMENT '联系方式',
  `email` VARCHAR(100) COMMENT '邮箱',
  `status` TINYINT DEFAULT 1 COMMENT '状态 1-正常 0-禁用',
  `approved_by` BIGINT COMMENT '审批人ID',
  `reject_reason` VARCHAR(255) COMMENT '拒绝原因',
  `approved_at` DATETIME COMMENT '审批时间',
  `last_policy_pull_time` DATETIME COMMENT '最近策略拉取时间',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_username(`username`),
  INDEX idx_company(`company_id`),
  INDEX idx_role(`role_id`)
) COMMENT='系统用户表';

CREATE TABLE `company` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '公司ID',
  `company_code` VARCHAR(64) NOT NULL COMMENT '公司编码',
  `company_name` VARCHAR(128) NOT NULL COMMENT '公司名称',
  `status` TINYINT DEFAULT 1 COMMENT '状态 1-启用 0-禁用',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  UNIQUE KEY uk_company_code(`company_code`)
) COMMENT='公司表';

CREATE TABLE `role` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
  `company_id` BIGINT COMMENT '公司ID',
  `name` VARCHAR(50) NOT NULL COMMENT '角色名称',
  `code` VARCHAR(50) NOT NULL COMMENT '角色编码',
  `description` VARCHAR(200) COMMENT '描述',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_role_company(`company_id`),
  INDEX idx_role_company_code(`company_id`,`code`)
) COMMENT='角色表';

CREATE TABLE `permission` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '权限ID',
  `name` VARCHAR(50) NOT NULL COMMENT '权限名称',
  `code` VARCHAR(50) NOT NULL COMMENT '权限编码',
  `type` VARCHAR(20) COMMENT '类型（菜单/按钮/数据）',
  `parent_id` BIGINT DEFAULT NULL COMMENT '父权限ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='权限表';

CREATE TABLE `role_permission` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `role_id` BIGINT NOT NULL,
  `permission_id` BIGINT NOT NULL,
  INDEX idx_role(`role_id`),
  INDEX idx_permission(`permission_id`)
) COMMENT='角色-权限关联表';

CREATE TABLE `data_asset` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '数据资产ID',
  `company_id` BIGINT COMMENT '公司ID',
  `name` VARCHAR(100) NOT NULL COMMENT '资产名称',
  `type` VARCHAR(50) COMMENT '类型（MySQL/Excel/API等）',
  `sensitivity_level` VARCHAR(20) COMMENT '敏感等级（公开/内部/敏感/受限）',
  `location` VARCHAR(200) COMMENT '存储位置/连接信息',
  `discovery_time` DATETIME COMMENT '发现时间',
  `owner_id` BIGINT COMMENT '负责人ID',
  `lineage` TEXT COMMENT '数据血缘信息（JSON）',
  `description` VARCHAR(200) COMMENT '描述',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_type(`type`),
  INDEX idx_sensitivity(`sensitivity_level`)
) COMMENT='数据资产表';

CREATE TABLE `ai_model` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '模型ID',
  `model_name` VARCHAR(100) NOT NULL COMMENT '模型名称',
  `model_code` VARCHAR(50) NOT NULL COMMENT '模型编码',
  `provider` VARCHAR(50) COMMENT '供应商',
  `api_url` VARCHAR(200) COMMENT '调用地址',
  `api_key` VARCHAR(200) COMMENT '密钥（加密存储）',
  `model_type` VARCHAR(50) COMMENT '模型类型',
  `risk_level` VARCHAR(20) COMMENT '风险等级（低/中/高）',
  `status` VARCHAR(20) DEFAULT 'enabled' COMMENT '状态 enabled/disabled',
  `call_limit` INT DEFAULT 0 COMMENT '每日限额',
  `current_calls` INT DEFAULT 0 COMMENT '当前已调用次数',
  `description` VARCHAR(200) COMMENT '描述',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='AI模型表';

CREATE TABLE `ai_call_log` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
  `user_id` BIGINT DEFAULT NULL COMMENT '调用用户',
  `data_asset_id` BIGINT DEFAULT NULL COMMENT '关联数据资产ID',
  `model_id` BIGINT DEFAULT NULL COMMENT '模型ID',
  `model_code` VARCHAR(100) DEFAULT NULL COMMENT '模型代码',
  `provider` VARCHAR(50) DEFAULT NULL COMMENT '供应商',
  `input_preview` VARCHAR(200) DEFAULT NULL COMMENT '输入预览（已脱敏）',
  `output_preview` VARCHAR(200) DEFAULT NULL COMMENT '输出预览（已脱敏）',
  `status` VARCHAR(20) DEFAULT NULL COMMENT 'success/fail',
  `error_msg` VARCHAR(500) DEFAULT NULL COMMENT '失败原因',
  `duration_ms` BIGINT DEFAULT NULL COMMENT '耗时毫秒',
  `token_usage` INT DEFAULT NULL COMMENT 'token 用量',
  `ip` VARCHAR(64) DEFAULT NULL COMMENT '调用者IP',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_model_code_date(`model_code`, `create_time`),
  INDEX idx_user_date(`user_id`, `create_time`)
) COMMENT='AI 调用审计日志';

CREATE TABLE `audit_log` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '日志ID',
  `user_id` BIGINT COMMENT '用户ID',
  `asset_id` BIGINT COMMENT '资产ID',
  `operation` VARCHAR(50) COMMENT '操作类型',
  `operation_time` DATETIME COMMENT '操作时间',
  `ip` VARCHAR(50) COMMENT 'IP地址',
  `device` VARCHAR(100) COMMENT '设备信息',
  `input_overview` VARCHAR(200) COMMENT '输入概要（脱敏）',
  `output_overview` VARCHAR(200) COMMENT '输出概要（脱敏）',
  `result` VARCHAR(20) COMMENT '结果（成功/失败）',
  `risk_level` VARCHAR(20) COMMENT '风险等级',
  `hash` VARCHAR(128) COMMENT '哈希链/签名',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  INDEX idx_user(`user_id`),
  INDEX idx_asset(`asset_id`),
  INDEX idx_time(`operation_time`),
  INDEX idx_user_operation_time(`user_id`,`operation_time`)
) COMMENT='审计日志表';

CREATE TABLE `approval_request` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '审批单ID',
  `company_id` BIGINT COMMENT '公司ID',
  `applicant_id` BIGINT COMMENT '申请人ID',
  `asset_id` BIGINT COMMENT '资产ID',
  `reason` VARCHAR(200) COMMENT '申请理由',
  `status` VARCHAR(20) COMMENT '状态（待审批/通过/拒绝）',
  `approver_id` BIGINT COMMENT '审批人ID',
  `process_instance_id` VARCHAR(64) COMMENT '流程实例ID',
  `task_id` VARCHAR(64) COMMENT '当前任务ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_approval_company(`company_id`),
  INDEX idx_applicant(`applicant_id`),
  INDEX idx_asset(`asset_id`)
) COMMENT='访问审批单表';

CREATE TABLE `compliance_policy` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '策略ID',
  `company_id` BIGINT COMMENT '公司ID',
  `name` VARCHAR(100) NOT NULL COMMENT '策略名称',
  `rule_content` TEXT COMMENT '规则内容（JSON/IF-THEN）',
  `scope` VARCHAR(50) COMMENT '生效范围（全局/指定资产/模型）',
  `status` TINYINT DEFAULT 1 COMMENT '状态 1-启用 0-停用',
  `version` INT DEFAULT 1 COMMENT '版本号',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_policy_company(`company_id`)
) COMMENT='合规策略表';

CREATE TABLE `risk_event` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '风险事件ID',
  `company_id` BIGINT COMMENT '公司ID',
  `type` VARCHAR(50) COMMENT '事件类型',
  `level` VARCHAR(20) COMMENT '风险等级',
  `related_log_id` BIGINT COMMENT '关联日志ID',
  `audit_log_ids` VARCHAR(500) COMMENT '关联审计日志ID集合',
  `status` VARCHAR(20) COMMENT '状态（待处理/已处理）',
  `handler_id` BIGINT COMMENT '处置人ID',
  `process_log` TEXT COMMENT '处置记录',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  INDEX idx_type(`type`),
  INDEX idx_level(`level`),
  INDEX idx_risk_company_status_time(`company_id`,`status`,`create_time`)
) COMMENT='风险事件表';

-- 敏感数据扫描任务
CREATE TABLE `sensitive_scan_task` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
  `source_type` VARCHAR(20) COMMENT '来源类型：file/db',
  `source_path` VARCHAR(200) COMMENT '文件路径或库表',
  `status` VARCHAR(20) COMMENT '状态：pending/running/done/failed',
  `sensitive_ratio` DECIMAL(5,2) COMMENT '敏感占比百分比',
  `report_path` VARCHAR(200) COMMENT '报表存储路径',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='敏感数据扫描任务表';

-- AI 调用成本统计
CREATE TABLE `model_call_stat` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `model_id` BIGINT,
  `user_id` BIGINT,
  `date` DATE,
  `call_count` INT DEFAULT 0,
  `total_latency_ms` BIGINT DEFAULT 0,
  `cost_cents` INT DEFAULT 0,
  INDEX idx_model_date(`model_id`, `date`),
  INDEX idx_user_date(`user_id`, `date`)
) COMMENT='模型调用成本统计';

-- 数据主体权利申请工单
CREATE TABLE `subject_request` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `company_id` BIGINT COMMENT '公司ID',
  `user_id` BIGINT,
  `type` VARCHAR(30) COMMENT 'access/export/delete',
  `status` VARCHAR(20) COMMENT 'pending/processing/done/rejected',
  `comment` VARCHAR(200),
  `handler_id` BIGINT,
  `result` TEXT,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_subject_company(`company_id`)
) COMMENT='数据主体权利申请工单';

-- 脱敏规则
CREATE TABLE `desensitize_rule` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(100),
  `pattern` VARCHAR(100),
  `mask` VARCHAR(20),
  `example` VARCHAR(200),
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='脱敏规则定义';

CREATE TABLE `desense_recommend_rule` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `data_category` VARCHAR(50) COMMENT '数据分类：如身份证、银行卡',
  `user_role` VARCHAR(50) COMMENT '调用方角色：如admin/auditor',
  `strategy` VARCHAR(50) COMMENT '策略：mask/hash/tokenize',
  `rule_id` BIGINT COMMENT '关联脱敏规则ID',
  `priority` INT DEFAULT 0 COMMENT '优先级，越小越高',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_cat_role(`data_category`, `user_role`)
) COMMENT='脱敏推荐规则表';

CREATE TABLE `system_config` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `config_key` VARCHAR(128) NOT NULL UNIQUE COMMENT '配置键',
  `config_value` TEXT NOT NULL COMMENT '配置值',
  `description` VARCHAR(255) COMMENT '配置说明',
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) COMMENT='系统配置表';

CREATE TABLE `security_event` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `company_id` BIGINT COMMENT '公司ID',
  `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型',
  `file_path` VARCHAR(512) COMMENT '涉及文件路径',
  `target_addr` VARCHAR(256) COMMENT '目标地址（模拟远端）',
  `employee_id` VARCHAR(128) COMMENT '员工标识',
  `hostname` VARCHAR(128) COMMENT '主机名',
  `file_size` BIGINT COMMENT '文件大小（字节）',
  `severity` VARCHAR(20) DEFAULT 'medium' COMMENT 'critical/high/medium/low',
  `status` VARCHAR(20) DEFAULT 'pending' COMMENT 'pending/blocked/ignored/reviewing',
  `source` VARCHAR(64) DEFAULT 'agent' COMMENT '上报来源',
  `policy_version` BIGINT COMMENT '触发事件时策略版本',
  `operator_id` BIGINT COMMENT '操作者ID',
  `event_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_event_status(`status`),
  INDEX idx_event_severity(`severity`),
  INDEX idx_event_employee(`employee_id`),
  INDEX idx_event_time(`event_time`),
  INDEX idx_sec_company_status_time(`company_id`,`status`,`event_time`),
  INDEX idx_sec_company_severity_time(`company_id`,`severity`,`event_time`)
) COMMENT='安全事件表';

CREATE TABLE `privacy_event` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `company_id` BIGINT COMMENT '公司ID',
  `user_id` VARCHAR(128) NOT NULL COMMENT '用户标识（用户名）',
  `event_type` VARCHAR(64) DEFAULT 'SENSITIVE_TEXT' COMMENT '事件类型',
  `content_masked` TEXT COMMENT '脱敏后的内容',
  `source` VARCHAR(32) DEFAULT 'extension' COMMENT '来源：extension/clipboard',
  `action` VARCHAR(32) DEFAULT 'detect' COMMENT '动作：ignore/desensitize/detect',
  `device_id` VARCHAR(128) COMMENT '设备ID',
  `hostname` VARCHAR(128) COMMENT '主机名',
  `window_title` VARCHAR(255) COMMENT '窗口标题',
  `matched_types` VARCHAR(255) COMMENT '命中的敏感类型',
  `policy_version` BIGINT COMMENT '触发事件时策略版本',
  `event_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_privacy_company(`company_id`),
  INDEX idx_privacy_user(`user_id`),
  INDEX idx_privacy_source(`source`),
  INDEX idx_privacy_time(`event_time`),
  INDEX idx_privacy_company_time(`company_id`,`event_time`),
  INDEX idx_privacy_company_user_time(`company_id`,`user_id`,`event_time`)
) COMMENT='隐私盾事件表';

CREATE TABLE `governance_event` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `company_id` BIGINT COMMENT '公司ID',
  `user_id` BIGINT COMMENT '关联用户ID',
  `username` VARCHAR(128) COMMENT '关联用户名',
  `event_type` VARCHAR(64) NOT NULL COMMENT '统一事件类型',
  `source_module` VARCHAR(64) NOT NULL COMMENT '来源模块',
  `severity` VARCHAR(20) DEFAULT 'medium' COMMENT '风险等级',
  `status` VARCHAR(20) DEFAULT 'pending' COMMENT '处置状态',
  `title` VARCHAR(255) COMMENT '告警标题',
  `description` TEXT COMMENT '告警描述',
  `source_event_id` VARCHAR(64) COMMENT '来源事件ID',
  `attack_type` VARCHAR(64) COMMENT '映射攻防类型',
  `policy_version` BIGINT COMMENT '触发时策略版本',
  `payload_json` LONGTEXT COMMENT '扩展载荷',
  `handler_id` BIGINT COMMENT '处置人ID',
  `dispose_note` VARCHAR(500) COMMENT '处置备注',
  `event_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '事件时间',
  `disposed_at` DATETIME COMMENT '处置时间',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_gov_company(`company_id`),
  INDEX idx_gov_user(`user_id`),
  INDEX idx_gov_type(`event_type`),
  INDEX idx_gov_status(`status`),
  INDEX idx_gov_time(`event_time`)
) COMMENT='统一治理事件表';

CREATE TABLE `adversarial_record` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `company_id` BIGINT COMMENT '公司ID',
  `user_id` BIGINT COMMENT '关联用户ID',
  `username` VARCHAR(128) COMMENT '关联用户名',
  `governance_event_id` BIGINT COMMENT '触发来源告警ID',
  `scenario` VARCHAR(64) COMMENT '攻防场景',
  `policy_version` BIGINT COMMENT '验证时策略版本',
  `result_json` LONGTEXT COMMENT '战报结果',
  `effectiveness_analysis` LONGTEXT COMMENT '策略有效性分析',
  `suggestions_json` LONGTEXT COMMENT '优化建议',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_adv_company(`company_id`),
  INDEX idx_adv_user(`user_id`),
  INDEX idx_adv_event(`governance_event_id`)
) COMMENT='攻防验证记录表';

CREATE TABLE `security_detection_rule` (
  `id` BIGINT AUTO_INCREMENT PRIMARY KEY,
  `name` VARCHAR(100) NOT NULL,
  `sensitive_extensions` VARCHAR(500) DEFAULT '.docx,.pdf,.xlsx,.pptx,.key,.csv,.sql,.env,.pem,.pfx',
  `sensitive_paths` VARCHAR(1000) DEFAULT 'C:/Users,/home,/Documents,/Desktop',
  `alert_threshold_bytes` BIGINT DEFAULT 1048576,
  `enabled` TINYINT(1) DEFAULT 1,
  `description` VARCHAR(500),
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) COMMENT='安全检测规则表';
