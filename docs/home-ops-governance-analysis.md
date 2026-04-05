# 首页与运维观测模块深度解析

本文面向治理管理员、评审专家与比赛解说场景，解释首页与运维观测两个核心模块的定位、数据来源、展示逻辑、业务价值和相互关系。内容按产品设计文档标准编写，可直接用于项目说明、答辩讲稿和视频口播。

## 1. 模块定位

### 1.1 首页功能模块

首页是治理管理员的总指挥台。它回答的问题不是“系统里有没有数据”，而是“当前平台最重要的治理信号是什么、风险在哪里、应该先做什么”。首页聚合了工作台总览、治理总览、模型谱系与漂移、AI 调用审计、攻防演练、追溯上下文、治理待办等内容，目标是让治理管理员在一个页面里完成态势识别、风险判断、证据定位和动作分发。

首页的核心价值有三点：

1. 把分散在资产、模型、风险、审计、权限、演练中的信号汇总成可执行脉冲。
2. 让管理员快速判断现在最该处理什么，而不是在多个页面里来回切换。
3. 让每个结论都能回到原始记录和审计链，避免只看大盘、不看证据。

### 1.2 运维观测功能模块

运维观测是平台的运行态总屏。它更关注趋势、结构、健康度和容量压力，回答的问题是“平台运行得稳不稳、负载是否异常、治理链路是否健康、哪些指标需要持续监控”。

运维观测的核心价值有三点：

1. 用趋势图、统计图和健康报告替代逐条明细，适合值守和周期巡检。
2. 把风险、告警、AI 调用和租户健康放在同一视角，便于快速判断是否需要升级处置。
3. 避免与首页重复展示明细，保证每个页面有清晰职责边界。

## 2. 核心概念解释

### 2.1 漂移

漂移指模型在现实环境中的表现，和训练时预期不再一致。它可能是数据分布变化、业务场景变化或攻击方式变化导致的。

页面里的“模型漂移分数”不是简单的噱头，而是治理管理员判断模型是否还值得继续按原策略运行的信号。漂移高，意味着模型可能已经偏离原来的业务规律，需要复核数据、策略和阈值。

### 2.2 模型谱系

模型谱系可以理解为模型的家族档案。它记录模型从训练、版本、发布、回滚到运行状态的演化链路。

对治理管理员来说，谱系的意义是：

1. 知道模型是谁训练的、什么时候改过、当前处于什么版本。
2. 出问题时可以回溯到具体一次训练或发布。
3. 避免模型以黑盒方式进入生产，导致不可审计。

### 2.3 租户隔离

租户隔离指同一套系统里，不同企业的数据、日志、模型调用和审批记录严格分开，只能看到自己公司的范围。

它解决的是多企业共用平台时的边界问题。没有租户隔离，平台会出现越权查看、数据串读、审计污染等严重风险。

### 2.4 审计

审计不是简单日志堆积，而是能证明谁在什么时间、对什么对象、做了什么操作、结果如何的证据链。

页面中的审计准备度、审计留痕、审计链校验，都是在回答同一个问题：当监管或内审追查时，平台能不能把事情说清楚。

### 2.5 治理健康分

治理健康分是一个综合评分，用来衡量平台当前治理状态是否稳定。它通常会把高敏资产、风险闭环、审批积压、审计覆盖率、AI 调用负载等因素一起计算进去。

它不是财务指标，也不是单一告警数量，而是整体治理韧性的表达方式。

### 2.6 审计覆盖率

审计覆盖率表示一段时间内，被纳入审计的用户或行为占整体范围的比例。覆盖率越高，说明平台越能把关键行为纳入证据链。

### 2.7 风险压力

风险压力是把待闭环告警、严重事件、高危事件、AI 使用负载、审批积压等综合折算成一个运维视角的压力值，用来判断当前系统是平稳、可控还是需要紧急干预。

## 3. 数据来源与计算逻辑

### 3.1 首页的数据来源

首页主要通过 `/api/dashboard/home-bundle` 一次性获取总数据包，内部再组合多个子模块：

1. `workbench`：工作台总览，来自 `/api/dashboard/workbench`。
2. `insights`：治理洞察，来自 `/api/dashboard/insights`。
3. `trustPulse`：治理脉冲，来自 `/api/dashboard/trust-pulse`。
4. `forecast`：风险趋势预测，来自 `risk/forecast` 或 dashboard 聚合结果。
5. `traceContext`：追溯上下文，来自公司与账号范围信息。
6. `traceModules`：模块追溯下钻入口与计数。

首页的计算逻辑偏总指挥：

1. 以公司维度和当前账号维度聚合。
2. 以 7 天、今天、最近一次演练等较短窗口为主。
3. 强调可下钻、可执行、可验证。

### 3.2 运维观测的数据来源

运维观测主要通过以下接口汇总：

1. `/api/dashboard/workbench`：风险趋势、审计留痕、AI 调用趋势、待办等。
2. `/api/alert-center/stats`：告警统计，包含待处理、已阻断、严重、高危、隐私告警、异常告警、影子 AI 等。
3. `/api/ai/monitor/summary`：近 30 天 AI 使用分析。
4. `/api/company/health/latest`：最近一次租户健康报告。
5. `/api/company/health-check`：触发即时体检。

运维观测的计算逻辑偏值守屏：

1. 更关注趋势、统计、分布和健康状态。
2. 不强调逐条追溯，而强调整体现象和风险聚合。
3. 适合值班、巡检、周报和管理层查看。

### 3.3 为什么首页和运维观测部分数据不完全重合

这是设计上的刻意区分，不是数据错误。

1. 时间窗口不同。首页会同时出现今日、7 天、当前状态、追溯上下文和演练结果；运维观测会混合 7 天趋势、30 天使用量和最新健康报告。
2. 聚合层级不同。首页更关注当前动作和证据，运维观测更关注整体态势和趋势结构。
3. 指标口径不同。首页的“今日审计留痕”是今日审计事件数，运维观测的“审计覆盖率”是健康报告中的比例指标，二者不是同一维度。
4. 数据来源不同。首页还包含 AI 审计日志、模型谱系、跨站防护、攻防演练等操作域数据；运维观测主要保留运行域和健康域数据。
5. 展示职责不同。首页要支持决策和动作，运维观测要支持巡检和看板，所以字段重叠不是目标，边界清晰才是目标。

## 4. 首页展示内容逐项解释

### 4.1 首页英雄区

首页顶部的大标题是平台定位说明，用于向评审快速传达这是一个企业级 AI 治理平台，不是普通看板。

当前指挥席位区域展示当前操作者身份、岗位和部门，意义是把谁在看这个上下文显式化，避免通用首页失去角色感。

### 4.2 首页追溯上下文

这一栏告诉用户：当前页面上的所有统计都基于哪个公司、哪个账号范围、何时生成，以及是否支持回溯。

它的作用是建立证据边界，避免用户误以为页面展示的是全平台全量数据。

### 4.3 模型谱系与漂移

这里展示模型训练运行数、跟踪模型数、最新运行 ID、漂移分数和发布状态。

这组信息告诉治理管理员：

1. 模型是否还活跃。
2. 最近是否发生训练或发布变化。
3. 模型是否出现偏移，需要重新校准或降级。

### 4.4 治理就绪度闭环

只有治理管理员看到的这块，是把治理结果是否闭环直接展示出来。

它包含已实现项、待补项、错误预算和自动处置状态，核心目的是让管理员知道平台不是只有告警，而是能推进整改、演练和证据导出。

### 4.5 模块追溯下钻

这里是首页与证据链的桥梁。风险事件、上传数据资产、审批流转、AI 调用审计都可以从这里下钻到原始记录。

它的意义是把总览变成可核实，防止首页只停留在概括层。

### 4.6 治理总览和治理信号

治理总览相当于首页的战略摘要，治理信号相当于管理员的处置提示。

它们不是单纯展示图表，而是把风险压力、模型可信、流程闭环、审计准备度拆成可行动的维度。

### 4.7 风险趋势、风险结构、待办编排、AI 调用审计

1. 风险趋势告诉你过去 7 天风险、审计和 AI 调用如何变化。
2. 风险结构告诉你当前风险主要集中在哪些等级。
3. 待办编排告诉你下一步应该先做什么。
4. AI 调用审计告诉你系统调用是否留痕、链路是否完整。

这些内容共同构成首页的决策闭环。

## 5. 运维观测展示内容逐项解释

### 5.1 审计驾驶舱

驾驶舱是运维观测的主视觉，强调态势总览而不是操作详情。

里面的信号芯片是快速读数：待闭环风险、待处理安全事件、最近体检时间。它们让值守人员先看态势，再下钻明细。

### 5.2 KPI 卡片

KPI 卡片聚焦四件事：风险压力分、租户健康状态、审计覆盖率、AI 调用总量。

这四项分别对应：

1. 运行压力。
2. 健康结论。
3. 合规覆盖。
4. 平台使用负载。

### 5.3 风险趋势图

它展示近 7 天的风险事件、审计留痕和 AI 调用。这里的重点不是某一条事件，而是趋势有没有拐点、波动有没有放大、是否存在异常抬升。

### 5.4 告警统计图

它展示告警的结构分布，帮助运维判断告警到底是多了还是变危险了。

### 5.5 AI 使用分析

它展示近 30 天不同模型的调用量，是平台能力负载和治理压力的重要侧面。

### 5.6 租户健康自检

健康自检是运维观测最具治理意味的部分。它把权限缺口、审计覆盖率、隐私债务和风险压力组合成健康报告，帮助值守者判断平台有没有表面正常、内部失衡的问题。

## 6. 业务意义

### 6.1 为什么首页要这样设计

如果没有首页，治理管理员会陷入多个模块来回切换、每个模块都看一点但无法形成全局判断的状态。

首页的业务意义是把治理动作前置到“看一眼就能知道该做什么”。这能显著缩短响应时间，也能减少漏看、错判和重复处置。

### 6.2 为什么运维观测要单独存在

运维观测如果和首页混在一起，会导致两个问题：

1. 决策层和运行层数据混在一起，页面职责混乱。
2. 追溯明细和统计看板混在一起，性能和可读性都会下降。

因此，运维观测必须保留趋势与健康，不和首页争夺证据链明细的职责。

### 6.3 不做会有什么问题

1. 没有首页，总览会碎片化，治理动作没有入口。
2. 没有运维观测，系统健康只能靠零散日志判断，无法做趋势巡检。
3. 如果两个模块口径混乱，评审时会显得数据假、概念乱、职责重叠。

## 7. 技术亮点

### 7.1 一次聚合，多视图复用

首页通过 bundle 方式一次性拿到多个治理域数据，减少前端多次请求和重复加载。

### 7.2 真实数据驱动，而非静态演示

页面里的指标不是写死的 mock，而是从数据库、审计日志、风险事件、模型调用统计和健康报告中计算得来。

### 7.3 多租户边界明确

所有核心聚合都基于公司 ID 和公司用户范围，避免跨租户数据泄漏。

### 7.4 证据链可下钻

首页从总览能下钻到风险事件、上传资产、审批流转和 AI 审计明细，形成概览到证据的闭环。

### 7.5 动态防御反馈

攻防演练不只是显示结果，而是能基于真实事件生成建议，并在人工确认后触发规则加固和反馈训练，这让演练具备治理闭环价值。

### 7.6 运行态与决策态分离

首页负责决策，运维观测负责运行态监控，职责清楚后，页面性能、语义清晰度和维护性都会更好。

## 8. 与其他模块的关系

### 8.1 与首页的区别

首页是治理管理员的总指挥台，强调现在该做什么。

运维观测是运行态看板，强调系统现在怎么样。

### 8.2 与引擎的区别

引擎是能力底座，负责计算、检测、预测和训练；页面只是把引擎输出转成可理解的治理语言。

### 8.3 与洞察的区别

洞察更偏策略和解释，回答为什么会这样、应该如何优化。

首页更偏操作，回答当前最关键的动作是什么。

### 8.4 与日志的区别

日志是原始事实，页面是事实的治理表达。日志负责可追溯，页面负责可理解。

### 8.5 与员工 AI 行为监控的区别

员工 AI 行为监控更聚焦人、终端、隐私盾和单点行为明细；运维观测不展示这些明细，只保留聚合趋势和健康态势，避免重复和职责混乱。

## 9. 60-90 秒口播稿

本系统的首页和运维观测不是同一个页面的重复，而是两个层次完全不同的治理视角。首页是治理管理员的总指挥台，它把高敏资产、模型谱系、风险闭环、AI 调用审计和攻防演练放在一张图里，帮助管理员快速判断当前最该处理什么，并且能直接下钻到原始证据链。运维观测则是运行态总屏，它更关注趋势、统计和健康度，比如风险趋势、告警结构、AI 调用负载和租户健康自检。前者回答“现在该做什么”，后者回答“系统稳不稳”。

这套设计的关键是把治理做成闭环，而不是只做展示。首页看到的是决策脉冲，运维观测看到的是运行态势，两者数据来源相连，但职责不同，所以部分数字不会完全重合。这不是问题，反而说明我们把决策层、运行层和证据层分开了。这样既能让治理管理员快速决策，也能让值守人员稳定巡检，还能保证每个结论都回到真实数据和审计链。

## 10. 文档版描述

首页模块定位为企业级 AI 治理平台的总控入口，面向治理管理员提供跨资产、模型、风险、审计、权限与演练的统一决策视图。页面通过公司与账号范围聚合真实数据，展示追溯上下文、模型谱系、漂移状态、治理闭环、风险趋势、待办编排和 AI 调用审计等核心信息，并支持从总览直接下钻到风险事件、上传资产、审批流转和 AI 审计明细。该模块的设计目标是让治理动作前置、让证据可回溯、让风险判断可执行。

运维观测模块定位为平台运行态态势屏，面向值守和巡检角色，聚焦趋势、统计和健康度。页面主要展示风险趋势、告警结构、AI 使用分析和租户健康自检，强调运行状态是否平稳、风险是否抬升、审计是否覆盖、权限和隐私治理是否存在缺口。该模块不承载首页的追溯明细职责，而是以聚合统计方式提供长期观察能力，确保与首页在职责边界、时间窗口和数据粒度上清晰分工。

两者共同构成“决策态 + 运行态 + 证据态”的治理体系：首页负责决策与闭环，运维观测负责巡检与健康监控，日志与审计模块负责事实沉淀与追溯证明。这样的结构既保证了产品层面的清晰分工，也保证了工程层面的可维护性和治理层面的可审计性。

## 11. 功能到技术实现的逐项映射（新增）

本章节专门回答“功能是如何实现的、核心代码在哪里”。

### 11.1 首页一屏加载：聚合接口 + 前端分发

实现思路：

1. 前端只打一条聚合请求，减少首页并发接口抖动。
2. 后端在同一个接口里拼好 workbench、insights、trustPulse、forecast、traceContext、traceModules。
3. 前端拿到后按板块分发给不同状态对象，再触发图表渲染。

核心代码定位：

1. 首页拉总包：[src/views/Home.vue](src/views/Home.vue#L1360)
2. 首页 API 封装：[src/api/dashboard.js](src/api/dashboard.js#L17)
3. 后端聚合入口：[backend/src/main/java/com/trustai/controller/DashboardController.java](backend/src/main/java/com/trustai/controller/DashboardController.java#L207)

关键代码逻辑（节选）：

```javascript
// Home.vue
const bundle = await dashboardApi.getHomeBundle()
const workbench = bundle?.workbench || {}
const insightData = bundle?.insights || {}
const pulseData = bundle?.trustPulse || {}
overview.value = personalizeWorkbench(workbench, userStore.userInfo)
insights.value = insightData
trustPulse.value = pulseData
schedulePrimaryChartRender()
```

```java
// DashboardController.java
bundle.put("workbench", workbench().getData());
bundle.put("insights", insights().getData());
bundle.put("trustPulse", trustPulse().getData());
bundle.put("forecast", riskForecastScheduler.getLatest());
bundle.put("traceContext", traceContext);
bundle.put("traceModules", buildTraceModules(companyId, companyUserIds));
```

### 11.2 首页指标计算：7天窗口 + 对比窗口

实现思路：

1. 以今天为基准，计算近7天与前7天两个窗口。
2. 风险事件、审计日志、AI 调用统计统一按公司和账号范围过滤。
3. 用两段窗口计算 delta，保证趋势是“变化量”而不是“静态值”。

核心代码定位：

1. 统计入口：[backend/src/main/java/com/trustai/controller/DashboardController.java](backend/src/main/java/com/trustai/controller/DashboardController.java#L75)
2. 指标组装：[backend/src/main/java/com/trustai/controller/DashboardController.java](backend/src/main/java/com/trustai/controller/DashboardController.java#L180)
3. 趋势组装：[backend/src/main/java/com/trustai/controller/DashboardController.java](backend/src/main/java/com/trustai/controller/DashboardController.java#L720)

关键技术点：

1. `last7Start` 与 `previous7Start` 双窗口比较。
2. `company_id + companyUserIds` 双边界过滤。
3. `buildMetrics` 计算指标值和 delta。
4. `buildTrend` 产出 labels、riskSeries、auditSeries、aiCallSeries。

### 11.3 首页追溯下钻：模块化查询分支

实现思路：

1. 前端点击模块卡片，带 `moduleKey` 调 `/dashboard/trace/drilldown`。
2. 后端按 `module` 分支到不同查询：`ai-audit`、`uploads`、`approvals`、`risk-events`。
3. 每个分支都返回 `traceRule + records`，用于解释追溯口径。

核心代码定位：

1. 前端入口：[src/views/Home.vue](src/views/Home.vue#L573)
2. 后端下钻接口：[backend/src/main/java/com/trustai/controller/DashboardController.java](backend/src/main/java/com/trustai/controller/DashboardController.java#L230)

关键代码逻辑（节选）：

```javascript
// Home.vue
const data = await request.get('/dashboard/trace/drilldown', {
	params: { module: moduleKey, limit: 20 }
})
traceDialog.value = {
	module: data?.module || moduleKey,
	traceRule: data?.traceRule || '',
	records: Array.isArray(data?.records) ? data.records : []
}
```

### 11.4 模型谱系与漂移：三接口并行拉取

实现思路：

1. 前端 `Promise.all` 并行拉取谱系、漂移、发布状态。
2. 后端分别从 AI 网关服务返回状态。
3. 前端根据结构容错（支持 `resp.lineage` 和平铺结构）。

核心代码定位：

1. 前端治理刷新：[src/views/Home.vue](src/views/Home.vue#L1406)
2. 网关接口：[backend/src/main/java/com/trustai/controller/AiGatewayController.java](backend/src/main/java/com/trustai/controller/AiGatewayController.java#L39)
3. 网关接口：[backend/src/main/java/com/trustai/controller/AiGatewayController.java](backend/src/main/java/com/trustai/controller/AiGatewayController.java#L45)
4. 网关接口：[backend/src/main/java/com/trustai/controller/AiGatewayController.java](backend/src/main/java/com/trustai/controller/AiGatewayController.java#L75)

### 11.5 攻防演练：真实评估 + 动态建议 + 人工确认加固

实现思路：

1. 前端点击“开始演练”调用 `/ai/adversarial/run`。
2. 后端先做真实态势评估：读取安全事件、风险事件、审计风险、隐私盾配置，计算 `riskScore`。
3. 再计算 `defenseStrengthScore` 和 `riskScoreAdjusted`。
4. 对 `real-threat-check` 场景直接返回评估结果，不注入假轮次数据。
5. 建议由 `buildOptimizationSuggestions` 动态生成，不再固定模板。
6. 管理员点击“人工确认并应用防御加固”，调用 `/ai/adversarial/apply-hardening`，更新规则阈值并触发反馈训练。

核心代码定位：

1. 前端演练入口：[src/views/Home.vue](src/views/Home.vue#L794)
2. 前端加固入口：[src/views/Home.vue](src/views/Home.vue#L833)
3. 后端演练主逻辑：[backend/src/main/java/com/trustai/service/AiGatewayService.java](backend/src/main/java/com/trustai/service/AiGatewayService.java#L426)
4. 风险评估构建：[backend/src/main/java/com/trustai/service/AiGatewayService.java](backend/src/main/java/com/trustai/service/AiGatewayService.java#L566)
5. 动态建议生成：[backend/src/main/java/com/trustai/service/AiGatewayService.java](backend/src/main/java/com/trustai/service/AiGatewayService.java#L820)
6. 人工确认加固：[backend/src/main/java/com/trustai/service/AiGatewayService.java](backend/src/main/java/com/trustai/service/AiGatewayService.java#L501)

关键代码逻辑（节选）：

```java
Map<String, Object> assessment = buildThreatAssessment(true);
long defenseStrengthScore = computeDefenseStrengthScore(assessment);
long riskScoreAdjusted = ...;
List<String> suggestions = buildOptimizationSuggestions(assessment, true);
```

```java
// 加固动作：阈值收紧 + 敏感范围扩展 + 反馈训练
rule.setAlertThresholdBytes(afterThreshold);
rule.setSensitiveExtensions(appendCsvToken(rule.getSensitiveExtensions(), ".sql"));
trainFeedback = aiInferenceClient.trainAdversarialFeedback(...);
```

### 11.6 首页卡顿优化：首帧延后 + 动效降载

实现思路：

1. 首次数据加载放到 `requestAnimationFrame`，避免与首屏渲染同帧抢资源。
2. 登录转场和 `prefers-reduced-motion` 场景直接跳过重动画。
3. 降低阴影和 hover 位移动效，减少重绘成本。

核心代码定位：

1. 动画入口：[src/views/Home.vue](src/views/Home.vue#L1336)
2. 首帧延后加载：[src/views/Home.vue](src/views/Home.vue#L1498)
3. 样式降载：[src/views/Home.vue](src/views/Home.vue#L2034)

### 11.7 运维观测：三路并发 + 可降级显示

实现思路：

1. 前端通过 `Promise.allSettled` 并发拉取：`/dashboard/workbench`、`/alert-center/stats`、`/ai/monitor/summary`。
2. 任一路失败不阻断全页，记录 `moduleFailure` 并展示可用模块。
3. 图表渲染前先 `dispose`，避免重复初始化导致内存和渲染抖动。
4. 最后再拉租户健康报告 `/company/health/latest`，作为运行态补充。

核心代码定位：

1. 并发加载入口：[src/views/OpsObservability.vue](src/views/OpsObservability.vue#L365)
2. 风险趋势图渲染：[src/views/OpsObservability.vue](src/views/OpsObservability.vue#L258)
3. 告警统计图渲染：[src/views/OpsObservability.vue](src/views/OpsObservability.vue#L297)
4. AI 使用图渲染：[src/views/OpsObservability.vue](src/views/OpsObservability.vue#L328)
5. 健康报告加载：[src/views/OpsObservability.vue](src/views/OpsObservability.vue#L420)
6. 体检触发：[src/views/OpsObservability.vue](src/views/OpsObservability.vue#L433)

### 11.8 运维与首页数据不重合的工程原因（代码级）

1. 首页使用 `/dashboard/home-bundle` 聚合，包含 trace 上下文和下钻模块。
2. 运维直接拉 `/dashboard/workbench + /alert-center/stats + /ai/monitor/summary + /company/health/latest`。
3. 首页强调动作域（演练、审计链校验、治理下钻）；运维强调运行域（趋势、统计、健康度）。
4. 运维端有 `Promise.allSettled` 容错，某些模块失败时仍展示局部结果；首页总包策略更偏“一次取全”。

## 12. 面向比赛解说的技术版口播词（新增）

我们这个平台不是把几个图拼在一起，而是做了完整的数据治理编排。首页先通过 `/dashboard/home-bundle` 一次性拿到 workbench、insights、trustPulse、forecast 和 trace 模块，再在前端按模块分发，保证首屏是一个统一决策视图。后端的 workbench 计算是双时间窗模型，近7天和前7天同时计算，所有查询都带公司和账号边界，确保多租户隔离和口径一致。

攻防演练这块，我们没有做静态建议。`adversarialRun` 先读取真实安全事件、风险事件、审计高风险样本和隐私盾配置，算出风险分与防御强度，再由 `buildOptimizationSuggestions` 根据事件类型、严重级别、文件后缀和外联特征动态生成建议。管理员点击确认后，`adversarialApplyHardening` 会真实收紧规则阈值、扩展敏感范围并触发对抗反馈训练，这样前后对比是可验证的工程结果。

运维观测则采用并发容错架构。`loadAll` 用 `Promise.allSettled` 同时拉趋势、告警和AI使用分析，任一模块失败不会拖垮全页，再补充租户健康报告。这样首页负责决策闭环，运维负责运行态势，日志负责证据沉淀，形成“决策态、运行态、证据态”三层治理架构。
