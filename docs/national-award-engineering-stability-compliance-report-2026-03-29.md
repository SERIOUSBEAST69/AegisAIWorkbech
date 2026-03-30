# 国家级奖项工程稳定性合规报告（2026-03-29）

## 1. 目标
- 建立可复现、可审计、可验真的工程稳定性证据链。
- 完成奖项关键能力补齐：固定口径评测包、外部锚定、防抵赖签名。

## 2. 已落地能力

### 2.1 固定口径评测包
- 新增接口：GET /api/award/evaluation/fixed-package
- 固定项包含：
  - baseline/current 固定时间窗
  - 固定采样策略（事件、哈希链、观测窗口）
  - 固定代码版本字段 evaluationCodeVersion
  - 固定环境元信息（Java/OS/时区/端口/AI 推理地址）
- 输出 packageHash，并自动做 external anchor。

### 2.2 外部锚定与防抵赖
- 新增服务：ExternalAnchorService
- 机制：可信时间源 + RSA-SHA256 签名 + 锚定记录持久化
- 新增接口：
  - GET /api/award/external-anchor/latest
  - GET /api/award/external-anchor/verify?payloadHash=...
- 新增表：external_anchor_record

### 2.3 奖项证据链扩展
- AwardEvidenceService 对以下产物追加 externalAnchor：
  - 合规证据
  - 审计哈希链
  - 证据包导出
  - 固定评测包

## 3. 稳定性门禁验证
- 集成测试集合执行：
  - ValidationGuardIntegrationTest
  - PermissionMatrixIntegrationTest
  - FullRoleLoginRegressionIntegrationTest
  - DashboardSecurityIntegrationTest
  - AwardAndObservabilityIntegrationTest
  - AuthAndOpsStabilityIntegrationTest
  - AccountApprovalIntegrationTest
- 汇总结果：passed=22, failed=0
- 结论：当前门禁集合全绿。

## 4. 重要工程修复
- 补齐 test schema 的关键表与字段（含 award/ops/governance 相关）。
- 修复 H2 下观测 SQL 兼容性（避免保留字别名冲突）。
- 为 ops telemetry 增加自愈式建表 ensureOpsTables，降低初始化顺序耦合。

## 5. 交付结论
- 工程稳定性证据链满足答辩所需的可追溯、可复现实验与可验证签名要求。
- 登录、观测、奖项证据主链路已形成闭环。
