# 评审证据索引表（2026-03-29）

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
| 可靠性演练可复现 | 演练接口+脚本 | scripts/run-reliability-drill.ps1 | POST /api/award/reliability/drill/run + GET /api/award/reliability/drill/history |
| 审计哈希链可自动验真 | 校验脚本 | scripts/verify-audit-hash-chain.ps1 | 重算 current_hash + 校验 prev_hash 链路一致性 |
| 关键证据可一键归档 | 编排脚本 | scripts/run-governance-readiness-evidence.ps1 | 一次执行生成 reliability/hash-chain/package/regression 证据目录 |
| 观测指标可追踪 | 观测服务 | backend/src/main/java/com/trustai/service/OpsTelemetryService.java | 查看 web-vitals 与 http-history 查询结果 |
| 证据包可导出 | 交付脚本 | scripts/generate-award-review-package.ps1 | 运行脚本生成 docs/award-package 输出 |

## 附：建议归档物
- 固定评测包 JSON
- 证据导出结果 JSON
- 外部锚定列表 JSON
- 对应 packageHash、payloadHash 与 anchorId 对照清单

## 最新一键证据运行（2026-04-02 23:26）
- 运行目录：`docs/governance-readiness-evidence/run-20260402-232554`
- 总状态：`completed`
- 哈希链验真：`passed=true`, `totalRows=672`, `violationCount=0`
- 身份权限矩阵回归：`mismatchCount=0`
- 账号职责分离回归：`mismatchCount=0`

## 最新一键证据运行（2026-04-02 23:47）
- 运行目录：`docs/governance-readiness-evidence/run-20260402-234734`
- 总状态：`completed`
- 可靠性演练：`sloStatus=met`, `availabilityMet=true`, `latencyMet=true`, `infraMet=true`
- 哈希链验真：`passed=true`, `totalRows=702`, `violationCount=0`
- 身份权限矩阵回归：`mismatchCount=0`
- 账号职责分离回归：`mismatchCount=0`

## 可靠性稳定性补充验证（2026-04-02 23:47）
- 连续 5 轮运行 `POST /api/award/reliability/drill/run`
- 结果：`breachCount=0`（均为 `sloStatus=met`）

## 最新一键证据运行（2026-04-03 00:52）
- 运行目录：`docs/governance-readiness-evidence/run-20260403-005224`
- 总状态：`completed`
- 可靠性演练：`sloStatus=met`, `availabilityMet=true`, `latencyMet=true`, `infraMet=true`, `redis=true`
- 哈希链验真：`passed=true`, `totalRows=726`, `violationCount=0`
- 身份权限矩阵回归：`mismatchCount=0`
- 账号职责分离回归：`mismatchCount=0`

## 可靠性稳定性补充验证（2026-04-03 00:52）
- 连续 5 轮运行 `POST /api/award/reliability/drill/run`
- 结果：`breachCount=0`（均为 `sloStatus=met`，且 `infraMet=true`）

## 不可变交接清单（2026-04-03 00:57 UTC）
- 清单文件：`docs/governance-readiness-evidence/run-20260403-005224/immutable-handoff-manifest.json`
- 说明：用于评审/验收时快速核对关键证据文件是否被篡改（SHA256）。
- `docs/governance-readiness-evidence/run-20260403-005224/governance-readiness-evidence-summary.json`
	- `sha256=f929dbc6f9d0c771357bc1baafe3519369f58895cfcbde963d99d449c31b038e`
- `docs/governance-readiness-evidence/run-20260403-005224/reliability-drill.json`
	- `sha256=e0ccce1fa78896ecb130a7a934fa5bf7049b26d0ab3ab066d3b5eba95559af2a`
- `docs/governance-readiness-evidence/run-20260403-005224/audit-hash-chain-build.json`
	- `sha256=c02450ec5a554d8bfe9c23974f3b42d8a9a7b02d4ff5279002d15280011388ec`
- `docs/governance-readiness-evidence/run-20260403-005224/audit-hash-chain-verify.json`
	- `sha256=c8cc95a7c10b9908c543e59e64d56e2c7201c3b6500886be02152bc73889dde4`
- `docs/identity-issue-regression-summary.json`
	- `sha256=9c4c417485763f659373ae4c45301a51b5dc64f84b46b0271bc1eb26e1939f01`
- `docs/account-duty-segregation-summary.json`
	- `sha256=37f6ac005e6485456ca95d5c2a0e6a6be31b8bb1d1c3f9466f88f948817e5ad8`
