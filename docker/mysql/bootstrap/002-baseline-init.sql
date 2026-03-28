INSERT INTO system_config (config_key, config_value, description, created_at, updated_at)
SELECT 'basic.system.name', 'Aegis Workbench', '系统名称', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'basic.system.name');

INSERT INTO system_config (config_key, config_value, description, created_at, updated_at)
SELECT 'security.session.timeout', '30', '会话超时时间（分钟）', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'security.session.timeout');

INSERT INTO system_config (config_key, config_value, description, created_at, updated_at)
SELECT 'notification.system.enabled', 'true', '是否启用系统通知', NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM system_config WHERE config_key = 'notification.system.enabled');
