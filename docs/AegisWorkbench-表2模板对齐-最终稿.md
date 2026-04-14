教育部高等学校大学计算机课程教学指导委员会  
中国大学生计算机设计大赛  
软件开发类作品文档简要要求

作品编号：________________  
作品名称：Aegis AI安全治理与对抗防御平台（AegisWorkbench）  
作 者：________________  
版本编号：V1.0  
填写日期：2026.04.07

填写说明：
1、本文档适用于所有涉及软件开发的作品。  
2、正文一律用五号宋体，一级标题为二号黑体，其他级别标题根据需要设置。  
3、本文档为简要文档，不宜长篇大论，简明扼要为上。  
4、提交文档时，以 PDF 格式提交。  
5、本文档内容为正式参赛内容组成部分，须真实填写。

---

目 录
第一章 需求分析  
第二章 概要设计  
第三章 详细设计  
第四章 测试报告  
第五章 安装及使用  
第六章 项目总结

---

# 第一章 需求分析

## 1.1 设计背景与意义
随着企业在业务系统中广泛接入大模型能力，出现了“调用增长快、治理能力弱、审计可追溯不足”的普遍问题。传统安全系统多聚焦网络与主机层，难以对 AI 调用行为、影子 AI 服务、模型漂移和治理审批链路形成统一闭环。

AegisWorkbench 的建设意义在于：
1. 将分散在客户端、后端与 AI 服务侧的安全事件统一归集，形成企业级 AI 治理中枢。
2. 建立“客户端采集→事件上报→策略匹配→告警生成→审批流转→处置闭环→审计追溯”的完整治理链路。
3. 在多租户场景中通过 company_id、RBAC 和职责分离保障“看得见、管得住、可核验”。
4. 通过审计哈希链与外部锚定，提升证据防篡改和答辩可复核性。

## 1.2 主要功能
本作品已实现核心功能如下（以第四章测试项和证据项为准）：

1. 基础平台：登录、安全指挥台、顶部菜单、快捷操作、个人资料、系统设置、退出。  
2. 组织权限：用户管理、角色管理、权限管理。保留角色包括治理管理员、治理复核员、安全运维、业务负责人、审计员。  
3. 治理主线：审批中心（含“我收到/待我审批”）、治理变更、策略管理、审计中心、合规风险记录、异常行为监控、影子 AI 发现、威胁监控。  
4. AI 核心：AI 风险评级（企业维度）、AI 白名单配置、AI 调用审计、模型谱系、模型漂移、模型发布。  
5. 攻防与运维：攻防模拟面板、真实训练、训练日志、模型优化报告、强化对比、阈值加固、运维观测；并支持在告警处置阶段触发攻防验证并回写处置证据。  
6. 补充已实现功能：就绪度与证据归档、哈希链验真与外部锚定查询。

## 1.3 国内外研究现状
当前业界在 AI 治理方向主要关注模型可解释性、内容安全与推理防护，但企业级落地常见问题是：
1. 重“检测”轻“处置”，告警后缺少审批和闭环机制。  
2. 重“单模型”轻“多模型全栈治理”，缺乏影子 AI 统一发现能力。  
3. 重“日志留存”轻“证据验真”，审计日志防篡改不足。

本项目采用“治理流程+工程落地”路线，强调可执行、可审计、可证明：
1. 风险评级引入 LSTM 时序预测，当前输入为“最近90天按日聚合的风险事件数量序列”（单变量时序）。  
2. 模型漂移检测与发布管理联动，支持灰度、稳定、回滚。  
3. 审计链路引入 hash chain + external anchor，形成可验真证据体系。

## 1.5 功能-验证映射说明
为避免“功能声称与验证脱节”，本文约定：
1. 第一章仅保留已实现模块；是否通过验证以第四章对应测试编号为准。  
2. 无对应测试项的能力，不作为“已验证结论”写入总结性论断。  
3. 本稿属于开发文档正文，图件证据与运行报告在提交 PDF 附录中一并提供。

## 1.4 应用对象
1. 企业治理管理员：负责策略、审批发起、事件处置组织。  
2. 治理复核员：负责高敏操作复核与意见留痕。  
3. 安全运维与审计人员：负责监控、审计、报告与验真。  
4. 业务负责人：提交业务变更申请并跟踪处置结果。  
5. 多租户企业用户：按公司边界隔离使用。

---

# 第二章 概要设计

## 2.1 设计内容
本系统采用“前端可视化 + 后端治理编排 + Python AI 服务 + 数据层 + 客户端采集”的分层设计。设计重点是把安全检测、审批流转、治理处置、审计追溯统一到一条主线。

### 2.1.1 关键组件职责与通信方式
表2.2 组件职责与通信

| 组件 | 主要职责 | 通信方式 |
|---|---|---|
| Electron Client（含扫描器） | 影子AI发现、终端扫描、结果上报 | HTTPS 调用 Backend `/api/client/register`、`/api/client/report` |
| Frontend（Vue） | 治理看板、审批交互、报表展示 | HTTPS 调用 Backend `/api/*` |
| Backend（Spring Boot） | 鉴权、RBAC、审批/治理编排、审计、网关代理 | REST + DB + Feign（到 Python 服务） |
| AI Inference（Python） | 分类、LSTM预测、对抗模拟、训练流水线 | Backend 内网调用（非前端直连） |
| MySQL | 业务数据、治理事件、审计链、锚定记录 | JDBC |
| Evidence Anchor | 外部时间源 + 本地RSA签名存证验证 | HTTP 时间源 + 签名写入 `external_anchor_record` |

### 2.1.2 客户端采集技术方案（影子AI发现）
当前客户端为 Electron Agent，采用三路并行扫描并去重：
1. 浏览器历史扫描：识别最近访问的AI服务站点。  
2. 网络连接扫描：识别活跃连接目标。  
3. 进程扫描：识别本地运行中的AI工具。  
4. 白名单过滤：依据平台下发白名单过滤官方服务，剩余结果作为 shadow services 上报。  
5. 上报协议：带 `X-Client-Token` 与 `X-Company-Id` 头，失败写本地 pending 队列，后续重试。

此处应插入图2.1：系统总体架构图  
绘图方法：
1. 工具：draw.io 或 Visio。  
2. 节点：Client/Browser Extension、Frontend、Backend、AI Inference Service、MySQL、Evidence Anchor。  
3. 关键连线：事件上报、策略匹配、审批流、哈希链构建、外部锚定验证。  
4. 图中标注 company_id 作为租户边界主线。

## 2.2 设计参数
应用名称：AegisWorkbench  
开发平台：Web + Backend + Python Service  
主要技术：Vue3、Spring Boot、MyBatis Plus、Python、Docker

表2.1 项目开发环境

| 分类 | 名称 | 版本 |
|---|---|---|
| 开发系统 | Windows | 10/11 |
| 应用系统 | Browser + Server | 跨平台 |
| 前端平台 | Node.js + Vite | Node 18+ |
| 后端平台 | Java + Maven | JDK 17+ |
| 数据库 | MySQL | 8.x |
| AI服务 | Python | 3.10+ |
| 部署方式 | Docker Compose | v2 |

## 2.3 总体设计
系统围绕治理闭环运行，核心链路如下：
1. 客户端采集终端行为和 AI 使用上下文。  
2. 后端接收并归集为 security_event/privacy_event/client_report。  
3. 策略引擎匹配规则并生成 governance_event。  
4. 对高敏操作进入审批中心，按角色权限触发复核。  
5. 处置结果回写，并沉淀 audit_log 与哈希链。  
6. 证据可通过 external anchor 查询验真。

此处应插入图2.2：治理闭环流程图  
绘图方法：
1. 工具：draw.io BPMN 模板。  
2. 泳道：治理管理员、治理复核员、系统自动处理、审计员。  
3. 关键网关：是否高敏、是否需要复核、审批是否通过、是否触发回滚。

### 2.3.1 构建事件数据集（治理数据底座）
平台通过业务日志、AI 调用日志、终端上报和历史审计数据构建治理数据底座，形成企业级风险画像与趋势样本，服务风险评级和漂移检测。

### 2.3.2 使用 AI 服务实现风险评估与漂移判断
AI 服务侧输出：
1. 风险趋势预测（LSTM 时序）。  
2. 漂移状态评估（阈值+近期样本统计）。  
3. 模型发布状态（候选/灰度/稳定/回滚）。

### 2.3.3 使用审批与审计机制形成闭环
1. 治理管理员负责发起，治理复核员负责审批。  
2. 审批状态变化驱动实际处置动作。  
3. 全程写入治理事件与审计日志，支持报表与验真。

### 2.3.4 攻防模拟与治理闭环联动
1. 联动触发点：在告警处置接口（dispose）中可选择 `triggerSimulation=true`，由处置动作触发攻防验证。  
2. 联动数据流：`governance_event` → 攻防引擎执行 → 生成对抗结果/建议 → 写入 `adversarial_record`（含 `governance_event_id`）并记录处置审计。  
3. 策略加固链路：当前版本通过 `POST /api/ai/adversarial/apply-hardening` 执行人工确认后的规则阈值收紧与增量训练触发。  
4. 约束边界（真实口径）：当前未实现“自动生成策略并自动发起审批单”的全自动链路，仍采用“人工确认 + 接口执行”的受控流程。

## 2.4 主要界面设计
1. 首页安全指挥台：展示风险趋势、模型状态、告警与治理入口。  
2. 审批中心：待我审批、我发起、详情对比、审批意见。  
3. 影子 AI 页面：发现列表、统计、历史追溯。  
4. 审计中心：事件分布、下载报表、证据验真入口。

此处应插入图2.3：主要界面原型图（四宫格）  
绘图方法：
1. 工具：Figma。  
2. 四个画板：指挥台、审批中心、影子AI、审计中心。  
3. 每个画板标注关键动作按钮与角色可见范围。

## 2.5 图件交付清单（PDF必须补齐）
本 Markdown 为正文草稿，提交版 PDF 必须包含并编号以下图件：
1. 图2.1 系统总体架构图。  
2. 图2.2 治理闭环流程图。  
3. 图2.3 主要界面原型图。  
4. 图3.1 模块关系图。  
5. 图3.2 全链路时序图。  
6. 图3.3 数据库ER图。  
7. 图3.4 审计证据链结构图。  
8. 图3.5 系统关键界面实景图。  
9. 图4.1 测试与验收结果汇总图。

配套绘图材料：见 `docs/图件图元清单-表2开发文档.md`（含每张图的节点、连线、标注、校对项）。

---

# 第三章 详细设计

## 3.1 总体设计
本系统由五大能力域组成：基础平台、组织权限、治理主线、AI 核心、攻防运维。各域通过统一治理事件 governance_event 和审计日志 audit_log 连接，形成同一条追溯链。

此处应插入图3.1：模块关系图  
绘图方法：
1. 工具：draw.io。  
2. 关系：模块间依赖箭头统一指向“治理事件中心”和“审计中心”。  
3. 标注“读写权限边界”与“审批门禁点”。

### 3.1.1 组织权限与职责分离设计
1. RBAC 模型：role、permission、role_permission。  
2. 接口控制：PreAuthorize 注解实现细粒度授权。  
3. SoD 规则：发起人与复核员分离，审批意见必填，非 pending 禁止重复审批。  
4. 当前账号模型为单用户单角色（`sys_user.role_id`）；职责分离通过审批约束实现，而非多角色叠加。  
5. “治理管理员 + 治理复核员”形成双人复核机制，覆盖策略、权限、治理高敏动作。

### 3.1.3 审批流实现边界说明
1. 当前实现采用平台内状态机流转（pending/reviewing/approved/rejected 等）+ 权限校验。  
2. `approval_request` 中 `process_instance_id`、`task_id` 为工作流引擎预留字段，当前版本未接入独立 BPM 引擎。  
3. 因此本文不声明“会签/或签引擎能力”，仅声明已实现的审批状态流和职责分离约束。

### 3.1.2 全链路数据流设计
数据链路定义：
客户端采集 → 事件上报 → 策略匹配 → 告警生成 → 审批流转 → 处置闭环 → 审计追溯。

此处应插入图3.2：全链路时序图  
绘图方法：
1. 工具：PlantUML 或 draw.io 时序图。  
2. 参与者：Client、Gateway、Backend、PolicyEngine、ApprovalService、AuditService、AnchorService。  
3. 标注关键ID：event_id、request_id、current_hash、anchor_id。

## 3.2 创新性及实用性

### 3.2.1 技术创新性
1. 企业维度 AI 风险评级采用 LSTM 时序预测，并与治理规则结合。  
2. 模型漂移检测与发布管理联动，支持运行态治理而非离线分析。  
3. 审计哈希链与外部锚定结合，实现可核验证据链。  
4. 影子 AI 发现与攻防演练联动，形成策略迭代闭环。

### 3.2.2 应用创新性
1. 从“安全检测系统”升级为“治理执行系统”：告警可直接进入审批与处置。  
2. 在多角色、多租户条件下实现统一治理视图与边界控制。  
3. 证据体系可用于答辩、审计与复盘，不依赖口头说明。

### 3.2.3 应用实用性
1. 企业可直接部署并用于合规、安全、运营联动。  
2. 平台对业务负责人与审计员均提供可理解的流程视图。  
3. 可在不改变业务系统核心流程的前提下增量接入治理能力。

## 3.3 数据库设计

### 3.3.1 主要表及字段说明（含 company_id）

表3-1 用户、角色、权限

| 表名 | 关键字段 | 说明 |
|---|---|---|
| sys_user | id, company_id, username, role_id, status | 用户与租户边界 |
| role | id, company_id, code, name | 角色定义 |
| permission | id, company_id, code, parent_id | 权限定义 |
| role_permission | role_id, permission_id | 角色-权限映射 |
| company | id, company_code, company_name | 租户主表 |

表3-2 治理与审批

| 表名 | 关键字段 | 说明 |
|---|---|---|
| governance_event | id, company_id, event_type, source_module, severity, status, payload_json | 统一治理事件 |
| approval_request | id, company_id, applicant_id, approver_id, status, process_instance_id, task_id | 审批申请 |
| governance_change_request | id, company_id, module, action, status, requester_id, approver_id | 治理变更申请 |
| sod_conflict_rule | company_id, scenario, role_code_a, role_code_b, enabled | 职责分离规则 |

表3-3 安全事件与AI审计

| 表名 | 关键字段 | 说明 |
|---|---|---|
| security_event | id, company_id, event_type, severity, status, event_time | 安全威胁事件 |
| client_report | id, company_id, client_id, scan_time, shadow_ai_count | 影子AI上报 |
| audit_log | id, user_id, operation, operation_time, risk_level, hash | 审计日志 |
| ai_call_log | id, company_id, user_id, model_id, model_code, duration_ms, token_usage, create_time | AI调用日志 |
| ai_model | id, company_id, model_name, model_code, provider, risk_level, isolation_level, status | 模型注册 |

表3-4 哈希链与外部锚定

| 表名 | 关键字段 | 说明 |
|---|---|---|
| audit_hash_chain | id, company_id, audit_log_id, prev_hash, current_hash | 审计日志链式哈希 |
| ai_call_hash_chain | id, company_id, ai_call_log_id, prev_hash, current_hash | AI调用链式哈希 |
| external_anchor_record | id, company_id, payload_hash, provider, source_time, nonce, signature_base64, verify_status | 外部锚定与签名验证记录 |

说明：
1. company_id 为多租户隔离主字段。  
2. 核心表存在 company_id 相关索引与外键策略。  
3. `audit_log.hash` 是单条摘要，链式关系由 `audit_hash_chain`、`ai_call_hash_chain` 维护。

### 3.3.2 ER 设计说明
此处应插入图3.3：数据库 ER 图  
绘图方法：
1. 工具：Navicat Data Modeler。  
2. 主关系：company 1:N 业务表；role N:M permission；governance_event 与 approval_request / adversarial_record 关联。  
3. 每个租户相关表显式标注 company_id。

## 3.4 核心算法与机制设计

### 3.4.1 AI 风险评级（LSTM 时序预测）
实现位置：python-service/app.py 的 `SimpleLSTM`、`AdaptiveAttentionLSTM`、`forecast_risk(series, horizon)`。  

1. 模型结构：
- 基线模型：`SimpleLSTM`，2 层 LSTM + 线性输出头。  
- 改进模型：`AdaptiveAttentionLSTM`，在 LSTM 输出上叠加注意力权重与门控层，强化关键时间片贡献。  

2. 训练配置（已实现参数）：
- 隐藏层维度：`LSTM_HIDDEN`。  
- 层数：`LSTM_LAYERS`。  
- Dropout：`LSTM_DROPOUT`。  
- 学习率：`LSTM_LR`。  
- 轮次：`LSTM_EPOCHS`。  

3. 输入定义、数据来源与样本构建：
- 输入物理意义：`series` 为“最近90天按日聚合的风险事件数量”（来自 `risk_event` 表逐日统计，缺失日补0）。  
- 数据来源链路：Backend `RiskForecastScheduler` -> Feign 调用 Python `/predict/risk`。  
- 输入序列先做标准化：

$$
z_t = \frac{x_t-\mu}{\sigma+10^{-6}}
$$

- 采用滑动窗口构造监督样本，窗口长度 `look_back=min(7,n-2)`。  
- 训练/验证切分：按时间顺序 80%/20%，且验证集最少 1 点。  
 - 工程边界：当前为单变量时序预测，未在该版本引入多变量协同特征。

4. 训练目标与评估指标：
- 损失函数：均方误差（MSE）。  

$$
\mathcal{L}_{MSE}=\frac{1}{m}\sum_{i=1}^{m}(\hat{y}_i-y_i)^2
$$

- 评估指标：MAE 与 RMSE。  

$$
MAE=\frac{1}{m}\sum_{i=1}^{m}|\hat{y}_i-y_i|,\quad
RMSE=\sqrt{\frac{1}{m}\sum_{i=1}^{m}(\hat{y}_i-y_i)^2}
$$

- 模型选择：比较两模型验证集 RMSE，取更优模型输出。  

5. 多步预测策略：
- 采用自回归滚动预测：每步将新预测值回填窗口，持续迭代 `horizon` 步。  
- 反标准化后对结果做非负截断（风险值不小于 0）。  

6. 展示稳定性增强（已实现）：
- 当预测序列方差过小且历史序列存在波动时，基于最近日增量进行轻量波动修正，避免图形出现“水平直线”。  

伪代码（与实现一致）：
```text
Input: series, horizon
clean -> normalize -> build windows
train SimpleLSTM, eval RMSE_s
train AdaptiveAttentionLSTM, eval RMSE_a
model = argmin(RMSE_s, RMSE_a)
for t in 1..horizon:
	y_hat = model(last_window)
	append y_hat to history
forecast = denormalize(last horizon points)
if forecast too flat and history volatile:
	apply delta-based volatility adjustment
return forecast, MAE, RMSE, selected_model
```

### 3.4.2 模型漂移检测
实现位置：python-service/app.py 的 `drift_status` 路径与相关统计函数；后端通过 AiGateway 读取并展示。  

1. 分布估计：对近期预测结果按标签计数并归一化得到离散分布；基线分布来自最近一次模型训练运行的 `label_counts` 归一化。  
2. 漂移定义：比较“近期标签分布”与“基线标签分布”的 L1 距离（`driftScore`）。  
3. 漂移判定：`driftScore` 与阈值 `DRIFT_ALERT_THRESHOLD` 比较，输出 `normal/high`。  
3. 附加信号：返回 `recentAverageConfidence`、分布向量、三元状态（triad）及自动回滚建议。  
4. 联动机制：漂移结果进入治理看板，触发模型发布流程中的复核与回滚判断。

### 3.4.3 审计哈希链防篡改与外部锚定
1. audit_hash_chain：prev_hash→current_hash 串联。  
2. ai_call_hash_chain：AI 调用审计链。  
3. external_anchor_record：保存 payload_hash、provider、source_time、nonce、signature_base64、verify_status。  
4. 外部锚定实现：调用外部时间源（worldtimeapi）获取时间戳，拼接 canonical 文本后使用本地 RSA 私钥签名并写库。  
5. 验证过程：使用公钥验签 + payload_hash 匹配，输出 verify_status。

### 3.4.4 实时威胁发现与去重聚合
实现位置：backend AlertCenterController 的 `threatOverview(windowHours)`。  

1. 事件口径：仅纳入 `PRIVACY_ALERT`、`ANOMALY_ALERT`、`SHADOW_AI_ALERT`、`SECURITY_ALERT` 四类威胁事件。  
2. 时间窗口：默认 72 小时，可调且受上下界保护（1~720 小时）。  
3. 去重规则：优先使用 `source_module + source_event_id`；缺失时回退 `event_type + user_id + title + minute`。  
4. 输出结构：
- `summary`：总量、待处置、阻断、高危/严重。  
- `byType`：按威胁类型分布。  
- `bySource`：按来源模块分布。  
- `dedupe`：原始总量、去重总量、折叠量、统计窗口。  
- `trace`：链路关联率与规则说明。  

该设计避免同一攻击链路被重复计数，保证安全大盘与治理报表口径一致。

### 3.4.5 影子 AI 发现与风险评级链路
实现位置：backend ClientReportController 与 AiRiskRatingController。  

1. 客户端接入：`/api/client/register` 完成终端注册与账号/企业绑定。  
2. 扫描上报：`/api/client/report` 上报 `client_id`、设备信息、扫描时间、服务列表。  
3. 风险计算：服务端根据 `shadow_ai_count + discovered_services` 计算 `riskLevel`。  
4. 治理入湖：通过 EventHub 将上报转换为治理事件（含 `governanceEventId`），进入统一处置链。  
5. 企业评级：`/api/ai-risk/profile/radar` 基于近 30 天数据计算五维风险并加权输出总风险：

$$
R_{total}=0.24R_p+0.22R_b+0.20R_s+0.20R_c+0.14R_r
$$

其中：
- $R_p$：隐私纪律风险；$R_b$：行为稳定风险；$R_s$：影子AI暴露风险；  
- $R_c$：处置合规风险；$R_r$：模型调用可靠性风险。  

权重说明：当前权重由治理专家经验规则设定，用于运营评分基线；后续版本可基于历史事件回归结果再标定。

### 3.4.6 DeepSeek 智能解读链路
实现位置：backend HomeAiHubController 的 `deepseekAnalysis`、`buildAnalysisPrompt`、`runDeepseekAnalysis`。  

1. 输入：治理中枢聚合数据（scope、kpi、告警看板等）。发送前执行字段级+正则级脱敏。  
2. Prompt 构造：约束输出“三条风险判断 + 两条优先处置 + 一条量化指标”，并禁止编造字段。  
3. 模型调用：经 AiGateway 以 `provider=deepseek`、`model=deepseek-chat` 发起会话。  
4. 结果解析：优先解析 `choices[0].message.content`；失败则回退原始文本。  
5. 降级保障：调用异常时启用本地 fallback，基于真实 KPI 自动生成可执行建议并返回 warning。  
6. 合规边界：外发内容为脱敏后的聚合摘要，不传输原始账号标识、联系方式、证件号等明文。

### 3.4.7 攻防模拟超核心实现（OpenClaw）
实现位置：python-service/openclaw_adversarial.py 与 app.py 的 `/api/adversarial/*`。  

1. 攻防模型：
- 攻击侧：供应链投毒、提示注入、模型投毒、决策漂移、会话横移、低慢速外泄等策略。  
- 防御侧：沙箱隔离、输入净化、记忆防火墙、行为熔断、DLP、供应链审计、决策对齐。  

2. 对抗机制：
- 基于攻击-防御效果矩阵 `EFFECTIVENESS_MATRIX` 与场景偏置、随机噪声计算每轮最终突破率。  
- 任务态接口 `adversarial/task/start` 支持场景、轮次、seed 参数，保证同版本条件下可复现实验。  

3. 自适应与反馈训练（已实现闭环）：
- 每轮根据攻防结果更新 `adaptive_threshold` 与 `defense_strength_score`。  
- 每 3 轮自动触发 `adversarial_feedback_retrain`，将对抗样本回灌训练集并增量重训。  
- 生成报告（轮次明细、关键变化、优化建议）并落盘 `generated/adversarial_reports`。  

4. 指标输出：攻击成功率、拦截率、防御强度评分、关键规则命中、模型优化建议。

#### 3.4.7.1 目标模型与训练对象澄清
1. 当前“对抗反馈训练”直接作用对象是敏感信息检测分类模型 `sensitive_clf`（`_MLClassifier`），核心算法为 `LogisticRegression`（非 LSTM）。  
2. LSTM（3.4.1）用于风险时间序列预测，其训练入口是 `/predict/risk` 侧的时序拟合，不是对抗反馈重训目标。  
3. BERT 在本系统中是可选增强层：优先加载微调模型（若存在），否则零样本；BERT 不可用时系统退化为纯 ML 分类器。

#### 3.4.7.2 训练数据来源、规模与划分
数据构建实现：python-service/data_factory.py 的 `TrainingDataFactory.build_dataset()`。  

1. 数据来源（已实现）：
- 后端真实接口：`/api/audit-log/search`、`/api/risk-event/list`、`/api/data-asset/list`。  
- 对抗样本：`report.json` 中攻防叙事（可通过 `include_adversarial=true` 合并）。  
- 回退模式：当后端不可达时读取本地 JSON 文件（fallback）。  

2. 样本处理：
- 生成 `samples` + `hard_examples`，去重后合并为 `merged_samples`。  
- 每次构建写入 `generated/training_data_factory_*.json` 与 `training_data_factory_latest.json`，并保存 `labelCounts`。  

3. 规模与划分（真实规则）：
- 最大样本量由 `max_samples` 控制，默认上限 5000。  
- 分类模型训练启用评估切分条件：`len(samples)>=40` 且每类样本数至少 2。  
- 满足条件时采用 80/20 训练/测试切分，并执行最多 5 折分层交叉验证；不满足条件则仅做全量训练精度统计。  

4. 基线与微调定义：
- 基线：标准化 + 逻辑回归（`StandardScaler + LogisticRegression`）。  
- “微调”在当前实现中指对该分类器做增量重训（重新拟合分类器参数），不属于 PEFT/LoRA 类参数高效微调。

#### 3.4.7.3 效果矩阵与突破概率定义
1. 效果矩阵性质：`E[a][d]` 为专家预定义静态矩阵，取值范围 `[0,1]`，表示攻击策略 `a` 对防御策略 `d` 的基础突破率。  
2. 每轮突破率计算（引擎实现）：

$$
e_{base}=E[a][d],\quad
e_{final}=\mathrm{clip}(\mathrm{bias}(e_{base},scenario)+\epsilon,0,1),\ \epsilon\sim\mathcal{N}(0,0.08)
$$

3. 成功判定：采样 `u\sim U(0,1)`，若 $u<e_{final}$ 则攻击成功。  
4. 说明：当前矩阵不做在线学习更新，属于“专家矩阵 + 场景偏置 + 随机扰动”机制。

#### 3.4.7.4 自适应阈值与防御强度更新算法
任务编排实现：python-service/app.py 的 `_run_adversarial_task()`。  

1. 成功判定逻辑：

$$
success_t = \mathbb{I}(attack\_signal_t > \theta_t)
$$

2. 阈值更新：

$$
θ_{t+1}=\mathrm{clip}(θ_t+\Delta_t,0.38,0.92),\ 
\Delta_t=\begin{cases}0.03,&success_t=1\\0.016,&success_t=0\end{cases}
$$

3. 防御强度更新：

$$
S_{t+1}=\mathrm{clip}(S_t+100\cdot\delta_t,40,99),\ 
\delta_t=\begin{cases}0.02,&success_t=1\\0.012,&success_t=0\end{cases}
$$

4. 解释：当前为规则驱动自适应，不是强化学习/多臂老虎机。

#### 3.4.7.5 核心流程联动与存证边界
1. 告警处置联动：`/api/alert-center/dispose` 可在处置时触发攻防验证，结果与 `governance_event_id` 关联写入 `adversarial_record`。  
2. 任务级持久化：`adversarial_task`、`adversarial_round_metric`、`adversarial_event_log`、`adversarial_model_update`、`adversarial_report` 记录全流程轨迹。  
3. 审计联动：攻防演练统计可在审计报告中汇总（`adversarialRuns` 指标）。  
4. 边界说明：当前版本未实现“自动生成策略并自动发起审批请求”的接口，策略加固通过 `adversarial/apply-hardening` 在人工确认后执行。

#### 3.4.7.6 可复现性与确定性声明
1. `seed` 保证的是伪随机过程可复现（同代码版本、同参数、同初始模型快照前提下）。  
2. 当训练数据、模型权重或代码版本变化时，结果不保证逐位一致，这是符合真实模型演进特性的。  
3. 文档中“可复现”仅用于实验重放与对比评估，不等同于“模型永远确定性输出”。

### 3.4.8 核心接口一览表（路径/输入/输出/异常）

表3-5 核心接口摘要（外部网关口径）

| 外部接口（Backend） | 内部转发（Python） | 关键输入 | 关键输出 | 典型异常/降级 |
|---|---|---|---|---|
| GET /api/risk/forecast | POST /predict/risk | history series, horizon | forecast, method | Python不可用时 trend_fallback |
| POST /api/ai/train/factory | POST /train/factory | datasetFile | 训练指标、候选发布信息 | 文件缺失/格式错误 |
| POST /api/ai/train/adversarial-feedback | POST /train/adversarial-feedback | backendBaseUrl, maxSamples | 对抗反馈训练结果 | 外部数据构建失败返回错误 |
| GET/POST /api/ai/adversarial/* | /api/adversarial/* | scenario, rounds, seed | battle、meta、task report | 对抗执行异常返回失败状态 |
| GET /api/alert-center/threat-overview | - | windowHours | summary/byType/bySource/dedupe/trace | 参数越界自动收敛 |
| POST /api/client/report | - | client/host/os/services | governanceEventId、riskLevel | 客户端令牌无效/绑定失败 |
| GET /api/dashboard/ai-hub/deepseek-analysis | - | scopeLevel, department, username | analysis、source、trace | DeepSeek失败自动 fallback |

此处应插入图3.4：审计证据链结构图  
绘图方法：
1. 工具：draw.io。  
2. 结构：audit_log 节点链 + anchor 节点。  
3. 标注字段：prev_hash、current_hash、payload_hash、anchor_id。

## 3.5 APP/系统实际界面
此处应插入图3.5：系统关键界面实景图  
建议至少包含：
1. 安全指挥台。  
2. 审批中心（待我审批）。  
3. 影子 AI 发现。  
4. 审计中心报表。

---

# 第四章 测试报告

## 4.1 测试目的
验证平台在真实业务流程中的可用性、安全性和可治理性，重点评估：
1. 功能闭环是否完整。  
2. 权限边界和职责分离是否有效。  
3. 多租户隔离是否生效。  
4. 性能与稳定性是否满足使用要求。

## 4.2 测试环境
1. 前端、后端、AI 推理服务通过 Docker Compose 启动。  
2. MySQL 作为核心数据存储。  
3. 使用集成测试、E2E 结果、验收报告与证据索引进行综合验证。

## 4.3 测试数据

表4-1 功能测试

| 测试编号 | 测试步骤 | 输入数据 | 预期结果 | 测试结果 |
|---|---|---|---|---|
| F-01 | 登录进入指挥台 | 正确账号密码 | 登录成功并加载首页 | 符合预期 |
| F-02 | 发起治理变更申请 | 变更参数 | 生成 pending 审批记录 | 符合预期 |
| F-03 | 复核员审批通过/驳回 | request_id + 意见 | 状态正确流转并留痕 | 符合预期 |
| F-04 | 影子AI上报与查看 | 客户端上报数据 | 列表/统计同步更新 | 符合预期 |
| F-05 | 审计报告生成与下载 | 时间范围 | 返回报表结果与下载链接 | 符合预期 |

表4-2 权限越权测试

| 测试编号 | 场景 | 预期结果 | 实际结果 |
|---|---|---|---|
| A-01 | 非审批角色执行审批接口 | 403/拒绝 | 符合预期 |
| A-02 | 发起人与复核员同账号 | SoD 拦截 | 符合预期 |
| A-03 | 无权限访问治理写接口 | 拒绝访问 | 符合预期 |
| A-04 | 审批意见为空 | 提交失败 | 符合预期 |

表4-3 多租户隔离测试

| 测试编号 | 场景 | 预期结果 | 实际结果 |
|---|---|---|---|
| T-01 | 公司A读取公司B数据 | 无数据/拒绝 | 符合预期 |
| T-02 | 跨租户审批记录查询 | 仅本公司可见 | 符合预期 |
| T-03 | company_id 索引分页 | 稳定返回 | 符合预期 |

表4-4 性能测试（明确指标）

| 测试编号 | 场景 | 指标阈值 | 实测结果 |
|---|---|---|---|
| P-01 | 就绪度报告查询 `/api/award/readiness/report` | 单次响应 < 7000ms | 6048ms |
| P-02 | 自动修复演练 `/api/award/readiness/auto-remediate?dryRun=true` | 单次响应 < 100ms | 35ms |
| P-03 | 模型解释 `/api/ai/model-explainability` | 单次响应 < 100ms | 20ms |
| P-04 | 发布流量统计 `/api/ai/model-release/traffic-stats` | 单次响应 < 100ms | 16ms |
| P-05 | AI风险列表 `/api/ai-risk/list` | 单次响应 < 100ms | 51ms |

表4-5 算法与训练验证测试

| 测试编号 | 场景 | 输入数据 | 预期结果 | 实际结果 |
|---|---|---|---|---|
| M-01 | LSTM 风险预测接口 | 近30天风险序列 + horizon=7 | 返回 forecast 与 MAE/RMSE，且 method 为 simple/adaptive 之一 | 符合预期 |
| M-02 | 双模型择优逻辑 | 同一序列重复调用训练评估 | 以更低 RMSE 模型作为最终输出 | 符合预期 |
| M-03 | 工厂数据集训练 | /train/factory + datasetFile | 返回 datasetSamples、labelCounts、训练指标、候选发布信息 | 符合预期 |
| M-04 | 对抗反馈再训练 | /train/adversarial-feedback | 触发 include_adversarial=true 的回灌训练流程，目标模型为 sensitive_clf | 符合预期 |
| M-05 | 训练集切分规则验证 | 样本量<40 与 >=40 两组 | 小样本不切分；满足阈值后执行 80/20 + 分层CV | 符合预期 |
| M-06 | 预测合理性断言 | 最近90天风险序列 | 预测值非负；与最近7日均值偏差不超过30%（展示阈值） | 符合预期 |

表4-6 实时威胁与影子AI验证测试

| 测试编号 | 场景 | 输入数据 | 预期结果 | 实际结果 |
|---|---|---|---|---|
| R-01 | threat-overview 去重口径 | windowHours=72 | 返回 rawTotal、uniqueTotal、collapsed 且口径一致 | 符合预期 |
| R-02 | 影子AI上报链路 | client/report 上报服务列表 | 生成 riskLevel 并写入 governanceEventId | 符合预期 |
| R-03 | 风险雷达五维评分 | profile/radar | 返回五维分项与 totalRisk 加权总分 | 符合预期 |
| R-04 | 非管理员可见范围 | 普通角色请求列表/统计 | 仅返回本人或授权范围数据 | 符合预期 |

表4-7 攻防对打稳定性验证测试

| 测试编号 | 场景 | 输入数据 | 预期结果 | 实际结果 |
|---|---|---|---|---|
| B-01 | 固定 seed 可复现实验 | scenario + rounds + seed | 关键趋势（成功率/拦截率）可复现 | 符合预期 |
| B-02 | 多轮自适应阈值更新 | rounds>=10 | 输出 adaptive_threshold、defense_strength_score 连续变化 | 符合预期 |
| B-03 | 每3轮反馈微调 | 长轮次任务 | 训练日志出现 fine-tune 阶段记录 | 符合预期 |
| B-04 | 报告生成完整性 | task 执行完成 | 生成 rounds/keyChanges/optimizationSuggestions | 符合预期 |
| B-05 | 确定性边界验证 | 固定seed但更换模型快照 | 指标趋势接近但不要求逐位一致 | 符合预期 |

表4-8 DeepSeek 主链与降级验证测试

| 测试编号 | 场景 | 输入数据 | 预期结果 | 实际结果 |
|---|---|---|---|---|
| D-01 | DeepSeek 主链成功 | ai-hub/deepseek-analysis | source=deepseek-chat，返回结构化解读 | 符合预期 |
| D-02 | DeepSeek 调用失败降级 | 模拟 provider 不可用 | source=deepseek-fallback，返回 warning 与本地聚合建议 | 符合预期 |

表4-9 治理增强模块验证测试

| 测试编号 | 场景 | 输入数据 | 预期结果 | 实际结果 |
|---|---|---|---|---|
| G-01 | 模型谱系查询 | GET /api/ai/model-lineage | 返回 lineage 结构或可用性状态 | 符合预期 |
| G-02 | 模型发布状态查询 | GET /api/ai/model-release/status | 返回 stable/canary/候选信息 | 符合预期 |
| G-03 | 阈值加固执行 | POST /api/ai/adversarial/apply-hardening | 返回阈值变更前后、训练反馈状态 | 符合预期 |
| G-04 | 告警处置触发攻防验证 | dispose + triggerSimulation=true | 写入 adversarial_record 并关联 governance_event_id | 符合预期 |
| G-05 | 运维观测可用性 | GET /api/ai/model-release/traffic-stats | 返回 totalRequests 与流量统计结构 | 符合预期 |
| G-06 | 就绪度与证据归档 | GET /api/award/readiness/report | 返回 implemented/total/score 等就绪度字段 | 符合预期 |
| G-07 | 哈希链验真 | GET /api/ai/monitor/logs/verify-chain | 返回 passed=true 且 violationCount=0 | 符合预期 |
| G-08 | 外部锚定查询 | GET /api/award/external-anchor/verify?payloadHash=... | 返回 found=true/verifyStatus 字段 | 符合预期 |

表4-10 安全性测试（平台自身）

| 测试编号 | 场景 | 输入数据 | 预期结果 | 实际结果 |
|---|---|---|---|---|
| S-01 | SQL注入防护 | 查询参数包含 `' OR 1=1 --` | 参数被安全处理，不出现越权数据泄露 | 符合预期 |
| S-02 | XSS防护 | 提交 `<script>alert(1)</script>` | 返回内容被转义/过滤，不执行脚本 | 符合预期 |
| S-03 | CSRF与未授权访问 | 无令牌直接调用写接口 | 返回 401/403，写操作拒绝 | 符合预期 |
| S-04 | 越权访问 | 低权限访问管理员接口 | 拒绝访问（403） | 符合预期 |

表4-11 性能指标与实测（readiness 抽样）

| 测试编号 | 接口 | 指标阈值 | 实测值 | 结论 |
|---|---|---|---|---|
| P-01 | /api/award/readiness/report | 单次响应 < 7000ms | 6048ms | 通过 |
| P-02 | /api/award/readiness/auto-remediate(dry-run) | 单次响应 < 100ms | 35ms | 通过 |
| P-03 | /api/ai/model-explainability | 单次响应 < 100ms | 20ms | 通过 |
| P-04 | /api/ai/model-release/traffic-stats | 单次响应 < 100ms | 16ms | 通过 |
| P-05 | /api/ai-risk/list | 单次响应 < 100ms | 51ms | 通过 |

注：当前性能数据来自治理就绪度验收脚本的抽样结果（非满载压测）；吞吐量/TPS 作为后续专项压测项。

## 4.4 用户反馈（量化）
基于试运行与验收数据（非主观口述）：
1. 治理就绪度验收：`totalChecks=6`、`passedChecks=6`、`failedChecks=0`、`passed=true`。  
2. 角色权限矩阵回归：`totalChecks=140`、`mismatchCount=0`。  
3. 职责分离回归：`mismatchCount=0`、`serverErrorCount=0`。  
4. 哈希链验真抽样：`passed=true`、`violationCount=0`（见证据索引）。  

样本与边界说明：
1. 当前反馈样本来自管理员/安全运维/审计角色的联调与验收脚本。  
2. 该结果可证明“流程可用与规则有效”，不等同于长期生产SLA结论。

此处应插入图4.1：测试与验收结果汇总图  
绘图方法：
1. 工具：Excel 或 Power BI。  
2. 图形：柱状图（通过/失败）、雷达图（功能/安全/隔离/性能）。

---

# 第五章 安装及使用

## 5.1 安装注意事项
1. 部署前确认 Docker、数据库与端口占用。  
2. 配置文件中密钥、地址、阈值参数需按环境调整。  
3. 多租户环境需确保 company 初始化与默认角色绑定完成。

## 5.2 安装说明
1. 获取项目代码。  
2. 准备 `.env.local`（后端容器会读取），至少确认以下变量：  

表5-1 关键环境变量

| 变量名 | 示例值 | 说明 |
|---|---|---|
| AI_INFERENCE_URL | http://ai-inference:5000 | 后端到Python服务地址 |
| SECURITY_JWT_SECRET | 长随机串（>=32） | JWT签名密钥 |
| CLIENT_INGRESS_ENFORCE | true | 是否强制客户端入口校验 |
| CLIENT_INGRESS_TOKEN | aegis-client-ingress-local-dev | 客户端上报令牌 |
| CLIENT_INGRESS_DEFAULT_COMPANY_ID | 1 | 默认租户ID |

3. 启动基础服务（MySQL/Redis/ES/RabbitMQ/AI）：

```bash
docker compose up -d mysql redis es rabbitmq ai-inference
```

4. 启动后端与前端：

```bash
docker compose up -d --build aegisai-backend aegisai-frontend
```

5. 健康检查：

```bash
curl http://localhost:8080/api/auth/registration-options
curl http://localhost:3000
```

6. 初始化数据（如需重置）：通过 `aegisai.sql` 与 `docker/mysql/bootstrap` 自动迁移完成基础结构与兼容补丁。

### 5.2.1 最小部署配置片段（示例）
```yaml
aegisai-backend:
	environment:
		SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/aegisai?useSSL=false
		SPRING_DATASOURCE_USERNAME: root
		SPRING_DATASOURCE_PASSWORD: root
		AI_INFERENCE_URL: http://ai-inference:5000
		CLIENT_INGRESS_TOKEN: aegis-client-ingress-local-dev
```

## 5.3 使用说明
1. 管理员登录后查看首页态势。  
2. 按组织权限配置用户、角色和权限。  
3. 在治理主线处理告警、审批与处置。  
4. 在 AI 核心模块查看风险评级、漂移与发布状态。  
5. 在审计中心生成报告并进行证据验真。

## 5.4 客户端部署与注册（影子AI采集）
1. 客户端形态：Electron Agent（位于 `electron/`）。  
2. 安装步骤（开发环境）：

```bash
cd electron
npm install
npm run start
```

3. 客户端配置项：
- backendUrl（如 `http://localhost:8080`）  
- clientToken（需与后端 `CLIENT_INGRESS_TOKEN` 一致）  
- companyId（默认 1）  
- scanIntervalMinutes（默认 30）  

4. 注册与上报流程：
- 首次调用 `/api/client/register` 绑定 clientId 与账号。  
- 周期扫描后调用 `/api/client/report` 上报发现结果。  
- 上报失败写入本地 pending 队列并在下次扫描自动重试。  

5. 验证方式：在后台“影子AI发现”页面检查最新 `client_id`、`scan_time`、`riskLevel` 是否更新。

此处应插入图5.1：部署与使用流程图  
绘图方法：
1. 工具：draw.io。  
2. 步骤块：安装、初始化、登录、治理执行、审计验真。

---

# 第六章 项目总结

## 6.1 项目总结
### 6.1.1 项目进度
1. 平台核心功能已完成并可运行。  
2. 治理闭环、角色边界、租户隔离和审计验真已形成可验证链路。  
3. 已形成测试与证据文件体系，支持展示与答辩抽查。

### 6.1.2 项目收获
1. 打通了 AI 治理从检测到处置的工程闭环。  
2. 建立了可复核的证据链实践（哈希链+外部锚定）。  
3. 在多角色协同场景中沉淀了职责分离与审批机制经验。

## 6.2 项目展望
1. 扩展漂移检测指标，增强解释性分析。  
2. 增加更长周期时序数据，提升 LSTM 风险预测稳定性。  
3. 补充大规模压测与容量评估报告。  
4. 继续强化攻防演练自动化与策略自适应能力。

## 6.3 指导老师自评（建议填写）
该作品围绕企业 AI 安全治理需求，形成了较完整的端云协同技术方案，在风险识别、审计追溯与攻防演练联动方面具有一定创新性。系统实现较为完整，覆盖基础平台、组织权限、治理主线、AI 核心与攻防运维等核心模块。项目已完成多轮功能、权限、隔离与稳定性验证，关键验收指标包括：治理就绪度检查 6/6 通过、角色权限矩阵检查 140 项零偏差、职责分离回归零冲突、哈希链验真抽样通过。学生团队分工清晰、协作有效，工程实现与问题闭环能力较强。总体达到预期培养目标，成果质量较好，具有一定应用与推广价值，同意参赛。

---

（完）
