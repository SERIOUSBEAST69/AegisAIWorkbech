# Aegis AI安全治理与对抗防御平台（AegisWorkbench）
## 中国大学生计算机设计大赛 软件开发类 作品开发文档（实施稿）

作者团队：AegisWorkbench 项目组  
完成日期：2026-04-07  
版本：V1.0（实施稿，待按 PDF 模板做最终版式对齐）

---

## 版式执行说明（Word 落地）
1. 一级标题：二号黑体，加粗，段前 12 磅，段后 6 磅。
2. 二级标题：三号黑体；三级标题：四号黑体。
3. 正文：五号宋体，1.5 倍行距，首行缩进 2 字符。
4. 英文与数字：Times New Roman 五号。
5. 图题：五号黑体，格式“图X-序号 名称”；置于图下方居中。
6. 表题：五号黑体，格式“表X-序号 名称”；置于表上方居中。
7. 表格：全边框 0.5 磅；表头浅灰底（10%）；内容五号宋体。
8. 页码：页脚居中，阿拉伯数字连续。

---

## 摘要
Aegis AI 安全治理与对抗防御平台（AegisWorkbench）面向企业级 AI 治理场景，围绕“可见、可控、可审计、可追溯”四个目标，构建了从客户端采集到治理闭环的全链路系统。平台覆盖基础平台、组织权限、治理主线、AI 核心能力、攻防与运维五大域，重点实现了多租户隔离（company_id）、细粒度 RBAC 权限、治理管理员与治理复核员职责分离、影子 AI 发现、模型漂移监测、AI 风险评级（LSTM 时序预测）、审计哈希链防篡改与外部锚定等能力。

本项目已完成完整工程化落地，具备统一治理事件归集、审批流转、处置闭环、审计追溯与证据归档能力。根据现有验收与证据报告，系统在功能完整性、权限边界、租户隔离、稳定性与可核验性方面达到可演示、可复现、可答辩状态。

关键词：AI治理，影子AI，LSTM预测，模型漂移，多租户隔离，RBAC，哈希链审计

---

## 目录
1. 需求分析  
2. 概要设计  
3. 详细设计  
4. 测试报告  
5. 安装及使用说明  
6. 项目总结  
7. 附录（证据索引与图表绘制指引）

---

# 1. 需求分析

## 1.1 项目背景
企业在引入生成式 AI 与智能自动化后，面临以下治理挑战：
1. AI 调用分散在终端、业务系统与第三方接口，缺乏统一可视化与可追溯能力。
2. 影子 AI（未经备案模型/服务）使用难以实时发现，存在数据外泄和合规风险。
3. 高敏治理操作需要审批与复核，若权限边界不清容易产生越权与职责冲突。
4. 审计日志若不可防篡改，难以支撑内外部审计与合规举证。

## 1.2 建设目标
1. 建立企业级 AI 治理中枢，统一汇聚风险、审批、审计、攻防和运维观测。
2. 打通“客户端采集→事件上报→策略匹配→告警生成→审批流转→处置闭环→审计追溯”全链路。
3. 实现多租户隔离与细粒度 RBAC，保证不同公司、不同角色数据和操作边界清晰。
4. 提供可验证证据链（哈希链+外部锚定），保障审计可信度与答辩可核验性。

## 1.3 角色与职责分离需求
平台核心角色包括：治理管理员、治理复核员、安全运维、业务负责人、审计员（及系统内保留角色集合）。

职责分离要求：
1. 治理管理员：发起治理变更、提交审批申请、执行已审批处置。
2. 治理复核员：仅负责审批通过/驳回，审批意见必填，不参与同单发起。
3. 安全运维：负责威胁处置、策略运维、观测与演练执行。
4. 业务负责人：发起业务域变更申请，查看本域结果。
5. 审计员：查看审计报表与证据，不参与治理写操作。

## 1.4 功能需求

### 1.4.1 基础平台
1. 登录与会话管理。
2. 安全指挥台、顶部菜单、快捷操作。
3. 个人资料、系统设置、退出。

### 1.4.2 组织权限
1. 用户管理。
2. 角色管理。
3. 权限管理。
4. 角色保留：治理管理员、治理复核员、安全运维、业务负责人、审计员。

### 1.4.3 治理主线
1. 审批中心（含“我收到”/待我审批）。
2. 治理变更。
3. 策略管理。
4. 审计中心。
5. 合规风险记录。
6. 异常行为监控。
7. 影子 AI 发现。
8. 威胁监控。

### 1.4.4 AI 核心
1. AI 风险评级（企业维度）。
2. AI 白名单配置。
3. AI 调用审计。
4. 模型谱系。
5. 模型漂移。
6. 模型发布。

### 1.4.5 攻防与运维
1. 攻防模拟面板。
2. 真实训练。
3. 训练日志。
4. 模型优化报告。
5. 强化对比。
6. 阈值加固。
7. 运维观测。

### 1.4.6 补充已实现功能（未在题目主清单中显式列出）
1. 数据主体权利工单（subject request）。
2. 隐私盾事件采集与脱敏策略。
3. 证据归档与就绪度报告（readiness）。
4. 固定包验真与外部锚定查询。

## 1.5 非功能需求
1. 安全性：高敏操作审批、二次校验、输入防护、日志防篡改。
2. 隔离性：company_id 级数据隔离、角色权限隔离、流程隔离。
3. 可追溯性：统一治理事件、审计链、证据锚定。
4. 可用性：容器化部署，前后端与推理服务可独立扩缩。
5. 可测试性：覆盖功能、权限、隔离、性能与稳定性回归。

---

# 2. 概要设计

## 2.1 系统总体架构
平台采用“前端展示层 + 后端治理层 + Python AI 推理层 + 数据存储层 + 终端采集层”的分层架构。

此处应插入图1：系统总体架构图  
绘图说明：
1. 工具建议：draw.io 或 Visio。
2. 图层划分：
- 终端层：浏览器插件/桌面客户端（采集端）。
- 接入层：Vue 前端、Nginx 网关。
- 服务层：Spring Boot（权限、审批、治理、审计、事件归集）。
- AI 层：Python service（风险预测、漂移评估、模型发布）。
- 数据层：MySQL（业务库）+ Elasticsearch（检索，可选）。
3. 关键连线标注：
- 终端上报到治理事件。
- AI 调用日志写入与哈希链构建。
- 外部锚定服务与证据校验接口。
4. 节点命名：要求与系统菜单/接口命名一致。

## 2.2 功能模块架构
此处应插入图2：功能模块图  
绘图说明：
1. 工具建议：draw.io（矩形分组图）。
2. 一级模块：基础平台、组织权限、治理主线、AI 核心、攻防运维。
3. 二级模块：按 1.4 各子功能展开。
4. 强调关系：
- 审批中心连接治理变更、策略管理、权限管理。
- 审计中心连接 AI 调用审计、治理事件、哈希链验真。

## 2.3 业务闭环设计
此处应插入图3：治理闭环业务流程图  
流程节点：
1. 客户端采集。
2. 事件上报（security_event/privacy_event/client_report）。
3. 策略匹配与风险评估。
4. 告警生成（governance_event）。
5. 审批流转（approval_request / governance_change_request）。
6. 处置执行与状态回写。
7. 审计追溯与证据归档（audit_log、audit_hash_chain、external_anchor_record）。

绘图方法：
1. 工具建议：Visio BPMN 或 draw.io 流程图。
2. 决策网关：是否高敏、是否需复核、是否通过审批。
3. 泳道建议：治理管理员、治理复核员、系统自动执行、审计员。

## 2.4 部署拓扑
此处应插入图4：部署拓扑图  
绘图说明：
1. 工具建议：draw.io 网络拓扑模板。
2. 节点：frontend 容器、backend 容器、ai-inference 容器、mysql、可选 redis/nginx。
3. 标注端口与协议：HTTP/REST、内部服务调用。
4. 标注高可用策略：服务可独立重启，日志与数据持久化。

---

# 3. 详细设计

## 3.1 技术栈
1. 前端：Vue 3 + Vite + Element Plus + ECharts。
2. 后端：Spring Boot + MyBatis Plus + Spring Security。
3. AI 服务：Python（Flask 体系）+ 训练与发布管理脚本。
4. 数据存储：MySQL（核心业务库）。
5. 运维：Docker Compose 容器化部署。

## 3.2 核心技术设计

### 3.2.1 AI 风险评级（LSTM 时序预测）
1. 在 AI 服务中配置 LSTM 架构参数（hidden/layers/dropout/epochs/lr），用于企业维度风险时序建模。
2. 风险评级结果与企业级统计结合，输出给前端风险看板与治理决策。
3. 与传统特征模型并行：规则+统计+时序预测形成组合评估，提高鲁棒性。

实现要点：
1. 时间窗口特征构建（按天/按周期聚合风险事件）。
2. 模型输出用于趋势研判，不直接替代审批决策。
3. 预测异常需进入治理流程，由人工复核闭环。

### 3.2.2 模型漂移检测
1. 通过漂移状态接口与近期预测样本统计，判断模型行为偏离程度。
2. 当漂移分数超过阈值时，触发告警并进入治理复核。
3. 漂移结果联动模型发布管理（候选、灰度、稳定、回滚）。

### 3.2.3 影子 AI 实时发现
1. 客户端采集终端上下文（进程/网络/窗口/行为）形成上报。
2. 服务端归集到 client_report 与 governance_event，生成影子 AI 告警。
3. 在影子 AI 模块提供列表、统计、处置流转与历史追溯。

### 3.2.4 攻防演练沙盒
1. 提供攻防模拟面板与脚本化演练（含攻击策略集）。
2. 演练结果写入 adversarial_record，并关联治理事件。
3. 输出强化对比与阈值加固建议，反哺策略管理。

### 3.2.5 多租户隔离（company_id）
1. 关键业务表统一持有 company_id 字段。
2. 索引层面提供 company_id 组合索引，保证查询隔离与性能。
3. 服务层统一公司范围注入，防止跨租户读取。
4. 外键层面将核心表关联到 company 主表，确保结构一致性。

### 3.2.6 细粒度 RBAC 与职责分离
1. 模型：role、permission、role_permission 三表驱动。
2. 控制：接口层 PreAuthorize 权限表达式控制读写边界。
3. SoD：治理管理员与治理复核员分离，审批意见必填，同人不可自审。
4. 审批中心支持“待我审批/我发起”双视图，按角色动态可见。

### 3.2.7 审计日志哈希链防篡改
1. 审计日志主表保存 hash 字段。
2. audit_hash_chain / ai_call_hash_chain 构造 prev_hash + current_hash 链式结构。
3. external_anchor_record 保存 payload_hash 外部锚定信息，支持在线验真。
4. 脚本可重算链路并校验一致性，形成可核验证据。

## 3.3 关键流程详细设计

### 3.3.1 治理审批流程（管理员 + 复核员）
此处应插入图5：审批状态机图  
状态：pending、approved、rejected、withdrawn（如启用撤回）。

关键规则：
1. 高敏操作统一先入审批，再执行变更。
2. 复核员审批通过/驳回时审批意见必填。
3. 非 pending 状态禁止重复审批。
4. 发起人与复核员不得为同一账号。
5. 审批事件写入治理事件与审计日志。

### 3.3.2 治理闭环追溯流程
此处应插入图6：事件追溯时序图  
时序参与者：Client、Backend、Policy Engine、Approval、Audit、Anchor Service。  
要求标注：event_id、request_id、hash、anchor_id 的传递链路。

## 3.4 数据库设计

### 3.4.1 ER 关系说明
此处应插入图7：数据库 ER 图  
绘图方法：
1. 工具建议：Navicat Data Modeler / PowerDesigner / draw.io。
2. 实体：company、sys_user、role、permission、role_permission、governance_event、approval_request、audit_log、security_event、client_report、ai_model、ai_call_log。
3. 关键关系：
- company 1:N sys_user / role / permission / governance_event / approval_request / security_event / ai_call_log。
- role 1:N sys_user；role N:M permission（通过 role_permission）。
- governance_event 与 adversarial_record 关联。
4. 所有租户域实体旁标注 company_id。

### 3.4.2 核心数据表字段设计

表3-1 用户表 sys_user（含租户隔离）

| 字段名 | 类型 | 说明 | 备注 |
|---|---|---|---|
| id | BIGINT | 用户ID | 主键 |
| company_id | BIGINT | 公司ID | 多租户隔离字段 |
| username | VARCHAR(50) | 用户名 | 唯一索引建议 |
| password | VARCHAR(100) | 密码 | 加密存储 |
| role_id | BIGINT | 角色ID | 关联 role |
| department | VARCHAR(50) | 部门 | 组织属性 |
| status | TINYINT | 账号状态 | 1/0 |
| approved_by | BIGINT | 审批人ID | 审批链路 |
| approved_at | DATETIME | 审批时间 | 审计追溯 |

表3-2 角色表 role

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 角色ID |
| company_id | BIGINT | 公司ID（隔离） |
| name | VARCHAR(50) | 角色名称 |
| code | VARCHAR(50) | 角色编码 |
| description | VARCHAR(200) | 描述 |

表3-3 权限表 permission

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 权限ID |
| company_id | BIGINT | 公司ID（隔离） |
| code | VARCHAR(50) | 权限编码 |
| parent_id | BIGINT | 父权限ID |
| status | VARCHAR(20) | active/disabled |

表3-4 公司表 company

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 公司ID |
| company_code | VARCHAR(64) | 公司编码 |
| company_name | VARCHAR(128) | 公司名称 |
| status | TINYINT | 状态 |

表3-5 安全事件表 security_event

| 字段名 | 类型 | 说明 | 备注 |
|---|---|---|---|
| id | BIGINT | 事件ID | 主键 |
| company_id | BIGINT | 公司ID | 隔离字段 |
| event_type | VARCHAR(64) | 事件类型 | 威胁分类 |
| severity | VARCHAR(20) | 严重度 | critical/high/medium/low |
| status | VARCHAR(20) | 状态 | pending/blocked 等 |
| source | VARCHAR(64) | 来源 | agent 等 |
| event_time | DATETIME | 事件时间 | 时序分析 |

表3-6 影子 AI 上报表 client_report

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| company_id | BIGINT | 公司ID（隔离） |
| client_id | VARCHAR | 客户端标识 |
| shadow_ai_count | INT | 影子AI计数 |
| scan_time | DATETIME | 扫描时间 |
| payload_json | LONGTEXT | 扫描详情 |

表3-7 统一治理事件表 governance_event

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 事件ID |
| company_id | BIGINT | 公司ID（隔离） |
| user_id | BIGINT | 关联用户 |
| event_type | VARCHAR(64) | 统一事件类型 |
| source_module | VARCHAR(64) | 来源模块 |
| severity | VARCHAR(20) | 严重度 |
| status | VARCHAR(20) | 处置状态 |
| payload_json | LONGTEXT | 扩展载荷 |

表3-8 审计日志表 audit_log

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 日志ID |
| user_id | BIGINT | 用户ID |
| operation | VARCHAR(50) | 操作类型 |
| operation_time | DATETIME | 操作时间 |
| risk_level | VARCHAR(20) | 风险等级 |
| hash | VARCHAR(128) | 审计哈希 |

表3-9 审批申请表 approval_request

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 审批单ID |
| company_id | BIGINT | 公司ID（隔离） |
| applicant_id | BIGINT | 申请人 |
| approver_id | BIGINT | 审批人 |
| reason | VARCHAR(200) | 申请理由 |
| status | VARCHAR(20) | 待审批/通过/拒绝 |
| process_instance_id | VARCHAR(64) | 流程实例ID |
| task_id | VARCHAR(64) | 当前任务ID |

表3-10 AI 调用日志表 ai_call_log

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| company_id | BIGINT | 公司ID（隔离，迁移后保障） |
| user_id | BIGINT | 调用用户 |
| model_id | BIGINT | 模型ID |
| model_code | VARCHAR(100) | 模型编码 |
| input_preview | VARCHAR(200) | 输入脱敏摘要 |
| output_preview | VARCHAR(200) | 输出脱敏摘要 |
| duration_ms | BIGINT | 调用耗时 |
| token_usage | INT | token用量 |
| create_time | DATETIME | 创建时间 |

表3-11 AI 模型表 ai_model

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 模型ID |
| company_id | BIGINT | 公司ID（隔离） |
| model_name | VARCHAR(100) | 模型名称 |
| model_code | VARCHAR(50) | 模型编码 |
| provider | VARCHAR(50) | 供应商 |
| risk_level | VARCHAR(20) | 风险等级 |
| isolation_level | VARCHAR(8) | L0-L4 隔离级别 |
| status | VARCHAR(20) | enabled/disabled |

### 3.4.3 审计链与锚定表（防篡改）

表3-12 审计哈希链 audit_hash_chain

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| company_id | BIGINT | 公司ID（隔离） |
| audit_log_id | BIGINT | 审计日志ID |
| prev_hash | VARCHAR(128) | 前一节点哈希 |
| current_hash | VARCHAR(128) | 当前哈希 |
| create_time | TIMESTAMP | 生成时间 |

表3-13 外部锚定记录 external_anchor_record

| 字段名 | 类型 | 说明 |
|---|---|---|
| id | BIGINT | 主键 |
| company_id | BIGINT | 公司ID（隔离） |
| evidence_type | VARCHAR(64) | 证据类型 |
| evidence_ref | VARCHAR(128) | 证据引用 |
| payload_hash | VARCHAR(128) | 负载哈希 |
| provider | VARCHAR(64) | 锚定服务商 |
| verify_status | VARCHAR(20) | 验证状态 |
| detail_json | LONGTEXT | 验证详情 |

---

# 4. 测试报告

## 4.1 测试环境
1. 操作系统：Windows。
2. 部署方式：Docker Compose（frontend/backend/ai-inference）。
3. 数据库：MySQL。
4. 测试来源：后端集成测试、E2E 报告、治理就绪度报告、职责分离回归结果。

## 4.2 功能测试

表4-1 关键功能测试用例

| 用例ID | 测试目标 | 前置条件 | 测试步骤 | 预期结果 |
|---|---|---|---|---|
| FT-01 | 登录与会话 | 账号存在 | 输入账号密码登录 | 登录成功，进入指挥台 |
| FT-02 | 审批中心“待我审批” | 复核员角色 | 打开审批中心并切换页签 | 仅显示可审批记录 |
| FT-03 | 治理变更流转 | 管理员角色 | 提交策略变更申请 | 生成 pending 审批单 |
| FT-04 | 影子AI发现 | 客户端上报开启 | 触发可疑 AI 访问行为 | 生成治理事件与告警 |
| FT-05 | 审计报表生成 | 有审计日志数据 | 进入审计中心生成报告 | 返回可下载结果与分布统计 |
| FT-06 | 模型发布状态查看 | 有模型版本记录 | 查询发布状态接口 | 返回候选/稳定/灰度状态 |

## 4.3 权限越权测试

表4-2 越权与职责分离测试

| 用例ID | 测试目标 | 步骤 | 预期结果 |
|---|---|---|---|
| AT-01 | 非审批角色执行审批 | 业务负责人调用审批接口 | 返回 403 或拒绝 |
| AT-02 | 发起人自审拦截 | 同账号发起并审批同单 | 系统阻断，提示 SoD 冲突 |
| AT-03 | 缺失权限访问治理写接口 | 审计员调用写接口 | 返回 403 |
| AT-04 | 复核意见必填 | 复核员空意见提交通过/驳回 | 校验失败，不允许提交 |

## 4.4 多租户隔离测试

表4-3 多租户隔离测试

| 用例ID | 测试目标 | 步骤 | 预期结果 |
|---|---|---|---|
| TT-01 | 跨公司数据读取隔离 | 公司A账号请求公司B事件 | 无数据或拒绝访问 |
| TT-02 | 写入隔离 | 公司A发起审批写入 | 记录 company_id=公司A |
| TT-03 | 索引隔离有效性 | 按 company_id + 状态分页查询 | 返回仅本公司结果，性能稳定 |
| TT-04 | 角色隔离 | 同角色不同公司访问相同菜单 | 仅查看本公司范围数据 |

## 4.5 性能测试

表4-4 性能测试设计

| 用例ID | 测试目标 | 输入规模 | 指标 | 预期结果 |
|---|---|---|---|---|
| PT-01 | 治理事件列表分页 | 万级事件数据 | P95 响应时间 | 在可接受范围内 |
| PT-02 | 审计报表统计 | 千级日志聚合 | 报表生成耗时 | 可在页面容忍时间内返回 |
| PT-03 | 漂移状态查询 | 连续调用 | 稳定性/错误率 | 连续请求无异常波动 |
| PT-04 | 审批并发提交 | 多角色并发操作 | 成功率、冲突处理 | 无越权，状态一致 |

## 4.6 已有测试与证据摘要
1. 治理就绪度验收：totalChecks=6，passedChecks=6，failedChecks=0，passed=true。
2. 角色权限 E2E 摘要：totalChecks=140，存在 mismatch 项并已进入治理修复闭环（用于持续改进记录）。
3. 门禁证据与哈希链验真：支持构建与校验脚本，具备可复验性。
4. 后端集成测试集覆盖：权限矩阵、职责分离、租户一致性、追溯与观测、登录稳定性等。

此处应插入图8：测试覆盖矩阵图  
绘图说明：
1. 横轴：模块（基础平台、组织权限、治理主线、AI核心、攻防运维）。
2. 纵轴：测试类型（功能、越权、租户隔离、性能、稳定性）。
3. 单元格标记：已覆盖/部分覆盖/计划补强。

---

# 5. 安装及使用说明

## 5.1 运行环境
1. Docker 与 Docker Compose。
2. JDK 17+（后端开发/编译）。
3. Node.js 18+（前端构建）。
4. Python 3.10+（AI 服务）。
5. MySQL 8.x。

## 5.2 部署步骤（推荐容器化）
1. 克隆项目到本地。
2. 准备环境变量与配置文件（数据库连接、模型配置、密钥配置）。
3. 执行容器编排启动：frontend、backend、ai-inference、db。
4. 访问前端入口并进行登录验证。
5. 通过就绪度接口与健康检查确认服务状态。

## 5.3 使用流程
1. 管理员登录后进入安全指挥台查看总览。
2. 在组织权限模块维护用户、角色与权限。
3. 在治理主线处理告警、变更与审批流转。
4. 在 AI 核心模块查看风险评级、漂移与发布状态。
5. 在攻防与运维模块执行演练、查看日志与加固建议。
6. 在审计中心导出报表并执行证据验真。

## 5.4 常见问题
1. 问题：审批接口返回无权限。  
处理：检查角色绑定与权限码是否包含 review/view 对应权限。
2. 问题：跨租户数据异常显示。  
处理：检查 company_id 注入逻辑与查询条件。
3. 问题：审计验真失败。  
处理：重跑哈希链校验脚本，检查 prev_hash 与 current_hash 连续性。
4. 问题：漂移指标为空。  
处理：检查 AI 服务最近预测样本与漂移阈值配置。

---

# 6. 项目总结

## 6.1 项目成果
1. 建成覆盖“发现-治理-审计-证据”闭环的一体化平台。
2. 完成多租户隔离、细粒度权限与职责分离落地。
3. 实现影子 AI 发现、模型漂移监测、风险评级与模型发布管理。
4. 形成哈希链+外部锚定可核验证据体系，支持审计答辩。

## 6.2 创新点
1. 治理闭环与审批流程深度融合，避免“告警不落地”。
2. 审计证据从日志存储升级为“可验真证据链”。
3. 影子 AI 发现与攻防演练联动，形成策略反哺机制。
4. 将 LSTM 风险趋势预测用于企业维度治理决策辅助。

## 6.3 局限与后续优化
1. 风险预测对长时间序列数据依赖较高，后续引入更长观测窗。
2. 漂移监测可增加多维统计（PSI/KL/分群漂移）提升解释性。
3. 性能测试将进一步补充高并发压测报告与容量规划。
4. 待你提供 PDF 模板后，文档将进行最终版式与目录编号一致化处理。

---

# 7. 附录（证据索引与绘图指引）

## 7.1 关键证据文件（用于答辩引用）
1. docs/系统说明文档-项目展示与答辩-2026-03-31.md
2. docs/approval-center-design-2026-04-05.md
3. docs/award-defense-evidence-index-2026-03-29.md
4. docs/governance-readiness-acceptance.json
5. docs/role-permission-e2e-summary.json
6. backend/src/main/resources/db.sql
7. backend/src/main/java/com/trustai/config/CompanySchemaInitializer.java
8. backend/src/main/java/com/trustai/config/AwardSchemaInitializer.java
9. backend/src/main/java/com/trustai/client/AiInferenceClient.java
10. python-service/app.py
11. python-service/openclaw_simulator.py
12. backend/src/test/java/com/trustai/integration（集成测试集合）

## 7.2 图表统一绘制规范
1. 颜色：主色深蓝，强调色青蓝，告警色橙红；与系统 UI 保持一致。
2. 字体：中文宋体，英文 Times New Roman。
3. 连线：主流程实线，异常/回滚虚线。
4. 编号：按章节连续编号，如图3-1、图3-2；表3-1、表3-2。
5. 输出格式：优先 SVG（可编辑）+ PNG（提交版）。

## 7.3 交付建议
1. 先将本 Markdown 导入 Word。
2. 按“版式执行说明”统一样式。
3. 按“此处应插入图X”完成图件制作和插入。
4. 使用你的 PDF 模板进行最终目录和样式对齐。
