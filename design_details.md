# AegisAI 系统级详尽说明（24项）

> 文档口径：基于当前仓库实际代码与配置整理，尽量避免推测。无法从代码直接确认的项已显式标注“需现场确认”。
> 版本时间：2026-04（由当前工作区代码快照生成）

> 2026-04-16 对齐说明（本次修订）：
> - 客户端身份绑定已明确为「X-Client-Token + X-Client-Username + 租户上下文」联合校验口径。
> - 客户端下载链路已明确为「后端自动识别各平台最新安装包」而非固定版本号。
> - Web 下载页已对接 /api/download/info 回显当前版本与文件名。

---

## 1. 系统定位与核心目标
AegisAI 是一套“企业内 AI 安全治理工作台”，核心目标不是单点检测，而是“发现 -> 告警 -> 审批 -> 处置 -> 审计取证 -> 外部锚定”的闭环治理。

- 业务目标：降低影子AI、异常行为、提示注入、数据外泄等风险。
- 技术目标：支持多角色运营（ADMIN/SECOPS/BUSINESS_OWNER/AUDIT 等）、多租户公司隔离、可追溯审计链。
- 合规目标：输出可复验证据包（哈希链、外部锚定、可靠性演练记录）。

证据来源：
- `README.md`
- `backend/src/main/java/com/trustai/controller/AwardEvidenceController.java`
- `backend/src/main/java/com/trustai/service/NationalAwardReadinessService.java`

---

## 2. 总体架构（前端/后端/AI服务/基础设施）
系统采用前后端分离 + AI微服务：

- 前端：Vue3 + Element Plus + ECharts（工作台可视化）。
- 后端：Spring Boot + MyBatis-Plus + JWT + RBAC + 规则治理。
- AI服务：Flask（分类模型、LSTM预测、漂移检测、对抗仿真任务引擎）。
- 中间件：MySQL、Redis、RabbitMQ、Elasticsearch。
- 部署：Docker Compose 一键拉起多容器。

证据来源：
- `docker-compose.yml`
- `backend/src/main/resources/application.yml`
- `python-service/app.py`

---

## 3. 主要业务模块
按能力分层，可划分为 8 个域：

1) 身份与权限：登录、角色授权、租户隔离。  
2) 告警中心：威胁聚合、统计、处置动作。  
3) 员工异常行为检测：IsolationForest 异常判定。  
4) 影子AI发现：客户端上报终端扫描结果。  
5) AI网关与攻防：模型调用审计、攻防任务、硬化建议。  
6) 审批中心：工单流转与待办。  
7) 审计与证据：审计日志、哈希链、证据包导出。  
8) 治理成熟度：readiness 报告与自动修复编排。

证据来源：
- `backend/src/main/java/com/trustai/controller/ClientReportController.java`
- `backend/src/main/java/com/trustai/controller/ClientDownloadController.java`
- `src/views/ShadowAiDiscovery.vue`
- `backend/src/main/java/com/trustai/controller/SecurityCockpitController.java`
- `backend/src/main/java/com/trustai/controller/AwardEvidenceController.java`

---

## 4. 角色模型与职责
代码中实际出现/校验的关键角色包括：

- ADMIN：系统管理与高权限操作。
- ADMIN_REVIEWER：治理审核类视角。
- SECOPS：安全运营处置。
- BUSINESS_OWNER：业务负责人视角。
- AUDIT：审计取证与报告查看。
- 兼容历史角色：SEC、DATA_ADMIN 等（见初始化 SQL）。

典型授权方式：`@PreAuthorize("@currentUserService.hasAnyRole(...)")`。

证据来源：
- `backend/src/main/java/com/trustai/controller/*.java`（多个控制器）
- `aegisai.sql`（role/user 初始数据）

---

## 5. 端到端闭环（核心流程）
已实现的闭环链路（从代码与近期验证结果可复现）：

1) 客户端登录态上报（`/api/client/report`）。  
2) 事件入治理域（生成 governance event / security event）。  
3) 告警中心与安全驾驶舱可见（recent/stats/拓扑）。  
4) 审批中心可流转（pending -> reviewed/approved）。  
5) 审计证据可导出（compliance + hash chain + anchor）。
6) 客户端下载自动指向最新资源（`/api/download/info` + `/api/download/client/*`）。

证据来源：
- `backend/src/main/java/com/trustai/controller/ClientReportController.java`
- `backend/src/main/java/com/trustai/controller/SecurityCockpitController.java`
- `backend/src/main/java/com/trustai/controller/AwardEvidenceController.java`

---

## 6. 数据库主存储与版本口径
当前仓库存在多份 schema 来源：

- 主初始化：`aegisai.sql`
- 容器增量补丁：`docker/mysql/bootstrap/*.sql`
- 运行时兜底建表：`AwardSchemaInitializer`（`CREATE TABLE IF NOT EXISTS`）

建议口径：
- 以“aegisai.sql + bootstrap增量 + AwardSchemaInitializer运行兜底”作为生产解释口径。
- 对历史文档中的旧表结构说明需标注版本差异。

证据来源：
- `aegisai.sql`
- `docker/mysql/bootstrap/003-simulation-events.sql`
- `backend/src/main/java/com/trustai/config/AwardSchemaInitializer.java`

---

## 7. 核心数据表（治理与安全）
可直接确认的关键表（非全量）：

- `governance_event`：治理事件中心。  
- `security_event`：安全事件。  
- `approval_request`：审批请求。  
- `audit_log`：审计日志。  
- `ai_call_log`：AI调用日志。  
- `simulation_events`：仿真事件（已补齐缺表）。  
- `audit_hash_chain` / `ai_call_hash_chain`：不可篡改链。  
- `external_anchor_record`：外部锚定记录。

证据来源：
- `aegisai.sql`
- `docker/mysql/bootstrap/003-simulation-events.sql`
- `backend/src/main/java/com/trustai/config/AwardSchemaInitializer.java`

---

## 8. 客户端接入机制（下载/登录态上报/策略快照）
主要接口：

- `GET /api/client/policy/snapshot`：拉取策略快照（能力位、TTL、checksum）。
- `POST /api/client/report`：上报扫描结果并去重。
- `GET /api/client/list|history|stats`：管理端查看。
- `GET /api/download/info`：返回各平台当前可下载安装包元信息（文件名/版本/URL）。
- `GET /api/download/client/windows|macos|linux`：按平台下载客户端安装包。

关键安全点：
- 支持 `X-Client-Token` + `X-Client-Username` 联合校验，并结合租户上下文绑定账号。
- report 去重与设备指纹生成已实现。

接入口径（2026-04-16）：
- Electron 客户端采用“登录态后直接上报”流程（`POST /api/client/report`）。
- 后端已下线 `POST /api/client/register`，统一以登录态绑定与上报接口为准。

关键可用性点：
- 下载控制器按 classpath 资源模式自动选择最新版本包，不再固定写死单一版本号。
- 下载响应附带 no-store/no-cache 头，降低浏览器命中旧安装包概率。

证据来源：
- `backend/src/main/java/com/trustai/controller/ClientReportController.java`

---

## 9. 告警与安全驾驶舱能力
驾驶舱接口覆盖：概览、部门热力、小时趋势、拓扑、实时流。

- 概览：今日威胁总量、阻断数、未处置数、高风险用户数。
- 趋势：按小时桶聚合 blocked/pending/ignored。
- 拓扑：source IP -> target 聚合，附风险等级与事件 IDs。
- 实时：SSE 推送新增告警。

证据来源：
- `backend/src/main/java/com/trustai/controller/SecurityCockpitController.java`

---

## 10. AI 网关与模型访问控制
AI网关服务具备：

- 模型目录过滤（仅可信模型关键字目录）。
- 调用前访问校验（资产、访问理由、消息内容）。
- 调用后响应外泄扫描（response exfiltration scan）。
- 调用日志异步写入审计链。

证据来源：
- `backend/src/main/java/com/trustai/service/AiGatewayService.java`

---

## 11. 模型分类算法（规则 + ML + BERT）
Python 服务采用分层融合：

1) Regex 基线（结构化高精度）。  
2) 手工特征 + LogisticRegression。  
3) BERT（微调优先，失败回退 zero-shot）。  
4) 融合逻辑：同标签时融合置信度，冲突时按策略回退。

核心评分：

$$
score_{ensemble} = \min\left(1,\ \frac{score_{ml}+score_{bert}}{2}+0.05\right)
$$

证据来源：
- `python-service/app.py`

---

## 12. LSTM 风险预测算法
预测模块同时训练 `SimpleLSTM` 和 `AdaptiveAttentionLSTM`，以验证集 RMSE 选优。

- 输入：时序风险序列。
- 输出：未来 `horizon` 步预测 + MAE/RMSE。
- 少样本自动降级：均值或 recent-value fallback。

模型选择准则：

$$
model^* = \arg\min_{m \in \{simple, adaptive\}} RMSE_m
$$

证据来源：
- `python-service/app.py`（`forecast_risk`）

---

## 13. 漂移检测（三元漂移）
实现了 label/performance/business KPI 三元漂移：

- 标签分布漂移：L1 距离。
- 性能漂移：近期平均置信度与基线性能差。
- 业务漂移：unknown 占比偏移。

标签分布漂移定义：

$$
D_{label} = \frac{1}{2} \sum_i |p_i - q_i|
$$

告警阈值在代码中默认：
- `DRIFT_ALERT_THRESHOLD = 0.35`
- performance drift >= 0.15
- business KPI drift >= 0.20

证据来源：
- `python-service/app.py`

---

## 14. 模型发布与灰度机制
发布管理具备完整动作：

- 注册候选：`/model-release/register-candidate`
- 提升 canary：`/model-release/promote-canary`
- 提升 stable：`/model-release/promote-stable`
- 回滚：`/model-release/rollback`
- 流量统计：`/model-release/traffic-stats`

灰度路由为文本哈希桶：

$$
bucket = \text{MD5}(text)\ \bmod\ 100
$$

若 `bucket < canaryPercent` 则走 canary。

证据来源：
- `python-service/app.py`

---

## 15. 攻防仿真引擎（OpenClaw）
系统内置攻防仿真，支持两层：

- 离线策略引擎：`openclaw_adversarial.py`（攻击/防御策略矩阵）。
- 在线任务编排：`/api/adversarial/task/*`（多轮训练、日志、报告）。

效果矩阵定义（攻击突破率）：

$$
E(a,d) \in [0,1]
$$

其中 $E$ 越高表示攻击越易突破防御。

证据来源：
- `python-service/openclaw_adversarial.py`
- `python-service/app.py`

---

## 16. 自适应阈值更新逻辑（攻防任务）
对抗任务会根据轮次攻击信号更新阈值配置：

- 维护 rolling signals。
- 计算 `p60/p90` 分位数。
- 动态更新 medium/high/critical thresholds。

关键更新形式（代码近似）：

$$
T_{new} = \text{clip}(0.72\cdot T_{old} + 0.28\cdot T_{target} + \Delta_{shift})
$$

证据来源：
- `python-service/app.py`（`_persist_adversarial_threshold_profile`, `_run_adversarial_task`）

---

## 17. 异常行为检测（员工AI行为）
采用 IsolationForest 对员工行为进行异常检测：

- 特征：部门、服务、时段、会话时长、消息长度等。
- 输出：`is_anomaly` + `anomaly_score` + risk level。
- 日志：SQLite `anomaly_events.db`。

阈值默认：
- high: score < -0.15
- medium: score < -0.05

证据来源：
- `python-service/app.py`

---

## 18. 审计哈希链（治理可追溯）
两条链路：

- 审计日志链：`audit_hash_chain`
- AI调用链：`ai_call_hash_chain`

链式哈希核心：

$$
H_i = SHA256(payload_i \| H_{i-1})
$$

其中 `payload_i` 包含 company、logId、user、status、time 等字段。

证据来源：
- `backend/src/main/java/com/trustai/service/AwardEvidenceService.java`
- `backend/src/main/java/com/trustai/service/impl/AiCallAuditServiceImpl.java`

---

## 19. 外部锚定与签名验真
外部锚定服务流程：

1) 构造 canonical 字符串（company/evidence/hash/time/nonce）。
2) 本地 RSA 私钥签名（SHA256withRSA）。
3) 公钥验证并落库 `external_anchor_record`。
4) 可按 `payloadHash` 反查验证状态。

说明：默认 time source 使用 worldtimeapi，失败会降级本地时间。

证据来源：
- `backend/src/main/java/com/trustai/service/ExternalAnchorService.java`

---

## 20. 可靠性演练与SLO
系统包含可靠性 drill：

- 探测 targetPath/injectPath 的可用性与延迟。
- 计算 SLI（availability、p95 latency、recovery）。
- SLO 违约时自动生成治理告警并记录 drill。

典型阈值：
- availability >= 99.9%
- p95 <= 800ms
- recovery <= 60s

证据来源：
- `backend/src/main/java/com/trustai/service/AwardEvidenceService.java`

---

## 21. 自动修复编排（Auto Remediation）
readiness 服务支持自动化治理动作（可 dry-run）：

- 阻断高危 pending/open/reviewing 事件。
- 自动生成审批单。
- 可对高频高危用户冻结账户。
- 漂移告警触发可尝试自动回滚 stable 模型。

证据来源：
- `backend/src/main/java/com/trustai/service/NationalAwardReadinessService.java`

---

## 22. 部署拓扑与运行配置
docker-compose 实际定义 8 类服务：

1) mysql  
2) redis  
3) elasticsearch  
4) ai-inference  
5) rabbitmq  
6) mysql-bootstrap  
7) aegisai-backend  
8) aegisai-frontend

关键连通：
- 后端通过 `AI_INFERENCE_URL=http://ai-inference:5000` 调用 AI 服务。
- 后端健康检查使用 `/api/auth/registration-options`。
- Web 端客户端分发页通过 `/api/download/info` 获取当前版本元信息。

证据来源：
- `docker-compose.yml`
- `backend/src/main/resources/application.yml`

---

## 23. 默认账号与初始化数据说明
从文档与 SQL 可见存在两套信息源（需区分环境）：

- README 开发说明：默认 `admin/admin`（偏本地演示口径）。
- `aegisai.sql`：存在 `admin/admin123`、`sec01/secpass`、`data01/datapass`、`audit01/auditpass` 等初始化记录（含明文示例与一条 bcrypt 记录并存）。

结论：不同脚本/版本下初始化账号口径不一致，建议上线前统一为“强制初始化改密 + 全量bcrypt”。

证据来源：
- `README.md`
- `aegisai.sql`

---

## 24. 团队分工、参考标准与改进清单
### 24.1 可从代码反推的职责分工（建议文档口径）
- 前端组：工作台页面、可视化大屏、组件动效。  
- 后端组：权限、治理流程、审计链、证据导出。  
- 算法组：分类模型、LSTM、漂移、攻防引擎。  
- 运维组：容器编排、配置治理、健康检查、回归脚本。

### 24.2 已映射的参考标准
- PIPL / DSL / GB/T 35273（在合规映射接口中有明确字段）。
- NIST AI RMF / ISO 27001 / MLPS 2.0（在行业对标接口给出覆盖声明）。

### 24.3 当前改进优先级（基于代码现状）
1) 账号初始化口径统一（去明文密码）。
2) 把 `CLIENT_INGRESS_TOKEN` 等敏感配置迁移到安全密钥管理。  
3) 漂移与回滚策略增加灰度保护条件（避免误回滚）。  
4) 为关键 SQL 版本差异建立 migration 总清单。  
5) 对外提交材料时补“实验复现实验脚本 + 固定输入样本包”。

证据来源：
- `backend/src/main/java/com/trustai/service/AwardEvidenceService.java`
- `backend/src/main/java/com/trustai/service/NationalAwardReadinessService.java`
- `aegisai.sql`
- `README.md`

---

# 附录A：关键接口清单（高频）

## A.1 后端（Spring）
- `/api/client/policy/snapshot`
- `/api/client/report`
- `/api/client/list`
- `/api/client/stats`
- `/api/download/info`
- `/api/download/client/windows`
- `/api/download/client/macos`
- `/api/download/client/linux`
- `/api/security-cockpit/overview`
- `/api/security-cockpit/trend/hourly`
- `/api/security-cockpit/topology`
- `/api/security-cockpit/alerts/stream`
- `/api/award/compliance-evidence/generate`
- `/api/award/audit-hash-chain/build`
- `/api/award/external-anchor/latest`
- `/api/award/external-anchor/verify`
- `/api/award/readiness/report`
- `/api/award/readiness/auto-remediate`

## A.2 Python（Flask）
- `/predict`
- `/predict/risk`
- `/drift/status`
- `/model-release/status`
- `/model-release/traffic-stats`
- `/api/anomaly/check`
- `/api/anomaly/events`
- `/api/adversarial/meta`
- `/api/adversarial/task/start`
- `/api/adversarial/task/status`
- `/api/adversarial/task/logs`
- `/api/adversarial/task/report`

---

# 附录B：本次文档可信度标注
- 高可信（代码直接可证）：接口、算法流程、阈值、表名、部署组件。
- 中可信（代码+README混合）：默认账号说明、开发运行口径。
- 需现场确认：组织成员姓名分工、生产环境最终参数、真实线上模型效果指标。
