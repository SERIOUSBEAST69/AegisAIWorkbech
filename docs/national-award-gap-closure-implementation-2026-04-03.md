# 治理差距闭环落地说明（2026-04-03）

本文对应 8 项差距的工程化落地，面向治理管理员（admin）与公司接手团队。

## 1) 从看板到自动处置（已落地）

- 新增能力：自动处置 playbook（限权/冻结/工单/通知/回滚编排）
- 接口：
  - `POST /api/award/readiness/auto-remediate`
  - `GET /api/award/readiness/auto-remediate/last`
- 主要动作：
  - 对高危待处置治理事件自动 `blocked`
  - 自动生成审批工单（`approval_request`）
  - 高频高危用户自动冻结（`account_status=frozen`）
  - 触发审计日志留痕
  - 满足条件时触发模型回滚
- 公司接手后能干啥：
  - 通过定时任务和接口把“发现风险”升级为“自动执行处置”

## 2) 漂移与效果评估三位一体（已落地）

- 新增能力：`triad drift`
  - 标签分布漂移（label drift）
  - 性能漂移（confidence vs baseline）
  - 业务 KPI 漂移（unknown 占比偏移）
- 新增反馈闭环：
  - `POST /predict/feedback` 上报 `predictedLabel/trueLabel/group`
  - 输出误报漏报：FP/FN/FPR/FNR
- 接口：
  - `GET /drift/status`（含 triad + autoRollback）
  - `POST /predict/feedback`
- 公司接手后能干啥：
  - 用真实反馈持续校准阈值并统计误报漏报

## 3) 发布策略与在线流量联动（已落地）

- 新增能力：
  - 在线请求按照 canary traffic 做稳定/金丝雀分桶
  - 自动记录 A/B 流量与均值分数对比
- 接口：
  - `GET /model-release/traffic-stats`
  - 现有发布链：candidate/canary/stable/rollback
- 公司接手后能干啥：
  - 观察 canary 与 stable 实际流量表现，形成发布决策依据

## 4) 外部可审计证据体系（已落地并增强可调用）

- 已有能力：
  - 外部锚定签名（RSA + 时间源）
  - hash chain 构建与核验
  - evidence package 导出
- 接口：
  - `GET /api/award/external-anchor/latest`
  - `GET /api/award/external-anchor/verify`
  - `POST /api/award/export`
- 公司接手后能干啥：
  - 直接输出第三方可复验材料（签名+哈希+时间锚）

## 5) 多租户隔离与红线证明（已落地并纳入报告）

- 已有能力：
  - CompanyScope 强制隔离
  - `TenantConsistencyIntegrationTest`
  - `role-isolation` e2e
- 新增汇总：就绪度报告中纳入 tenant isolation 证据摘要
- 接口：
  - `GET /api/award/readiness/report`
- 公司接手后能干啥：
  - 周期性输出隔离证明，而非临时跑测试

## 6) Explainability 升级（已落地）

- 新增能力：
  - 全局特征重要性（global importance）
  - 分群解释（segment explainability）
  - 公平性监控（group accuracy disparity）
- 接口：
  - `GET /explainability/report`
- 公司接手后能干啥：
  - 对外解释模型依据并监控群体偏差风险

## 7) 工程韧性与 SLO（已落地并纳入就绪度）

- 已有能力：
  - 可靠性演练（reliability drill）
  - 可用性/延迟/恢复指标
- 新增汇总：
  - readiness report 输出 error budget（基于近期演练可用性）
- 接口：
  - `GET /api/award/readiness/report`
  - `POST /api/award/reliability/drill/run`

## 8) 业务价值闭环（已落地并纳入就绪度）

- 已有能力：
  - experiment KPI 对比
  - 治理事件闭环率/误报率/拦截率统计
- 新增汇总：
  - readiness report 输出 businessValue 段
- 接口：
  - `GET /api/award/readiness/report`
  - `GET /api/award/summary`

## Admin 首页可见变化

- 新增卡片：`治理就绪度闭环`
- 页面字段：
  - 已实现项/待补项
  - 错误预算
  - 自动处置状态
  - 一键“自动处置演练”按钮
- 前端文件：`src/views/Home.vue`

## 交接建议（公司接手）

1. 将 `POST /api/award/readiness/auto-remediate` 由 dry-run 切换到实执行业务流程。
2. 在业务系统对接 `POST /predict/feedback`，至少每日回灌真实标签。
3. 将 `GET /api/award/readiness/report` 接入周报自动化，作为治理 KPI 主报表。
4. 将证据导出与 hash-chain 核验脚本纳入月度审计固定流程。
