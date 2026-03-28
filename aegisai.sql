-- MySQL dump 10.13  Distrib 8.0.43, for Win64 (x86_64)
-- Host: localhost    Database: aegisai
-- ------------------------------------------------------
-- Server version 8.0.43

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `ai_call_log`
--

DROP TABLE IF EXISTS `ai_call_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_call_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint DEFAULT NULL COMMENT '调用用户',
  `data_asset_id` bigint DEFAULT NULL COMMENT '关联数据资产ID',
  `model_id` bigint DEFAULT NULL COMMENT '模型ID',
  `model_code` varchar(100) DEFAULT NULL COMMENT '模型代码',
  `provider` varchar(50) DEFAULT NULL COMMENT '供应商',
  `input_preview` varchar(200) DEFAULT NULL COMMENT '输入预览（已脱敏）',
  `output_preview` varchar(200) DEFAULT NULL COMMENT '输出预览（已脱敏）',
  `status` varchar(20) DEFAULT NULL COMMENT 'success/fail',
  `error_msg` varchar(500) DEFAULT NULL COMMENT '失败原因',
  `duration_ms` bigint DEFAULT NULL COMMENT '耗时毫秒',
  `token_usage` int DEFAULT NULL COMMENT 'token 用量',
  `ip` varchar(64) DEFAULT NULL COMMENT '调用者IP',
  `create_time` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_model_code_date` (`model_code`,`create_time`),
  KEY `idx_user_date` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI 调用审计日志';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ai_call_log` WRITE;
/*!40000 ALTER TABLE `ai_call_log` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_call_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_model`
--

DROP TABLE IF EXISTS `ai_model`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_model` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '模型ID',
  `model_name` varchar(100) DEFAULT NULL,
  `model_code` varchar(100) DEFAULT NULL,
  `model_type` varchar(30) DEFAULT NULL,
  `risk_level` varchar(20) DEFAULT NULL COMMENT '风险等级（低/中/高）',
  `api_url` varchar(255) DEFAULT NULL,
  `api_key` text,
  `provider` varchar(50) DEFAULT NULL,
  `status` varchar(20) DEFAULT NULL,
  `call_limit` int DEFAULT '0',
  `current_calls` int DEFAULT '0',
  `description` varchar(200) DEFAULT NULL COMMENT '描述',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=2028092187896475654 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI模型表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `ai_model` WRITE;
/*!40000 ALTER TABLE `ai_model` DISABLE KEYS */;
INSERT INTO `ai_model` VALUES (2028092187896475650,'1','model-2028092187896475650','1','1',NULL,'',NULL,'enabled',0,0,NULL,'2026-03-01 20:57:12','2026-03-06 22:13:40'),(2028092187896475651,'合规审计LLM','model-2028092187896475651','外部API','中','https://api.llm/audit','','团队A','enabled',0,0,'用于合规问答','2026-03-01 21:09:11','2026-03-06 22:13:40'),(2028092187896475652,'敏感识别模型','model-2028092187896475652','ONNX','高','onnx://models/sensitive.onnx','','团队B','enabled',0,0,'检测敏感字段','2026-03-01 21:09:11','2026-03-06 22:13:40'),(2028092187896475653,'成本预测模型','model-2028092187896475653','外部API','低','https://api.llm/cost','','团队C','enabled',0,0,'调用成本预测','2026-03-01 21:09:11','2026-03-06 22:13:40');
/*!40000 ALTER TABLE `ai_model` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `alert_record`
--

DROP TABLE IF EXISTS `alert_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `alert_record` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(100) DEFAULT NULL,
  `level` varchar(20) DEFAULT NULL,
  `status` varchar(20) DEFAULT NULL COMMENT 'open/claimed/resolved/archived',
  `assignee_id` bigint DEFAULT NULL,
  `related_log_id` bigint DEFAULT NULL,
  `resolution` text,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='告警记录';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `alert_record` WRITE;
/*!40000 ALTER TABLE `alert_record` DISABLE KEYS */;
INSERT INTO `alert_record` VALUES (1,'高危越权','高','claimed',2,1,'','2026-02-12 09:00:00','2026-03-07 00:15:32'),(2,'批量导出预警','中','claimed',2,2,'确认需求','2026-02-12 10:00:00','2026-03-01 21:09:11'),(3,'异常搜索词','低','resolved',2,3,'已教育用户','2026-02-12 11:00:00','2026-03-01 21:09:11');
/*!40000 ALTER TABLE `alert_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `approval_request`
--

DROP TABLE IF EXISTS `approval_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `approval_request` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '审批单ID',
  `company_id` bigint DEFAULT NULL COMMENT '公司ID',
  `applicant_id` bigint DEFAULT NULL COMMENT '申请人ID',
  `asset_id` bigint DEFAULT NULL COMMENT '资产ID',
  `reason` varchar(200) DEFAULT NULL COMMENT '申请事由',
  `status` varchar(20) DEFAULT NULL COMMENT '状态（待审批/通过/拒绝）',
  `approver_id` bigint DEFAULT NULL COMMENT '审批人ID',
  `process_instance_id` varchar(64) DEFAULT NULL COMMENT '流程实例ID',
  `task_id` varchar(64) DEFAULT NULL COMMENT '任务ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_approval_company` (`company_id`),
  KEY `idx_applicant` (`applicant_id`),
  KEY `idx_asset` (`asset_id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='访问审批单表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `approval_request` WRITE;
/*!40000 ALTER TABLE `approval_request` DISABLE KEYS */;
INSERT INTO `approval_request` VALUES (1,1,2,1,'调试合规审计','待审批',1,NULL,NULL,'2026-03-01 21:09:11','2026-03-01 21:09:11'),(2,1,3,2,'数据订正','通过',1,'PI_DEMO_001',NULL,'2026-03-01 21:09:11','2026-03-01 21:09:11'),(3,1,4,3,'审计复核','拒绝',1,'PI_DEMO_002',NULL,'2026-03-01 21:09:11','2026-03-01 21:09:11');
/*!40000 ALTER TABLE `approval_request` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `audit_log`
--

DROP TABLE IF EXISTS `audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_log` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID',
  `asset_id` bigint DEFAULT NULL COMMENT '资产ID',
  `operation` varchar(50) DEFAULT NULL COMMENT '操作类型',
  `operation_time` datetime DEFAULT NULL COMMENT '操作时间',
  `ip` varchar(50) DEFAULT NULL COMMENT 'IP地址',
  `device` varchar(100) DEFAULT NULL COMMENT '设备信息',
  `input_overview` varchar(200) DEFAULT NULL COMMENT '输入摘要（脱敏）',
  `output_overview` varchar(200) DEFAULT NULL COMMENT '输出摘要（脱敏）',
  `result` varchar(20) DEFAULT NULL COMMENT '结果（成功/失败）',
  `risk_level` varchar(20) DEFAULT NULL COMMENT '风险等级（NORMAL/LOW/MEDIUM/HIGH）',
  `hash` varchar(128) DEFAULT NULL COMMENT '哈希/签名',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_asset` (`asset_id`),
  KEY `idx_time` (`operation_time`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='审计日志表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `audit_log` WRITE;
/*!40000 ALTER TABLE `audit_log` DISABLE KEYS */;
INSERT INTO `audit_log` VALUES (1,1,1,'查询','2026-02-10 09:00:00','10.0.0.1','Chrome','uid=123','name=张*','成功','NORMAL','h1','2026-03-01 21:09:11'),(2,2,2,'导出','2026-02-10 10:00:00','10.0.0.2','Chrome','date=2026-02-01','file=pay.csv','成功','LOW','h2','2026-03-01 21:09:11'),(3,3,3,'索引检索','2026-02-10 11:00:00','10.0.0.3','Edge','kw=违规','hits=20','失败','MEDIUM','h3','2026-03-01 21:09:11');
/*!40000 ALTER TABLE `audit_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `compliance_policy`
--

DROP TABLE IF EXISTS `compliance_policy`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `compliance_policy` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '策略ID',
  `company_id` bigint DEFAULT NULL COMMENT '公司ID',
  `name` varchar(100) NOT NULL COMMENT '策略名称',
  `rule_content` text COMMENT '规则内容（JSON/IF-THEN）',
  `scope` varchar(50) DEFAULT NULL COMMENT '生效范围（全局/指定资产/模型）',
  `status` tinyint DEFAULT '1' COMMENT '状态 1-启用 0-禁用',
  `version` int DEFAULT '1' COMMENT '版本号',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_policy_company` (`company_id`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='合规策略表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `compliance_policy` WRITE;
/*!40000 ALTER TABLE `compliance_policy` DISABLE KEYS */;
INSERT INTO `compliance_policy` VALUES (1,1,'手机号脱敏','{"mask":"****"}','全局',1,1,'2026-03-01 21:09:11','2026-03-01 21:09:11'),(2,1,'支付导出审批','{"require_approval":true}','支付流水',1,1,'2026-03-01 21:09:11','2026-03-01 21:09:11');
/*!40000 ALTER TABLE `compliance_policy` ENABLE KEYS */;
UNLOCK TABLES;
--
-- Table structure for table `system_config`
--

DROP TABLE IF EXISTS `system_config`;
CREATE TABLE `system_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `config_key` varchar(128) NOT NULL COMMENT '配置键',
  `config_value` text COMMENT '配置值',
  `description` varchar(255) DEFAULT NULL COMMENT '描述',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置表';

LOCK TABLES `system_config` WRITE;
/*!40000 ALTER TABLE `system_config` DISABLE KEYS */;
/*!40000 ALTER TABLE `system_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `data_asset`
--
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `data_asset` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '数据资产ID',
  `name` varchar(100) NOT NULL COMMENT '资产名称',
  `type` varchar(50) DEFAULT NULL COMMENT '类型（MySQL/Excel/API等）',
  `sensitivity_level` varchar(20) DEFAULT NULL COMMENT '敏感等级（公开/内部/敏感/受限）',
  `location` varchar(200) DEFAULT NULL COMMENT '存储位置/连接信息',
  `discovery_time` datetime DEFAULT NULL COMMENT '发现时间',
  `owner_id` bigint DEFAULT NULL COMMENT '负责人ID',
  `lineage` text COMMENT '数据血缘信息（JSON）',
  `description` varchar(200) DEFAULT NULL COMMENT '描述',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_type` (`type`),
  KEY `idx_sensitivity` (`sensitivity_level`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据资产表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `data_asset` WRITE;
/*!40000 ALTER TABLE `data_asset` DISABLE KEYS */;
INSERT INTO `data_asset` VALUES (1,'用户表','MySQL','敏感','mysql://db/user_db.user','2026-02-01 10:00:00',3,'{"from":"ods.user","to":"dm.user_profile"}','核心用户数据','2026-03-01 21:09:11','2026-03-01 21:09:11'),(2,'支付流水','MySQL','受限','mysql://db/pay_db.txn','2026-02-02 11:00:00',3,'{"from":"ods.pay","to":"dm.pay_agg"}','支付交易流水','2026-03-01 21:09:11','2026-03-01 21:09:11'),(3,'聊天记录','ES','敏感','es://cluster/chat_index','2026-02-03 12:00:00',3,'{"from":"kafka.chat","to":"es.chat_index"}','IM 聊天索引','2026-03-01 21:09:11','2026-03-01 21:09:11'),(4,'风控画像','CSV','内部','/data/risk/profile.csv','2026-02-04 13:00:00',3,'{"from":"dm.user_profile","to":"risk.profile"}','风险标签','2026-03-01 21:09:11','2026-03-01 21:09:11');
/*!40000 ALTER TABLE `data_asset` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `data_share_request`
--

DROP TABLE IF EXISTS `data_share_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `data_share_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `asset_id` bigint DEFAULT NULL,
  `applicant_id` bigint DEFAULT NULL,
  `collaborators` varchar(200) DEFAULT NULL COMMENT '协作人ID列表',
  `reason` varchar(200) DEFAULT NULL COMMENT '申请原因',
  `status` varchar(20) DEFAULT NULL COMMENT 'pending/approved/rejected',
  `approver_id` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据资产共享审批';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `data_share_request` WRITE;
/*!40000 ALTER TABLE `data_share_request` DISABLE KEYS */;
INSERT INTO `data_share_request` VALUES (1,1,2,'3,4','联合分析','rejected',1,'2026-02-14 09:00:00','2026-03-07 00:11:40'),(2,2,3,'4','风控建模','rejected',1,'2026-02-14 10:00:00','2026-03-07 00:11:43'),(3,3,4,'2','审计复核','approved',1,'2026-02-14 11:00:00','2026-03-07 00:11:45');
/*!40000 ALTER TABLE `data_share_request` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `desensitize_rule`
--

DROP TABLE IF EXISTS `desensitize_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `desensitize_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `name` varchar(100) DEFAULT NULL,
  `pattern` varchar(100) DEFAULT NULL,
  `mask` varchar(20) DEFAULT NULL,
  `example` varchar(200) DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='脱敏规则定义';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `desensitize_rule` WRITE;
/*!40000 ALTER TABLE `desensitize_rule` DISABLE KEYS */;
INSERT INTO `desensitize_rule` VALUES (1,'手机号','\\d{11}','*','138****0000','2026-03-01 21:09:13','2026-03-01 21:09:13'),(2,'邮箱','[^@]+@[^@]+','*','u***@example.com','2026-03-01 21:09:13','2026-03-01 21:09:13'),(3,'身份证','\\d{18}','*','110***********1234','2026-03-01 21:09:13','2026-03-01 21:09:13');
/*!40000 ALTER TABLE `desensitize_rule` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `model_call_stat`
--

DROP TABLE IF EXISTS `model_call_stat`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `model_call_stat` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `model_id` bigint DEFAULT NULL,
  `user_id` bigint DEFAULT NULL,
  `date` date DEFAULT NULL,
  `call_count` int DEFAULT '0',
  `total_latency_ms` bigint DEFAULT '0',
  `cost_cents` int DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_model_date` (`model_id`,`date`),
  KEY `idx_user_date` (`user_id`,`date`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='模型调用成本统计';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `model_call_stat` WRITE;
/*!40000 ALTER TABLE `model_call_stat` DISABLE KEYS */;
INSERT INTO `model_call_stat` VALUES (1,1,1,'2026-02-10',120,240000,800),(2,1,2,'2026-02-10',80,160000,500),(3,2,3,'2026-02-10',60,90000,300),(4,3,1,'2026-02-10',40,60000,200);
/*!40000 ALTER TABLE `model_call_stat` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `permission`
--

DROP TABLE IF EXISTS `permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `permission` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '权限ID',
  `name` varchar(50) NOT NULL COMMENT '权限名称',
  `code` varchar(50) NOT NULL COMMENT '权限编码',
  `type` varchar(20) DEFAULT NULL COMMENT '类型（菜单/按钮/数据）',
  `parent_id` bigint DEFAULT NULL COMMENT '父权限ID',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='权限表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `permission` WRITE;
/*!40000 ALTER TABLE `permission` DISABLE KEYS */;
INSERT INTO `permission` VALUES (1,'查看资产','ASSET_VIEW','menu',NULL,'2026-03-01 21:09:11','2026-03-01 21:09:11'),(2,'编辑资产','ASSET_EDIT','button',NULL,'2026-03-01 21:09:11','2026-03-01 21:09:11'),(3,'查看审计','AUDIT_VIEW','menu',NULL,'2026-03-01 21:09:11','2026-03-01 21:09:11');
/*!40000 ALTER TABLE `permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `risk_event`
--

DROP TABLE IF EXISTS `risk_event`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `risk_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '风险事件ID',
  `type` varchar(50) DEFAULT NULL COMMENT '事件类型',
  `level` varchar(20) DEFAULT NULL COMMENT '风险等级',
  `related_log_id` bigint DEFAULT NULL COMMENT '关联日志ID',
  `status` varchar(20) DEFAULT NULL COMMENT '状态（待处理/已处理）',
  `handler_id` bigint DEFAULT NULL COMMENT '处理人ID',
  `process_log` text COMMENT '处理记录',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_type` (`type`),
  KEY `idx_level` (`level`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='风险事件表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `risk_event` WRITE;
/*!40000 ALTER TABLE `risk_event` DISABLE KEYS */;
INSERT INTO `risk_event` VALUES (2,'批量导出','中',2,'待处理',2,'待复核','2026-02-11 10:00:00','2026-03-01 21:09:11'),(3,'异常检索','低',3,'已处理',2,'提醒用户','2026-02-11 11:00:00','2026-03-01 21:09:11');
/*!40000 ALTER TABLE `risk_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `role`
--

DROP TABLE IF EXISTS `role`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '角色ID',
  `company_id` bigint DEFAULT NULL COMMENT '公司ID',
  `name` varchar(50) NOT NULL COMMENT '角色名称',
  `code` varchar(50) NOT NULL COMMENT '角色编码',
  `description` varchar(200) DEFAULT NULL COMMENT '描述',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_role_company` (`company_id`),
  KEY `idx_role_company_code` (`company_id`,`code`)
) ENGINE=InnoDB AUTO_INCREMENT=2028091268882526215 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `role` WRITE;
/*!40000 ALTER TABLE `role` DISABLE KEYS */;
INSERT INTO `role` VALUES (2028091268882526210,1,'管理员','ADMIN','系统默认管理员角色','2026-03-01 20:53:33','2026-03-01 20:53:33'),(2028091268882526211,1,'管理员','ADMIN','系统管理员','2026-03-01 21:09:11','2026-03-01 21:09:11'),(2028091268882526212,1,'安全官','SEC','安全合规负责人','2026-03-01 21:09:11','2026-03-01 21:09:11'),(2028091268882526213,1,'数据管理员','DATA_ADMIN','数据资产管理','2026-03-01 21:09:11','2026-03-01 21:09:11'),(2028091268882526214,1,'审计员','AUDIT','审计日志查看','2026-03-01 21:09:11','2026-03-01 21:09:11');
/*!40000 ALTER TABLE `role` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `role_permission`
--

DROP TABLE IF EXISTS `role_permission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `role_permission` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `role_id` bigint NOT NULL,
  `permission_id` bigint NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_role` (`role_id`),
  KEY `idx_permission` (`permission_id`)
) ENGINE=InnoDB AUTO_INCREMENT=10 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色-权限关联表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `role_permission` WRITE;
/*!40000 ALTER TABLE `role_permission` DISABLE KEYS */;
INSERT INTO `role_permission` VALUES (1,1,1),(2,1,2),(3,1,3),(4,1,4),(5,2,3),(6,2,4),(7,3,1),(8,3,2),(9,4,3);
/*!40000 ALTER TABLE `role_permission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sensitive_scan_task`
--

DROP TABLE IF EXISTS `sensitive_scan_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sensitive_scan_task` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `source_type` varchar(20) DEFAULT NULL COMMENT '来源类型：file/db',
  `source_path` varchar(200) DEFAULT NULL COMMENT '文件路径或库表',
  `status` varchar(20) DEFAULT NULL COMMENT '状态：pending/running/done/failed',
  `sensitive_ratio` decimal(5,2) DEFAULT NULL COMMENT '敏感占比百分比',
  `report_path` varchar(200) DEFAULT NULL COMMENT '报告存储路径',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='敏感数据扫描任务表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `sensitive_scan_task` WRITE;
/*!40000 ALTER TABLE `sensitive_scan_task` DISABLE KEYS */;
INSERT INTO `sensitive_scan_task` VALUES (1,'file','/data/users.xlsx','done',42.50,'/reports/task-1.pdf','2026-02-15 09:00:00','2026-02-15 10:00:00'),(2,'db','user_db.user','running',NULL,NULL,'2026-02-15 11:00:00','2026-02-15 11:05:00'),(3,'file','/data/chat.csv','failed',NULL,NULL,'2026-02-15 12:00:00','2026-02-15 12:10:00');
/*!40000 ALTER TABLE `sensitive_scan_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `subject_request`
--

DROP TABLE IF EXISTS `subject_request`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `subject_request` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `company_id` bigint DEFAULT NULL COMMENT '公司ID',
  `user_id` bigint DEFAULT NULL,
  `type` varchar(30) DEFAULT NULL COMMENT 'access/export/delete',
  `status` varchar(20) DEFAULT NULL COMMENT 'pending/processing/done/rejected',
  `comment` varchar(200) DEFAULT NULL COMMENT '备注',
  `handler_id` bigint DEFAULT NULL COMMENT '处理人ID',
  `result` text,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_subject_company` (`company_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2028095310606135299 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='数据主体权利请求工单';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `subject_request` WRITE;
/*!40000 ALTER TABLE `subject_request` DISABLE KEYS */;
INSERT INTO `subject_request` VALUES (2028092719054745602,1,NULL,'access','pending','',NULL,NULL,'2026-03-01 20:59:19','2026-03-01 20:59:19'),(2028092719054745603,1,10,'access','pending','请求访问个人数据',2,'','2026-02-13 09:00:00','2026-03-01 21:09:11'),(2028092719054745604,1,11,'delete','processing','删除历史记录',2,'处理中','2026-02-13 10:00:00','2026-03-01 21:09:11'),(2028092719054745605,1,12,'export','done','导出邮件',2,'已提供','2026-02-13 11:00:00','2026-03-01 21:09:11'),(2028095310606135298,1,NULL,'access','pending','',NULL,NULL,'2026-03-01 21:09:37','2026-03-01 21:09:36');
/*!40000 ALTER TABLE `subject_request` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `user`
--

DROP TABLE IF EXISTS `user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) NOT NULL COMMENT '用户名',
  `password` varchar(100) NOT NULL COMMENT '密码（加密存储）',
  `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名',
  `role_id` bigint DEFAULT NULL COMMENT '角色ID',
  `department` varchar(50) DEFAULT NULL COMMENT '部门',
  `phone` varchar(20) DEFAULT NULL COMMENT '联系方式',
  `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
  `status` tinyint DEFAULT '1' COMMENT '状态 1-正常 0-停用',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_username` (`username`),
  KEY `idx_role` (`role_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2028091269201293319 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统用户表';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `user` WRITE;
/*!40000 ALTER TABLE `user` DISABLE KEYS */;
INSERT INTO `user` VALUES (2028091269201293314,'admin','$2a$10$Od5yDqrMgBD/I8ldyJtdVOWqecZtK/D/J95OISN.x4rpi9IL/DtcC','平台管理员',2028091268882526210,NULL,NULL,NULL,1,'2026-03-01 20:53:33','2026-03-01 20:53:33'),(2028091269201293315,'admin','admin123','平台管理员',1,'合规部','13800000001','admin@example.com',1,'2026-03-01 21:09:11','2026-03-01 21:09:11'),(2028091269201293316,'sec01','secpass','安全官-张三',2,'安全部','13800000002','sec01@example.com',1,'2026-03-01 21:09:11','2026-03-01 21:09:11'),(2028091269201293317,'data01','datapass','数据管-李四',3,'数据部','13800000003','data01@example.com',1,'2026-03-01 21:09:11','2026-03-01 21:09:11'),(2028091269201293318,'audit01','auditpass','审计员-王五',4,'审计部','13800000004','audit01@example.com',1,'2026-03-01 21:09:11','2026-03-01 21:09:11');
/*!40000 ALTER TABLE `user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `client_report`
--

DROP TABLE IF EXISTS `client_report`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `client_report` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `company_id` bigint DEFAULT NULL COMMENT '公司ID',
  `client_id` varchar(64) NOT NULL COMMENT '客户端唯一标识（UUID）',
  `hostname` varchar(255) DEFAULT NULL COMMENT '主机名',
  `os_username` varchar(255) DEFAULT NULL COMMENT '操作系统用户名',
  `os_type` varchar(32) DEFAULT NULL COMMENT '操作系统类型',
  `client_version` varchar(32) DEFAULT NULL COMMENT '客户端版本号',
  `discovered_services` json DEFAULT NULL COMMENT '发现的AI服务列表（JSON数组）',
  `shadow_ai_count` int DEFAULT '0' COMMENT '影子AI服务数量',
  `risk_level` varchar(20) DEFAULT 'none' COMMENT '风险等级：none/low/medium/high',
  `scan_time` datetime DEFAULT NULL COMMENT '扫描时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_client_report_company` (`company_id`),
  KEY `idx_client_id` (`client_id`),
  KEY `idx_scan_time` (`scan_time`),
  KEY `idx_risk_level` (`risk_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='轻量级客户端扫描报告（影子AI发现）';
/*!40101 SET character_set_client = @saved_cs_client */;

LOCK TABLES `client_report` WRITE;
/*!40000 ALTER TABLE `client_report` DISABLE KEYS */;
/*!40000 ALTER TABLE `client_report` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `security_event`
--

DROP TABLE IF EXISTS `security_event`;
CREATE TABLE `security_event` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '事件ID',
  `company_id` bigint DEFAULT NULL COMMENT '公司ID',
  `event_type` varchar(50) DEFAULT NULL COMMENT '事件类型：FILE_STEAL/SUSPICIOUS_UPLOAD/BATCH_COPY/EXFILTRATION/DATA_SCRAPE/CREDENTIAL_DUMP',
  `file_path` varchar(500) DEFAULT NULL COMMENT '涉及文件路径',
  `target_addr` varchar(200) DEFAULT NULL COMMENT '目标地址',
  `employee_id` varchar(50) DEFAULT NULL COMMENT '员工标识',
  `hostname` varchar(100) DEFAULT NULL COMMENT '主机名',
  `file_size` bigint DEFAULT NULL COMMENT '文件大小（字节）',
  `severity` varchar(20) DEFAULT NULL COMMENT '风险等级：critical/high/medium/low',
  `status` varchar(20) DEFAULT 'pending' COMMENT '状态：pending/blocked/ignored/reviewing',
  `source` varchar(50) DEFAULT NULL COMMENT '上报来源：openclaw-sim/agent/manual',
  `operator_id` bigint DEFAULT NULL COMMENT '操作者ID',
  `event_time` datetime DEFAULT NULL COMMENT '事件发生时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_company` (`company_id`),
  KEY `idx_severity` (`severity`),
  KEY `idx_status` (`status`),
  KEY `idx_event_time` (`event_time`),
  KEY `idx_employee` (`employee_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全事件表（OpenClaw代理上报）';

LOCK TABLES `security_event` WRITE;
/*!40000 ALTER TABLE `security_event` DISABLE KEYS */;
/*!40000 ALTER TABLE `security_event` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `client_scan_queue`
--

DROP TABLE IF EXISTS `client_scan_queue`;
CREATE TABLE `client_scan_queue` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `company_id` bigint DEFAULT NULL COMMENT '公司ID',
  `platform` varchar(20) DEFAULT NULL COMMENT '平台：windows/macos/linux',
  `hostname` varchar(100) DEFAULT NULL COMMENT '主机名',
  `os_username` varchar(50) DEFAULT NULL COMMENT '操作系统用户名',
  `user_agent` varchar(200) DEFAULT NULL COMMENT '浏览器User-Agent',
  `status` varchar(20) DEFAULT 'queued' COMMENT '状态：queued/scanning/done/failed',
  `scan_result` text COMMENT '扫描结果（JSON）',
  `download_time` datetime DEFAULT NULL COMMENT '下载时间',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_client_queue_company` (`company_id`),
  KEY `idx_status` (`status`),
  KEY `idx_platform` (`platform`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='客户端扫描队列';

LOCK TABLES `client_scan_queue` WRITE;
/*!40000 ALTER TABLE `client_scan_queue` DISABLE KEYS */;
/*!40000 ALTER TABLE `client_scan_queue` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `desense_recommend_rule`
--

DROP TABLE IF EXISTS `desense_recommend_rule`;
CREATE TABLE `desense_recommend_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `data_category` varchar(50) DEFAULT NULL COMMENT '数据类别',
  `user_role` varchar(50) DEFAULT NULL COMMENT '用户角色',
  `strategy` varchar(50) DEFAULT NULL COMMENT '脱敏策略',
  `rule_id` bigint DEFAULT NULL COMMENT '关联脱敏规则ID',
  `priority` int DEFAULT '0' COMMENT '优先级',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_category` (`data_category`),
  KEY `idx_role` (`user_role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='脱敏推荐规则表';

LOCK TABLES `desense_recommend_rule` WRITE;
/*!40000 ALTER TABLE `desense_recommend_rule` DISABLE KEYS */;
/*!40000 ALTER TABLE `desense_recommend_rule` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `security_detection_rule`
--

DROP TABLE IF EXISTS `security_detection_rule`;
CREATE TABLE `security_detection_rule` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '规则ID',
  `name` varchar(100) DEFAULT NULL COMMENT '规则名称',
  `sensitive_extensions` varchar(500) DEFAULT NULL COMMENT '敏感文件类型（逗号分隔）',
  `sensitive_paths` text COMMENT '敏感目录（逗号分隔）',
  `alert_threshold_bytes` bigint DEFAULT NULL COMMENT '告警阈值（字节）',
  `enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用',
  `description` varchar(200) DEFAULT NULL COMMENT '描述',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_enabled` (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='安全检测规则表';

LOCK TABLES `security_detection_rule` WRITE;
/*!40000 ALTER TABLE `security_detection_rule` DISABLE KEYS */;
INSERT INTO `security_detection_rule` VALUES (1,'默认检测规则','.docx,.pdf,.xlsx,.csv,.sql,.bak,.pem,.pfx,.key,.env','/Documents,/Desktop,/HR,/backup,/export,/certs,/keys,/config',5242880,1,'默认安全检测规则','2026-03-01 21:09:11','2026-03-01 21:09:11');
/*!40000 ALTER TABLE `security_detection_rule` ENABLE KEYS */;
UNLOCK TABLES;

 /*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

 /*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
 /*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
 /*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
 /*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
 /*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
 /*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
 /*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-03-08 11:08:04