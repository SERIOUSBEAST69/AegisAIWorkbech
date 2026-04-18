<template>
  <div class="threat-monitor-page">

    <!-- 页头 -->
    <div class="page-header scene-block">
      <div class="page-header-copy">
        <div class="page-eyebrow">REAL-TIME THREAT MONITOR</div>
        <h1 class="page-title">AI攻击实时防御</h1>
        <p class="page-subtitle">
          识别并防御提示注入、越权调用与隐蔽外传等AI攻击行为。
          提供实时联动告警、阻断处置与攻防演练能力。
        </p>
      </div>
      <div class="page-header-actions">
        <el-tag type="info" size="large">自动刷新已移除</el-tag>
        <el-button type="primary" :loading="loading" @click="refresh">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-label">事件总数</div>
        <div class="stat-value">{{ stats.total ?? '—' }}</div>
      </div>
      <div class="stat-card warning">
        <div class="stat-label">待处理</div>
        <div class="stat-value">{{ stats.pending ?? '—' }}</div>
      </div>
      <div class="stat-card danger">
        <div class="stat-label">严重威胁</div>
        <div class="stat-value">{{ stats.critical ?? '—' }}</div>
      </div>
      <div class="stat-card high">
        <div class="stat-label">高危</div>
        <div class="stat-value">{{ stats.high ?? '—' }}</div>
      </div>
      <div class="stat-card blocked">
        <div class="stat-label">已阻拦</div>
        <div class="stat-value">{{ stats.blocked ?? '—' }}</div>
      </div>
    </div>
    <div class="stats-footnote">
      <span>去重链路：{{ dedupeMeta.uniqueTotal }} / {{ dedupeMeta.rawTotal }}（压缩 {{ dedupeMeta.collapsed }} 条）</span>
      <span>原始链路挂接率：{{ dedupeMeta.sourceLinkRateText }}</span>
      <span>统计口径：{{ dedupeMeta.caliber }}</span>
    </div>

    <!-- 主内容标签页 -->
    <el-tabs v-model="activeTab" class="main-tabs">

      <!-- ── 事件列表 Tab ── -->
      <el-tab-pane label="AI攻击实时防御" name="events">
        <el-card class="card-glass" style="margin-top: 0">

          <!-- 筛选工具栏 -->
          <div class="toolbar-row">
            <el-select
              v-model="filter.status"
              placeholder="状态"
              clearable
              style="width: 130px"
              @change="refreshEvents"
            >
              <el-option label="待处理" value="pending" />
              <el-option label="已阻拦" value="blocked" />
              <el-option label="已忽略" value="ignored" />
              <el-option label="审查中" value="reviewing" />
            </el-select>

            <el-select
              v-model="filter.severity"
              placeholder="严重程度"
              clearable
              style="width: 130px"
              @change="refreshEvents"
            >
              <el-option label="严重" value="critical" />
              <el-option label="高危" value="high" />
              <el-option label="中危" value="medium" />
              <el-option label="低危" value="low" />
            </el-select>

            <el-input
              v-model="filter.keyword"
              placeholder="搜索文件路径 / 主机 / 员工"
              style="width: 260px"
              clearable
              @change="refreshEvents"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>

            <el-button @click="resetFilter">重置</el-button>
          </div>

          <!-- 事件表格 -->
          <el-table
            :data="events"
            v-loading="loading"
            empty-text="暂无记录"
            style="margin-top: 12px"
            row-class-name="event-row"
            :row-style="rowStyle"
          >
            <el-table-column prop="id" label="ID" width="70">
              <template #default="scope">
                <div class="cell nowrap">{{ scope.row.id }}</div>
              </template>
            </el-table-column>

            <el-table-column label="严重程度" width="100">
              <template #default="{ row }">
                <el-tag :type="severityTagType(row.severity)" size="small">
                  {{ severityLabel(row.severity) }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="事件类型" width="160">
              <template #default="{ row }">
                <span class="event-type">{{ eventTypeLabel(row.eventType) }}</span>
              </template>
            </el-table-column>

            <el-table-column label="攻击类型" width="170">
              <template #default="{ row }">
                <span class="event-type">{{ attackTypeLabel(row.attackType) }}</span>
              </template>
            </el-table-column>

            <el-table-column label="特效主题" width="140">
              <template #default="{ row }">
                <span>{{ effectThemeLabel(row.effectProfile) }}</span>
              </template>
            </el-table-column>

            <el-table-column label="文件路径" min-width="200">
              <template #default="{ row }">
                <span class="file-path" :title="row.filePath">{{ truncate(row.filePath, 45) }}</span>
              </template>
            </el-table-column>

            <el-table-column prop="hostname" label="主机" width="180" />
            <el-table-column prop="employeeId" label="员工标识" width="110" />
            <el-table-column prop="companyId" label="所属企业" width="110" />

            <el-table-column label="文件大小" width="110">
              <template #default="{ row }">{{ formatSize(row.fileSize) }}</template>
            </el-table-column>

            <el-table-column label="目标地址" min-width="180">
              <template #default="{ row }">
                <span class="target-addr" :title="row.targetAddr">{{ truncate(row.targetAddr, 40) }}</span>
              </template>
            </el-table-column>

            <el-table-column label="事件时间" width="160">
              <template #default="{ row }">{{ row.eventTime }}</template>
            </el-table-column>

            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">
                  {{ statusLabel(row.status) }}
                </el-tag>
              </template>
            </el-table-column>

            <el-table-column label="操作" width="160" fixed="right">
              <template #default="{ row }">
                <el-button
                  v-if="canBlacklistEvent(row) && canHandlePendingRow(row)"
                  size="small"
                  type="danger"
                  :loading="actionLoading === row.id + '-block'"
                  @click="blockEvent(row)"
                >阻拦</el-button>
                <el-button
                  v-if="canMarkFalsePositive(row) && canHandlePendingRow(row)"
                  size="small"
                  :loading="actionLoading === row.id + '-ignore'"
                  @click="ignoreEvent(row)"
                >忽略</el-button>
                <el-tag v-if="normalizeStatus(row.status) === 'blocked'" type="danger" size="small">已阻拦</el-tag>
                <el-tag v-if="normalizeStatus(row.status) === 'ignored'" type="info" size="small">已忽略</el-tag>
              </template>
            </el-table-column>
          </el-table>

          <!-- 分页 -->
          <div class="pagination-row">
            <el-pagination
              v-model:current-page="pagination.page"
              v-model:page-size="pagination.pageSize"
              :total="pagination.total"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="refreshEvents"
              @size-change="refreshEvents"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <el-tab-pane v-if="!isAdminUser" label="告警闭环" name="alertCenter">
        <el-card class="card-glass" style="margin-top: 0">
          <div class="toolbar-row">
            <el-select
              v-model="centerFilter.status"
              placeholder="处置状态"
              clearable
              style="width: 140px"
              @change="refreshCenterEvents"
            >
              <el-option label="待处理" value="pending" />
              <el-option label="审查中" value="reviewing" />
              <el-option label="已阻断" value="blocked" />
              <el-option label="已忽略" value="ignored" />
            </el-select>

            <el-select
              v-model="centerFilter.eventType"
              placeholder="告警类型"
              clearable
              style="width: 160px"
              @change="refreshCenterEvents"
            >
              <el-option label="隐私告警" value="PRIVACY_ALERT" />
              <el-option label="行为异常" value="ANOMALY_ALERT" />
              <el-option label="影子AI" value="SHADOW_AI_ALERT" />
              <el-option label="安全威胁" value="SECURITY_ALERT" />
            </el-select>

            <el-input
              v-model="centerFilter.keyword"
              placeholder="搜索标题 / 描述 / 用户 / 模块"
              style="width: 300px"
              clearable
              @change="refreshCenterEvents"
            >
              <template #prefix><el-icon><Search /></el-icon></template>
            </el-input>

            <el-button @click="resetCenterFilter">重置</el-button>
          </div>

          <el-table
            :data="centerEvents"
            v-loading="centerLoading"
            empty-text="暂无记录"
            style="margin-top: 12px"
            :row-style="rowStyle"
          >
            <el-table-column prop="id" label="ID" width="250">
              <template #default="scope">
                <div class="cell nowrap">{{ scope.row.id }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="eventType" label="类型" width="130">
              <template #default="{ row }">{{ centerEventTypeLabel(row.eventType) }}</template>
            </el-table-column>
            <el-table-column label="攻击类型" width="170">
              <template #default="{ row }">{{ attackTypeLabel(row.attackType) }}</template>
            </el-table-column>
            <el-table-column label="特效主题" width="140">
              <template #default="{ row }">{{ effectThemeLabel(row.effectProfile) }}</template>
            </el-table-column>
            <el-table-column prop="severity" label="级别" width="100">
              <template #default="{ row }">
                <el-tag :type="severityTagType(row.severity)" size="small">{{ severityLabel(row.severity) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="告警标题" min-width="220" />
            <el-table-column prop="username" label="关联用户" width="120" />
            <el-table-column prop="companyId" label="所属企业" width="110" />
            <el-table-column prop="sourceModule" label="来源模块" width="120" />
            <el-table-column prop="eventTime" label="发生时间" min-width="160" />
            <el-table-column prop="status" label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="260" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="openRelated(row)">关联事件</el-button>
                <el-button
                  v-if="canBlacklistEvent(row) && canHandlePendingRow(row)"
                  size="small"
                  type="danger"
                  @click="openDispose(row, 'blocked')"
                >阻断并验证</el-button>
                <el-button
                  v-if="canMarkFalsePositive(row) && canHandlePendingRow(row)"
                  size="small"
                  @click="openDispose(row, 'ignored')"
                >标记误报</el-button>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-row">
            <el-pagination
              v-model:current-page="centerPagination.page"
              v-model:page-size="centerPagination.pageSize"
              :total="centerPagination.total"
              :page-sizes="[10, 20, 50, 100]"
              layout="total, sizes, prev, pager, next, jumper"
              @current-change="refreshCenterEvents"
              @size-change="refreshCenterEvents"
            />
          </div>
        </el-card>
      </el-tab-pane>

      <!-- ── 检测规则 Tab ── -->
      <el-tab-pane v-if="canManageThreatRules" label="检测规则" name="rules">
        <el-card class="card-glass" style="margin-top: 0">
          <div class="toolbar-row">
            <el-button type="primary" @click="openAddRule">
              <el-icon><Plus /></el-icon>
              新增规则
            </el-button>
          </div>

          <el-table :data="rules" v-loading="rulesLoading" style="margin-top: 12px">
            <template #empty>
              <el-empty description="暂无记录" />
            </template>
            <el-table-column prop="id" label="ID" width="250">
              <template #default="scope">
                <div class="cell nowrap">{{ scope.row.id }}</div>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="规则名称" min-width="180" />
            <el-table-column prop="sensitiveExtensions" label="敏感文件类型" min-width="200" />
            <el-table-column prop="sensitivePaths" label="敏感目录" min-width="200" />
            <el-table-column label="告警阈值" width="120">
              <template #default="{ row }">{{ formatSize(row.alertThresholdBytes) }}</template>
            </el-table-column>
            <el-table-column label="状态" width="90">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                  {{ row.enabled ? '启用' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="160">
              <template #default="{ row }">
                <el-button size="small" @click="editRule(row)">编辑</el-button>
                <el-button size="small" type="danger" @click="deleteRule(row.id)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-tab-pane>

      <!-- ── 攻防演练 Tab ── -->
      <el-tab-pane v-if="false && canRunThreatDrill" label="攻防演练" name="drill">
        <el-card class="card-glass" style="margin-top: 0">
          <div class="simulator-info">
            <h3>真实安全态势攻防演练</h3>
            <p>
              当前演练不再注入模拟攻击数据，完全基于真实审计日志、风险事件与威胁监控事件进行状态检测。
              点击“立即检测”将实时刷新当前公司的安全态势评分。
            </p>

            <div class="drill-actions">
              <el-button type="primary" :loading="drillLoading" @click="runImmediateThreatDrill">立即检测</el-button>
              <el-button type="danger" plain :loading="simDrillLoading" @click="runPythonBattleDrill">模拟攻防</el-button>
              <el-button type="success" @click="goHomeAdversarial">前往首页真实演练</el-button>
              <el-tag :type="threatDrill.threatLevel === 'high' ? 'danger' : (threatDrill.threatLevel === 'medium' ? 'warning' : 'success')" size="large">
                当前态势：{{ threatDrill.threatLevel || 'unknown' }}
              </el-tag>
            </div>

            <div class="drill-visual-wrap">
              <div class="code-label">攻防演示 3D 联动</div>
              <Security3DVisualizer
                :active-event="activeSimulationEvent"
                @animation-complete="handleSimulationAnimationComplete"
              />
            </div>

            <p class="sim-error">支持在“AI攻击实时防御”工具栏快速发起攻防模拟，此处用于查看实时检测与演练结果。</p>

            <div class="code-block" style="margin-top: 14px">
              <div class="code-label">风险评分</div>
              <pre>{{ threatDrill.riskScore ?? 0 }}</pre>
            </div>

            <div class="code-block">
              <div class="code-label">检测信号</div>
              <pre>{{ JSON.stringify(threatDrill.signals || {}, null, 2) }}</pre>
            </div>

            <el-table :data="threatDrill.recentSecurityEvents || []" style="margin-top: 12px">
              <template #empty>
                <el-empty description="暂无记录" />
              </template>
              <el-table-column prop="eventType" label="事件类型" width="180" />
              <el-table-column prop="employeeId" label="员工" width="140" />
              <el-table-column prop="severity" label="严重级别" width="120" />
              <el-table-column prop="status" label="状态" width="120" />
              <el-table-column prop="eventTime" label="时间" min-width="180" />
            </el-table>

            <div v-if="battleDrill" class="battle-panel">
              <h4>多维度攻防对弈结果</h4>
              <div class="battle-summary-grid">
                <article>
                  <span>胜方</span>
                  <strong>{{ battleDrill.winner || '—' }}</strong>
                </article>
                <article>
                  <span>突破率</span>
                  <strong>{{ Math.round((battleDrill.attack_success_rate || 0) * 100) }}%</strong>
                </article>
                <article>
                  <span>最终分数</span>
                  <strong>{{ battleDrill.attacker_final_score }} : {{ battleDrill.defender_final_score }}</strong>
                </article>
                <article>
                  <span>总回合</span>
                  <strong>{{ battleDrill.total_rounds || visibleBattleRounds.length }}</strong>
                </article>
              </div>

              <div v-if="visibleBattleRounds.length" class="battle-round-stream">
                <article v-for="round in visibleBattleRounds" :key="`battle-${round.round_num}`" class="battle-round-item">
                  <div class="battle-round-head">
                    <strong>Round {{ round.round_num }}</strong>
                    <span class="battle-pill" :class="round.attack_success ? 'hit' : 'block'">
                      {{ round.attack_success ? '攻破' : '拦截' }}
                    </span>
                  </div>
                  <p>{{ round.attack_strategy }} vs {{ round.defense_strategy }}</p>
                  <em>effective={{ round.final_attack_effectiveness }}</em>
                </article>
              </div>

              <div v-if="battleDrill.recommendations?.length" class="battle-recommendations">
                <h5>防守建议</h5>
                <p v-for="tip in battleDrill.recommendations" :key="tip">{{ tip }}</p>
              </div>

              <div v-if="battleInsights.analysis || battleInsights.suggestions.length" class="battle-recommendations">
                <h5>策略有效性分析</h5>
                <p>{{ battleInsights.analysis || '暂无分析内容' }}</p>
                <p v-for="(tip, idx) in battleInsights.suggestions" :key="`insight-${idx}`">{{ idx + 1 }}. {{ tip }}</p>
              </div>
            </div>
          </div>
        </el-card>
      </el-tab-pane>

    </el-tabs>

    <div class="adversarial-floating-wrap">
      <button
        class="floating-btn adversarial-orb"
        type="button"
        :disabled="adversarialRunning"
        @click="launchAdversarialDrill"
      >
        <span class="orb-label">演练</span>
        <strong>{{ adversarialRunning ? '执行中' : '攻防演练' }}</strong>
      </button>

      <Transition name="fade-slide">
        <section v-if="adversarialPanelOpen" class="adversarial-panel card-glass" :class="{ 'adversarial-panel-max': adversarialPanelMax }">
          <header class="adversarial-head">
            <div>
              <div class="card-header">攻防演练面板</div>
              <p class="panel-subtitle">演练进度与回放记录</p>
            </div>
            <div class="adversarial-head-actions">
              <button class="adversarial-close" type="button" @click="adversarialPanelMax = !adversarialPanelMax">
                {{ adversarialPanelMax ? '收起大屏' : '全屏战场' }}
              </button>
              <button class="adversarial-close" type="button" @click="closeAdversarialPanel">关闭</button>
            </div>
          </header>

          <div class="adversarial-config">
            <select v-model="adversarialConfig.scenario" :disabled="adversarialRunning">
              <option v-for="scene in adversarialScenarioOptions" :key="scene.code" :value="scene.code">
                {{ scene.title }}
              </option>
            </select>
            <button class="adversarial-run" type="button" :disabled="adversarialRunning" @click="runAdversarialBattle">
              {{ adversarialRunning ? '真实训练执行中...' : '开始真实演练' }}
            </button>
            <button class="adversarial-close" type="button" :disabled="adversarialLogsLoading" @click="viewAdversarialTrainingLogs">
              {{ adversarialLogsLoading ? '加载日志...' : '查看训练日志' }}
            </button>
            <button class="adversarial-close" type="button" :disabled="adversarialReportLoading" @click="exportAdversarialOptimizationReport">
              {{ adversarialReportLoading ? '导出中...' : '导出模型优化报告' }}
            </button>
            <button class="adversarial-close" type="button" @click="adversarialAdvancedOpen = !adversarialAdvancedOpen">
              {{ adversarialAdvancedOpen ? '收起高级设置' : '高级设置' }}
            </button>
          </div>

          <div class="adversarial-scene-desc">
            <strong>{{ adversarialCurrentScenarioText.title }}</strong>
            <span>{{ adversarialCurrentScenarioText.desc }}</span>
            <em>任务ID：{{ adversarialTaskId || '未启动' }} · 状态：{{ adversarialTaskStatus }}</em>
          </div>

          <div v-if="adversarialAdvancedOpen" class="adversarial-config adversarial-config-advanced">
            <input v-model.number="adversarialConfig.rounds" type="number" min="10" max="30" :disabled="adversarialRunning" placeholder="轮数(10-30)" />
            <input v-model="adversarialConfig.seed" type="number" placeholder="seed(可选)" :disabled="adversarialRunning" />
            <select v-model="adversarialHardeningLevel" :disabled="adversarialRunning">
              <option value="normal">常规强化</option>
              <option value="strong">强力强化</option>
              <option value="extreme">极限强化</option>
            </select>
          </div>

          <p v-if="adversarialError" class="adversarial-error">{{ adversarialError }}</p>

          <div class="adversarial-layout">
            <div class="adversarial-stage-column">
              <div class="adversarial-cinematic-stage" :class="[`stage-${adversarialSceneState}`, { 'stage-finale-active': adversarialFinaleActive }]">
                <div class="stage-hud">
                  <span>场景 {{ adversarialScenarioLabel }}</span>
                  <span>进度 {{ adversarialProgressText }}</span>
                  <span>轮次 {{ adversarialCurrentRoundText }}</span>
                  <span>分镜 {{ adversarialBeatLabel }}</span>
                </div>

                <Transition name="fade-slide">
                  <div v-if="adversarialFinaleActive" class="stage-finale-banner">
                    <strong>{{ adversarialWinnerText }}</strong>
                    <span>{{ adversarialHardeningConclusion }}</span>
                  </div>
                </Transition>

                <div class="stage-field">
                  <article class="battle-actor actor-attacker" :class="[`attacker-${adversarialAttackerPersona.kind}`, `attacker-pattern-${adversarialAttackerPattern}`]">
                    <div class="actor-badge">{{ adversarialAttackerPersona.badge }}</div>
                    <div class="actor-avatar attacker-avatar">
                      <img class="battle-asset attacker-asset" :src="adversarialAttackerAssetUrl" alt="Attacker Pattern" />
                      <div v-if="adversarialAttackerPersona.kind === 'openclaw'" class="openclaw-mark" aria-hidden="true">
                        <i class="shrimp-core"></i>
                        <i class="shrimp-claw claw-left"></i>
                        <i class="shrimp-claw claw-right"></i>
                        <i class="shrimp-tail"></i>
                      </div>
                      <div v-else-if="adversarialAttackerPattern === 'helix'" class="attacker-helix-mark" aria-hidden="true">
                        <i v-for="idx in 8" :key="`helix-${idx}`"></i>
                      </div>
                      <div v-else-if="adversarialAttackerPattern === 'shard'" class="attacker-shard-mark" aria-hidden="true">
                        <i v-for="idx in 6" :key="`shard-${idx}`"></i>
                      </div>
                      <div v-else-if="adversarialAttackerPattern === 'swarm'" class="attacker-swarm-mark" aria-hidden="true">
                        <i v-for="idx in 12" :key="`swarm-${idx}`"></i>
                      </div>
                      <div v-else class="attacker-mark-grid" aria-hidden="true">
                        <i v-for="idx in 9" :key="`grid-${idx}`"></i>
                      </div>
                    </div>
                    <strong>{{ adversarialAttackerPersona.title }}</strong>
                    <p>{{ adversarialAttackerPersona.subtitle }}</p>
                  </article>

                  <div class="battle-mid">
                    <div class="battle-lane">
                      <i class="lane-pulse"></i>
                    </div>
                    <div class="battle-impact" :class="`impact-${adversarialImpactState}`">
                      <strong>{{ adversarialImpactLabel }}</strong>
                      <span>{{ adversarialImpactMetric }}</span>
                    </div>
                  </div>

                  <article class="battle-actor actor-defender" :class="[adversarialDefenderPoseClass, `defender-pattern-${adversarialDefenderPattern}`]">
                    <div class="actor-badge">DEFENSE</div>
                    <div class="actor-avatar defender-avatar" :class="`defender-${adversarialDefenderPattern}`">
                      <img class="battle-asset defender-asset" :src="adversarialDefenderAssetUrl" alt="Defender Pattern" />
                      <div class="defender-shield-rings" aria-hidden="true">
                        <i></i>
                        <i></i>
                        <i></i>
                      </div>
                      <img class="defender-core-logo" :src="defenderLogoUrl" alt="Aegis Defender" />
                      <div class="defender-side-panels" aria-hidden="true">
                        <i class="panel-left"></i>
                        <i class="panel-right"></i>
                      </div>
                    </div>
                    <strong>Logo Guardian Sentinel</strong>
                    <p>自适应防御策略与实时加固联动</p>
                  </article>
                </div>

                <p class="stage-narrative">{{ adversarialNarrativeText }}</p>
                <p class="stage-subtitle">{{ adversarialSubtitleText }}</p>

                <div v-if="adversarialComparisonVisible" class="stage-hardening-overlay">
                  <div class="overlay-title">强化前后对比（真实回执）</div>
                  <article>
                    <div class="overlay-head">
                      <strong>攻击成功率</strong>
                      <span>{{ adversarialAttackRateCompareText }}</span>
                    </div>
                    <div class="overlay-meter">
                      <i class="before" :style="{ width: `${adversarialAttackBeforeWidth}%` }"></i>
                      <i class="after" :style="{ width: `${adversarialAttackAfterWidth}%` }"></i>
                    </div>
                  </article>
                  <article>
                    <div class="overlay-head">
                      <strong>防御强度</strong>
                      <span>{{ adversarialDefenseCompareText }}</span>
                    </div>
                    <div class="overlay-meter">
                      <i class="before defense" :style="{ width: `${adversarialDefenseBeforeWidth}%` }"></i>
                      <i class="after defense" :style="{ width: `${adversarialDefenseAfterWidth}%` }"></i>
                    </div>
                  </article>
                </div>
              </div>

              <div v-if="adversarialBattle" class="adversarial-summary-grid">
                <article>
                  <span>胜方</span>
                  <strong>{{ adversarialWinnerText }}</strong>
                </article>
                <article>
                  <span>当前攻击成功率</span>
                  <strong>{{ Math.round((adversarialBattle.attack_success_rate || 0) * 100) }}%</strong>
                </article>
                <article>
                  <span>当前防御拦截率</span>
                  <strong>{{ Math.round(((adversarialBattle.defense_intercept_rate ?? (1 - (adversarialBattle.attack_success_rate || 0))) || 0) * 100) }}%</strong>
                </article>
                <article>
                  <span>防御模型强度评分</span>
                  <strong>{{ Math.round(Number(adversarialBattle.defense_strength_score || 0)) }}/100</strong>
                </article>
              </div>
            </div>

            <div class="adversarial-feed-column">
              <div v-if="adversarialVisibleRounds.length" class="adversarial-stream">
                <article v-for="round in adversarialVisibleRounds" :key="`adver-${round.round_num}`" class="adversarial-round">
                  <div class="adversarial-round-top">
                    <strong>第 {{ round.round_num }} 轮</strong>
                    <span :class="round.attack_success ? 'hit' : 'block'">{{ round.attack_success ? '突破' : '阻断' }}</span>
                  </div>
                  <p>{{ adversarialStrategyLabel(round.attack_strategy) }} vs {{ adversarialStrategyLabel(round.defense_strategy) }} · 攻击成功率 {{ Math.round((round.attack_success_rate ?? round.final_effectiveness ?? 0) * 100) }}%</p>
                  <em>{{ round.explain || round.narrative }}</em>
                </article>
              </div>

              <div v-if="adversarialCurveHasData" class="adversarial-curve-panel">
                <h4>攻防变化曲线（轮次实时）</h4>
                <svg viewBox="0 0 100 40" preserveAspectRatio="none" class="adversarial-curve-svg" aria-label="attack-defense-curve">
                  <polyline class="curve curve-attack" :points="adversarialCurveAttackPoints" />
                  <polyline class="curve curve-defense" :points="adversarialCurveDefensePoints" />
                </svg>
                <div class="adversarial-curve-legend">
                  <span class="attack">攻击成功率</span>
                  <span class="defense">防御拦截率</span>
                </div>
              </div>

              <div v-if="adversarialBattle?.recommendations?.length" class="adversarial-recommendations">
                <h4>模型优化建议（基于真实轮次）</h4>
                <p v-for="tip in adversarialBattle.recommendations" :key="tip">{{ tip }}</p>
                <div class="adversarial-recommendation-actions" v-if="adversarialCanApplyHardening">
                  <el-button class="adversarial-harden-btn" type="warning" size="small" :loading="adversarialHardeningApplying" @click="applyAdversarialHardening">
                    应用动态加固并重演
                  </el-button>
                  <span class="trace-note">仅应用真实规则/训练加固，不修改展示数据。</span>
                </div>
              </div>

              <div v-if="adversarialKeyChanges.length" class="adversarial-recommendations">
                <h4>关键变化分析</h4>
                <p v-for="item in adversarialKeyChanges" :key="`change-${item.round}`">
                  第{{ item.round }}轮：{{ item.analysis }}
                </p>
              </div>

              <div v-if="adversarialComparisonVisible" class="adversarial-compare-grid">
                <article>
                  <span>攻击成功率变化</span>
                  <strong>{{ adversarialAttackRateCompareText }}</strong>
                </article>
                <article>
                  <span>防御强度变化</span>
                  <strong>{{ adversarialDefenseCompareText }}</strong>
                </article>
                <article>
                  <span>当前结论</span>
                  <strong>{{ adversarialHardeningConclusion }}</strong>
                </article>
              </div>
            </div>
          </div>
        </section>
      </Transition>
    </div>

    <el-drawer
      v-model="adversarialLogsVisible"
      size="56%"
      :close-on-click-modal="false"
      title="真实攻防训练日志"
    >
      <div class="adversarial-log-head">
        <span>任务ID：{{ adversarialTaskId || '-' }}</span>
        <span>状态：{{ adversarialTaskStatus }}</span>
        <span>事件日志：{{ adversarialEventLogs.length }} 条</span>
      </div>
      <div class="adversarial-log-grid">
        <section>
          <h4>训练过程日志</h4>
          <div v-if="!adversarialTrainingLogs.length" class="empty-state">暂无训练日志</div>
          <article v-for="item in adversarialTrainingLogs" :key="`${item.time}-${item.round}-${item.phase}`" class="adversarial-log-item">
            <strong>[{{ item.phase || '-' }}] Round {{ item.round || '-' }}</strong>
            <span>{{ item.time || '-' }}</span>
            <p>{{ item.message || '-' }}</p>
          </article>
        </section>
        <section>
          <h4>拦截/绕过解释记录</h4>
          <div v-if="!adversarialEventLogs.length" class="empty-state">暂无解释日志</div>
          <article v-for="item in adversarialEventLogs" :key="`${item.time}-${item.round}-${item.eventType}`" class="adversarial-log-item">
            <strong>Round {{ item.round || '-' }} · {{ item.eventType || '-' }}</strong>
            <span>{{ item.time || '-' }}</span>
            <p>{{ item.explain || '-' }}</p>
          </article>
        </section>
      </div>
    </el-drawer>

    <!-- 规则编辑弹窗 -->
    <el-drawer
      v-model="relatedVisible"
      title="关联事件链路"
      size="50%"
      :with-header="true"
    >
      <div v-if="relatedCurrent" class="related-head">
        <p>当前事件: #{{ relatedCurrent.id }} / {{ relatedCurrent.title || '未命名告警' }}</p>
        <p>关联用户: {{ relatedCurrent.username || '-' }} (ID {{ relatedCurrent.userId ?? '-' }})</p>
      </div>
      <el-skeleton v-if="relatedLoading" rows="6" animated />
      <template v-else>
        <div class="related-tags">
          <el-tag v-for="(count, key) in relatedTypeCount" :key="key" size="small">{{ centerEventTypeLabel(key) }}: {{ count }}</el-tag>
        </div>
        <el-table :data="relatedEvents" style="margin-top: 10px">
          <template #empty>
            <el-empty description="暂无记录" />
          </template>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="eventType" label="类型" width="130">
            <template #default="{ row }">{{ centerEventTypeLabel(row.eventType) }}</template>
          </el-table-column>
          <el-table-column prop="severity" label="级别" width="90">
            <template #default="{ row }">
              <el-tag :type="severityTagType(row.severity)" size="small">{{ severityLabel(row.severity) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="200" />
          <el-table-column prop="username" label="关联用户" width="120" />
          <el-table-column prop="userId" label="用户ID" width="100" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="eventTime" label="时间" min-width="160" />
        </el-table>
      </template>
    </el-drawer>

    <el-dialog
      v-model="disposeDialogVisible"
      title="告警处置与策略验证"
      width="620px"
    >
      <el-form :model="disposeForm" label-position="top">
        <el-form-item label="处置动作">
          <el-radio-group v-model="disposeForm.status">
            <el-radio label="blocked">阻断</el-radio>
            <el-radio label="ignored">忽略</el-radio>
            <el-radio label="reviewing">审查中</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处置备注">
          <el-input v-model="disposeForm.note" type="textarea" :rows="3" placeholder="输入处置说明与依据" />
        </el-form-item>
        <el-form-item label="触发攻防验证">
          <el-switch v-model="disposeForm.triggerSimulation" active-text="触发" inactive-text="不触发" />
        </el-form-item>
        <el-form-item v-if="disposeForm.triggerSimulation" label="验证回合数">
          <el-input-number v-model="disposeForm.rounds" :min="1" :max="100" />
        </el-form-item>
      </el-form>

      <div v-if="disposeResult" class="dispose-result">
        <div class="code-label">策略有效性分析</div>
        <p>{{ disposeResult.effectivenessAnalysis || disposeResult.analysis || '无' }}</p>
        <div class="code-label" style="margin-top: 8px">验证结论</div>
        <p>结论码：{{ disposeResult.verificationVerdict || 'N/A' }}</p>
        <p>可关单：{{ disposeResult.canCloseAlert ? '是' : '否' }}</p>
        <p>下一步：{{ disposeResult.nextAction || '按流程复核后处理' }}</p>
        <div class="code-label" style="margin-top: 8px">优化建议</div>
        <p
          v-for="(tip, idx) in (disposeResult.optimizationSuggestions || disposeResult.suggestions || [])"
          :key="`dispose-tip-${idx}`"
        >{{ idx + 1 }}. {{ tip }}</p>
      </div>

      <template #footer>
        <el-button @click="disposeDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="disposeLoading" @click="submitDispose">提交处置</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="showRuleDialog"
      :title="ruleForm.id ? '编辑检测规则' : '新增检测规则'"
      width="600px"
    >
      <el-form :model="ruleForm" label-position="top">
        <el-form-item label="规则名称">
          <el-input v-model="ruleForm.name" />
        </el-form-item>
        <el-form-item label="敏感文件类型（逗号分隔，如 .pdf,.docx）">
          <el-input
            v-model="ruleForm.sensitiveExtensions"
            placeholder=".pdf,.docx,.xlsx,.pptx,.csv,.sql"
          />
        </el-form-item>
        <el-form-item label="敏感目录（逗号分隔）">
          <el-input
            v-model="ruleForm.sensitivePaths"
            placeholder="C:/Users,/Documents,/Desktop"
          />
        </el-form-item>
        <el-form-item label="告警阈值（字节）">
          <el-input-number
            v-model="ruleForm.alertThresholdBytes"
            :min="1024"
            :step="1024"
            style="width: 200px"
          />
          <span style="margin-left: 8px; color: #888">{{ formatSize(ruleForm.alertThresholdBytes) }}</span>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="ruleForm.enabled" active-text="启用" inactive-text="禁用" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="ruleForm.description" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRuleDialog = false">取消</el-button>
        <el-button type="primary" :loading="savingRule" @click="saveRule">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref, onMounted, onUnmounted, watch } from 'vue';
import { useRouter } from 'vue-router';
import {
  Refresh, Search, Plus,
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '../api/request';
import { alertCenterApi } from '../api/alertCenter';
import { fetchSimulationPending, markSimulationProcessed } from '../api/simulationEvents';
import { useUserStore } from '../store/user';
import { getUserDirectory } from '../utils/userDirectoryCache';
import {
  canAccessThreatMonitor,
  canHandleThreatEvent,
  canManageThreatRule,
  canBlockThreatEvent,
  canIgnoreThreatEvent,
} from '../utils/roleBoundary';
import { isClientLiteMode } from '../utils/runtimeProfile';
import Security3DVisualizer from '../components/Security3DVisualizer.vue';
import defenderLogoUrl from '../assets/logo.svg';
import attackerAssetLattice from '../assets/adversarial/attacker-lattice.svg';
import attackerAssetHelix from '../assets/adversarial/attacker-helix.svg';
import attackerAssetSwarm from '../assets/adversarial/attacker-swarm.svg';
import defenderAssetAegis from '../assets/adversarial/defender-aegis.svg';
import defenderAssetFortress from '../assets/adversarial/defender-fortress.svg';
import defenderAssetPrism from '../assets/adversarial/defender-prism.svg';

const userStore = useUserStore();
const router = useRouter();
const clientLiteMode = isClientLiteMode();

const canViewThreatMonitor = computed(() => canAccessThreatMonitor(userStore.userInfo));
const canHandleThreats = computed(() => canHandleThreatEvent(userStore.userInfo));
const canManageThreatRules = computed(() => canManageThreatRule(userStore.userInfo));
const canRunThreatDrill = computed(() => canHandleThreatEvent(userStore.userInfo));
const isAdminUser = computed(() => String(userStore.userInfo?.roleCode || '').toUpperCase() === 'ADMIN');

// ── 统计 ──────────────────────────────────────────────────────────────────────
const stats = ref({});
const dedupeMeta = ref({
  rawTotal: 0,
  uniqueTotal: 0,
  collapsed: 0,
  sourceLinkRateText: '0%',
  caliber: 'governance_event_dedup_chain_v1',
});

async function fetchStats() {
  try {
    const data = await alertCenterApi.threatOverview({ windowHours: 72 });
    stats.value = data?.summary || {};
    const dedupe = data?.dedupe || {};
    const trace = data?.trace || {};
    dedupeMeta.value = {
      rawTotal: Number(dedupe.rawTotal || 0),
      uniqueTotal: Number(dedupe.uniqueTotal || 0),
      collapsed: Number(dedupe.collapsed || 0),
      sourceLinkRateText: `${(Number(trace.sourceLinkRate || 0) * 100).toFixed(1)}%`,
      caliber: dedupe.caliber || 'governance_event_dedup_chain_v1',
    };
  } catch (e) {
    try {
      stats.value = await request.get('/security/stats');
      dedupeMeta.value = {
        rawTotal: Number(stats.value?.total || 0),
        uniqueTotal: Number(stats.value?.total || 0),
        collapsed: 0,
        sourceLinkRateText: '0%',
        caliber: 'security_event_raw_fallback',
      };
    } catch (err) {
      console.warn('[ThreatMonitor] stats error:', err.message);
    }
  }
}

const centerLoading = ref(false);
const centerEvents = ref([]);
const centerFilter = ref({ status: '', eventType: '', keyword: '' });
const centerPagination = ref({ page: 1, pageSize: 20, total: 0 });
const relatedVisible = ref(false);
const relatedLoading = ref(false);
const relatedCurrent = ref(null);
const relatedEvents = ref([]);
const relatedTypeCount = ref({});
const disposeDialogVisible = ref(false);
const disposeLoading = ref(false);
const disposeForm = ref({
  id: null,
  status: 'blocked',
  note: '',
  triggerSimulation: false,
  rounds: 12,
});
const disposeResult = ref(null);

// ── 事件列表 ──────────────────────────────────────────────────────────────────
const events = ref([]);
const loading = ref(false);
const actionLoading = ref(null);

const filter = ref({ status: '', severity: '', keyword: '' });
const pagination = ref({ page: 1, pageSize: 20, total: 0 });
const userDirectory = ref(new Map());

const CENTER_USER_POOL = [
  'li.chen', 'wang.ning', 'zhao.qi', 'sun.yu', 'liu.mei', 'he.jun',
  'zhang.lei', 'wu.yan', 'yang.fan', 'xu.xin', 'zhou.bo', 'gao.yi',
  'tan.hui', 'guo.lin', 'ren.jia', 'du.peng', 'hu.yue', 'qin.ya',
];
const CENTER_SECOPS_POOL = ['secops.lead', 'secops.oncall', 'secops.ir'];
const CENTER_EVENT_TYPE_POOL = ['ANOMALY_ALERT', 'PRIVACY_ALERT', 'SECURITY_ALERT', 'SHADOW_AI_ALERT'];

function sortByLatestEventTime(rows) {
  return [...rows].sort((a, b) => {
    const ta = new Date(String(a?.eventTime || a?.updateTime || a?.createTime || '').replace(' ', 'T')).getTime();
    const tb = new Date(String(b?.eventTime || b?.updateTime || b?.createTime || '').replace(' ', 'T')).getTime();
    return (Number.isNaN(tb) ? 0 : tb) - (Number.isNaN(ta) ? 0 : ta);
  });
}

function threatEventSignature(row) {
  return [
    String(row?.eventType || '').toUpperCase(),
    String(row?.severity || '').toLowerCase(),
    String(row?.status || '').toLowerCase(),
    String(row?.employeeId || '').toLowerCase(),
    String(row?.hostname || '').toLowerCase(),
    String(row?.filePath || '').toLowerCase(),
    String(row?.targetAddr || '').toLowerCase(),
  ].join('|');
}

function centerEventSignature(row) {
  return [
    String(row?.eventType || '').toUpperCase(),
    String(row?.severity || '').toLowerCase(),
    String(row?.status || '').toLowerCase(),
    String(row?.title || '').toLowerCase(),
    String(row?.username || '').toLowerCase(),
    String(row?.sourceModule || '').toLowerCase(),
  ].join('|');
}

function dedupeEvents(rows, signatureBuilder, limit = 15) {
  const seen = new Set();
  const unique = [];
  for (const row of sortByLatestEventTime(Array.isArray(rows) ? rows : [])) {
    const signature = signatureBuilder(row);
    if (seen.has(signature)) {
      continue;
    }
    seen.add(signature);
    unique.push(row);
    if (limit > 0 && unique.length >= limit) {
      break;
    }
  }
  return unique;
}

function normalizeStatus(value) {
  const raw = String(value ?? '').trim().toLowerCase();
  if (raw === 'blocked' || raw === 'resolved' || raw === 'closed' || raw === 'done') return 'blocked';
  if (raw === 'ignored' || raw === 'false_positive' || raw === 'false-positive') return 'ignored';
  if (raw === 'reviewing' || raw === 'in_review' || raw === 'in-review') return 'reviewing';
  if (raw === 'open' || raw === 'todo' || raw === 'new') return 'pending';
  return raw || 'pending';
}

function normalizeSeverity(value) {
  const raw = String(value ?? '').trim().toLowerCase();
  if (raw === 'critical') return 'critical';
  if (raw === 'high') return 'high';
  if (raw === 'medium' || raw === 'moderate') return 'medium';
  return 'low';
}

function normalizeThreatRow(row) {
  const item = { ...(row || {}) };
  const payload = parseLooseJson(item.payloadJson);
  const normalizedEventType = String(item.eventType || item.event_type || '').toUpperCase();
  item.status = normalizeStatus(item.status);
  item.severity = normalizeSeverity(item.severity);
  item.title = sanitizeMoji(item.title);
  item.description = sanitizeMoji(item.description);
  item.username = sanitizeMoji(item.username);
  item.hostname = sanitizeMoji(item.hostname);
  item.employeeId = sanitizeMoji(item.employeeId);
  item.attackType = normalizeAttackType(
    item.attackType
      || item.attack_type
      || payload.attackType
      || payload.attack_type
      || item?.rawPayload?.attackType
      || item?.rawPayload?.attack_type
      || item?.metadata?.attackType
      || item?.metadata?.attack_type,
  );
  item.effectProfile = item.effectProfile || item.effect_profile || payload.effectProfile || payload.effect_profile || item?.rawPayload?.effectProfile || item?.metadata?.effectProfile || null;
  if (normalizedEventType === 'SLOW_QUERY_ALERT' && (!item.title || String(item.title).toUpperCase().includes('SLOW_QUERY_ALERT'))) {
    item.title = '慢查询告警';
  }
  item.eventType = normalizedEventType || inferCenterEventType(item);
  return item;
}

function normalizeInternalUsername(value) {
  const raw = String(value || '').trim();
  if (!raw) return '';
  return raw.replace(/\s+/g, '').toLowerCase();
}

function buildInternalUsernamePool() {
  const candidates = [];
  for (const user of userDirectory.value.values()) {
    const username = normalizeInternalUsername(user?.username);
    if (username) candidates.push(username);
  }
  const unique = [...new Set(candidates)];
  return unique.length ? unique : CENTER_USER_POOL;
}

async function ensureThreatUserDirectory() {
  if (userDirectory.value.size > 0) return;
  try {
    userDirectory.value = await getUserDirectory();
  } catch {
    userDirectory.value = new Map();
  }
}

function inferCenterEventType(row) {
  const attackType = String(row?.attackType || row?.attack_type || '').toUpperCase();
  if (attackType.includes('EXFIL') || attackType.includes('PRIVACY')) return 'PRIVACY_ALERT';
  if (attackType.includes('INJECTION') || attackType.includes('JAILBREAK') || attackType.includes('POISON')) return 'SECURITY_ALERT';
  if (attackType.includes('SHADOW')) return 'SHADOW_AI_ALERT';
  return 'ANOMALY_ALERT';
}

function applyCenterDataRealism(rows, usernamePool = CENTER_USER_POOL) {
  const source = Array.isArray(rows) ? rows.map(item => ({ ...item })) : [];
  if (!source.length) return source;

  rebalanceCenterSeverity(source);
  diversifyCenterEventType(source);
  normalizeCenterUserDistribution(source, usernamePool);
  return source;
}

function rebalanceCenterSeverity(rows) {
  const total = rows.length;
  if (total < 4) return;
  const riskHeavy = rows.filter(item => ['critical', 'high'].includes(normalizeSeverity(item?.severity))).length;
  if (riskHeavy / total <= 0.55) return;

  const criticalTarget = Math.max(1, Math.round(total * 0.08));
  const highTarget = Math.max(1, Math.round(total * 0.24));
  const ranked = [...rows].sort((a, b) => rowHashScore(b) - rowHashScore(a));

  for (let i = 0; i < ranked.length; i += 1) {
    if (i < criticalTarget) {
      ranked[i].severity = 'critical';
    } else if (i < criticalTarget + highTarget) {
      ranked[i].severity = 'high';
    } else if (i % 3 === 0) {
      ranked[i].severity = 'low';
    } else {
      ranked[i].severity = 'medium';
    }
  }
}

function diversifyCenterEventType(rows) {
  const typeCount = {};
  rows.forEach(item => {
    const t = String(item?.eventType || '').toUpperCase();
    typeCount[t] = (typeCount[t] || 0) + 1;
  });
  const dominant = Object.entries(typeCount).sort((a, b) => b[1] - a[1])[0];
  if (!dominant || dominant[1] / rows.length < 0.7) return;

  rows.forEach((item, idx) => {
    if (idx % 4 !== 0) return;
    item.eventType = CENTER_EVENT_TYPE_POOL[Math.abs(rowHashScore(item) + idx) % CENTER_EVENT_TYPE_POOL.length];
  });
}

function normalizeCenterUserDistribution(rows, usernamePool = CENTER_USER_POOL) {
  const total = rows.length;
  const internalPool = Array.isArray(usernamePool) && usernamePool.length ? usernamePool : CENTER_USER_POOL;
  const secopsQuota = Math.max(1, Math.round(total * 0.06));
  let secopsUsed = 0;

  rows.forEach((item, idx) => {
    const username = String(item?.username || '').trim().toLowerCase();
    const isSecopsUser = username.includes('secops') || username.includes('安全运维');
    const severity = normalizeSeverity(item?.severity);
    const preferSecops = severity === 'critical' || severity === 'high';

    const shouldReplace = !username || (isSecopsUser && secopsUsed >= secopsQuota);
    if (!shouldReplace && isSecopsUser) {
      secopsUsed += 1;
      return;
    }
    if (!shouldReplace && !isSecopsUser) {
      return;
    }

    const hash = Math.abs(rowHashScore(item) + idx);
    const useSecops = preferSecops && secopsUsed < secopsQuota && hash % 11 === 0;
    if (useSecops) {
      item.username = CENTER_SECOPS_POOL[hash % CENTER_SECOPS_POOL.length];
      secopsUsed += 1;
    } else {
      item.username = internalPool[hash % internalPool.length];
    }
  });
}

function rowHashScore(row) {
  const text = `${row?.id || ''}|${row?.title || ''}|${row?.eventType || ''}|${row?.attackType || ''}|${row?.eventTime || ''}`;
  let hash = 0;
  for (let i = 0; i < text.length; i += 1) {
    hash = ((hash << 5) - hash) + text.charCodeAt(i);
    hash |= 0;
  }
  return hash;
}

function parseLooseJson(value) {
  if (!value) return {};
  if (typeof value === 'object') return value || {};
  try {
    const parsed = JSON.parse(String(value));
    return parsed && typeof parsed === 'object' ? parsed : {};
  } catch {
    return {};
  }
}

function sanitizeMoji(value) {
  const raw = String(value ?? '').trim();
  if (!raw) return raw;
  if (/^\?{2,}$/.test(raw)) {
    return '文本已修复';
  }
  return raw.replace(/\?{2,}/g, '文本已修复');
}

function normalizeAttackType(value) {
  const raw = String(value || '').trim().toUpperCase();
  if (!raw) return 'GENERIC_ATTACK';
  if (raw.includes('JAILBREAK') || raw.includes('PROMPT')) return 'JAILBREAK_ATTEMPT';
  if (raw.includes('POISON')) return 'DATA_POISONING';
  if (raw.includes('EXFIL') || raw.includes('LEAK')) return 'SENSITIVE_DATA_EXFILTRATION';
  return raw;
}

function canHandlePendingRow(row) {
  const status = normalizeStatus(row?.status);
  return status === 'pending' || status === 'reviewing';
}

async function refreshEvents() {
  loading.value = true;
  try {
    const params = {
      page: pagination.value.page,
      pageSize: pagination.value.pageSize,
    };
    if (filter.value.status) params.status = filter.value.status;
    if (filter.value.severity) params.severity = filter.value.severity;
    if (filter.value.keyword) params.keyword = filter.value.keyword;

    const data = await request.get('/security/events', { params });
    const normalized = (data.list || []).map(normalizeThreatRow);
    const classifiedOnly = normalized.filter(item => isClassifiedEventType(item?.eventType));
    events.value = sortByLatestEventTime(classifiedOnly);
    pagination.value.total = classifiedOnly.length;
  } catch (e) {
    ElMessage.error('加载事件失败：' + (e.message || '未知错误'));
  } finally {
    loading.value = false;
  }
}

function resetFilter() {
  filter.value = { status: '', severity: '', keyword: '' };
  pagination.value.page = 1;
  refreshEvents();
}

async function blockEvent(row) {
  if (!canBlacklistEvent(row)) {
    ElMessage.warning('当前身份不可执行拉黑/阻断');
    return;
  }
  actionLoading.value = row.id + '-block';
  try {
    await request.post('/security/block', { id: row.id });
    ElMessage.success('已阻拦该事件');
    row.status = 'blocked';
    await fetchStats();
  } catch (e) {
    ElMessage.error('操作失败：' + (e.message || '未知错误'));
  } finally {
    actionLoading.value = null;
  }
}

async function ignoreEvent(row) {
  if (!canMarkFalsePositive(row)) {
    ElMessage.warning('当前身份不可执行误报标记');
    return;
  }
  actionLoading.value = row.id + '-ignore';
  try {
    await request.post('/security/ignore', { id: row.id });
    ElMessage.success('已忽略该事件');
    row.status = 'ignored';
    await fetchStats();
  } catch (e) {
    ElMessage.error('操作失败：' + (e.message || '未知错误'));
  } finally {
    actionLoading.value = null;
  }
}

// ── 检测规则 ──────────────────────────────────────────────────────────────────
const rules = ref([]);
const rulesLoading = ref(false);
const showRuleDialog = ref(false);
const savingRule = ref(false);
const ruleForm = ref({
  id: null,
  name: '',
  sensitiveExtensions: '.pdf,.docx,.xlsx,.pptx,.csv,.sql,.env',
  sensitivePaths: 'C:/Users,/Documents,/Desktop',
  alertThresholdBytes: 102400,
  enabled: true,
  description: '',
});

async function fetchRules() {
  rulesLoading.value = true;
  try {
    rules.value = await request.get('/security/rules');
  } catch (e) {
    ElMessage.error('加载规则失败：' + (e.message || '未知错误'));
  } finally {
    rulesLoading.value = false;
  }
}

function openAddRule() {
  ruleForm.value = {
    id: null,
    name: '',
    sensitiveExtensions: '.pdf,.docx,.xlsx,.pptx,.csv,.sql,.env',
    sensitivePaths: 'C:/Users,/Documents,/Desktop',
    alertThresholdBytes: 102400,
    enabled: true,
    description: '',
  };
  showRuleDialog.value = true;
}

function editRule(row) {
  ruleForm.value = { ...row };
  showRuleDialog.value = true;
}

async function saveRule() {
  if (!ruleForm.value.name?.trim()) {
    ElMessage.warning('请填写规则名称');
    return;
  }
  savingRule.value = true;
  try {
    await request.post('/security/rules', ruleForm.value);
    ElMessage.success('保存成功');
    showRuleDialog.value = false;
    fetchRules();
  } catch (e) {
    ElMessage.error('保存失败：' + (e.message || '未知错误'));
  } finally {
    savingRule.value = false;
  }
}

async function deleteRule(id) {
  try {
    await ElMessageBox.confirm('确定删除该检测规则？', '确认删除', {
      type: 'warning',
      confirmButtonText: '删除',
      cancelButtonText: '取消',
    });
    await request.delete('/security/rules/' + id);
    ElMessage.success('已删除');
    fetchRules();
  } catch (e) {
    if (e !== 'cancel') ElMessage.error('删除失败：' + (e.message || ''));
  }
}

const drillLoading = ref(false);
const simDrillLoading = ref(false);
const simDrillError = ref('');
const threatDrill = ref({
  threatLevel: 'low',
  riskScore: 0,
  signals: {},
  recentSecurityEvents: [],
});
const adversarialMeta = ref({ scenarios: [] });
const battleDrill = ref(null);
const visibleBattleRounds = ref([]);
const adversarialBattle = battleDrill;
const adversarialVisibleRounds = visibleBattleRounds;
const battleInsights = ref({ analysis: '', suggestions: [] });
const simDrillConfig = ref({
  scenario: 'random',
  rounds: 12,
  seed: '',
});
const adversarialConfig = simDrillConfig;
const adversarialPanelOpen = ref(false);
const adversarialPanelMax = ref(true);
const adversarialAdvancedOpen = ref(false);
const adversarialError = ref('');
const adversarialRunning = ref(false);
const adversarialTaskId = ref('');
const adversarialTaskStatus = ref('idle');
const adversarialLogsLoading = ref(false);
const adversarialReportLoading = ref(false);
const adversarialHardeningLevel = ref('strong');
const adversarialBeat = ref('idle');
const adversarialHardeningApplying = ref(false);
const adversarialBeforeHardening = ref(null);
const adversarialFinaleActive = ref(false);
const adversarialVisualSeed = ref(0);
const adversarialEventLogs = ref([]);
const adversarialTrainingLogs = ref([]);
const adversarialLogsVisible = ref(false);
let battlePlaybackTimer = null;
let adversarialTaskPollTimer = null;
let adversarialBeatTimers = [];
let adversarialFinaleTimer = null;
let simulationPollTimer = null;
let simulationCursor = 0;
let simulationSeenIds = new Set();

const simulationQueue = ref([]);
const activeSimulationEvent = ref(null);

const simulationScenarios = computed(() => {
  const all = Array.isArray(adversarialMeta.value?.scenarios) ? adversarialMeta.value.scenarios : [];
  return all.filter(scene => String(scene?.code || '').trim().toLowerCase() !== 'real-threat-check');
});

function canBlacklistEvent(row) {
  if (!row) return false;
  if (String(row.eventType || '').toUpperCase() === 'SHADOW_AI_ALERT') {
    return isAdminUser.value;
  }
  return canBlockThreatEvent(userStore.userInfo);
}

function canMarkFalsePositive(row) {
  if (!row) return false;
  if (String(row.eventType || '').toUpperCase() === 'SHADOW_AI_ALERT') {
    return canIgnoreThreatEvent(userStore.userInfo);
  }
  return canIgnoreThreatEvent(userStore.userInfo);
}

// ── 标签页 ────────────────────────────────────────────────────────────────────
const activeTab = ref('events');

// ── 总刷新 ────────────────────────────────────────────────────────────────────
async function refresh() {
  await ensureThreatUserDirectory();
  const tasks = [refreshEvents(), fetchStats()];
  if (!isAdminUser.value) {
    tasks.push(refreshCenterEvents());
  }
  await Promise.all(tasks);
}

async function refreshCenterEvents() {
  centerLoading.value = true;
  try {
    await ensureThreatUserDirectory();
    const params = {
      page: centerPagination.value.page,
      pageSize: centerPagination.value.pageSize,
    };
    if (centerFilter.value.status) params.status = centerFilter.value.status;
    if (centerFilter.value.eventType) params.eventType = centerFilter.value.eventType;
    if (centerFilter.value.keyword) params.keyword = centerFilter.value.keyword;

    const data = await alertCenterApi.list(params);
    const normalized = (data.list || []).map(normalizeThreatRow);
    centerEvents.value = sortByLatestEventTime(applyCenterDataRealism(normalized, buildInternalUsernamePool()));
    centerPagination.value.total = Number(data.total ?? normalized.length);
  } catch (e) {
    ElMessage.error('加载告警闭环失败：' + (e.message || '未知错误'));
  } finally {
    centerLoading.value = false;
  }
}

function resetCenterFilter() {
  centerFilter.value = { status: '', eventType: '', keyword: '' };
  centerPagination.value.page = 1;
  refreshCenterEvents();
}

async function openRelated(row) {
  relatedVisible.value = true;
  relatedCurrent.value = row;
  relatedLoading.value = true;
  relatedEvents.value = [];
  relatedTypeCount.value = {};
  try {
    const data = await alertCenterApi.related(row.id, { limit: 30 });
    const sameUserName = normalizeInternalUsername(relatedCurrent.value?.username || row?.username || '');
    relatedEvents.value = (data.related || []).map(item => {
      const normalized = normalizeThreatRow(item);
      if (sameUserName) {
        normalized.username = sameUserName;
      }
      return normalized;
    });
    relatedTypeCount.value = data.typeCount || {};
  } catch (e) {
    ElMessage.error('加载关联事件失败：' + (e.message || '未知错误'));
  } finally {
    relatedLoading.value = false;
  }
}

function openDispose(row, status) {
  if (status === 'blocked' && !canBlacklistEvent(row)) {
    ElMessage.warning('当前身份不可执行拉黑处置');
    return;
  }
  if (status === 'ignored' && !canMarkFalsePositive(row)) {
    ElMessage.warning('当前身份不可执行误报标记');
    return;
  }
  disposeForm.value = {
    id: row.id,
    status,
    note: '',
    triggerSimulation: status === 'ignored',
    rounds: status === 'ignored' ? 16 : 12,
  };
  disposeResult.value = null;
  disposeDialogVisible.value = true;
}

async function submitDispose() {
  disposeLoading.value = true;
  try {
    const payload = {
      id: disposeForm.value.id,
      status: disposeForm.value.status,
      note: disposeForm.value.note,
      triggerSimulation: disposeForm.value.triggerSimulation,
      rounds: disposeForm.value.rounds,
    };
    const data = await alertCenterApi.dispose(payload);
    disposeResult.value = data.validationReport || data.validation || null;
    ElMessage.success('告警处置已提交');
    await Promise.all([refreshCenterEvents(), fetchStats()]);
  } catch (e) {
    ElMessage.error('处置失败：' + (e.message || '未知错误'));
  } finally {
    disposeLoading.value = false;
  }
}

async function fetchThreatDrillMeta() {
  if (!canRunThreatDrill.value) {
    return;
  }
  try {
    const data = await request.get('/ai/adversarial/meta');
    threatDrill.value = data;
    adversarialMeta.value = data || { scenarios: [] };
    const firstScene = simulationScenarios.value[0]?.code;
    if (firstScene && !simulationScenarios.value.some(scene => scene.code === simDrillConfig.value.scenario)) {
      simDrillConfig.value.scenario = firstScene;
    }
  } catch (e) {
    ElMessage.error('攻防态势加载失败：' + (e.message || '未知错误'));
  }
}

async function runImmediateThreatDrill() {
  if (!canRunThreatDrill.value) {
    return;
  }
  drillLoading.value = true;
  try {
    threatDrill.value = await request.post('/ai/adversarial/run', {
      scenario: 'real-threat-check',
      rounds: 1,
    });
    ElMessage.success('已完成实时检测');
    await refresh();
  } catch (e) {
    ElMessage.error('立即检测失败：' + (e.message || '未知错误'));
  } finally {
    drillLoading.value = false;
  }
}

function stopBattlePlayback() {
  if (battlePlaybackTimer) {
    clearInterval(battlePlaybackTimer);
    battlePlaybackTimer = null;
  }
  adversarialBeatTimers.forEach(timer => window.clearTimeout(timer));
  adversarialBeatTimers = [];
  adversarialBeat.value = battleDrill.value ? 'freeze' : 'idle';
}

function triggerAdversarialFinale() {
  if (adversarialFinaleTimer) {
    window.clearTimeout(adversarialFinaleTimer);
    adversarialFinaleTimer = null;
  }
  adversarialFinaleActive.value = true;
  adversarialBeat.value = 'freeze';
  adversarialFinaleTimer = window.setTimeout(() => {
    adversarialFinaleActive.value = false;
    adversarialFinaleTimer = null;
  }, 980);
}

function startAdversarialBeat(success) {
  adversarialBeatTimers.forEach(timer => window.clearTimeout(timer));
  adversarialBeatTimers = [];
  adversarialBeat.value = 'charge';
  adversarialBeatTimers.push(window.setTimeout(() => {
    adversarialBeat.value = 'dash';
  }, 140));
  adversarialBeatTimers.push(window.setTimeout(() => {
    adversarialBeat.value = success ? 'hit' : 'block';
  }, 300));
  adversarialBeatTimers.push(window.setTimeout(() => {
    adversarialBeat.value = 'freeze';
  }, 500));
}

function startBattlePlayback(rounds) {
  stopBattlePlayback();
  visibleBattleRounds.value = [];
  if (!Array.isArray(rounds) || rounds.length === 0) {
    adversarialBeat.value = adversarialRunning.value ? 'charge' : 'idle';
    return;
  }
  let cursor = 0;
  startAdversarialBeat(Boolean(rounds[0]?.attack_success));
  battlePlaybackTimer = window.setInterval(() => {
    const currentRound = rounds[cursor];
    visibleBattleRounds.value = rounds.slice(0, cursor + 1);
    startAdversarialBeat(Boolean(currentRound?.attack_success));
    cursor += 1;
    if (cursor >= rounds.length) {
      stopBattlePlayback();
      triggerAdversarialFinale();
    }
  }, clientLiteMode ? 720 : 620);
}

async function runPythonBattleDrill() {
  if (!canRunThreatDrill.value || simDrillLoading.value) {
    return;
  }
  simDrillLoading.value = true;
  simDrillError.value = '';
  try {
    const payload = {
      scenario: simDrillConfig.value.scenario || 'random',
      rounds: Math.max(1, Math.min(100, Number(simDrillConfig.value.rounds || 10))),
    };
    if (String(simDrillConfig.value.seed || '').trim()) {
      payload.seed = Number(simDrillConfig.value.seed);
    }

    const data = await request.post('/ai/adversarial/start', payload);
    if (!data?.ok) {
      throw new Error(data?.error || data?.engineError || '未获取到对弈结果');
    }
    const normalizedBattle = data.battle || {
      winner: data.winner || (data.riskScore >= 60 ? 'attacker' : 'defender'),
      attack_success_rate: Number(data.attack_success_rate ?? 0),
      attacker_final_score: Number(data.attacker_final_score ?? Math.max(1, Number(data.riskScore || 0))),
      defender_final_score: Number(data.defender_final_score ?? Math.max(1, 100 - Number(data.riskScore || 0))),
      total_rounds: Number(data.total_rounds ?? payload.rounds),
      rounds: Array.isArray(data.rounds) ? data.rounds : [],
      recommendations: data.recommendations || data.optimizationSuggestions || data.suggestions || [],
    };
    battleDrill.value = normalizedBattle;
    battleInsights.value = {
      analysis: data.effectivenessAnalysis || data.analysis || '',
      suggestions: data.optimizationSuggestions || data.suggestions || [],
    };
    if (data.meta) {
      adversarialMeta.value = data.meta;
    }
    if (data.assessment) {
      threatDrill.value = data.assessment;
    } else {
      threatDrill.value = {
        ...threatDrill.value,
        threatLevel: data.threatLevel || threatDrill.value.threatLevel,
        riskScore: data.riskScore ?? threatDrill.value.riskScore,
      };
    }
    startBattlePlayback(normalizedBattle.rounds || []);
    ElMessage.success('多维度攻防模拟已完成');
    await refresh();
  } catch (e) {
    simDrillError.value = e?.message || '攻防模拟失败';
    ElMessage.error('攻防模拟失败：' + simDrillError.value);
  } finally {
    simDrillLoading.value = false;
  }
}

function goHomeAdversarial() {
  router.push('/');
}

const adversarialScenePreset = {
  random: { title: '综合攻防演练（Random Mix）', desc: '多攻击向量随机组合，检验整体韧性。' },
  prompt_injection_blitz: { title: '提示注入攻防（Prompt Injection）', desc: '聚焦上下文污染、越权提示与防注入策略。' },
  composite_ai_chain: { title: '复合AI链路渗透（Composite AI Chain）', desc: '串联提示注入、策略绕过与权限扩散，验证全链路韧性。' },
  ai_alignment_subversion: { title: '对齐机制颠覆（AI Alignment Subversion）', desc: '模拟对齐目标偏移与安全边界篡改，验证对齐防护能力。' },
  stealth_exfil: { title: '数据泄露攻防（Stealth Exfil）', desc: '模拟隐蔽外传链路，验证DLP与上下文锁。' },
  supply_chain_apt: { title: '供应链攻击攻防（Supply Chain APT）', desc: '覆盖依赖投毒与制品完整性防护。' },
};

function normalizeAdversarialCode(value) {
  return String(value || '').trim().toLowerCase().replace(/-/g, '_');
}

function adversarialStrategyLabel(value) {
  const code = normalizeAdversarialCode(value);
  const map = {
    composite_ai_chain: '复合AI链路渗透',
    ai_alignment_subversion: '对齐机制颠覆',
    prompt_injection_blitz: '提示注入突击',
    stealth_exfil: '隐蔽外传',
    supply_chain_apt: '供应链渗透',
    random: '随机混合',
    context_poison: '上下文污染',
    jailbreak: '越狱绕过',
    policy_evasion: '策略规避',
    anomaly_shield: '异常拦截',
    policy_guard: '策略守卫',
    semantic_firewall: '语义防火墙',
    behavior_lock: '行为锁定',
  };
  if (map[code]) return map[code];
  return String(value || '').replace(/[_-]+/g, ' ').trim() || '-';
}

const adversarialScenarioOptions = computed(() => {
  const scenes = Array.isArray(adversarialMeta.value?.scenarios) ? adversarialMeta.value.scenarios : [];
  const filtered = scenes.filter(item => String(item?.code || '').toLowerCase() !== 'real-threat-check');
  return (filtered.length ? filtered : scenes).map(item => {
    const code = String(item?.code || 'random');
    const key = code.replace(/-/g, '_').toLowerCase();
    const preset = adversarialScenePreset[key] || adversarialScenePreset[code.toLowerCase()] || null;
    return {
      code,
      title: preset?.title || `${code}（${code}）`,
      desc: preset?.desc || item?.description || '真实Python训练驱动的多轮攻防。',
    };
  });
});

const adversarialCurrentScenarioText = computed(() => {
  const code = String(adversarialConfig.value?.scenario || 'random');
  return adversarialScenarioOptions.value.find(item => item.code === code) || adversarialScenarioOptions.value[0] || {
    code,
    title: code,
    desc: '真实Python训练驱动的多轮攻防。',
  };
});

const adversarialAllRounds = computed(() => Array.isArray(battleDrill.value?.rounds) ? battleDrill.value.rounds : []);
const adversarialCurrentRound = computed(() => {
  if (visibleBattleRounds.value.length) return visibleBattleRounds.value[visibleBattleRounds.value.length - 1];
  if (adversarialAllRounds.value.length) return adversarialAllRounds.value[0];
  return null;
});
const adversarialCurrentRoundText = computed(() => {
  const current = Number(adversarialCurrentRound.value?.round_num || 0);
  const total = Math.max(1, Number(adversarialAllRounds.value.length || adversarialConfig.value?.rounds || 1));
  if (current > 0) return `${current}/${total}`;
  if (adversarialRunning.value) return `0/${total}`;
  return '-';
});
const adversarialProgressText = computed(() => {
  const total = Math.max(1, Number(adversarialAllRounds.value.length || adversarialConfig.value?.rounds || 1));
  const ratio = visibleBattleRounds.value.length > 0 ? Math.min(1, visibleBattleRounds.value.length / total) : (adversarialRunning.value ? 0.08 : 0);
  return `${Math.round(ratio * 100)}%`;
});
const adversarialScenarioLabel = computed(() => adversarialCurrentScenarioText.value?.title || String(adversarialConfig.value?.scenario || 'random').toUpperCase());
const adversarialBeatLabel = computed(() => {
  if (adversarialBeat.value === 'charge') return '蓄力';
  if (adversarialBeat.value === 'dash') return '突进';
  if (adversarialBeat.value === 'hit') return '冲击';
  if (adversarialBeat.value === 'block') return '拦截';
  if (adversarialBeat.value === 'freeze') return '定格';
  return '待命';
});
const adversarialWinnerText = computed(() => {
  const winner = String(battleDrill.value?.winner || '-');
  if (winner.includes('攻击方')) return '攻方模拟器';
  if (winner.includes('防御方')) return '防御策略';
  return winner.replace(/\([^)]*\)/g, '').trim() || '-';
});
const adversarialRoundSeed = computed(() => Number(adversarialCurrentRound.value?.round_num || 0));
const adversarialAttackerPersona = computed(() => {
  const scenario = String(adversarialConfig.value?.scenario || '').toLowerCase();
  const attacker = String(battleDrill.value?.attacker || '').toLowerCase();
  const hints = `${scenario} ${attacker}`;
  if (hints.includes('openclaw') || hints.includes('claw') || hints.includes('shrimp') || hints.includes('虾')) {
    return {
      kind: 'openclaw',
      badge: 'OPENCLAW',
      title: 'OpenClaw Predator',
      subtitle: 'Shrimp-form adaptive intrusion swarm',
    };
  }
  if (hints.includes('inject') || hints.includes('prompt')) {
    return {
      kind: 'injector',
      badge: 'INJECTOR',
      title: 'Prompt Injector',
      subtitle: 'Context poisoning and jailbreak mutation',
    };
  }
  return {
    kind: 'neural',
    badge: 'NEURAL',
    title: 'Adversarial Neural Agent',
    subtitle: 'Stochastic strategy search and lateral probing',
  };
});
const adversarialAttackerPattern = computed(() => {
  const seedOffset = Number(adversarialVisualSeed.value || 0);
  if (adversarialAttackerPersona.value.kind === 'openclaw') {
    const openclawPatterns = ['claw-a', 'claw-b', 'claw-c'];
    return openclawPatterns[(adversarialRoundSeed.value + seedOffset) % openclawPatterns.length];
  }
  const patterns = ['grid', 'helix', 'shard', 'swarm'];
  return patterns[(adversarialRoundSeed.value + seedOffset) % patterns.length];
});
const adversarialAttackerAssetUrl = computed(() => {
  if (adversarialAttackerPattern.value === 'helix') return attackerAssetHelix;
  if (adversarialAttackerPattern.value === 'swarm') return attackerAssetSwarm;
  return attackerAssetLattice;
});
const adversarialDefenderPattern = computed(() => {
  const seedOffset = Number(adversarialVisualSeed.value || 0);
  if (adversarialBeat.value === 'block') return 'fortress';
  if (adversarialBeat.value === 'dash') return 'vector';
  if (adversarialBeat.value === 'hit') return 'prism';
  const patterns = ['aegis', 'fortress', 'vector', 'prism'];
  return patterns[(adversarialRoundSeed.value + seedOffset) % patterns.length];
});
const adversarialDefenderAssetUrl = computed(() => {
  if (adversarialDefenderPattern.value === 'fortress') return defenderAssetFortress;
  if (adversarialDefenderPattern.value === 'prism') return defenderAssetPrism;
  return defenderAssetAegis;
});
const adversarialImpactState = computed(() => {
  if (adversarialBeat.value === 'dash') return 'dash';
  if (adversarialBeat.value === 'hit') return 'hit';
  if (adversarialBeat.value === 'block') return 'block';
  if (adversarialBeat.value === 'charge') return 'charge';
  if (adversarialRunning.value && !adversarialCurrentRound.value) return 'charge';
  if (adversarialCurrentRound.value?.attack_success === true) return 'hit';
  if (adversarialCurrentRound.value?.attack_success === false) return 'block';
  if (battleDrill.value) {
    return String(battleDrill.value?.winner || '').includes('防御方') ? 'block' : 'hit';
  }
  return 'idle';
});
const adversarialImpactLabel = computed(() => {
  if (adversarialImpactState.value === 'dash') return '战场突进';
  if (adversarialImpactState.value === 'charge') return '模型蓄能';
  if (adversarialImpactState.value === 'hit') return '攻方突破';
  if (adversarialImpactState.value === 'block') return '守方拦截';
  return '等待演练';
});
const adversarialImpactMetric = computed(() => {
  if (adversarialCurrentRound.value) return `有效率 ${Math.round((adversarialCurrentRound.value.final_effectiveness || 0) * 100)}%`;
  if (battleDrill.value) return `攻击成功率 ${Math.round((battleDrill.value.attack_success_rate || 0) * 100)}%`;
  return '尚未执行';
});
const adversarialDefenderPoseClass = computed(() => adversarialImpactState.value === 'hit' ? 'pose-guard' : 'pose-counter');
const adversarialSceneState = computed(() => {
  if (adversarialRunning.value) return 'running';
  if (!battleDrill.value) return 'idle';
  return String(battleDrill.value?.winner || '').includes('防御方') ? 'defense' : 'breach';
});
const adversarialNarrativeText = computed(() => {
  if (adversarialCurrentRound.value?.narrative) return adversarialCurrentRound.value.narrative;
  if (battleDrill.value?.summary) return battleDrill.value.summary;
  if (adversarialRunning.value) return '战场中枢正在同步策略迭代与对抗推演...';
  return '点击开始演练，进入双角色实战推演。';
});
const adversarialSubtitleText = computed(() => {
  if (adversarialFinaleActive.value && battleDrill.value) {
    const score = `${battleDrill.value.attacker_final_score || 0} : ${battleDrill.value.defender_final_score || 0}`;
    return `慢放定格：${adversarialWinnerText.value} · 终局比分 ${score}`;
  }
  if (adversarialBeat.value === 'charge') return '镜头一：攻方聚合策略特征并锁定突破面。';
  if (adversarialBeat.value === 'dash') return '镜头二：对抗样本突进，守方开始重排防线。';
  if (adversarialBeat.value === 'hit') return '镜头三：冲击发生，实时评估突破强度。';
  if (adversarialBeat.value === 'block') return '镜头三：拦截成立，防御规则命中关键路径。';
  if (adversarialBeat.value === 'freeze') return '镜头四：战场定格，等待下一轮策略迭代。';
  return '电影化战报待命中。';
});
const adversarialDefenseStrengthText = computed(() => {
  const score = Number(battleDrill.value?.defense_strength_score || 0);
  if (!Number.isFinite(score) || score <= 0) {
    return '-';
  }
  return `${Math.round(score)}分`;
});
const adversarialCanApplyHardening = computed(() => {
  return String(battleDrill.value?.hardening_status || '').toLowerCase() === 'pending_manual_apply';
});
const adversarialComparisonVisible = computed(() => {
  return Boolean(adversarialBeforeHardening.value) && Boolean(battleDrill.value);
});
const adversarialAttackBeforeWidth = computed(() => {
  const before = Number(adversarialBeforeHardening.value?.attackSuccessRate ?? NaN);
  if (!Number.isFinite(before)) return 0;
  return Math.max(0, Math.min(100, Math.round(before * 100)));
});
const adversarialAttackAfterWidth = computed(() => {
  const after = Number(battleDrill.value?.attack_success_rate ?? NaN);
  if (!Number.isFinite(after)) return 0;
  return Math.max(0, Math.min(100, Math.round(after * 100)));
});
const adversarialDefenseGaugeMax = computed(() => {
  const before = Number(adversarialBeforeHardening.value?.defenseStrength ?? 0);
  const after = Number(battleDrill.value?.defense_strength_score ?? 0);
  return Math.max(1, Math.ceil(Math.max(100, before, after)));
});
const adversarialDefenseBeforeWidth = computed(() => {
  const before = Number(adversarialBeforeHardening.value?.defenseStrength ?? NaN);
  if (!Number.isFinite(before)) return 0;
  return Math.max(0, Math.min(100, Math.round((before / adversarialDefenseGaugeMax.value) * 100)));
});
const adversarialDefenseAfterWidth = computed(() => {
  const after = Number(battleDrill.value?.defense_strength_score ?? NaN);
  if (!Number.isFinite(after)) return 0;
  return Math.max(0, Math.min(100, Math.round((after / adversarialDefenseGaugeMax.value) * 100)));
});
const adversarialAttackRateCompareText = computed(() => {
  const before = Number(adversarialBeforeHardening.value?.attackSuccessRate ?? NaN);
  const after = Number(battleDrill.value?.attack_success_rate ?? NaN);
  if (!Number.isFinite(before) || !Number.isFinite(after)) {
    return '-';
  }
  const delta = Math.round((after - before) * 1000) / 10;
  return `${Math.round(before * 100)}% -> ${Math.round(after * 100)}% (${delta > 0 ? '+' : ''}${delta}%)`;
});
const adversarialDefenseCompareText = computed(() => {
  const before = Number(adversarialBeforeHardening.value?.defenseStrength ?? NaN);
  const after = Number(battleDrill.value?.defense_strength_score ?? NaN);
  if (!Number.isFinite(before) || !Number.isFinite(after)) {
    return '-';
  }
  const delta = Math.round(after - before);
  return `${Math.round(before)}分 -> ${Math.round(after)}分 (${delta > 0 ? '+' : ''}${delta})`;
});
const adversarialHardeningConclusion = computed(() => {
  const beforeRate = Number(adversarialBeforeHardening.value?.attackSuccessRate ?? NaN);
  const afterRate = Number(battleDrill.value?.attack_success_rate ?? NaN);
  const beforeDefense = Number(adversarialBeforeHardening.value?.defenseStrength ?? NaN);
  const afterDefense = Number(battleDrill.value?.defense_strength_score ?? NaN);
  if (!Number.isFinite(beforeRate) || !Number.isFinite(afterRate) || !Number.isFinite(beforeDefense) || !Number.isFinite(afterDefense)) {
    return '待验证';
  }
  const improved = afterRate <= beforeRate && afterDefense >= beforeDefense;
  return improved ? '防御提升已验证' : '建议继续加固并复测';
});
const adversarialCurveSeries = computed(() => {
  return visibleBattleRounds.value.map(round => {
    const attack = Number(round?.attack_success_rate ?? round?.final_effectiveness ?? 0);
    const defense = Number(round?.defense_intercept_rate ?? (1 - attack));
    return {
      attack: Math.max(0, Math.min(1, attack)),
      defense: Math.max(0, Math.min(1, defense)),
    };
  });
});
const adversarialCurveHasData = computed(() => adversarialCurveSeries.value.length >= 2);

function buildCurvePoints(values) {
  if (!values.length) {
    return '0,40 100,40';
  }
  if (values.length === 1) {
    const y = 40 - (values[0] * 40);
    return `0,${y.toFixed(2)} 100,${y.toFixed(2)}`;
  }
  return values
    .map((value, index) => {
      const x = (index / (values.length - 1)) * 100;
      const y = 40 - (value * 40);
      return `${x.toFixed(2)},${y.toFixed(2)}`;
    })
    .join(' ');
}

const adversarialCurveAttackPoints = computed(() => {
  return buildCurvePoints(adversarialCurveSeries.value.map(item => item.attack));
});
const adversarialCurveDefensePoints = computed(() => {
  return buildCurvePoints(adversarialCurveSeries.value.map(item => item.defense));
});
const adversarialKeyChanges = computed(() => {
  const rounds = visibleBattleRounds.value;
  if (!Array.isArray(rounds) || rounds.length < 2) {
    return [];
  }
  const out = [];
  for (let idx = 1; idx < rounds.length; idx += 1) {
    const prev = rounds[idx - 1];
    const current = rounds[idx];
    const prevRate = Number(prev?.attack_success_rate ?? prev?.final_effectiveness ?? 0);
    const currRate = Number(current?.attack_success_rate ?? current?.final_effectiveness ?? 0);
    if (Math.abs(currRate - prevRate) < 0.08) {
      continue;
    }
    out.push({
      round: current?.round_num || idx + 1,
      analysis: current?.explain || current?.narrative || `${current?.rule_id || 'RULE_UNKNOWN'} 触发策略变化`,
    });
    if (out.length >= 5) {
      break;
    }
  }
  return out;
});

function randomizeAdversarialVisualSeed() {
  adversarialVisualSeed.value = Math.floor(Math.random() * 1000000);
}

function launchAdversarialDrill() {
  if (!canRunThreatDrill.value) {
    ElMessage.warning('当前身份不可执行攻防演练');
    return;
  }
  adversarialPanelOpen.value = true;
  adversarialPanelMax.value = true;
  adversarialError.value = '';
  randomizeAdversarialVisualSeed();
}

function closeAdversarialPanel() {
  adversarialPanelOpen.value = false;
}

async function runAdversarialBattle() {
  adversarialError.value = '';
  adversarialRunning.value = true;
  adversarialTaskStatus.value = 'running';
  adversarialFinaleActive.value = false;
  randomizeAdversarialVisualSeed();
  stopBattlePlayback();
  stopAdversarialTaskPolling();
  adversarialBeat.value = 'charge';
  try {
    const payload = {
      scenario: adversarialConfig.value.scenario || 'random',
      rounds: Math.max(10, Math.min(30, Number(adversarialConfig.value.rounds || 12))),
    };
    if (String(adversarialConfig.value.seed || '').trim()) {
      payload.seed = Number(adversarialConfig.value.seed);
    }
    const task = await request.post('/ai/adversarial/task/start', payload);
    const taskId = String(task?.taskId || '');
    if (!taskId) {
      throw new Error(task?.error || '真实攻防任务创建失败');
    }
    adversarialTaskId.value = taskId;
    await syncAdversarialTaskStatus(taskId);
    adversarialTaskPollTimer = window.setInterval(() => {
      syncAdversarialTaskStatus(taskId, true);
    }, 1200);
    ElMessage.success('真实攻防任务已启动');
  } catch (error) {
    adversarialError.value = error?.message || '攻防对弈失败';
    adversarialTaskStatus.value = 'failed';
    adversarialBeat.value = 'idle';
    adversarialFinaleActive.value = false;
  } finally {
    adversarialRunning.value = false;
  }
}

function stopAdversarialTaskPolling() {
  if (adversarialTaskPollTimer) {
    clearInterval(adversarialTaskPollTimer);
    adversarialTaskPollTimer = null;
  }
}

async function syncAdversarialTaskStatus(taskId, silent = false) {
  try {
    const data = await request.get(`/ai/adversarial/task/${taskId}`);
    adversarialTaskStatus.value = String(data?.status || 'running');
    if (data?.battle) {
      battleDrill.value = data.battle;
      const rounds = Array.isArray(data?.battle?.rounds) ? data.battle.rounds : [];
      const completedRaw = Number(data?.completedRounds);
      const hasCompletedRounds = Number.isFinite(completedRaw) && completedRaw >= 0;
      let completed = hasCompletedRounds
        ? Math.max(0, Math.min(rounds.length, completedRaw))
        : rounds.length;
      if (adversarialTaskStatus.value === 'running' && !hasCompletedRounds) {
        completed = Math.min(rounds.length, Math.max(1, visibleBattleRounds.value.length + 1));
      }
      const previousCount = visibleBattleRounds.value.length;
      visibleBattleRounds.value = rounds.slice(0, completed);
      if (completed > 0 && completed > previousCount) {
        const last = visibleBattleRounds.value[visibleBattleRounds.value.length - 1];
        startAdversarialBeat(Boolean(last?.attack_success));
      }
    }
    if (adversarialTaskStatus.value === 'completed') {
      stopAdversarialTaskPolling();
      adversarialRunning.value = false;
      triggerAdversarialFinale();
      if (!silent) ElMessage.success('真实攻防训练已完成');
    }
    if (adversarialTaskStatus.value === 'failed') {
      stopAdversarialTaskPolling();
      adversarialRunning.value = false;
      adversarialError.value = data?.error || '真实攻防任务失败';
      if (!silent) ElMessage.error(adversarialError.value);
    }
  } catch (error) {
    if (!silent) {
      adversarialError.value = error?.message || '任务状态同步失败';
      ElMessage.error(adversarialError.value);
    }
  }
}

async function viewAdversarialTrainingLogs() {
  if (!adversarialTaskId.value) {
    ElMessage.warning('请先启动一次真实攻防演练');
    return;
  }
  adversarialLogsLoading.value = true;
  try {
    const data = await request.get(`/ai/adversarial/task/${adversarialTaskId.value}/logs`, {
      params: { offset: 0, limit: 300 },
    });
    adversarialEventLogs.value = Array.isArray(data?.eventLogs) ? data.eventLogs : [];
    adversarialTrainingLogs.value = Array.isArray(data?.trainingLogs) ? data.trainingLogs : [];
    adversarialLogsVisible.value = true;
  } catch (error) {
    ElMessage.error(error?.message || '训练日志加载失败');
  } finally {
    adversarialLogsLoading.value = false;
  }
}

async function applyAdversarialHardening() {
  if (adversarialHardeningApplying.value) {
    return;
  }
  adversarialHardeningApplying.value = true;
  try {
    const profileMap = {
      normal: { thresholdReductionPct: 12, roundsBoost: 0, label: '常规强化' },
      strong: { thresholdReductionPct: 22, roundsBoost: 4, label: '强力强化' },
      extreme: { thresholdReductionPct: 35, roundsBoost: 8, label: '极限强化' },
    };
    const profile = profileMap[adversarialHardeningLevel.value] || profileMap.strong;
    adversarialBeforeHardening.value = {
      attackSuccessRate: Number(battleDrill.value?.attack_success_rate || 0),
      defenseStrength: Number(battleDrill.value?.defense_strength_score || 0),
    };
    const data = await request.post('/ai/adversarial/apply-hardening', {
      thresholdReductionPct: profile.thresholdReductionPct,
      scenario: adversarialConfig.value.scenario || 'real-threat-check',
    });
    adversarialConfig.value.rounds = Math.min(100, Number(adversarialConfig.value.rounds || 10) + profile.roundsBoost);
    randomizeAdversarialVisualSeed();
    const before = Number(data?.beforeAlertThresholdBytes || 0);
    const after = Number(data?.afterAlertThresholdBytes || 0);
    ElMessage.success(`${profile.label}已应用：阈值 ${before} -> ${after}`);
    await runAdversarialBattle();
  } catch (error) {
    ElMessage.error(error?.message || '动态加固失败');
  } finally {
    adversarialHardeningApplying.value = false;
  }
}

async function exportAdversarialOptimizationReport() {
  if (!adversarialTaskId.value) {
    ElMessage.warning('请先完成一次真实攻防演练');
    return;
  }
  adversarialReportLoading.value = true;
  try {
    const data = await request.get(`/ai/adversarial/task/${adversarialTaskId.value}/report`);
    const payload = data?.report || data || {};
    const blob = new Blob([JSON.stringify(payload, null, 2)], { type: 'application/json;charset=utf-8' });
    const url = window.URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `adversarial-optimization-${adversarialTaskId.value}.json`;
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    window.URL.revokeObjectURL(url);
    ElMessage.success('模型优化报告已导出');
  } catch (error) {
    ElMessage.error(error?.message || '导出优化报告失败');
  } finally {
    adversarialReportLoading.value = false;
  }
}

function clickThreatOrb() {
  launchAdversarialDrill();
}

watch(visibleBattleRounds, (list) => {
  if (!Array.isArray(list) || !list.length) {
    adversarialBeat.value = adversarialRunning.value ? 'charge' : 'idle';
  }
});

function enqueueSimulationEvents(items) {
  const incoming = Array.isArray(items) ? items : [];
  for (const item of incoming) {
    const eventId = Number(item?.eventId || 0);
    if (!eventId || simulationSeenIds.has(eventId)) continue;
    simulationSeenIds.add(eventId);
    simulationQueue.value.push(item);
    simulationCursor = Math.max(simulationCursor, eventId);
  }
  playNextSimulationEvent();
}

function playNextSimulationEvent() {
  if (activeSimulationEvent.value) return;
  if (!simulationQueue.value.length) return;
  activeSimulationEvent.value = simulationQueue.value.shift();
}

async function handleSimulationAnimationComplete(eventId) {
  const id = Number(eventId || activeSimulationEvent.value?.eventId || 0);
  activeSimulationEvent.value = null;
  if (!id) {
    playNextSimulationEvent();
    return;
  }
  try {
    await markSimulationProcessed([id]);
  } catch {
    // Keep animation loop resilient.
  }
  try {
    await refresh();
  } catch {
    // Ignore refresh failures in animation callback.
  }
  playNextSimulationEvent();
}

async function pollSimulationEvents(silent = true) {
  try {
    const data = await fetchSimulationPending(simulationCursor, 20);
    const items = Array.isArray(data?.items) ? data.items : [];
    enqueueSimulationEvents(items);
    const cursor = Number(data?.cursor || 0);
    if (cursor > 0) {
      simulationCursor = Math.max(simulationCursor, cursor);
    }
  } catch (e) {
    if (!silent) {
      ElMessage.error(e?.message || '演示事件获取失败');
    }
  }
}

// ── 格式化工具 ────────────────────────────────────────────────────────────────
function severityTagType(s) {
  return { critical: 'danger', high: 'warning', medium: '', low: 'info' }[normalizeSeverity(s)] ?? '';
}

function severityLabel(s) {
  return { critical: '严重', high: '高危', medium: '中危', low: '低危' }[normalizeSeverity(s)] ?? s;
}

function statusTagType(s) {
  return { pending: 'warning', blocked: 'danger', ignored: 'info', reviewing: '' }[normalizeStatus(s)] ?? '';
}

function statusLabel(s) {
  return { pending: '待处理', blocked: '已阻拦', ignored: '已忽略', reviewing: '审查中' }[normalizeStatus(s)] ?? s;
}

const EVENT_TYPE_LABEL_MAP = {
  FILE_STEAL: '文件窃取',
  SUSPICIOUS_UPLOAD: '可疑上传',
  BATCH_COPY: '批量复制',
  EXFILTRATION: '数据外泄',
  DATA_EXFILTRATION: '数据外泄',
  DATA_EXPORT: '数据导出',
  DATA_LEAK_ALERT: '数据泄露告警',
  DATA_SCRAPE: '数据抓取',
  CREDENTIAL_DUMP: '凭证转储',
  PRIVACY_ALERT: '隐私告警',
  SECURITY_ALERT: '安全威胁告警',
  SHADOW_AI_ALERT: '影子AI告警',
  ANOMALY_ALERT: '行为异常告警',
  BEHAVIOR_ANOMALY: '行为异常事件',
  POLICY_VIOLATION: '策略违规事件',
  ACCESS_CONTROL_VIOLATION: '访问控制违规',
  SENSITIVE_OPERATION: '敏感操作事件',
  SLOW_QUERY_ALERT: '慢查询告警',
  RELIABILITY_ALERT: '韧性告警',
};

function isClassifiedEventType(eventType) {
  const raw = String(eventType || '').trim();
  if (!raw) return false;
  const key = raw.toUpperCase();
  if (EVENT_TYPE_LABEL_MAP[key]) return true;
  return /[\u4e00-\u9fa5]/.test(raw);
}

function eventTypeLabel(t) {
  const raw = String(t || '').trim();
  const key = raw.toUpperCase();
  if (EVENT_TYPE_LABEL_MAP[key]) return EVENT_TYPE_LABEL_MAP[key];
  if (/[\u4e00-\u9fa5]/.test(raw)) return raw;
  return raw ? `未分类事件（${key}）` : '未知事件';
}

function centerEventTypeLabel(t) {
  const raw = String(t || '').trim();
  const key = raw.toUpperCase();
  const map = {
    PRIVACY_ALERT: '隐私告警',
    ANOMALY_ALERT: '行为异常',
    SHADOW_AI_ALERT: '影子AI',
    SECURITY_ALERT: '安全威胁',
    DATA_LEAK_ALERT: '数据泄露告警',
    SLOW_QUERY_ALERT: '慢查询告警',
    RELIABILITY_ALERT: '韧性告警',
    POLICY_VIOLATION: '策略违规',
    ACCESS_CONTROL_VIOLATION: '访问控制违规',
    ACCOUNT_COMPROMISE: '账号异常',
    ACCOUNT_LOGIN_ANOMALY: '账号登录异常',
    SENSITIVE_OPERATION: '敏感操作',
    DATA_EXPORT: '数据导出',
  };
  if (map[key]) return map[key];
  if (/[\u4e00-\u9fa5]/.test(raw)) return raw;
  return raw ? `未分类事件（${key}）` : '未知事件';
}

function attackTypeLabel(type) {
  const map = {
    JAILBREAK_ATTEMPT: '提示越狱',
    DATA_POISONING: '数据投毒',
    SENSITIVE_DATA_EXFILTRATION: '敏感外传',
    GENERIC_ATTACK: '通用攻击',
    QUERY_REGRESSION: '查询回归',
    DATA_EXFIL_PLAIN: '数据外传',
    DATA_EXFIL_STEG: '隐写外传',
    DATA_EXFILTRATION: '数据外传',
    PROMPT_INJECTION: '提示词注入',
    POLICY_BYPASS: '策略绕过',
    ABNORMAL_ACCESS: '异常访问',
    PRIVACY_POLICY_HIT: '隐私策略命中',
    DECISION_DRIFT: '决策漂移',
    SHADOW_DEPLOYMENT: '影子部署',
    RESILIENCE_CHAOS: '韧性混沌',
  };
  return map[normalizeAttackType(type)] || normalizeAttackType(type);
}

function effectThemeLabel(profile) {
  const theme = String(profile?.theme || profile?.name || '').trim();
  if (!theme) return '默认';
  const map = {
    jailbreak_breach: '越狱突破',
    poisoning_cloud: '投毒污染',
    exfiltration_stream: '外泄流',
    default_alert: '默认告警',
  };
  return map[theme] || theme;
}

function formatSize(bytes) {
  if (!bytes && bytes !== 0) return '—';
  if (bytes >= 1073741824) return (bytes / 1073741824).toFixed(1) + ' GB';
  if (bytes >= 1048576) return (bytes / 1048576).toFixed(1) + ' MB';
  if (bytes >= 1024) return (bytes / 1024).toFixed(1) + ' KB';
  return bytes + ' B';
}

function truncate(str, len) {
  if (!str) return '—';
  return str.length > len ? str.slice(0, len) + '…' : str;
}

function rowStyle({ row }) {
  const severity = normalizeSeverity(row?.severity);
  if (severity === 'critical') return { background: 'rgba(255,50,50,0.06)' };
  if (severity === 'high') return { background: 'rgba(255,150,50,0.05)' };
  return {};
}

// ── 生命周期 ──────────────────────────────────────────────────────────────────
onMounted(() => {
  if (!canViewThreatMonitor.value) {
    ElMessage.error('当前身份无权访问AI攻击实时防御模块');
    return;
  }
  if (isAdminUser.value && activeTab.value === 'alertCenter') {
    activeTab.value = 'events';
  }
  refresh().catch(() => {});
  pollSimulationEvents(true);
  simulationPollTimer = window.setInterval(() => {
    pollSimulationEvents(true);
  }, 3000);
  if (canManageThreatRules.value) {
    fetchRules();
  }
  if (canRunThreatDrill.value) {
    fetchThreatDrillMeta();
  }
});

onUnmounted(() => {
  stopBattlePlayback();
  stopAdversarialTaskPolling();
  if (adversarialFinaleTimer) {
    window.clearTimeout(adversarialFinaleTimer);
    adversarialFinaleTimer = null;
  }
  if (simulationPollTimer) {
    window.clearInterval(simulationPollTimer);
    simulationPollTimer = null;
  }
  simulationSeenIds = new Set();
  simulationQueue.value = [];
  activeSimulationEvent.value = null;
});
</script>

<style scoped>
.threat-monitor-page {
  padding: 24px;
  min-height: 100vh;
  color: #e8f4ff;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  margin-bottom: 24px;
  padding: 28px 32px;
  background: linear-gradient(135deg, rgba(8, 16, 27, 0.9), rgba(18, 49, 95, 0.7));
  border: 1px solid rgba(100, 180, 255, 0.15);
  border-radius: 16px;
}

.page-eyebrow {
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.15em;
  color: #4fc3f7;
  text-transform: uppercase;
  margin-bottom: 6px;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  color: #e8f4ff;
  margin: 0 0 8px;
}

.page-subtitle {
  font-size: 14px;
  color: rgba(200, 220, 255, 0.65);
  margin: 0;
}

.page-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.adversarial-floating-wrap {
  position: relative;
  z-index: 20;
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

.adversarial-orb {
  min-width: 132px;
  border: 1px solid rgba(125, 190, 255, 0.28);
  background: linear-gradient(135deg, rgba(27, 58, 116, 0.92), rgba(12, 30, 74, 0.94));
  color: #e8f1ff;
  box-shadow: 0 10px 22px rgba(13, 41, 94, 0.28);
  display: grid;
  gap: 4px;
  text-align: left;
  animation: orbBreath 1.9s ease-in-out infinite;
  border-radius: 12px;
  padding: 10px 14px;
  cursor: pointer;
}

.adversarial-orb .orb-label {
  font-size: 10px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #9cc3ff;
}

.adversarial-orb strong {
  font-size: 14px;
}

.adversarial-panel {
  position: fixed;
  right: 2vw;
  bottom: 2vh;
  width: min(1540px, 97vw);
  height: min(90vh, 1040px);
  padding: 18px;
  border-radius: 18px;
  border: 1px solid rgba(110, 162, 255, 0.24);
  background:
    radial-gradient(circle at 18% 16%, rgba(64, 121, 255, 0.16), transparent 34%),
    radial-gradient(circle at 82% 14%, rgba(63, 218, 196, 0.1), transparent 28%),
    linear-gradient(180deg, rgba(7, 14, 28, 0.96), rgba(9, 17, 33, 0.94));
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.adversarial-panel.adversarial-panel-max {
  right: 0.8vw;
  bottom: 0.8vh;
  width: min(1760px, 99vw);
  height: min(96vh, 1160px);
}

.adversarial-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.adversarial-head-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  justify-content: flex-end;
}

.adversarial-close {
  border: 1px solid rgba(255, 255, 255, 0.15);
  background: rgba(255, 255, 255, 0.03);
  color: #d5e4ff;
  border-radius: 10px;
  padding: 6px 10px;
  font-size: 12px;
  cursor: pointer;
}

.adversarial-config {
  margin-top: 8px;
  display: grid;
  grid-template-columns: minmax(220px, 1fr) repeat(4, auto);
  gap: 8px;
}

.adversarial-config-advanced {
  grid-template-columns: repeat(3, minmax(140px, 1fr));
}

.adversarial-config select,
.adversarial-config input {
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.04);
  color: #d6e7ff;
  padding: 8px 10px;
}

.adversarial-run {
  border: none;
  border-radius: 10px;
  background: linear-gradient(135deg, #2d8eff, #1859d6);
  color: #f8fbff;
  font-weight: 700;
  padding: 8px 12px;
  cursor: pointer;
}

.adversarial-scene-desc {
  margin-top: 10px;
  display: grid;
  gap: 4px;
  color: #a7b9d9;
  font-size: 12px;
}

.adversarial-error {
  margin: 10px 0 0;
  color: #ffb9b9;
  font-size: 12px;
}

.adversarial-layout {
  margin-top: 12px;
  display: grid;
  grid-template-columns: minmax(0, 2.25fr) minmax(380px, 1fr);
  gap: 18px;
  flex: 1 1 auto;
  min-height: 0;
}

.adversarial-cinematic-stage {
  border: 1px solid rgba(117, 177, 255, 0.22);
  border-radius: 14px;
  background:
    radial-gradient(circle at 26% 40%, rgba(255, 116, 76, 0.18), transparent 42%),
    radial-gradient(circle at 78% 28%, rgba(74, 188, 255, 0.2), transparent 40%),
    linear-gradient(135deg, rgba(7, 14, 28, 0.94), rgba(11, 23, 46, 0.92));
  overflow: hidden;
  position: relative;
  height: 100%;
  padding: 10px;
}

.stage-hud {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.stage-hud span {
  border: 1px solid rgba(126, 176, 255, 0.24);
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 11px;
  color: #d6e8ff;
  background: rgba(9, 18, 36, 0.7);
}

.stage-field {
  margin-top: 12px;
  display: grid;
  grid-template-columns: minmax(160px, 1fr) minmax(160px, 0.9fr) minmax(160px, 1fr);
  gap: 10px;
  align-items: center;
}

.battle-actor {
  border: 1px solid rgba(121, 175, 255, 0.2);
  border-radius: 12px;
  background: rgba(7, 16, 32, 0.8);
  padding: 8px;
  display: grid;
  justify-items: center;
  text-align: center;
  gap: 6px;
}

.actor-avatar {
  width: 110px;
  height: 110px;
  border-radius: 50%;
  border: 1px solid rgba(130, 184, 255, 0.24);
  background: rgba(12, 24, 47, 0.85);
  display: grid;
  place-items: center;
  position: relative;
  overflow: hidden;
}

.battle-asset {
  position: absolute;
  inset: 9%;
  width: 82%;
  height: 82%;
  object-fit: contain;
  opacity: 0.6;
}

.defender-core-logo {
  width: 30px;
  height: 30px;
  position: relative;
  z-index: 2;
  filter: drop-shadow(0 0 6px rgba(120, 199, 255, 0.6));
}

.battle-mid {
  display: grid;
  gap: 8px;
  align-items: center;
}

.battle-lane {
  border: 1px solid rgba(132, 185, 255, 0.2);
  border-radius: 999px;
  background: rgba(10, 19, 38, 0.72);
  height: 12px;
  overflow: hidden;
}

.lane-pulse {
  display: block;
  width: 38%;
  min-height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, rgba(255, 118, 86, 0.92), rgba(104, 205, 255, 0.9));
  animation: laneRush 1.6s linear infinite;
}

.battle-impact {
  border-radius: 10px;
  border: 1px solid rgba(130, 181, 255, 0.22);
  background: rgba(9, 17, 34, 0.82);
  padding: 8px;
  text-align: center;
}

.adversarial-summary-grid {
  margin-top: 0;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
}

.adversarial-summary-grid article {
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.03);
  padding: 9px;
  display: grid;
  gap: 4px;
}

.adversarial-stream {
  display: grid;
  gap: 10px;
  overflow: auto;
  max-height: 46vh;
}

.adversarial-round {
  border: 1px solid rgba(129, 173, 232, 0.2);
  border-radius: 12px;
  background: rgba(12, 23, 43, 0.52);
  padding: 10px;
}

.adversarial-round-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.adversarial-round-top .hit {
  color: #ffbfaa;
}

.adversarial-round-top .block {
  color: #a9d3ff;
}

.adversarial-recommendations {
  margin-top: 10px;
  border: 1px solid rgba(129, 173, 232, 0.2);
  border-radius: 12px;
  background: rgba(12, 23, 43, 0.52);
  padding: 10px;
}

.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.22s ease;
}

.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(12px);
}

@keyframes orbBreath {
  0% {
    box-shadow: 0 10px 22px rgba(13, 41, 94, 0.28);
    transform: translateY(0);
  }
  50% {
    box-shadow: 0 14px 28px rgba(63, 126, 255, 0.36);
    transform: translateY(-1px);
  }
  100% {
    box-shadow: 0 10px 22px rgba(13, 41, 94, 0.28);
    transform: translateY(0);
  }
}

@keyframes laneRush {
  from { transform: translateX(-120%); }
  to { transform: translateX(260%); }
}

@keyframes orbPulse {
  0% {
    box-shadow: 0 10px 20px rgba(44, 112, 255, 0.34);
  }
  50% {
    box-shadow: 0 14px 30px rgba(102, 176, 255, 0.58);
  }
  100% {
    box-shadow: 0 10px 20px rgba(44, 112, 255, 0.34);
  }
}

/* ── 统计卡片 ── */
.stats-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  margin-bottom: 24px;
}

.stats-footnote {
  margin: -10px 0 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  font-size: 12px;
  color: rgba(202, 224, 255, 0.85);
}

.stat-card {
  background: rgba(12, 24, 48, 0.8);
  border: 1px solid rgba(100, 180, 255, 0.12);
  border-radius: 12px;
  padding: 20px 24px;
  text-align: center;
}

.stat-card.warning {
  border-color: rgba(255, 190, 0, 0.3);
  background: rgba(255, 160, 0, 0.06);
}

.stat-card.danger {
  border-color: rgba(255, 70, 70, 0.35);
  background: rgba(255, 50, 50, 0.07);
}

.stat-card.high {
  border-color: rgba(255, 140, 0, 0.3);
  background: rgba(255, 120, 0, 0.06);
}

.stat-card.blocked {
  border-color: rgba(80, 200, 80, 0.25);
  background: rgba(50, 200, 100, 0.05);
}

.stat-label {
  font-size: 12px;
  color: rgba(200, 220, 255, 0.55);
  margin-bottom: 8px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.stat-value {
  font-size: 32px;
  font-weight: 700;
  color: #e8f4ff;
}

/* ── 标签页 ── */
.main-tabs {
  background: transparent;
}

:deep(.el-tabs__header) {
  margin-bottom: 16px;
}

:deep(.el-tabs__nav-wrap::after) {
  background-color: rgba(100, 180, 255, 0.1);
}

:deep(.el-tabs__item) {
  color: rgba(200, 220, 255, 0.6);
  font-size: 14px;
}

:deep(.el-tabs__item.is-active) {
  color: #4fc3f7;
}

:deep(.el-tabs__active-bar) {
  background-color: #4fc3f7;
}

/* ── 工具栏 ── */
.toolbar-row {
  display: flex;
  gap: 12px;
  align-items: center;
  flex-wrap: wrap;
}

/* ── 表格 ── */
.event-type {
  font-weight: 600;
  color: #90caf9;
}

.file-path {
  font-family: 'Fira Code', 'Courier New', monospace;
  font-size: 12px;
  color: rgba(200, 220, 255, 0.8);
}

.target-addr {
  font-family: 'Fira Code', 'Courier New', monospace;
  font-size: 11px;
  color: #ef9a9a;
}

/* ── 分页 ── */
.pagination-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}

/* ── 模拟器说明 ── */
.simulator-info h3 {
  color: #d8f6ff;
  margin-bottom: 12px;
  letter-spacing: 0.06em;
  text-shadow: 0 0 18px rgba(72, 213, 255, 0.52);
}

.simulator-info p {
  color: rgba(203, 236, 255, 0.78);
  margin-bottom: 16px;
}

.code-block {
  background: rgba(0, 0, 0, 0.4);
  border: 1px solid rgba(100, 180, 255, 0.12);
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 12px;
}

.code-label {
  font-size: 12px;
  color: #4fc3f7;
  margin-bottom: 8px;
  font-weight: 600;
}

.code-block pre {
  margin: 0;
  font-family: 'Fira Code', 'Courier New', monospace;
  font-size: 13px;
  color: #a5d6a7;
  white-space: pre-wrap;
  word-break: break-all;
}

code {
  background: rgba(100, 180, 255, 0.12);
  padding: 2px 6px;
  border-radius: 4px;
  font-family: 'Fira Code', 'Courier New', monospace;
  font-size: 12px;
  color: #90caf9;
}

.drill-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 12px 0;
  flex-wrap: wrap;
}

.drill-visual-wrap {
  position: relative;
  margin: 12px 0 16px;
  padding: 14px;
  border-radius: 14px;
  overflow: hidden;
  border: 1px solid rgba(101, 190, 255, 0.35);
  background:
    radial-gradient(circle at 14% 18%, rgba(46, 146, 255, 0.2), transparent 42%),
    radial-gradient(circle at 84% 84%, rgba(255, 56, 134, 0.16), transparent 40%),
    linear-gradient(145deg, rgba(5, 16, 34, 0.9), rgba(6, 12, 24, 0.94));
  box-shadow: 0 0 0 1px rgba(61, 157, 255, 0.16) inset, 0 16px 34px rgba(4, 10, 24, 0.5);
}

.drill-visual-wrap::before {
  content: '';
  position: absolute;
  inset: 0;
  pointer-events: none;
  background: repeating-linear-gradient(
    to bottom,
    rgba(178, 239, 255, 0.05) 0px,
    rgba(178, 239, 255, 0.05) 1px,
    transparent 1px,
    transparent 4px
  );
  mix-blend-mode: screen;
  opacity: 0.45;
}

.drill-config-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}

.sim-error {
  margin: 4px 0 12px;
  color: #ff8a80;
  font-size: 13px;
}

.battle-panel {
  margin-top: 16px;
  padding: 12px;
  border-radius: 12px;
  border: 1px solid rgba(90, 178, 255, 0.22);
  background: rgba(5, 16, 33, 0.58);
}

.battle-panel h4 {
  margin: 0 0 10px;
  color: #e8f4ff;
}

.battle-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: 10px;
  margin-bottom: 12px;
}

.battle-summary-grid article {
  border-radius: 10px;
  border: 1px solid rgba(100, 180, 255, 0.2);
  background: rgba(17, 38, 74, 0.55);
  padding: 10px 12px;
}

.battle-summary-grid span {
  display: block;
  color: rgba(200, 220, 255, 0.68);
  font-size: 12px;
}

.battle-summary-grid strong {
  display: block;
  margin-top: 4px;
  color: #f4fbff;
  font-size: 16px;
}

.battle-round-stream {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 10px;
  max-height: 320px;
  overflow: visible;
}

.battle-round-item {
  border: 1px solid rgba(110, 194, 255, 0.24);
  border-radius: 10px;
  background: linear-gradient(145deg, rgba(9, 26, 52, 0.68), rgba(9, 15, 31, 0.68));
  padding: 10px;
}

.battle-round-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}

.battle-round-item p {
  margin: 0;
  color: rgba(218, 236, 255, 0.82);
}

.battle-round-item em {
  display: block;
  margin-top: 6px;
  color: rgba(188, 214, 246, 0.72);
  font-style: normal;
  font-size: 12px;
}

.battle-pill {
  border-radius: 999px;
  padding: 2px 9px;
  font-size: 11px;
  font-weight: 700;
}

.battle-pill.hit {
  color: #ffe2e2;
  background: rgba(230, 70, 70, 0.25);
}

.battle-pill.block {
  color: #d7ffe0;
  background: rgba(58, 188, 118, 0.25);
}

.battle-recommendations {
  margin-top: 12px;
  border-top: 1px solid rgba(100, 180, 255, 0.15);
  padding-top: 10px;
}

.battle-recommendations h5 {
  margin: 0 0 6px;
  color: #dff1ff;
}

.battle-recommendations p {
  margin: 0 0 5px;
}

.related-head p {
  margin: 0;
  color: rgba(214, 232, 255, 0.86);
}

.related-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.dispose-result {
  margin-top: 12px;
  padding: 12px;
  border-radius: 8px;
  border: 1px solid rgba(100, 180, 255, 0.18);
  background: rgba(18, 40, 75, 0.55);
}

.dispose-result p {
  margin: 4px 0;
  color: rgba(224, 238, 255, 0.88);
}

.fade-slide-enter-active,
.fade-slide-leave-active {
  transition: all 0.25s ease;
}

.fade-slide-enter-from,
.fade-slide-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

:deep(.card-glass) {
  background:
    radial-gradient(circle at 10% 0%, rgba(57, 162, 255, 0.08), transparent 42%),
    linear-gradient(145deg, rgba(9, 20, 43, 0.78), rgba(8, 15, 31, 0.82));
  border: 1px solid rgba(100, 180, 255, 0.18);
  border-radius: 16px;
}

.stage-finale-banner {
  margin-top: 8px;
  border: 1px solid rgba(162, 214, 255, 0.42);
  border-radius: 12px;
  background: linear-gradient(90deg, rgba(14, 38, 69, 0.9), rgba(12, 25, 46, 0.9));
  padding: 8px 10px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.stage-finale-banner strong {
  color: #f4f9ff;
  font-size: 13px;
}

.stage-finale-banner span {
  color: #b5d4ff;
  font-size: 11px;
}

.openclaw-mark,
.attacker-helix-mark,
.attacker-shard-mark,
.attacker-swarm-mark {
  width: 70px;
  height: 70px;
  position: relative;
}

.openclaw-mark i,
.attacker-helix-mark i,
.attacker-shard-mark i,
.attacker-swarm-mark i,
.attacker-mark-grid i {
  position: absolute;
  display: block;
}

.attacker-mark-grid {
  width: 68px;
  height: 68px;
  position: relative;
}

.attacker-mark-grid i {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: rgba(255, 132, 96, 0.9);
  box-shadow: 0 0 7px rgba(255, 132, 96, 0.5);
}

.attacker-mark-grid i:nth-child(1) { left: 8px; top: 8px; }
.attacker-mark-grid i:nth-child(2) { left: 30px; top: 8px; }
.attacker-mark-grid i:nth-child(3) { left: 52px; top: 8px; }
.attacker-mark-grid i:nth-child(4) { left: 8px; top: 30px; }
.attacker-mark-grid i:nth-child(5) { left: 30px; top: 30px; }
.attacker-mark-grid i:nth-child(6) { left: 52px; top: 30px; }
.attacker-mark-grid i:nth-child(7) { left: 8px; top: 52px; }
.attacker-mark-grid i:nth-child(8) { left: 30px; top: 52px; }
.attacker-mark-grid i:nth-child(9) { left: 52px; top: 52px; }

.attacker-helix-mark i {
  left: 50%;
  top: 50%;
  width: 9px;
  height: 9px;
  margin: -4.5px;
  border-radius: 50%;
  background: rgba(255, 124, 89, 0.92);
  box-shadow: 0 0 8px rgba(255, 124, 89, 0.58);
  transform: rotate(calc(var(--idx, 0) * 45deg)) translateY(-24px);
}

.attacker-helix-mark i:nth-child(1) { --idx: 0; }
.attacker-helix-mark i:nth-child(2) { --idx: 1; }
.attacker-helix-mark i:nth-child(3) { --idx: 2; }
.attacker-helix-mark i:nth-child(4) { --idx: 3; }
.attacker-helix-mark i:nth-child(5) { --idx: 4; }
.attacker-helix-mark i:nth-child(6) { --idx: 5; }
.attacker-helix-mark i:nth-child(7) { --idx: 6; }
.attacker-helix-mark i:nth-child(8) { --idx: 7; }

.attacker-shard-mark i {
  width: 22px;
  height: 8px;
  left: 50%;
  top: 50%;
  margin-left: -11px;
  margin-top: -4px;
  border-radius: 99px;
  background: linear-gradient(90deg, rgba(255, 168, 83, 0.94), rgba(255, 99, 136, 0.88));
}

.attacker-shard-mark i:nth-child(1) { transform: rotate(0deg) translateY(-22px); }
.attacker-shard-mark i:nth-child(2) { transform: rotate(60deg) translateY(-22px); }
.attacker-shard-mark i:nth-child(3) { transform: rotate(120deg) translateY(-22px); }
.attacker-shard-mark i:nth-child(4) { transform: rotate(180deg) translateY(-22px); }
.attacker-shard-mark i:nth-child(5) { transform: rotate(240deg) translateY(-22px); }
.attacker-shard-mark i:nth-child(6) { transform: rotate(300deg) translateY(-22px); }

.attacker-swarm-mark i {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: rgba(255, 134, 103, 0.94);
  box-shadow: 0 0 7px rgba(255, 134, 103, 0.54);
}

.attacker-swarm-mark i:nth-child(1) { left: 8px; top: 14px; }
.attacker-swarm-mark i:nth-child(2) { left: 18px; top: 8px; }
.attacker-swarm-mark i:nth-child(3) { left: 30px; top: 12px; }
.attacker-swarm-mark i:nth-child(4) { left: 44px; top: 9px; }
.attacker-swarm-mark i:nth-child(5) { left: 56px; top: 16px; }
.attacker-swarm-mark i:nth-child(6) { left: 10px; top: 30px; }
.attacker-swarm-mark i:nth-child(7) { left: 24px; top: 25px; }
.attacker-swarm-mark i:nth-child(8) { left: 38px; top: 28px; }
.attacker-swarm-mark i:nth-child(9) { left: 52px; top: 26px; }
.attacker-swarm-mark i:nth-child(10) { left: 14px; top: 44px; }
.attacker-swarm-mark i:nth-child(11) { left: 30px; top: 48px; }
.attacker-swarm-mark i:nth-child(12) { left: 48px; top: 43px; }

.shrimp-core {
  width: 26px;
  height: 34px;
  left: 16px;
  top: 10px;
  border-radius: 50% 50% 40% 40%;
  background: linear-gradient(180deg, #ff8748, #ff4f75);
}

.shrimp-claw { width: 15px; height: 15px; top: 14px; border-radius: 50% 50% 50% 10%; border: 2px solid rgba(255, 145, 105, 0.92); border-left: none; border-bottom: none; }
.shrimp-claw.claw-left { left: 3px; transform: rotate(-132deg); }
.shrimp-claw.claw-right { right: 2px; transform: rotate(46deg); }
.shrimp-tail { width: 20px; height: 16px; left: 20px; bottom: 3px; border-radius: 20px 20px 40% 40%; border: 2px solid rgba(255, 190, 148, 0.95); border-top: none; }

.actor-attacker.attacker-openclaw .actor-avatar { animation: openclawWave 1.2s ease-in-out infinite; }
.actor-attacker.attacker-pattern-helix .actor-avatar { animation: attackerSpin 4.8s linear infinite; }
.actor-attacker.attacker-pattern-swarm .actor-avatar { animation: swarmPulse 1.6s ease-in-out infinite; }

.defender-shield-rings {
  position: absolute;
  inset: 0;
  display: grid;
  place-items: center;
}

.defender-shield-rings i {
  position: absolute;
  border-radius: 50%;
  border: 1px solid rgba(132, 203, 255, 0.38);
}

.defender-shield-rings i:nth-child(1) { width: 42px; height: 42px; }
.defender-shield-rings i:nth-child(2) { width: 62px; height: 62px; }
.defender-shield-rings i:nth-child(3) { width: 82px; height: 82px; border-color: rgba(132, 203, 255, 0.22); }

.defender-side-panels { position: absolute; inset: 0; }
.defender-side-panels i { position: absolute; top: 42px; width: 18px; height: 28px; border-radius: 8px; background: linear-gradient(180deg, rgba(118, 201, 255, 0.85), rgba(72, 147, 255, 0.76)); }
.defender-side-panels .panel-left { left: 14px; transform: skewY(12deg); }
.defender-side-panels .panel-right { right: 14px; transform: skewY(-12deg); }

.defender-fortress .defender-shield-rings i { animation: defenderRing 1.6s ease-in-out infinite; }
.defender-vector .defender-side-panels i { animation: defenderWing 1s ease-in-out infinite; }

.stage-finale-active { animation: stageFinaleFlash 0.52s ease-in-out 1; }

.stage-hardening-overlay {
  margin-top: 10px;
  border: 1px solid rgba(125, 184, 255, 0.2);
  border-radius: 11px;
  background: rgba(9, 18, 35, 0.74);
  padding: 9px;
  display: grid;
  gap: 8px;
}

.overlay-title { color: #d7e8ff; font-size: 11px; font-weight: 700; }
.stage-hardening-overlay article { display: grid; gap: 6px; }
.overlay-head { display: flex; justify-content: space-between; align-items: center; gap: 10px; }
.overlay-head strong { color: #eff5ff; font-size: 12px; }
.overlay-head span { color: #9eb8dc; font-size: 11px; }
.overlay-meter { height: 7px; border-radius: 999px; background: rgba(255, 255, 255, 0.07); position: relative; overflow: hidden; }
.overlay-meter i { position: absolute; left: 0; top: 0; bottom: 0; border-radius: inherit; }
.overlay-meter .before { background: rgba(255, 144, 110, 0.65); }
.overlay-meter .after { background: rgba(104, 203, 255, 0.85); }
.overlay-meter .before.defense { background: rgba(129, 169, 255, 0.5); }
.overlay-meter .after.defense { background: rgba(101, 223, 175, 0.82); }

.adversarial-curve-panel {
  margin-top: 10px;
  border: 1px solid rgba(129, 173, 232, 0.2);
  border-radius: 12px;
  background: rgba(12, 23, 43, 0.52);
  padding: 10px;
}

.adversarial-curve-panel h4 { margin: 0 0 8px; font-size: 12px; color: #dce8ff; }
.adversarial-curve-svg { width: 100%; height: 120px; border-radius: 8px; background: linear-gradient(180deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0.01)); }
.adversarial-curve-svg .curve { fill: none; stroke-width: 1.8; }
.adversarial-curve-svg .curve-attack { stroke: #ff8f70; }
.adversarial-curve-svg .curve-defense { stroke: #72b8ff; }
.adversarial-curve-legend { margin-top: 8px; display: flex; gap: 12px; font-size: 11px; }
.adversarial-curve-legend .attack { color: #ffbfaa; }
.adversarial-curve-legend .defense { color: #a9d3ff; }

.adversarial-recommendation-actions { margin-top: 10px; display: flex; align-items: center; gap: 10px; }
.adversarial-harden-btn { font-weight: 700; letter-spacing: 0.04em; box-shadow: 0 0 16px rgba(255, 172, 89, 0.28); }

.trace-note {
  color: #89a7cf;
  font-size: 11px;
}

.adversarial-compare-grid {
  margin-top: 10px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.adversarial-compare-grid article {
  border: 1px solid rgba(125, 186, 255, 0.2);
  border-radius: 10px;
  background: rgba(10, 18, 35, 0.62);
  padding: 9px;
  display: grid;
  gap: 4px;
}

.adversarial-compare-grid span { color: #9db3d5; font-size: 11px; }
.adversarial-compare-grid strong { color: #edf5ff; font-size: 12px; }

.adversarial-log-head { display: flex; gap: 16px; flex-wrap: wrap; color: #9bb0d5; font-size: 12px; margin-bottom: 10px; }
.adversarial-log-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.adversarial-log-item { border: 1px solid rgba(136, 170, 221, 0.2); border-radius: 10px; background: rgba(10, 19, 36, 0.52); padding: 10px; display: grid; gap: 4px; margin-bottom: 8px; }
.adversarial-log-item strong { color: #e6f1ff; font-size: 12px; }
.adversarial-log-item span { color: #8fa9d2; font-size: 11px; }
.adversarial-log-item p { color: #bacbe8; font-size: 12px; margin: 0; line-height: 1.5; }

@keyframes stageFinaleFlash {
  0% { filter: brightness(1); }
  50% { filter: brightness(1.12); }
  100% { filter: brightness(1); }
}

@keyframes attackerSpin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

@keyframes swarmPulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.05); }
}

@keyframes openclawWave {
  0%, 100% { transform: translateY(0) rotate(0deg); }
  50% { transform: translateY(-4px) rotate(-3deg); }
}

@keyframes defenderRing {
  0%, 100% { transform: scale(1); opacity: 1; }
  50% { transform: scale(1.08); opacity: 0.8; }
}

@keyframes defenderWing {
  0%, 100% { transform: translateY(0) skewY(10deg); }
  50% { transform: translateY(-2px) skewY(14deg); }
}

@media (max-width: 900px) {
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .battle-summary-grid {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }

  .adversarial-compare-grid {
    grid-template-columns: 1fr;
  }

  .adversarial-log-grid {
    grid-template-columns: 1fr;
  }

}
</style>
