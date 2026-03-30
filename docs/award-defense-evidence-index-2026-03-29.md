# 答辩证据索引表（2026-03-29）

| 主张 | 证据类型 | 证据位置 | 验证方式 |
|---|---|---|---|
| 7 身份登录与会话稳定 | 集成测试 | backend/src/test/java/com/trustai/integration/FullRoleLoginRegressionIntegrationTest.java | runTests 结果 passed=2 failed=0 |
| 员工登录异常闭环 | 后端修复代码 | backend/src/main/java/com/trustai/controller/AuthController.java | 检查 verifyPasswordAndUpgradeIfNeeded 路径 |
| 登录页 7 身份可视化 | 前端修复代码 | src/views/Login.vue | 检查 identity-support 与 DEFAULT_IDENTITIES |
| 门禁全绿（核心集成） | 测试结果 | 7 个 integration 用例集合 | runTests 汇总 passed=22 failed=0 |
| 评测口径固定可复现 | 评测接口 | backend/src/main/java/com/trustai/controller/AwardEvidenceController.java | GET /api/award/evaluation/fixed-package |
| 固定包可验真 | 哈希+锚定 | backend/src/main/java/com/trustai/service/AwardEvidenceService.java | 检查 packageHash 与 externalAnchor |
| 防抵赖签名链 | 签名服务 | backend/src/main/java/com/trustai/service/ExternalAnchorService.java | 检查 RSA 签名、verify、落库 |
| 外部锚定可查询 | 审计接口 | /api/award/external-anchor/latest, /api/award/external-anchor/verify | 在线查询 payloadHash 对应锚点 |
| 观测指标可追踪 | 观测服务 | backend/src/main/java/com/trustai/service/OpsTelemetryService.java | 查看 web-vitals 与 http-history 查询结果 |
| 证据包可导出 | 交付脚本 | scripts/generate-award-review-package.ps1 | 运行脚本生成 docs/award-package 输出 |

## 附：建议归档物
- 固定评测包 JSON
- 证据导出结果 JSON
- 外部锚定列表 JSON
- 对应 packageHash、payloadHash 与 anchorId 对照清单
