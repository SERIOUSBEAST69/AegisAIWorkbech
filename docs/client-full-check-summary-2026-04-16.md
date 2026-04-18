# 客户端全功能 + 全流程 + 权限校验报告（2026-04-16）

## 1. 执行范围
- 五身份演示账号登录与会话一致性校验
- 五身份权限矩阵校验（9 个关键接口 x 5 身份 = 45 检查）
- 检测功能全链路校验（22 项）
- 企业全流程实验脚本（邀请注册 -> 账号可见 -> 核心看板冒烟）

## 2. 五身份账号登录结果
- ADMIN: admin（通过）
- EXECUTIVE: executive（通过）
- SECOPS: secops（通过）
- DATA_ADMIN: dataadmin（通过）
- AUDIT: audit01（通过）

会话校验（/api/auth/me）：5/5 通过，roleCode 与 companyId 一致（companyId=1）。

## 3. 检测功能结果
- 总计 22 项，全部通过（22/22）
- 覆盖能力：
  - AI 监测：summary/trend/bootstrap-trace/verify-chain
  - AI 风险：ai-risk list
  - 对抗：adversarial meta/run（含 EXECUTIVE 期望拒绝校验）
  - 异常：anomaly status/check/events
  - 隐私：privacy events/config get/config update
  - 安全：security events/stats/rules
  - 客户端：client list/history/stats
  - 可靠性：award reliability drill run

## 4. 权限校验结果
- 总检查：45
- 不一致：6

不一致明细：
- DATA_ASSET_LIST
  - SECOPS 实际可访问（期望拒绝）
  - DATA_ADMIN 实际被拒绝（期望可访问）
  - AUDIT 实际可访问（期望拒绝）
- AUDIT_LOG_SEARCH
  - EXECUTIVE 实际可访问（期望拒绝）
  - SECOPS 实际可访问（期望拒绝）
  - DATA_ADMIN 实际可访问（期望拒绝）

说明：该 6 项属于“权限策略/矩阵定义不一致”，不是服务可用性故障。

## 5. 全流程实验结果（Walkthrough）
- 脚本：scripts/run-enterprise-identity-walkthrough.ps1
- 执行结果：通过
- 新邀请码：AEGIS-B1552D9605
- 新注册用户：walkthrough_20260416135407
- 流程节点：
  - Admin 登录
  - 创建邀请码
  - 基于邀请码获取注册选项
  - 邀请注册新用户
  - 校验账号可见性
  - 核心看板冒烟

## 6. 结论
- 客户端检测功能链路：通过
- 五身份登录与会话一致性：通过
- 全流程实验闭环：通过
- 主要风险点：权限策略存在 6 处不一致，建议优先修正 role->endpoint 授权映射并回归复测。

## 7. 原始结果文件
- ./docs/client-full-check-20260416-135241.json
