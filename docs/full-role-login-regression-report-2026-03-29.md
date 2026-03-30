# 全角色登录回归测试报告（2026-03-29）

## 1. 目标
- 验证 7 类身份账号均可正常登录。
- 验证登录后会话接口 /api/auth/me 返回与登录身份一致。
- 验证员工账号不再出现系统异常 50000。

## 2. 覆盖范围
- 登录接口：POST /api/auth/login
- 会话接口：GET /api/auth/me
- 角色覆盖：ADMIN, EXECUTIVE, SECOPS, DATA_ADMIN, AI_BUILDER, BUSINESS_OWNER, EMPLOYEE

## 3. 关键修复
- 后端登录兼容修复：支持历史明文密码校验，并在成功后自动升级为 BCrypt。
- 测试环境 schema 修复：补齐 sys_user.last_policy_pull_time，消除登录初始化异常。
- 前端身份展示增强：登录页显式展示 7 个身份标签，并对 registration-options 做标准化兜底。

## 4. 回归执行与结果
- 用例文件：backend/src/test/java/com/trustai/integration/FullRoleLoginRegressionIntegrationTest.java
- 执行结果：passed=2, failed=0
- 判定：通过

## 5. 接口返回校验要点（逐角色）
- 对每个角色，/api/auth/login 断言 code=20000。
- 对每个角色，登录返回 token 非空。
- 对每个角色，登录返回 user.roleCode 与目标角色一致。
- 对每个角色，/api/auth/me 返回 code=20000，且 user.username、user.roleCode 与登录身份一致。

## 6. 异常根因与处置闭环
- 根因 A：历史账号密码存储形态不一致（明文与 BCrypt 并存）导致匹配异常。
  - 处置：增加兼容校验路径 + 自动迁移为 BCrypt。
- 根因 B：测试库 schema 缺失 last_policy_pull_time 字段导致上下文初始化报错。
  - 处置：补齐 test-schema.sql 列定义。

## 7. 结论
- 7 角色登录与会话回归已通过。
- 员工账号系统异常问题在回归链路中已闭环。
