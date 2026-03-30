# 治理管理员页面按钮 UAT 签收清单（2026-03-30）

## 使用说明
- 适用身份：治理管理员。
- 判定口径：
  - 页面不跳转至登录页。
  - 按钮点击后返回成功提示或符合预期校验提示。
  - 控制台无 401/5xx 异常接口。
- 签收建议：每项填写 通过/不通过、证据截图路径、备注。

## 一、核心治理页面

| 页面 | 按钮/动作 | 预期结果 | 结果 | 证据 | 备注 |
|---|---|---|---|---|---|
| /governance-change-manage | 查询 | 列表正常返回 |  |  |  |
| /governance-change-manage | 发起治理变更 | 弹窗打开，字段可编辑 |  |  |  |
| /governance-change-manage | 提交申请 | 二次密码弹窗，提交成功 |  |  |  |
| /governance-change-manage | 通过 | 对 pending 申请可通过并更新状态 |  |  |  |
| /governance-change-manage | 拒绝 | 对 pending 申请可拒绝并更新状态 |  |  |  |
| /sod-rule-manage | 查询 | 列表正常返回 |  |  |  |
| /sod-rule-manage | 新增规则 | 保存成功并可见 |  |  |  |
| /sod-rule-manage | 编辑规则 | 更新成功并可见 |  |  |  |
| /sod-rule-manage | 删除规则 | 二次密码校验后删除成功 |  |  |  |

## 二、身份与权限页面

| 页面 | 按钮/动作 | 预期结果 | 结果 | 证据 | 备注 |
|---|---|---|---|---|---|
| /user-manage | 查询 | 列表正常返回 |  |  |  |
| /user-manage | 新增用户 | 保存成功 |  |  |  |
| /user-manage | 编辑用户 | 更新成功 |  |  |  |
| /user-manage | 删除用户 | 二次密码校验后成功，回收站可见 |  |  |  |
| /role-manage | 查询 | 列表正常返回 |  |  |  |
| /role-manage | 新增角色 | 保存成功 |  |  |  |
| /role-manage | 编辑角色 | 更新成功 |  |  |  |
| /role-manage | 删除角色 | 二次密码校验后成功（默认 ADMIN 不可删） |  |  |  |
| /permission-manage | 查询 | 列表正常返回 |  |  |  |
| /permission-manage | 新增权限 | 保存成功 |  |  |  |
| /permission-manage | 编辑权限 | 更新成功 |  |  |  |
| /permission-manage | 删除权限 | 二次密码校验后成功 |  |  |  |

## 三、流转与合规页面

| 页面 | 按钮/动作 | 预期结果 | 结果 | 证据 | 备注 |
|---|---|---|---|---|---|
| /approval-manage | 查询 | 列表正常返回 |  |  |  |
| /approval-manage | 新建审批申请 | 提交成功 |  |  |  |
| /approval-manage | 审批通过/拒绝 | 状态更新成功 |  |  |  |
| /policy-manage | 新增策略 | 二次密码校验后保存成功 |  |  |  |
| /policy-manage | 编辑策略 | 二次密码校验后更新成功 |  |  |  |
| /policy-manage | 删除策略 | 二次密码校验后删除成功 |  |  |  |
| /subject-request | 查询/处理 | 列表与处理动作正常 |  |  |  |

## 四、会话与异常检查

| 检查项 | 预期 | 结果 | 证据 | 备注 |
|---|---|---|---|---|
| 连续跨页面操作 | 不出现未登录跳转 |  |  |  |
| 提交治理变更后会话 | 保持登录态 |  |  |  |
| Network 接口状态 | 无 401/5xx 异常 |  |  |  |

## 五、签收结论
- UAT 负责人：
- 签收日期：
- 总体结论：通过 / 不通过
- 不通过项与整改单号：

## 参考自动化脚本
- [e2e/governance-admin-full-workflow.spec.js](e2e/governance-admin-full-workflow.spec.js)
- [backend/src/test/java/com/trustai/integration/GovernanceAdminWorkflowIntegrationTest.java](backend/src/test/java/com/trustai/integration/GovernanceAdminWorkflowIntegrationTest.java)
- [backend/src/test/java/com/trustai/integration/SecOpsPolicyWorkflowIntegrationTest.java](backend/src/test/java/com/trustai/integration/SecOpsPolicyWorkflowIntegrationTest.java)