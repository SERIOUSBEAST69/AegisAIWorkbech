# 审批中心/待办中心模块设计方案（2026-04-05）

## 1. 模块目标与定位

审批中心/待办中心是系统中“敏感治理操作”的统一流转入口，用于实现：

1. 主管理员发起变更申请（权限、策略、用户、角色、数据删除等）。
2. 复核员独立审批（通过/驳回，必须填写意见）。
3. 全流程留痕（提交、审批、撤回）并可审计追溯。

该模块不替代业务管理页面，而是作为“风险动作执行前置闸门”。

## 2. 与现有系统适配策略（推荐：复用现有治理链路）

当前系统已存在：

1. 治理变更主表与流程接口：`/api/governance-change/*`
2. 现有页面：`/governance-change-manage`、`/approval-manage`
3. 审计日志：`/api/audit-log/*`

推荐方案：

1. 不新建完全独立引擎，直接扩展 `governance_change` 流程能力。
2. 前端新增统一入口页：`/approval-center`，聚合“待我审批 + 我发起的申请 + 详情对比”。
3. 业务页面（权限管理/策略管理/用户管理/角色管理）继续发起申请，但跳转统一审批中心查看进度。

## 3. 菜单与入口设计

### 3.1 菜单位置

建议放在“治理管理”分组下：

1. 审批中心（新）：`/approval-center`
2. 治理变更（可保留，后续可并入审批中心）
3. 审批管理（可保留兼容，后续下线）

### 3.2 菜单显示权限

1. `approval:center:view` 或 `govern:change:view` 可见审批中心菜单。
2. `govern:change:create` 可见“我发起的申请”与“提交申请”。
3. `govern:change:review` 可见“待我审批”与审批操作按钮。

## 4. 页面原型与字段设计

## 4.1 页面A：审批中心列表页（统一入口）

分为两个 Tab：

1. `待我审批`（复核员）
2. `我发起的申请`（主管理员）

公共筛选条件：

1. 申请时间（起止）
2. 申请类型（ROLE/PERMISSION/POLICY/USER/DATA_DELETE）
3. 状态（pending/approved/rejected/revoked）
4. 关键词（申请ID、标题、申请人）

列表字段：

1. 申请ID
2. 申请类型
3. 申请标题
4. 申请人
5. 申请时间
6. 当前状态
7. 最新处理人
8. 最新处理时间
9. 操作（查看详情/审批/撤回）

按钮规则：

1. 复核员在 `pending` 行看到：`通过`、`驳回`、`查看详情`。
2. 发起人在 `pending` 且未处理时看到：`撤回`、`查看详情`。
3. 非本公司/无权限用户仅可见授权范围内记录。

## 4.2 页面B：申请详情页

区块结构：

1. 基本信息：申请ID、类型、标题、风险级别、申请人、申请时间、状态。
2. 变更原因：业务原因、紧急程度、预期影响。
3. 变更内容：结构化 JSON 展示。
4. 对比视图：`修改前` vs `修改后`。
5. 审批历史：提交、审批、撤回时间线。
6. 审批意见区：复核员必填。

审批操作区：

1. `通过`（必填意见）
2. `驳回`（必填意见）
3. `撤回申请`（仅发起人、仅 pending）

## 4.3 页面C：变更对比组件

统一组件：`ChangeDiffPanel`

展示方式：

1. 左右两栏（Before / After）
2. 字段级高亮（新增/删除/修改）
3. 对大字段（如策略 JSON）支持折叠展开

对比生成规则：

1. 结构化字段优先（name/code/status/permissionCodes 等）
2. JSON 字段 fallback 为文本 diff（行级）

## 5. 状态机与流转规则

状态定义：

1. `pending` 待审批
2. `approved` 已通过
3. `rejected` 已驳回
4. `revoked` 已撤回（新增）

状态流转：

1. 主管理员提交：`draft -> pending`
2. 复核员通过：`pending -> approved`
3. 复核员驳回：`pending -> rejected`
4. 发起人撤回：`pending -> revoked`

硬约束：

1. 发起人与复核员不得为同一账号（SoD）。
2. `approve/reject` 必填审批意见。
3. 非 `pending` 状态禁止再次审批。

## 6. 权限模型设计

新增/复用权限码建议：

1. `approval:center:view` 查看审批中心
2. `govern:change:create` 提交申请
3. `govern:change:review` 复核审批
4. `govern:change:revoke` 撤回申请
5. `govern:change:view` 查看申请详情

角色建议（沿用现有体系）：

1. 主管理员：`ADMIN`（create/view/revoke）
2. 复核员：`ADMIN_REVIEWER`、`SECOPS_TRIAGE`（review/view）
3. 审计员：`EXECUTIVE_COMPLIANCE`（view）

数据隔离：

1. 强制 company_id 过滤。
2. 发起人默认仅看自己发起记录；复核员看本公司待办。

## 7. 后端接口设计（在现有接口基础上扩展）

建议新增/扩展：

1. `GET /api/approval-center/todo-page`
2. `GET /api/approval-center/my-page`
3. `GET /api/approval-center/detail/{requestId}`
4. `POST /api/approval-center/approve`
5. `POST /api/approval-center/reject`
6. `POST /api/approval-center/revoke`
7. `GET /api/approval-center/diff/{requestId}`

与现有接口映射：

1. 可直接封装到 `GovernanceChangeController`，统一使用 `governance_change` 数据。
2. `approve/reject` 可复用现有 `/governance-change/approve`（增加意见必填校验）。
3. `revoke` 为新增。

请求/响应关键字段：

1. `requestId`
2. `module`
3. `action`
4. `title`
5. `reason`
6. `impact`
7. `payloadJson`
8. `approveNote`
9. `status`
10. `requesterId/approverId`
11. `createTime/approvedAt/revokedAt`

## 8. 数据库设计（最小改造）

## 8.1 复用现有表

复用：`governance_change_request`（实体：`GovernanceChangeRequest`）

建议新增字段：

1. `title` VARCHAR(200) 申请标题
2. `reason` VARCHAR(500) 变更原因
3. `impact` VARCHAR(500) 预期影响
4. `request_type` VARCHAR(50) 申请类型
5. `revoked_by` BIGINT
6. `revoked_at` DATETIME
7. `revoke_note` VARCHAR(500)

索引建议：

1. `(company_id, status, update_time)`
2. `(company_id, requester_id, create_time)`
3. `(company_id, approver_id, approved_at)`

## 8.2 审批操作明细表（推荐新增）

表：`approval_action_log`

字段：

1. `id` BIGINT PK
2. `request_id` BIGINT
3. `action` ENUM(submit,approve,reject,revoke)
4. `operator_id` BIGINT
5. `operator_role_code` VARCHAR(64)
6. `comment` VARCHAR(500)
7. `action_time` DATETIME
8. `trace_json` TEXT
9. `company_id` BIGINT

用途：

1. 审批时间线回放。
2. 审计取证。

## 9. 审计日志方案

所有关键动作写入 `audit_log`：

1. `approval_submit`
2. `approval_approve`
3. `approval_reject`
4. `approval_revoke`

审计字段要求：

1. `userId`（操作者）
2. `operation`（动作）
3. `inputOverview`（requestId + module + action）
4. `outputOverview`（结果状态）
5. `result`（success/fail）
6. `riskLevel`
7. `operationTime`

## 10. 与现有页面打通方案

## 10.1 权限管理页

1. 提交治理变更后，弹窗提示“已进入待审批”。
2. 提供按钮：`前往审批中心`（携带 `requestId` 查询参数）。

## 10.2 策略管理页

1. 新增/编辑/启停/删除后统一提交审批。
2. 在操作结果区展示审批状态与处理人。

## 10.3 用户/角色管理页

1. 敏感操作走审批中心，不直接落库。
2. 支持从详情页回跳到原业务页。

## 11. 前端实现建议（Vue3 + Element Plus）

新增文件建议：

1. `src/views/ApprovalCenter.vue`
2. `src/components/approval/ApprovalFilterBar.vue`
3. `src/components/approval/ApprovalTable.vue`
4. `src/components/approval/ApprovalDetailDrawer.vue`
5. `src/components/approval/ChangeDiffPanel.vue`
6. `src/api/approvalCenter.js`

路由与菜单：

1. 路由：`/approval-center`
2. 菜单权限：`approval:center:view`

## 12. 后端实现建议（Spring Boot + MyBatis-Plus）

推荐方式：

1. 新建 `ApprovalCenterController`（聚合查询与视图字段整形）。
2. 复用 `GovernanceChangeRequestService` 和现有审批执行逻辑。
3. 新增 `revoke` 业务方法（状态检查 + SoD 校验 + 审计）。
4. 新增 `ApprovalActionLogService`（可选）。

## 13. 分阶段落地计划

阶段1（快速上线，1-2天）：

1. 新增审批中心列表页（待我审批 + 我发起）。
2. 复用现有审批接口，补意见必填校验。
3. 增加撤回接口与按钮。

阶段2（增强，2-3天）：

1. 详情页 + 差异对比组件。
2. 审批时间线与操作明细。
3. 增强筛选（类型、时间、状态、关键词）。

阶段3（治理强化，2天）：

1. 审批 SLA 超时提醒。
2. 通知机制（站内信/邮件/Webhook）。
3. 审批统计看板（通过率、平均时长、驳回原因）。

## 14. 验收清单（对应“必须实现”）

1. 已具备两类列表：待我审批、我发起。
2. 支持时间/类型/状态筛选。
3. 详情展示：内容、原因、影响、前后对比。
4. 复核员通过/驳回必须填写意见。
5. 主管理员可撤回未处理申请。
6. 状态流转正确：pending -> approved/rejected/revoked。
7. 权限隔离生效：仅审批角色可见且仅看授权范围。
8. 审计日志完整：提交/通过/驳回/撤回均留痕。
