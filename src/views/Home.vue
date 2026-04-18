<template>
  <div
    class="workbench-home"
    :class="[
      {
        'immersive-war-room': adversarialPanelOpen,
        'reduce-motion': prefersReducedMotion,
        'home-cinematic-pending': cinematicRevealPending,
        'home-cinematic-running': cinematicRevealRunning,
        'home-cinematic-overlay-active': revealOverlayVisible,
      },
      `motion-tier-${motionTier}`
    ]"
    ref="stageRef"
  >
    <Transition name="home-reveal-fade">
      <div v-if="revealOverlayVisible" ref="revealOverlayRef" class="home-reveal-overlay">
        <div ref="revealBackdropRef" class="home-preloader-backdrop">
          <div class="home-pb-row">
            <div class="home-pb-col"></div>
            <div class="home-pb-col"></div>
            <div class="home-pb-col"></div>
            <div class="home-pb-col"></div>
            <div class="home-pb-col"></div>
          </div>
          <div class="home-pb-row">
            <div class="home-pb-col"></div>
            <div class="home-pb-col"></div>
            <div class="home-pb-col"></div>
            <div class="home-pb-col"></div>
            <div class="home-pb-col"></div>
          </div>
          <div class="home-pb-row home-pb-row-meta">
            <div class="home-pb-col"><p>Identity Verified</p></div>
            <div class="home-pb-col"><p>// governance lattice synced</p></div>
            <div class="home-pb-col"><p>Threat map online</p></div>
            <div class="home-pb-col"><p>模型与风险谱系已就绪</p></div>
            <div class="home-pb-col"><p>Aegis Workbench</p></div>
          </div>
        </div>

        <div ref="revealPreloaderRef" class="home-preloader-panel">
          <div class="home-p-row home-p-row-top">
            <p><span class="home-preloader-line">Initiating</span></p>
            <p><span class="home-preloader-line">Access Sequence</span></p>
          </div>
          <div class="home-p-row home-p-row-bottom">
            <p><span class="home-preloader-line">Phase 01</span></p>
            <p><span class="home-preloader-line">Signal Scan · 07 Layers</span></p>
            <p><span class="home-preloader-line">Policy Graph Locked</span></p>
            <p><span class="home-preloader-line">Ready To Enter</span></p>
          </div>

          <div class="home-reveal-core" ref="revealCoreRef">
            <button
              type="button"
              class="home-reveal-logo-btn"
              ref="revealLogoBtnRef"
              @click="skipHomeReveal"
              @mouseenter="handleRevealLogoHover"
              @focus="handleRevealLogoHover"
              :aria-label="revealAwaitingClick ? '点击进入工作台' : '工作台引导进行中'"
            >
              <img :src="defenderLogoUrl" alt="Aegis" class="home-reveal-logo" />
            </button>
            <div class="home-pbc-svg-strokes">
              <svg viewBox="0 0 320 320" fill="none" xmlns="http://www.w3.org/2000/svg">
                <circle
                  ref="revealTrackRef"
                  class="home-stroke-track"
                  cx="160"
                  cy="160"
                  r="155"
                  stroke-width="2"
                />
                <circle
                  ref="revealProgressRef"
                  class="home-stroke-progress"
                  cx="160"
                  cy="160"
                  r="155"
                  stroke-width="2"
                />
              </svg>
            </div>
          </div>
        </div>
      </div>
    </Transition>

    <section class="hero-scene card-glass scene-block magazine-masthead" ref="heroRef">
      <div class="hero-stage">
        <div class="hero-copy">
          <div class="eyebrow">{{ personaExperience.kicker }}</div>
          <h1 class="hero-headline workbench-title-core" data-workbench-title-anchor="home">
            <span class="hero-title-line hero-title-line-aegis">
              <span
                v-for="(char, idx) in mastheadAegisChars"
                :key="`masthead-aegis-char-${idx}`"
                class="hero-char-mask hero-char-primary"
                :class="{ 'hero-char-first-aegis': idx === mastheadAegisChars.length - 1 }"
              >
                <span class="hero-char">{{ char }}</span>
                <span v-if="idx === mastheadAegisChars.length - 1" class="hero-planet-core" aria-hidden="true"></span>
              </span>
            </span>
            <span class="hero-title-line hero-title-line-workbench">
              <span class="hero-bench-orbit-net" aria-hidden="true"></span>
              <span v-for="(char, idx) in mastheadWorkbenchChars" :key="`masthead-workbench-char-${idx}`" class="hero-char-mask hero-char-primary">
                <span class="hero-char">{{ char }}</span>
              </span>
            </span>
          </h1>
        </div>
        <aside class="hero-side-meta">
          <section class="hero-side-pane">
            <div class="hero-side-block">
              <div class="hero-side-label">态势摘要</div>
              <p class="hero-side-subline">{{ heroSubheadline }}</p>
            </div>
            <div class="hero-side-block">
              <div class="hero-pillar-pills">
                <span v-for="pill in heroPillarPills" :key="pill" class="hero-pillar-pill">{{ pill }}</span>
              </div>
            </div>
          </section>
          <section class="hero-side-pane">
            <div class="hero-side-block hero-side-meta-lines">
              <p>公司ID {{ traceContext.companyId ?? '-' }} · 账号 {{ traceContext.companyUserCount ?? 0 }} 人</p>
              <p>生成 {{ traceContext.generatedAt || '-' }}</p>
            </div>
            <div class="operator-ribbon">
              <div class="operator-label">当前指挥席位</div>
              <div class="operator-name">{{ overview.operator.displayName }}</div>
              <div class="operator-meta">{{ overview.operator.roleName }} · {{ overview.operator.department }}</div>
            </div>
          </section>
        </aside>
      </div>
    </section>

    <div class="stat-grid scene-block reveal-phase phase-stats">
      <stat-card
        v-for="card in statCards"
        :key="card.key"
        :title="card.label"
        :value="card.value"
        :suffix="card.suffix"
        :icon="card.icon"
        :color="card.color"
        :trend="card.delta"
      />
    </div>

    <HomeAiAnalysisHub
      class="scene-block reveal-phase phase-ai"
      :hub="homeAiHubData"
      :loading="homeAiHubLoading"
      :motion-tier="motionTier"
      :reduced-motion="prefersReducedMotion"
      @refresh="refreshHomeAiHub"
      @jump="handleHubJump"
      @scope-change="handleHubScopeChange"
      @detail="handleHubDetail"
    />

    <section
      v-if="!compactHomeEnabled && !hideHomeTraceModules"
      class="trace-grid scene-block reveal-phase phase-governance"
      :class="{ 'trace-grid-admin': isAdmin }"
    >
      <el-card class="trace-card card-glass">
        <div class="panel-head">
          <div>
            <div class="card-header">首页追溯上下文</div>
            <p class="panel-subtitle">数据范围与溯源状态</p>
          </div>
        </div>
        <div class="trace-context-row">
          <span>公司ID：{{ traceContext.companyId ?? '-' }}</span>
          <span>账号范围：{{ traceContext.companyUserCount ?? 0 }} 人</span>
          <span>当前账号：{{ traceContext.currentUsername || '-' }} (#{{ traceContext.currentUserId ?? '-' }})</span>
          <span>生成时间：{{ traceContext.generatedAt || '-' }}</span>
          <span>统一异常：{{ traceContext.monitorAnomaly ?? 0 }}</span>
          <span>统一隐私：{{ traceContext.monitorPrivacy ?? 0 }}</span>
          <span>统一待处置：{{ traceContext.monitorPending ?? 0 }}</span>
        </div>
        <p class="trace-note">{{ traceContext.traceabilityStatement || '数据范围：按当前公司与账号统计；支持按原始记录溯源。' }}</p>
        <p v-if="traceContext.monitorCaliberNote" class="trace-note">{{ traceContext.monitorCaliberNote }}</p>
      </el-card>

      <el-card class="trace-card card-glass">
        <div class="panel-head">
          <div>
            <div class="card-header">模型谱系与漂移</div>
            <p class="panel-subtitle">模型状态、样本覆盖与发布状态</p>
          </div>
          <div class="panel-actions">
            <span class="verify-badge" :class="modelDriftBadgeClass">
              {{ modelDriftBadgeText }}
            </span>
            <el-button size="small" :loading="modelGovernanceLoading" @click="fetchModelGovernance">刷新</el-button>
          </div>
        </div>
        <div class="trace-context-row">
          <span>累计训练运行：{{ modelLineage.totalRuns ?? 0 }}</span>
          <span>跟踪模型数：{{ modelLineage.trackedModelCount ?? 0 }}</span>
          <span>最新运行ID：{{ modelLineageRunId }}</span>
          <span>漂移分数：{{ modelDriftScoreText }}</span>
          <span>发布状态：{{ modelReleaseText }}</span>
        </div>
        <p class="trace-note">{{ modelGovernanceNote }}</p>
      </el-card>

      <el-card class="trace-card card-glass" v-if="isAdmin">
        <div class="panel-head">
          <div>
            <div class="card-header">治理就绪度闭环</div>
            <p class="panel-subtitle">闭环状态、风险预算与处置进展</p>
          </div>
          <div class="panel-actions">
            <el-button size="small" :loading="awardReadinessLoading" @click="fetchAwardReadiness">刷新</el-button>
            <el-button size="small" type="warning" :loading="autoRemediationLoading" @click="runAutoRemediationDryRun">自动处置演练</el-button>
            <el-button size="small" type="success" :loading="exportEvidenceLoading" @click="exportAwardEvidencePackage">导出证据包</el-button>
          </div>
        </div>
        <div class="trace-context-row">
          <span>已实现项：{{ awardReadinessImplemented }}/8</span>
          <span>待补项：{{ 8 - awardReadinessImplemented }}</span>
          <span>错误预算：{{ awardErrorBudgetText }}</span>
          <span>自动处置状态：{{ autoRemediationStatusText }}</span>
        </div>
        <p class="trace-note">{{ awardReadinessNote }}</p>
      </el-card>
    </section>

    <el-card
      v-if="!compactHomeEnabled && !hideHomeTraceModules"
      class="trace-modules-card card-glass scene-block reveal-phase phase-governance"
    >
      <div class="panel-head">
        <div>
          <div class="card-header">模块追溯下钻</div>
          <p class="panel-subtitle">按模块查看原始记录</p>
        </div>
      </div>
      <div class="trace-module-list">
        <button
          v-for="entry in traceModuleEntries"
          :key="entry.key"
          type="button"
          class="trace-module-item"
          @click="openTraceDrilldown(entry.key)"
        >
          <div class="trace-module-title">{{ entry.label }}</div>
          <div class="trace-module-meta">{{ entry.traceRule }}</div>
          <div class="trace-module-count">{{ entry.count }}</div>
        </button>
      </div>
    </el-card>

    <section class="pulse-grid scene-block reveal-phase phase-governance">
      <el-card class="pulse-card card-glass">
        <div class="panel-head">
          <div>
            <div class="card-header">治理总览</div>
            <p class="panel-subtitle">关键状态、风险信号与处理建议</p>
          </div>
          <div class="pulse-chip">{{ trustPulse.innovationLabel }}</div>
        </div>
        <div class="pulse-layout">
          <div class="pulse-score-ring">
            <span class="pulse-score">{{ trustPulse.score }}</span>
            <em>{{ trustPulse.pulseLevel }}</em>
          </div>
          <div class="pulse-copy">
            <strong>{{ trustPulse.mission }}</strong>
            <div class="pulse-dimensions">
              <article v-for="dimension in trustPulse.dimensions" :key="dimension.code" class="pulse-dimension">
                <div class="pulse-dimension-head">
                  <span>{{ dimension.label }}</span>
                  <strong>{{ dimension.score }}</strong>
                </div>
                <div class="pulse-bar"><i :style="{ width: `${dimension.score}%` }"></i></div>
                <p>{{ dimension.description }}</p>
              </article>
            </div>
          </div>
        </div>
      </el-card>

      <el-card class="pulse-signal-card card-glass">
        <div class="panel-head">
          <div>
            <div class="card-header">治理信号</div>
            <p class="panel-subtitle">当前优先事项与结果摘要</p>
          </div>
        </div>
        <div class="pulse-signal-list">
          <article
            v-for="(signal, idx) in governanceOverviewSignals"
            :key="signal.title"
            class="pulse-signal-item"
            :class="{ 'pulse-signal-new': shouldFlashSignal(signal.title) }"
          >
            <div class="pulse-signal-top">
              <strong>{{ signal.title }}</strong>
              <span :class="['pulse-tone', signal.tone]">{{ signal.value }}</span>
            </div>
            <p>{{ signal.action }}</p>
          </article>
        </div>
      </el-card>
    </section>

    <el-card
      class="chart-card card-glass trend-card scene-block reveal-phase phase-governance"
    >
      <div class="panel-head">
        <div>
          <div class="card-header">治理脉冲趋势</div>
          <p class="panel-subtitle">趋势窗口：{{ trendEvidenceText }} · {{ forecastInlineNote }}</p>
        </div>
        <div class="panel-actions">
          <button class="mini-refresh-btn" :disabled="forecastRefreshing" @click="refreshForecastNow">
            {{ forecastRefreshing ? '刷新中...' : '刷新预测' }}
          </button>
          <div class="panel-badge">T+1 预测 {{ overview.trend.forecastNextDay }}</div>
        </div>
      </div>
      <div ref="trendChartRef" class="chart-canvas trend-canvas"></div>
    </el-card>


    <el-card
      class="chart-card card-glass risk-card scene-block reveal-phase phase-governance"
    >
      <div class="panel-head">
        <div>
          <div class="card-header">风险结构剖面</div>
          <p class="panel-subtitle">当前风险分布</p>
        </div>
      </div>
      <div class="risk-layout">
        <div ref="riskChartRef" class="chart-canvas risk-canvas"></div>
        <div class="risk-list">
          <div v-for="item in overview.riskDistribution" :key="item.level" class="risk-item">
            <span class="risk-dot" :class="riskTone(item.level)"></span>
            <div class="risk-copy">
              <strong>{{ item.level }}</strong>
              <span>{{ item.value }} 起</span>
            </div>
          </div>
        </div>
      </div>
    </el-card>

    <el-card
      class="module-entry-card card-glass scene-block reveal-phase phase-governance"
    >
      <div class="panel-head">
        <div>
          <div class="card-header">能力模块入口</div>
          <p class="panel-subtitle">高频治理能力快速进入</p>
        </div>
      </div>
      <div class="module-entry-grid">
        <button
          v-for="item in homeModuleEntries"
          :key="item.title"
          class="module-entry-item clickable"
          @click="$router.push(item.route)"
        >
          <div class="module-entry-tag">{{ item.priority }}</div>
          <div class="module-entry-copy">
            <strong>{{ item.title }}</strong>
            <p>{{ item.description }}</p>
          </div>
          <div class="module-entry-metric">{{ item.metric }}</div>
        </button>
      </div>
    </el-card>

    <el-card
      v-if="!compactHomeEnabled && !hideHomeTraceModules"
      class="ai-workbench-card card-glass scene-block reveal-phase phase-governance"
      style="grid-column: 1 / -1"
    >
      <div class="panel-head">
        <div>
          <div class="card-header">AI 调用审计日志</div>
          <p class="panel-subtitle">审计记录与链路状态</p>
        </div>
        <div class="panel-actions">
          <span class="verify-badge" :class="aiAuditVerify.passed ? 'ok' : 'warn'">
            {{ aiAuditVerify.passed ? '链路验真通过' : '链路待校验' }}
          </span>
          <el-button :loading="aiAuditVerify.loading" @click="verifyAiAuditChain">校验链路</el-button>
          <el-button :loading="aiAuditLoading" @click="loadAiAuditLogs">刷新</el-button>
        </div>
      </div>
      <div v-if="aiAuditLoading" class="empty-state">加载中...</div>
      <div v-else-if="!aiAuditLogs.length" class="empty-state">暂无记录</div>
      <div v-else class="event-list">
        <div v-for="item in aiAuditLogs" :key="item.id" class="event-item">
          <strong>{{ item.modelCode || '-' }}</strong>
          <span>{{ item.provider || '-' }}</span>
          <span>{{ item.status || '-' }}</span>
          <span>{{ item.durationMs || 0 }}ms</span>
          <span>{{ item.username || '-' }} (#{{ item.userId ?? '-' }})</span>
          <span>公司 {{ item.companyId ?? '-' }}</span>
          <span>资产 {{ item.dataAssetId ?? '-' }}</span>
          <span>{{ item.createTime || '-' }}</span>
        </div>
      </div>
    </el-card>

    <div v-if="false && isAdmin" class="adversarial-floating-wrap">
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

    <el-dialog
      v-if="!hideHomeTraceModules"
      v-model="traceDialogVisible"
      width="72%"
      :close-on-click-modal="false"
      title="模块追溯明细"
    >
      <div class="trace-dialog-head">
        <span>模块：{{ traceDialog.module || '-' }}</span>
        <span>规则：{{ traceDialog.traceRule || '-' }}</span>
        <span>记录数：{{ traceDialog.records.length }}</span>
      </div>
      <div v-if="traceDialogLoading" class="empty-state">加载中...</div>
      <div v-else-if="!traceDialog.records.length" class="empty-state">暂无可追溯记录</div>
      <div v-else class="trace-record-list">
        <article v-for="record in traceDialog.records" :key="record.id || JSON.stringify(record)" class="trace-record-item">
          <span v-for="(value, key) in record" :key="key"><strong>{{ key }}:</strong> {{ value ?? '-' }}</span>
        </article>
      </div>
    </el-dialog>

    <el-drawer
      v-model="hubDetailVisible"
      size="58%"
      :close-on-click-modal="false"
      :title="hubDetail.title || 'AI中枢证据下钻'"
    >
      <div class="hub-detail-head">
        <span>类型：{{ hubDetail.kind || '-' }}</span>
        <span>标识：{{ hubDetail.key || '-' }}</span>
        <span>记录数：{{ hubDetail.records.length }}</span>
      </div>
      <p v-if="hubDetail.description" class="hub-detail-desc">{{ hubDetail.description }}</p>
      <div v-if="hubDetailLoading" class="empty-state">加载中...</div>
      <div v-else-if="!hubDetail.records.length" class="empty-state">暂无明细记录</div>
      <el-table v-else :data="hubDetail.records" border size="small" height="62vh">
        <el-table-column
          v-for="col in hubDetailColumns"
          :key="col"
          :prop="col"
          :label="hubDetailColumnLabel(col)"
          min-width="140"
          show-overflow-tooltip
        >
          <template #default="scope">
            {{ formatHubDetailValue(scope.row[col]) }}
          </template>
        </el-table-column>
      </el-table>
    </el-drawer>

  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import gsap from 'gsap';
import { ElMessage } from 'element-plus';
import { dashboardApi } from '../api/dashboard';
import request from '../api/request';
import StatCard from '../components/StatCard.vue';
import HomeAiAnalysisHub from '../components/home/HomeAiAnalysisHub.vue';
import defenderLogoUrl from '../assets/logo.svg';
import attackerAssetLattice from '../assets/adversarial/attacker-lattice.svg';
import attackerAssetHelix from '../assets/adversarial/attacker-helix.svg';
import attackerAssetSwarm from '../assets/adversarial/attacker-swarm.svg';
import defenderAssetAegis from '../assets/adversarial/defender-aegis.svg';
import defenderAssetFortress from '../assets/adversarial/defender-fortress.svg';
import defenderAssetPrism from '../assets/adversarial/defender-prism.svg';
import { useUserStore } from '../store/user';
import { getPersonaExperience, personalizeWorkbench } from '../utils/persona';
import { useRouter } from 'vue-router';
import { useHomeCinematicReveal } from '../composables/useHomeCinematicReveal';

function createEmptyOverview() {
  return {
    operator: {
      displayName: 'Guest',
      roleName: 'Unidentified Role',
      department: 'Trusted AI Governance Center',
      avatar: ''
    },
    headline: 'Aegis Workbench 可信AI数据治理与隐私合规工作台',
    subheadline: '正在加载真实治理态势...',
    sceneTags: [],
    metrics: [],
    trend: {
      labels: [],
      riskSeries: [],
      auditSeries: [],
      aiCallSeries: [],
      costSeries: [],
      forecastNextDay: 0,
      riskEventSampleCount: 0,
      auditLogSampleCount: 0,
      modelStatSampleCount: 0,
      trendWindowDays: 7,
    },
    riskDistribution: [],
    todos: [],
    feeds: [],
    _dataSource: 'real_db',
  };
}

const stageRef = ref(null);
const heroRef = ref(null);
const trendChartRef = ref(null);
const riskChartRef = ref(null);
const userStore = useUserStore();
const router = useRouter();
const overview = ref(createEmptyOverview());
const insights = ref({ postureScore: 0, summary: {}, highlights: [], recommendations: [] });
const trustPulse = ref({ score: 0, pulseLevel: '', mission: '', innovationLabel: '', dimensions: [], signals: [] });
const loading = ref(true);
const forecastDataSource = ref('real_db');
const traceContext = ref({
  companyId: null,
  companyUserCount: 0,
  currentUserId: null,
  currentUsername: '',
  generatedAt: '',
  traceabilityStatement: '',
  monitorAnomaly: 0,
  monitorPrivacy: 0,
  monitorPending: 0,
  monitorCaliberNote: '',
});
const forecastExplain = ref({
  method: '',
  historyPoints: 0,
  note: '',
  fallback: false,
  dataSource: 'real_db',
});
const traceModules = ref({});
const modelGovernanceLoading = ref(false);
const modelLineage = ref({
  totalRuns: 0,
  trackedModelCount: 0,
  latestByModel: {},
  updatedAt: '',
});
const modelDrift = ref({
  available: false,
  driftScore: null,
  driftLevel: 'unknown',
  alert: false,
  reason: '',
  recentCount: 0,
});
const modelRelease = ref({
  stable: null,
  canary: null,
  updatedAt: '',
});
const awardReadinessLoading = ref(false);
const autoRemediationLoading = ref(false);
const exportEvidenceLoading = ref(false);
const awardReadiness = ref({
  gapChecklist: {},
  resilience: {},
  autoRemediation: {},
  generatedAt: '',
});
const traceDialogVisible = ref(false);
const traceDialogLoading = ref(false);
const traceDialog = ref({
  module: '',
  traceRule: '',
  records: [],
});

const aiDraftMessage = ref('');
const aiModelOptions = ref([]);
const selectedAiModelCode = ref('');
const aiModelLoadState = ref('idle');
const aiModelLoadMessage = ref('');
const aiAccessReason = ref('工作台交互请求');
const aiResponsePreview = ref('');
const aiSending = ref(false);
const forecastRefreshing = ref(false);
const aiAuditLoading = ref(false);
const aiAuditLogs = ref([]);
const aiAuditVerify = ref({ loading: false, passed: false, checkedRows: 0, violationCount: 0 });
const governanceSignalSeen = ref(new Set());
const governanceSignalFlash = ref(new Set());
const motionTier = ref('high');
const prefersReducedMotion = ref(false);
const routePathRef = computed(() => router.currentRoute.value.path);
const isAdmin = computed(() => {
  const role = String(userStore.userInfo?.roleCode || userStore.userInfo?.role || '')
    .trim()
    .toUpperCase();
  return role === 'ADMIN';
});
const adversarialMeta = ref({ scenarios: [] });
const adversarialConfig = ref({ scenario: 'random', rounds: 12, seed: '' });
const adversarialPanelOpen = ref(false);
const adversarialPanelMax = ref(true);
const adversarialHardeningLevel = ref('strong');
const adversarialAdvancedOpen = ref(false);
const adversarialError = ref('');
const adversarialRunning = ref(false);
const adversarialTaskId = ref('');
const adversarialTaskStatus = ref('idle');
const adversarialBattle = ref(null);
const adversarialVisibleRounds = ref([]);
const adversarialHardeningApplying = ref(false);
const adversarialBeforeHardening = ref(null);
const adversarialBeat = ref('idle');
const adversarialFinaleActive = ref(false);
const adversarialVisualSeed = ref(0);
const adversarialEventLogs = ref([]);
const adversarialTrainingLogs = ref([]);
const adversarialLogsVisible = ref(false);
const adversarialLogsLoading = ref(false);
const adversarialReportLoading = ref(false);
const hubScope = ref({ level: 'company', department: '', username: '' });
const compactHomeEnabled = ref(false);
const hideHomeTraceModules = ref(true);
const homeAiHubRemote = ref(null);
const homeAiHubLoading = ref(false);
const homeAiHubCursor = ref(0);
const homeAiHubStreamConnected = ref(false);
const hubDetailVisible = ref(false);
const hubDetailLoading = ref(false);
const hubDetail = ref({
  kind: '',
  key: '',
  title: '',
  description: '',
  records: [],
});
const {
  cinematicRevealPending,
  cinematicRevealRunning,
  revealOverlayVisible,
  revealMobileLite,
  revealAwaitingClick,
  revealOverlayRef,
  revealBackdropRef,
  revealPreloaderRef,
  revealCoreRef,
  revealLogoBtnRef,
  revealTrackRef,
  revealProgressRef,
  handleRevealLogoHover,
  skipHomeReveal,
  playEntryScene,
  initHomeRevealPending,
  disposeHomeReveal,
} = useHomeCinematicReveal({
  stageRef,
  heroRef,
  motionTier,
  prefersReducedMotion,
  routePathRef,
});
let adversarialPlaybackTimer = null;
let adversarialTaskPollTimer = null;
let adversarialBeatTimers = [];
let adversarialFinaleTimer = null;
let homeLoadFrameId = null;
let homeDeferredTaskTimers = [];
let homeDeferredIdleIds = [];
let homeAiHubStreamHandle = null;
let homeAiHubReconnectTimer = null;
let reducedMotionQuery = null;
let prevRouteStageOverflowY = null;
let prevAppMainOverflowY = null;

function stabilizeHomeScrollContainers() {
  const routeStage = document.querySelector('.route-stage');
  if (routeStage) {
    if (prevRouteStageOverflowY === null) {
      prevRouteStageOverflowY = routeStage.style.overflowY || '';
    }
    routeStage.style.overflowY = 'visible';
  }

  const appMain = document.querySelector('.app-main');
  if (appMain) {
    if (prevAppMainOverflowY === null) {
      prevAppMainOverflowY = appMain.style.overflowY || '';
    }
    appMain.style.overflowY = 'auto';
  }
}

function restoreHomeScrollContainers() {
  const routeStage = document.querySelector('.route-stage');
  if (routeStage && prevRouteStageOverflowY !== null) {
    routeStage.style.overflowY = prevRouteStageOverflowY;
    prevRouteStageOverflowY = null;
  }

  const appMain = document.querySelector('.app-main');
  if (appMain && prevAppMainOverflowY !== null) {
    appMain.style.overflowY = prevAppMainOverflowY;
    prevAppMainOverflowY = null;
  }
}

function evaluateMotionProfile() {
  if (typeof window === 'undefined') {
    motionTier.value = 'medium';
    prefersReducedMotion.value = false;
    return;
  }
  const mediaReduce = typeof window.matchMedia === 'function'
    ? window.matchMedia('(prefers-reduced-motion: reduce)')
    : null;
  prefersReducedMotion.value = Boolean(mediaReduce?.matches);

  if (prefersReducedMotion.value) {
    motionTier.value = 'low';
    return;
  }

  const cores = Number(navigator.hardwareConcurrency || 4);
  const memory = Number(navigator.deviceMemory || 4);
  const conn = navigator.connection || navigator.mozConnection || navigator.webkitConnection;
  const saveData = Boolean(conn?.saveData);
  const isSlowConnection = /(2g|slow-2g)/i.test(String(conn?.effectiveType || ''));

  let score = 0;
  if (cores >= 8) score += 2;
  else if (cores >= 4) score += 1;
  if (memory >= 8) score += 2;
  else if (memory >= 4) score += 1;
  if (!saveData && !isSlowConnection) score += 1;

  if (score >= 4) motionTier.value = 'high';
  else if (score >= 2) motionTier.value = 'medium';
  else motionTier.value = 'low';
}

function selectedModel() {
  return aiModelOptions.value.find(item => item.modelCode === selectedAiModelCode.value)
    || aiModelOptions.value[0]
    || null;
}

function normalizeAiReply(payload) {
  if (typeof payload === 'string') return payload;
  if (typeof payload?.reply === 'string') return payload.reply;
  if (typeof payload?.content === 'string') return payload.content;
  if (typeof payload?.data?.reply === 'string') return payload.data.reply;
  return '已收到响应';
}

async function loadAiAuditLogs() {
  aiAuditLoading.value = true;
  try {
    const data = await request.get('/ai/monitor/logs', { params: { page: 1, pageSize: 20 } });
    aiAuditLogs.value = Array.isArray(data?.list) ? data.list : [];
  } catch {
    aiAuditLogs.value = [];
  } finally {
    aiAuditLoading.value = false;
  }
}

async function verifyAiAuditChain() {
  aiAuditVerify.value.loading = true;
  try {
    const data = await request.get('/ai/monitor/logs/verify-chain');
    const checkedRows = Number(data?.checkedRows || 0);
    const passed = Boolean(data?.passed) && checkedRows > 0;
    aiAuditVerify.value = {
      loading: false,
      passed,
      checkedRows,
      violationCount: Number(data?.violationCount || 0),
    };
    if (aiAuditVerify.value.passed && aiAuditVerify.value.checkedRows > 0) {
      ElMessage.success(`AI 调用审计链校验通过（${aiAuditVerify.value.checkedRows} 条）`);
    } else if (aiAuditVerify.value.checkedRows === 0) {
      ElMessage.info('暂无可校验记录，请先完成真实业务调用后再校验链路');
    } else {
      ElMessage.error(`AI 调用审计链存在异常（${aiAuditVerify.value.violationCount} 处）`);
    }
  } catch (error) {
    aiAuditVerify.value.loading = false;
    ElMessage.error(error?.message || 'AI 审计链校验失败');
  }
}

async function openTraceDrilldown(moduleKey) {
  traceDialogVisible.value = true;
  traceDialogLoading.value = true;
  traceDialog.value = { module: moduleKey, traceRule: '', records: [] };
  try {
    const data = await request.get('/dashboard/trace/drilldown', {
      params: { module: moduleKey, limit: 20 }
    });
    traceDialog.value = {
      module: data?.module || moduleKey,
      traceRule: data?.traceRule || '',
      records: Array.isArray(data?.records) ? data.records : [],
    };
  } catch (error) {
    traceDialog.value = {
      module: moduleKey,
      traceRule: '',
      records: [],
    };
    ElMessage.error(error?.message || '追溯明细加载失败');
  } finally {
    traceDialogLoading.value = false;
  }
}

async function sendAiDraft() {
  if (!aiDraftMessage.value.trim()) {
    ElMessage.warning('请输入要发送给 AI 的内容');
    return;
  }
  const model = selectedModel();
  if (!model) {
    ElMessage.warning('请先选择一个可用模型');
    return;
  }
  aiSending.value = true;
  try {
    const res = await request.post('/ai/chat', {
      provider: model.provider || 'qwen',
      model: model.modelCode,
      accessReason: aiAccessReason.value,
      messages: [{ role: 'user', content: aiDraftMessage.value.trim() }],
    });
    aiResponsePreview.value = normalizeAiReply(res);
    ElMessage.success('消息已发送，已收到网关响应');
    aiDraftMessage.value = '';
  } catch (error) {
    ElMessage.error(error?.message || 'AI 请求失败');
  } finally {
    aiSending.value = false;
  }
}

async function fetchAiModels() {
  aiModelLoadState.value = 'loading';
  aiModelLoadMessage.value = '';

  try {
    let payload;
    try {
      payload = await request.get('/ai/catalog');
    } catch (error) {
      const message = String(error?.message || '').toLowerCase();
      if (message.includes('no static resource') || message.includes('404')) {
        payload = await request.get('/ai/catalog/list');
      } else {
        throw error;
      }
    }
    const list = normalizeModelListPayload(payload)
      .filter(isEnabledModel)
      .map(item => ({
        ...item,
        modelName: cleanModelName(item.modelName || item.name || item.modelCode) || '未命名模型',
        modelCode: cleanModelName(item.modelCode) || ''
      }));
    aiModelOptions.value = list;
    if (!selectedAiModelCode.value && aiModelOptions.value.length > 0) {
      selectedAiModelCode.value = aiModelOptions.value[0].modelCode;
    }
    if (aiModelOptions.value.length === 0) {
      aiModelLoadState.value = 'empty';
      aiModelLoadMessage.value = '当前没有可发送的启用模型，请联系管理员检查模型目录。';
    } else {
      aiModelLoadState.value = 'ready';
    }
  } catch (error) {
    aiModelOptions.value = [];
    selectedAiModelCode.value = '';
    aiModelLoadState.value = 'error';
    aiModelLoadMessage.value = error?.message || '模型目录加载失败';
  }
}

function normalizeModelListPayload(payload) {
  if (Array.isArray(payload)) return payload;
  if (Array.isArray(payload?.records)) return payload.records;
  if (Array.isArray(payload?.list)) return payload.list;
  if (Array.isArray(payload?.items)) return payload.items;
  if (Array.isArray(payload?.rows)) return payload.rows;
  if (Array.isArray(payload?.data)) return payload.data;
  if (Array.isArray(payload?.data?.records)) return payload.data.records;
  return [];
}

function cleanModelName(name) {
  if (!name || typeof name !== 'string') return '';
  return String(name).trim();
}

function isPermissionDeniedError(error) {
  const code = Number(error?.code || error?.status || 0);
  if (code === 403 || code === 40300) {
    return true;
  }
  const message = String(error?.message || '').toLowerCase();
  return message.includes('无权限') || message.includes('403');
}

function isEnabledModel(item) {
  const status = String(item?.status ?? '').trim().toLowerCase();
  return !status || status === 'enabled' || status === 'active' || status === '1' || status === 'true';
}

async function fetchCrossSiteGuardStatus(silent = true) {
  crossSiteGuard.value.loading = true;
  try {
    const status = await request.get('/security/cross-site/status');
    crossSiteGuard.value = {
      loading: false,
      enabled: Boolean(status?.enabled),
      mode: status?.mode || 'disabled',
      allowedOrigins: Array.isArray(status?.allowedOrigins) ? status.allowedOrigins : [],
      blockedCount: Number(status?.blockedCount || 0),
      lastBlockedAt: status?.lastBlockedAt || null,
      message: ''
    };
  } catch (error) {
    crossSiteGuard.value = {
      ...crossSiteGuard.value,
      loading: false,
      message: error?.message || '跨站拦截状态获取失败'
    };
    if (!silent) {
      ElMessage.warning(crossSiteGuard.value.message);
    }
  }
}

async function fetchAiModelStack() {
  aiModelStack.value.loading = true;
  try {
    const data = await request.get('/ai/model-metrics');
    const metrics = data?.metrics && typeof data.metrics === 'object' ? data.metrics : {};
    aiModelStack.value = {
      loading: false,
      available: Boolean(data?.available),
      classifierStack: Array.isArray(metrics?.classifier_stack) ? metrics.classifier_stack : [],
      benchmark: metrics?.benchmark || null,
      message: data?.available ? '' : (data?.message || '训练模型服务暂不可达')
    };
  } catch (error) {
    aiModelStack.value = {
      loading: false,
      available: false,
      classifierStack: [],
      benchmark: null,
      message: error?.message || '训练模型指标拉取失败'
    };
  }
}

function stopAdversarialPlayback() {
  if (adversarialPlaybackTimer) {
    clearInterval(adversarialPlaybackTimer);
    adversarialPlaybackTimer = null;
  }
  adversarialBeatTimers.forEach(timer => window.clearTimeout(timer));
  adversarialBeatTimers = [];
  adversarialBeat.value = adversarialBattle.value ? 'freeze' : 'idle';
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

function randomizeAdversarialVisualSeed() {
  adversarialVisualSeed.value = Math.floor(Math.random() * 1000000);
}

function stopAdversarialTaskPolling() {
  if (adversarialTaskPollTimer) {
    clearInterval(adversarialTaskPollTimer);
    adversarialTaskPollTimer = null;
  }
}

const adversarialScenePreset = {
  random: { title: '综合攻防演练（Random Mix）', desc: '多攻击向量随机组合，检验整体韧性。' },
  prompt_injection_blitz: { title: '提示注入攻防（Prompt Injection）', desc: '聚焦上下文污染、越权提示与防注入策略。' },
  composite_ai_chain: { title: '复合AI链路渗透（Composite AI Chain）', desc: '串联提示注入、策略绕过与权限扩散，验证全链路韧性。' },
  ai_alignment_subversion: { title: '对齐机制颠覆（AI Alignment Subversion）', desc: '模拟对齐目标偏移与安全边界篡改，验证对齐防护能力。' },
  stealth_exfil: { title: '数据泄露攻防（Stealth Exfil）', desc: '模拟隐蔽外传链路，验证DLP与上下文锁。' },
  supply_chain_apt: { title: '供应链攻击攻防（Supply Chain APT）', desc: '覆盖依赖投毒与制品完整性防护。' },
  real_threat_check: { title: '实时威胁检测（Real Threat Check）', desc: '基于真实日志进行即时态势评估。' },
};

const adversarialStrategyLabelMap = {
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

function normalizeAdversarialCode(value) {
  return String(value || '').trim().toLowerCase().replace(/-/g, '_');
}

function adversarialStrategyLabel(value) {
  const code = normalizeAdversarialCode(value);
  if (!code) return '-';
  if (adversarialStrategyLabelMap[code]) {
    return adversarialStrategyLabelMap[code];
  }
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

function startAdversarialPlayback(rounds) {
  stopAdversarialPlayback();
  adversarialVisibleRounds.value = [];
  if (!Array.isArray(rounds) || rounds.length === 0) {
    adversarialBeat.value = adversarialRunning.value ? 'charge' : 'idle';
    return;
  }
  let cursor = 0;
  startAdversarialBeat(Boolean(rounds[0]?.attack_success));
  adversarialPlaybackTimer = window.setInterval(() => {
    const currentRound = rounds[cursor];
    adversarialVisibleRounds.value = rounds.slice(0, cursor + 1);
    startAdversarialBeat(Boolean(currentRound?.attack_success));
    cursor += 1;
    if (cursor >= rounds.length) {
      stopAdversarialPlayback();
      triggerAdversarialFinale();
    }
  }, 620);
}

async function fetchAdversarialMeta() {
  if (!isAdmin.value || adversarialMeta.value.scenarios.length > 0) {
    return;
  }
  const data = await request.get('/ai/adversarial/meta');
  adversarialMeta.value = data || { scenarios: [] };
  if (adversarialScenarioOptions.value.length > 0) {
    adversarialConfig.value.scenario = adversarialScenarioOptions.value[0].code;
  }
}

async function toggleAdversarialPanel() {
  adversarialPanelOpen.value = !adversarialPanelOpen.value;
  if (adversarialPanelOpen.value) {
    adversarialError.value = '';
    randomizeAdversarialVisualSeed();
    try {
      await fetchAdversarialMeta();
    } catch (error) {
      adversarialError.value = error?.message || '攻防元数据加载失败';
    }
  }
}

function closeAdversarialPanel() {
  adversarialPanelOpen.value = false;
  stopAdversarialTaskPolling();
}

async function launchAdversarialDrill() {
  await router.push({
    path: '/threat-monitor',
    query: { tab: 'drill', from: 'home' },
  });
}

async function runAdversarialBattle() {
  adversarialError.value = '';
  adversarialRunning.value = true;
  adversarialTaskStatus.value = 'running';
  adversarialFinaleActive.value = false;
  randomizeAdversarialVisualSeed();
  stopAdversarialPlayback();
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

async function syncAdversarialTaskStatus(taskId, silent = false) {
  try {
    const data = await request.get(`/ai/adversarial/task/${taskId}`);
    adversarialTaskStatus.value = String(data?.status || 'running');
    if (data?.battle) {
      adversarialBattle.value = data.battle;
      const rounds = Array.isArray(data?.battle?.rounds) ? data.battle.rounds : [];
      const completedRaw = Number(data?.completedRounds);
      const hasCompletedRounds = Number.isFinite(completedRaw) && completedRaw >= 0;
      let completed = hasCompletedRounds
        ? Math.max(0, Math.min(rounds.length, completedRaw))
        : rounds.length;
      if (adversarialTaskStatus.value === 'running' && !hasCompletedRounds) {
        completed = Math.min(rounds.length, Math.max(1, adversarialVisibleRounds.value.length + 1));
      }
      const previousCount = adversarialVisibleRounds.value.length;
      adversarialVisibleRounds.value = rounds.slice(0, completed);
      if (completed > 0 && completed > previousCount) {
        const last = adversarialVisibleRounds.value[adversarialVisibleRounds.value.length - 1];
        startAdversarialBeat(Boolean(last?.attack_success));
      }
    }
    if (adversarialTaskStatus.value === 'completed') {
      stopAdversarialTaskPolling();
      adversarialRunning.value = false;
      triggerAdversarialFinale();
      if (!silent) {
        ElMessage.success('真实攻防训练已完成');
      }
    }
    if (adversarialTaskStatus.value === 'failed') {
      stopAdversarialTaskPolling();
      adversarialRunning.value = false;
      adversarialError.value = data?.error || '真实攻防任务失败';
      if (!silent) {
        ElMessage.error(adversarialError.value);
      }
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
      attackSuccessRate: Number(adversarialBattle.value?.attack_success_rate || 0),
      defenseStrength: Number(adversarialBattle.value?.defense_strength_score || 0),
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
    ElMessage.error(error?.message || '应用防御加固失败');
  } finally {
    adversarialHardeningApplying.value = false;
  }
}

async function refreshForecastNow() {
  forecastRefreshing.value = true;
  try {
    const forecastData = await request.post('/dashboard/forecast/company-refresh');
    if (forecastData?.forecast?.length) {
      const series = forecastData.forecast.map(v => Math.round(v * 10) / 10);
      overview.value = {
        ...overview.value,
        trend: {
          ...overview.value.trend,
          forecastSeries: series,
          forecastNextDay: Math.round(series[0] ?? overview.value.trend.forecastNextDay),
        }
      };
      forecastDataSource.value = forecastData._dataSource || 'real_db';
      forecastExplain.value = {
        method: forecastData.method || '',
        historyPoints: Number(forecastData.historyPoints || (forecastData.inputHistory?.length || 0)),
        note: forecastData.note || '',
        fallback: Boolean(forecastData.fallback),
        dataSource: forecastData._dataSource || 'real_db',
      };
      ElMessage.success('预测已刷新');
    }
  } catch (error) {
    ElMessage.error(error?.message || '预测刷新失败');
  } finally {
    forecastRefreshing.value = false;
  }
}

let trendChart;
let riskChart;
let resizeHandler;
let echartsLib;
let primaryChartRenderTimer;

async function ensureEcharts() {
  if (!echartsLib) {
    echartsLib = await import('echarts');
  }
  return echartsLib;
}

const metricVisualMap = {
  assets: { icon: 'DataAnalysis', color: 'var(--color-primary)' },
  alerts: { icon: 'Warning', color: 'var(--color-danger)' },
  aiCalls: { icon: 'StarFilled', color: 'var(--color-success)' },
  audits: { icon: 'Timer', color: 'var(--color-info)' },
};

const statCards = computed(() => overview.value.metrics.map(item => ({
  ...item,
  icon: metricVisualMap[item.key]?.icon || 'DataAnalysis',
  color: metricVisualMap[item.key]?.color || 'var(--color-primary)'
})));
const trendEvidenceText = computed(() => {
  const trend = overview.value?.trend || {};
  const days = Number(trend.trendWindowDays || 7);
  const riskSamples = Number(trend.riskEventSampleCount || 0);
  const auditSamples = Number(trend.auditLogSampleCount || 0);
  const modelSamples = Number(trend.modelStatSampleCount || 0);
  const source = String(overview.value?._dataSource || 'real_db').toLowerCase();
  const sourceLabel = source === 'real_db' ? '真实DB' : '降级源';
  return `数据窗${days}天 · 风险样本${riskSamples} · 审计样本${auditSamples} · 调用样本${modelSamples} · ${sourceLabel}`;
});
const forecastInlineNote = computed(() => {
  const method = forecastExplain.value?.method || 'LSTM';
  const points = Number(forecastExplain.value?.historyPoints || 0);
  const status = forecastExplain.value?.fallback ? '降级' : '正常';
  return `预测：${method} · 历史点 ${points} · 状态 ${status} · T+1 ${overview.value?.trend?.forecastNextDay ?? 0}`;
});
const heroHeadline = computed(() => {
  const prefix = 'Aegis Workbench';
  const headline = String(overview.value.headline || prefix).trim();
  if (headline.startsWith(prefix)) {
    return {
      primary: prefix,
      suffix: headline.slice(prefix.length).trim()
    };
  }
  return {
    primary: prefix,
    suffix: headline === prefix ? '' : headline
  };
});
function splitHeadlineChars(text) {
  return Array.from(String(text || '')).map(char => (char === ' ' ? '\u00A0' : char));
}
const mastheadAegisChars = computed(() => splitHeadlineChars('Aegis'));
const mastheadWorkbenchChars = computed(() => splitHeadlineChars('Workbench'));
const heroPillarPills = computed(() => ['权限治理', '风险闭环', '模型治理']);
const heroPrimaryChars = computed(() => splitHeadlineChars(heroHeadline.value.primary));
const heroSuffixChars = computed(() => splitHeadlineChars(heroHeadline.value.suffix));
const identityRoleLine = computed(() => {
  const role = String(overview.value?.operator?.roleName || '-').trim();
  const dept = String(overview.value?.operator?.department || '-').trim();
  return `${role} / ${dept}`;
});
const heroSubheadline = computed(() => {
  const scopedCompanyId = traceContext.value?.companyId ?? '-';
  const scopedUsers = traceContext.value?.companyUserCount ?? 0;
  const sourceText = String(overview.value?.subheadline || '').trim();
  if (sourceText) {
    let normalized = sourceText;
    if (/传统平台/.test(normalized)) {
      normalized = '当前治理状态已同步';
    }
    if (/作战|总控|全域/.test(normalized)) {
      normalized = '已汇总资产、模型与风险状态';
    }
    return normalized;
  }
  return `数据范围：公司 ID ${scopedCompanyId} / 账号 ${scopedUsers} 人；支持按原始记录溯源。`;
});
const personaExperience = computed(() => getPersonaExperience(userStore.userInfo));
const traceModuleEntries = computed(() => {
  const modules = traceModules.value || {};
  return Object.entries(modules)
    .filter(([key, val]) => key !== 'userScope' && val && typeof val === 'object')
    .map(([key, val]) => ({
      key,
      label: val.label || key,
      traceRule: val.traceRule || '-',
      count: Number(val.count || 0),
    }));
});
const homeModuleEntries = computed(() => {
  const todos = Array.isArray(overview.value?.todos) ? overview.value.todos : [];
  const base = todos.slice(0, 6).map(item => ({
    title: item.title,
    description: item.description,
    route: item.route,
    metric: item.metric,
    priority: item.priority,
  }));
  if (base.length >= 4) {
    return base;
  }
  return [
    ...base,
    {
      title: '威胁监控中心',
      description: '聚焦待处置告警与阻断结果，追踪治理闭环。',
      route: '/threat-monitor',
      metric: `待处置 ${traceContext.value?.monitorPending ?? 0}`,
      priority: 'P0',
    },
    {
      title: '影子AI治理',
      description: '定位白名单外模型调用，执行审批和策略收敛。',
      route: '/shadow-ai',
      metric: `异常 ${traceContext.value?.monitorAnomaly ?? 0}`,
      priority: 'P1',
    },
    {
      title: '隐私防护态势',
      description: '按账号与部门审查隐私风险并补齐证据链。',
      route: '/privacy-monitor',
      metric: `隐私 ${traceContext.value?.monitorPrivacy ?? 0}`,
      priority: 'P1',
    },
    {
      title: '模型治理与发布',
      description: '查看漂移状态、候选发布与回滚路径。',
      route: '/ai/model-governance',
      metric: modelReleaseText.value,
      priority: 'P2',
    },
  ].slice(0, 6);
});
const latestSensitiveRun = computed(() => {
  const latestByModel = modelLineage.value?.latestByModel || {};
  if (latestByModel.sensitive_clf) {
    return latestByModel.sensitive_clf;
  }
  const recentRuns = Array.isArray(modelLineage.value?.recentRuns) ? modelLineage.value.recentRuns : [];
  return recentRuns.find(item => item?.modelKey === 'sensitive_clf') || null;
});
const modelLineageRunId = computed(() => {
  const runId = String(latestSensitiveRun.value?.runId || '').trim();
  if (!runId) return '-';
  return runId.length > 22 ? `${runId.slice(0, 22)}...` : runId;
});
const modelDriftScoreText = computed(() => {
  const score = modelDrift.value?.driftScore;
  if (typeof score !== 'number' || Number.isNaN(score)) return '-';
  return score.toFixed(4);
});
const modelDriftBadgeClass = computed(() => {
  if (!modelDrift.value?.available) return 'warn';
  return modelDrift.value?.alert ? 'warn' : 'ok';
});
const modelDriftBadgeText = computed(() => {
  if (!modelDrift.value?.available) return '漂移待评估';
  return modelDrift.value?.alert ? '漂移告警' : '漂移正常';
});
const modelReleaseText = computed(() => {
  const stable = modelRelease.value?.stable;
  const canary = modelRelease.value?.canary;
  if (canary?.candidateId) {
    return `Canary ${canary.candidateId}`;
  }
  if (stable?.candidateId) {
    return `Stable ${stable.candidateId}`;
  }
  return '未发布';
});
const modelGovernanceNote = computed(() => {
  if (!modelDrift.value?.available) {
    const reason = String(modelDrift.value?.reason || '').toUpperCase();
    if (reason.includes('INSUFFICIENT')) {
      return '暂无足够历史数据，暂无法完成模型漂移评估。';
    }
    return '模型漂移评估暂不可用，请稍后重试。';
  }
  const source = latestSensitiveRun.value?.source || 'unknown_source';
  const runTime = latestSensitiveRun.value?.timestamp || modelLineage.value?.updatedAt || '-';
  return `模型来源：${source}；更新时间：${runTime}；发布状态：${modelReleaseText.value}；最近在线样本：${modelDrift.value?.recentCount || 0}。`;
});
const awardReadinessImplemented = computed(() => {
  const checklist = awardReadiness.value?.gapChecklist || {};
  return Object.values(checklist).filter(item => item?.status === 'implemented').length;
});
const awardErrorBudgetText = computed(() => {
  const budget = awardReadiness.value?.resilience?.errorBudget;
  if (typeof budget !== 'number' || Number.isNaN(budget)) return '-';
  return `${budget.toFixed(2)}%`;
});
const autoRemediationStatusText = computed(() => {
  const executed = awardReadiness.value?.autoRemediation?.executed;
  const dryRun = awardReadiness.value?.autoRemediation?.dryRun;
  if (executed === true) return '已执行';
  if (dryRun === true) return '演练模式';
  return '未触发';
});
const awardReadinessNote = computed(() => {
  const checklist = awardReadiness.value?.gapChecklist || {};
  const missing = Object.entries(checklist)
    .filter(([, item]) => item?.status !== 'implemented')
    .map(([, item]) => item?.goal)
    .filter(Boolean);
  if (!missing.length) return '系统状态健康，建议优先处理高风险项。';
  return `建议优先处理：${missing.slice(0, 2).join('；')}。`;
});
const governanceOverviewSignals = computed(() => {
  const merged = [];
  const seenTitles = new Set();

  (trustPulse.value?.signals || []).forEach((item) => {
    const title = String(item?.title || '').trim();
    if (!title || seenTitles.has(title)) return;
    seenTitles.add(title);
    merged.push({
      title,
      value: item?.value || '-',
      tone: item?.tone || 'neutral',
      action: item?.action || '持续跟进',
    });
  });

  (insights.value?.highlights || []).forEach((item) => {
    const title = String(item?.title || '').trim();
    if (!title || seenTitles.has(title)) return;
    seenTitles.add(title);
    merged.push({
      title,
      value: item?.value || '-',
      tone: 'neutral',
      action: item?.description || '状态已同步',
    });
  });

  (insights.value?.recommendations || []).forEach((item) => {
    const title = String(item?.title || '').trim();
    if (!title || seenTitles.has(title)) return;
    seenTitles.add(title);
    merged.push({
      title,
      value: item?.metric || item?.priority || '-',
      tone: item?.priority === 'P0' ? 'danger' : (item?.priority === 'P1' ? 'warning' : 'safe'),
      action: item?.description || '建议跟进',
    });
  });

  return merged.slice(0, 6);
});

function shouldFlashSignal(title) {
  return governanceSignalFlash.value.has(String(title || ''));
}

watch(
  governanceOverviewSignals,
  list => {
    for (const item of list || []) {
      const title = String(item?.title || '').trim();
      if (!title || governanceSignalSeen.value.has(title)) continue;
      governanceSignalSeen.value.add(title);
      governanceSignalFlash.value.add(title);
      window.setTimeout(() => {
        governanceSignalFlash.value.delete(title);
      }, 1000);
    }
  },
  { immediate: true, deep: true }
);
const homeAiHubData = computed(() => {
  if (homeAiHubRemote.value) {
    return homeAiHubRemote.value;
  }
  const metrics = Array.isArray(overview.value?.metrics) ? overview.value.metrics : [];
  const metricMap = new Map(metrics.map(item => [String(item?.key || ''), Number(item?.value || 0)]));
  const riskScore = Number(trustPulse.value?.score || 0);
  const kpis = [
    {
      key: 'assetTotal',
      label: '资产库对象',
      value: String(metricMap.get('assets') || 0),
      note: 'Data Asset Snapshot',
    },
    {
      key: 'riskEvents',
      label: '风险事件总量',
      value: String(metricMap.get('alerts') || 0),
      note: 'Risk Event Aggregation',
    },
    {
      key: 'aiCalls',
      label: '模型调用总量',
      value: String(metricMap.get('aiCalls') || 0),
      note: 'AI Call Audit',
    },
    {
      key: 'auditLogs',
      label: '审计留痕记录',
      value: String(metricMap.get('audits') || 0),
      note: 'Audit Chain',
    },
    {
      key: 'governanceScore',
      label: '治理脉冲分',
      value: String(riskScore),
      note: `Scope: ${hubScope.value.level}`,
    },
  ];

  const graphNodes = [
    { id: 'company', label: `公司#${traceContext.value?.companyId ?? '-'}`, value: 8, color: '#38bdf8' },
    { id: 'asset', label: '资产库', value: Math.max(2, Number(metricMap.get('assets') || 0)), color: '#22d3ee' },
    { id: 'risk', label: '风险事件', value: Math.max(2, Number(metricMap.get('alerts') || 0)), color: '#f97316' },
    { id: 'audit', label: '审计日志', value: Math.max(2, Number(metricMap.get('audits') || 0)), color: '#a78bfa' },
    { id: 'ai', label: '模型调用', value: Math.max(2, Number(metricMap.get('aiCalls') || 0)), color: '#34d399' },
  ];
  const graphEdges = [
    { source: 'company', target: 'asset', value: 2, color: 'rgba(34,211,238,0.75)' },
    { source: 'company', target: 'risk', value: 3, color: 'rgba(249,115,22,0.8)' },
    { source: 'company', target: 'audit', value: 2, color: 'rgba(167,139,250,0.78)' },
    { source: 'asset', target: 'ai', value: 2, color: 'rgba(52,211,153,0.72)' },
    { source: 'ai', target: 'risk', value: 3, color: 'rgba(251,146,60,0.76)' },
    { source: 'risk', target: 'audit', value: 2, color: 'rgba(147,197,253,0.7)' },
  ];

  const radarDimensions = (trustPulse.value?.dimensions || []).map(item => ({
    code: item?.code || item?.label,
    label: item?.label || '-',
    value: Math.max(0, Math.min(100, Number(item?.score || 0))),
  }));

  return {
    kpis,
    graph: { nodes: graphNodes, edges: graphEdges },
    radar: { dimensions: radarDimensions },
    alertBoard: { items: [] },
    scopePersona: { stats: [] },
    pulseWall: { nodes: [] },
  };
});
const adversarialWinnerText = computed(() => {
  const winner = String(adversarialBattle.value?.winner || '-');
  if (winner.includes('攻击方')) return '攻方模拟器';
  if (winner.includes('防御方')) return '防御策略';
  return winner.replace(/\([^)]*\)/g, '').trim() || '-';
});
const adversarialAllRounds = computed(() => {
  return Array.isArray(adversarialBattle.value?.rounds) ? adversarialBattle.value.rounds : [];
});
const adversarialCurrentRound = computed(() => {
  if (adversarialVisibleRounds.value.length) {
    return adversarialVisibleRounds.value[adversarialVisibleRounds.value.length - 1];
  }
  if (adversarialAllRounds.value.length) {
    return adversarialAllRounds.value[0];
  }
  return null;
});
const adversarialCurrentRoundText = computed(() => {
  const current = Number(adversarialCurrentRound.value?.round_num || 0);
  const total = Math.max(1, Number(adversarialAllRounds.value.length || adversarialConfig.value?.rounds || 1));
  if (current > 0) {
    return `${current}/${total}`;
  }
  if (adversarialRunning.value) {
    return `0/${total}`;
  }
  return '-';
});
const adversarialRoundSeed = computed(() => {
  return Number(adversarialCurrentRound.value?.round_num || 0);
});
const adversarialProgressRatio = computed(() => {
  const total = Math.max(1, Number(adversarialAllRounds.value.length || adversarialConfig.value?.rounds || 1));
  if (adversarialVisibleRounds.value.length > 0) {
    return Math.min(1, adversarialVisibleRounds.value.length / total);
  }
  if (adversarialRunning.value) {
    return 0.08;
  }
  if (adversarialBattle.value) {
    return 1;
  }
  return 0;
});
const adversarialProgressText = computed(() => {
  return `${Math.round(adversarialProgressRatio.value * 100)}%`;
});
const adversarialScenarioLabel = computed(() => {
  return adversarialCurrentScenarioText.value?.title || String(adversarialConfig.value?.scenario || 'random').toUpperCase();
});
const adversarialBeatLabel = computed(() => {
  if (adversarialBeat.value === 'charge') return '蓄力';
  if (adversarialBeat.value === 'dash') return '突进';
  if (adversarialBeat.value === 'hit') return '冲击';
  if (adversarialBeat.value === 'block') return '拦截';
  if (adversarialBeat.value === 'freeze') return '定格';
  return '待命';
});
const adversarialAttackerPersona = computed(() => {
  const scenario = String(adversarialConfig.value?.scenario || '').toLowerCase();
  const attacker = String(adversarialBattle.value?.attacker || '').toLowerCase();
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
  if (adversarialBattle.value) {
    return String(adversarialBattle.value?.winner || '').includes('防御方') ? 'block' : 'hit';
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
  if (adversarialCurrentRound.value) {
    return `有效率 ${Math.round((adversarialCurrentRound.value.final_effectiveness || 0) * 100)}%`;
  }
  if (adversarialBattle.value) {
    return `攻击成功率 ${Math.round((adversarialBattle.value.attack_success_rate || 0) * 100)}%`;
  }
  return '尚未执行';
});
const adversarialDefenderPoseClass = computed(() => {
  return adversarialImpactState.value === 'hit' ? 'pose-guard' : 'pose-counter';
});
const adversarialSceneState = computed(() => {
  if (adversarialRunning.value) return 'running';
  if (!adversarialBattle.value) return 'idle';
  return String(adversarialBattle.value?.winner || '').includes('防御方') ? 'defense' : 'breach';
});
const adversarialNarrativeText = computed(() => {
  if (adversarialCurrentRound.value?.narrative) {
    return adversarialCurrentRound.value.narrative;
  }
  if (adversarialBattle.value?.summary) {
    return adversarialBattle.value.summary;
  }
  if (adversarialRunning.value) {
    return '战场中枢正在同步策略迭代与对抗推演...';
  }
  return '点击开始演练，进入双角色实战推演。';
});
const adversarialSubtitleText = computed(() => {
  if (adversarialFinaleActive.value && adversarialBattle.value) {
    const score = `${adversarialBattle.value.attacker_final_score || 0} : ${adversarialBattle.value.defender_final_score || 0}`;
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
  const score = Number(adversarialBattle.value?.defense_strength_score || 0);
  if (!Number.isFinite(score) || score <= 0) {
    return '-';
  }
  return `${Math.round(score)}分`;
});
const adversarialCanApplyHardening = computed(() => {
  return String(adversarialBattle.value?.hardening_status || '').toLowerCase() === 'pending_manual_apply';
});
const adversarialComparisonVisible = computed(() => {
  return Boolean(adversarialBeforeHardening.value) && Boolean(adversarialBattle.value);
});
const adversarialAttackBeforeWidth = computed(() => {
  const before = Number(adversarialBeforeHardening.value?.attackSuccessRate ?? NaN);
  if (!Number.isFinite(before)) return 0;
  return Math.max(0, Math.min(100, Math.round(before * 100)));
});
const adversarialAttackAfterWidth = computed(() => {
  const after = Number(adversarialBattle.value?.attack_success_rate ?? NaN);
  if (!Number.isFinite(after)) return 0;
  return Math.max(0, Math.min(100, Math.round(after * 100)));
});
const adversarialDefenseGaugeMax = computed(() => {
  const before = Number(adversarialBeforeHardening.value?.defenseStrength ?? 0);
  const after = Number(adversarialBattle.value?.defense_strength_score ?? 0);
  return Math.max(1, Math.ceil(Math.max(100, before, after)));
});
const adversarialDefenseBeforeWidth = computed(() => {
  const before = Number(adversarialBeforeHardening.value?.defenseStrength ?? NaN);
  if (!Number.isFinite(before)) return 0;
  return Math.max(0, Math.min(100, Math.round((before / adversarialDefenseGaugeMax.value) * 100)));
});
const adversarialDefenseAfterWidth = computed(() => {
  const after = Number(adversarialBattle.value?.defense_strength_score ?? NaN);
  if (!Number.isFinite(after)) return 0;
  return Math.max(0, Math.min(100, Math.round((after / adversarialDefenseGaugeMax.value) * 100)));
});
const adversarialAttackRateCompareText = computed(() => {
  const before = Number(adversarialBeforeHardening.value?.attackSuccessRate ?? NaN);
  const after = Number(adversarialBattle.value?.attack_success_rate ?? NaN);
  if (!Number.isFinite(before) || !Number.isFinite(after)) {
    return '-';
  }
  const delta = Math.round((after - before) * 1000) / 10;
  return `${Math.round(before * 100)}% -> ${Math.round(after * 100)}% (${delta > 0 ? '+' : ''}${delta}%)`;
});
const adversarialDefenseCompareText = computed(() => {
  const before = Number(adversarialBeforeHardening.value?.defenseStrength ?? NaN);
  const after = Number(adversarialBattle.value?.defense_strength_score ?? NaN);
  if (!Number.isFinite(before) || !Number.isFinite(after)) {
    return '-';
  }
  const delta = Math.round(after - before);
  return `${Math.round(before)}分 -> ${Math.round(after)}分 (${delta > 0 ? '+' : ''}${delta})`;
});
const adversarialHardeningConclusion = computed(() => {
  const beforeRate = Number(adversarialBeforeHardening.value?.attackSuccessRate ?? NaN);
  const afterRate = Number(adversarialBattle.value?.attack_success_rate ?? NaN);
  const beforeDefense = Number(adversarialBeforeHardening.value?.defenseStrength ?? NaN);
  const afterDefense = Number(adversarialBattle.value?.defense_strength_score ?? NaN);
  if (!Number.isFinite(beforeRate) || !Number.isFinite(afterRate) || !Number.isFinite(beforeDefense) || !Number.isFinite(afterDefense)) {
    return '待验证';
  }
  const improved = afterRate <= beforeRate && afterDefense >= beforeDefense;
  return improved ? '防御提升已验证' : '建议继续加固并复测';
});
const adversarialCurveSeries = computed(() => {
  return adversarialVisibleRounds.value.map(round => {
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
  const rounds = adversarialVisibleRounds.value;
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

function riskTone(level) {
  const value = String(level || '').toLowerCase();
  if (value.includes('高') || value.includes('high') || value.includes('critical') || value.includes('p0')) return 'danger';
  if (value.includes('中') || value.includes('medium') || value.includes('processing') || value.includes('p1')) return 'warning';
  if (value.includes('低') || value.includes('low')) return 'safe';
  return 'neutral';
}

function routePathFromSignal(signal) {
  const text = `${signal?.title || ''} ${signal?.action || ''}`.toLowerCase();
  if (text.includes('影子') || text.includes('ai')) return '/shadow-ai';
  if (text.includes('审计')) return '/audit-center';
  if (text.includes('告警') || text.includes('阻断')) return '/threat-monitor';
  return '/ops-observability';
}

function routeLabelFromSignal(signal) {
  const path = routePathFromSignal(signal);
  if (path === '/shadow-ai') return '影子AI';
  if (path === '/audit-center') return '审计中心';
  if (path === '/threat-monitor') return 'AI攻击防御';
  return '治理观测';
}

function routeQueryFromSignal(signal) {
  const path = routePathFromSignal(signal);
  if (path === '/threat-monitor') return { tab: 'alertCenter' };
  if (path === '/shadow-ai') return { tab: 'risk' };
  return {};
}

function handleHubScopeChange(scope) {
  hubScope.value = { ...hubScope.value, ...scope };
  refreshHomeAiHub();
  restartHomeAiHubStream();
}

function handleHubJump(rec) {
  if (!rec?.route) return;
  router.push({ path: rec.route, query: rec.query || {} });
}

const hubDetailColumns = computed(() => {
  const rows = Array.isArray(hubDetail.value?.records) ? hubDetail.value.records : [];
  if (!rows.length || typeof rows[0] !== 'object' || rows[0] == null) return [];
  return Object.keys(rows[0]);
});

function hubDetailColumnLabel(key) {
  const map = {
    id: 'ID',
    userId: '用户ID',
    username: '账号',
    eventType: '事件类型',
    severity: '风险级别',
    status: '状态',
    sourceModule: '来源模块',
    operation: '操作',
    operationTime: '操作时间',
    createTime: '创建时间',
    eventTime: '事件时间',
    durationMs: '耗时(ms)',
    modelCode: '模型编码',
    provider: '供应商',
    name: '名称',
    type: '类型',
    sensitivityLevel: '敏感级别',
    ownerId: '责任人',
    title: '标题',
    label: '指标',
    value: '数值',
    note: '说明',
    key: '键',
  };
  if (map[key]) return map[key];
  return String(key || '')
    .replace(/([A-Z])/g, ' $1')
    .replace(/^./, s => s.toUpperCase())
    .trim();
}

function formatHubDetailValue(value) {
  if (value == null || value === '') return '-';
  if (typeof value === 'object') {
    try {
      return JSON.stringify(value);
    } catch {
      return String(value);
    }
  }
  return String(value);
}

async function handleHubDetail(event) {
  if (!event?.kind || !event?.key) return;
  hubDetailVisible.value = true;
  hubDetailLoading.value = true;
  hubDetail.value = {
    kind: event.kind,
    key: event.key,
    title: event.label || '中枢明细',
    description: '',
    records: [],
  };

  try {
    const payload = await dashboardApi.getHomeAiHubDetail({
      ...normalizedHubScopeParams(),
      kind: event.kind,
      key: event.key,
      source: event.source,
      target: event.target,
      limit: 30,
    });
    hubDetail.value = {
      kind: payload?.kind || event.kind,
      key: payload?.key || event.key,
      title: payload?.title || event.label || '中枢明细',
      description: payload?.description || '',
      records: Array.isArray(payload?.records) ? payload.records : [],
    };
  } catch (error) {
    hubDetail.value = {
      kind: event.kind,
      key: event.key,
      title: event.label || '中枢明细',
      description: '',
      records: [],
    };
    ElMessage.error(error?.message || 'AI中枢明细加载失败');
  } finally {
    hubDetailLoading.value = false;
  }
}

function normalizedHubScopeParams() {
  const level = String(hubScope.value?.level || 'company').toLowerCase();
  const params = { scopeLevel: level };
  if (level === 'department' && String(hubScope.value?.department || '').trim()) {
    params.department = String(hubScope.value.department).trim();
  }
  if (level === 'user' && String(hubScope.value?.username || '').trim()) {
    params.username = String(hubScope.value.username).trim();
  }
  return params;
}

async function refreshHomeAiHub() {
  homeAiHubLoading.value = true;
  try {
    const data = await dashboardApi.getHomeAiHub(normalizedHubScopeParams());
    if (data && typeof data === 'object') {
      homeAiHubRemote.value = {
        kpis: Array.isArray(data.kpis) ? data.kpis : [],
        graph: data.graph || { nodes: [], edges: [] },
        radar: data.radar || { dimensions: [] },
        alertBoard: data.alertBoard || { items: [] },
        scopePersona: data.scopePersona || { stats: [] },
        pulseWall: data.pulseWall || { nodes: [] },
      };
      homeAiHubCursor.value = Number(data.cursor || 0);
    }
  } catch (error) {
    if (!isPermissionDeniedError(error)) {
      ElMessage.error(error?.message || '首页AI中枢数据加载失败');
    }
  } finally {
    homeAiHubLoading.value = false;
  }
}

function mergeHomeAiHubDelta(payload) {
  if (!payload || !homeAiHubRemote.value) return;
  const current = homeAiHubRemote.value;
  homeAiHubRemote.value = {
    ...current,
    kpis: Array.isArray(payload.kpis) && payload.kpis.length ? payload.kpis : current.kpis,
    alertBoard: payload.alertBoard || current.alertBoard || { items: [] },
    scopePersona: payload.scopePersona || current.scopePersona || { stats: [] },
    pulseWall: payload.pulseWall || current.pulseWall || { nodes: [] },
  };
  homeAiHubCursor.value = Math.max(homeAiHubCursor.value, Number(payload.cursor || 0));
}

function clearHomeAiHubReconnect() {
  if (homeAiHubReconnectTimer) {
    window.clearTimeout(homeAiHubReconnectTimer);
    homeAiHubReconnectTimer = null;
  }
}

function stopHomeAiHubStream() {
  if (homeAiHubStreamHandle) {
    homeAiHubStreamHandle.close();
    homeAiHubStreamHandle = null;
  }
  homeAiHubStreamConnected.value = false;
  clearHomeAiHubReconnect();
}

function scheduleHomeAiHubReconnect() {
  if (homeAiHubReconnectTimer) return;
  homeAiHubReconnectTimer = window.setTimeout(() => {
    homeAiHubReconnectTimer = null;
    startHomeAiHubStream();
  }, 4000);
}

function startHomeAiHubStream() {
  stopHomeAiHubStream();
  const params = normalizedHubScopeParams();
  try {
    homeAiHubStreamHandle = dashboardApi.openHomeAiHubStream({
      ...params,
      cursor: homeAiHubCursor.value,
      onSnapshot: payload => {
        homeAiHubStreamConnected.value = true;
        if (!payload || typeof payload !== 'object') return;
        homeAiHubRemote.value = {
          kpis: Array.isArray(payload.kpis) ? payload.kpis : [],
          graph: payload.graph || { nodes: [], edges: [] },
          radar: payload.radar || { dimensions: [] },
          alertBoard: payload.alertBoard || { items: [] },
          scopePersona: payload.scopePersona || { stats: [] },
          pulseWall: payload.pulseWall || { nodes: [] },
        };
        homeAiHubCursor.value = Number(payload.cursor || homeAiHubCursor.value || 0);
      },
      onDelta: payload => {
        homeAiHubStreamConnected.value = true;
        mergeHomeAiHubDelta(payload);
      },
      onError: () => {
        homeAiHubStreamConnected.value = false;
        scheduleHomeAiHubReconnect();
      },
    });
  } catch {
    homeAiHubStreamConnected.value = false;
    scheduleHomeAiHubReconnect();
  }
}

function restartHomeAiHubStream() {
  startHomeAiHubStream();
}

function clampSeriesOutliers(series, lowQ = 0.05, highQ = 0.95) {
  const numeric = (series || []).filter(v => typeof v === 'number' && Number.isFinite(v));
  if (numeric.length < 6) {
    return [...(series || [])];
  }
  const sorted = [...numeric].sort((a, b) => a - b);
  const low = sorted[Math.max(0, Math.floor((sorted.length - 1) * lowQ))];
  const high = sorted[Math.max(0, Math.floor((sorted.length - 1) * highQ))];
  return (series || []).map(v => {
    if (typeof v !== 'number' || !Number.isFinite(v)) return v;
    return Math.min(high, Math.max(low, v));
  });
}

function resolveYAxisMax(seriesList) {
  const values = seriesList.flat().filter(v => typeof v === 'number' && Number.isFinite(v));
  if (values.length === 0) {
    return 10;
  }
  const max = Math.max(...values);
  const padded = max * 1.2;
  return Math.max(10, Math.ceil(padded));
}

async function renderTrendChart() {
  const echarts = await ensureEcharts();
  if (!trendChartRef.value) return;
  if (!trendChart) {
    trendChart = echarts.init(trendChartRef.value);
  }
  const trend = overview.value.trend;
  const hasForecast = Array.isArray(trend.forecastSeries) && trend.forecastSeries.length > 0;
  const riskSeries = clampSeriesOutliers(trend.riskSeries || []);
  const auditSeries = clampSeriesOutliers(trend.auditSeries || []);
  const aiCallSeries = clampSeriesOutliers(trend.aiCallSeries || []);
  const costSeries = clampSeriesOutliers(trend.costSeries || []);
  const forecastSeries = clampSeriesOutliers(trend.forecastSeries || []);

  const historicLabels = trend.labels || [];
  const forecastLabels = hasForecast
    ? forecastSeries.map((_, i) => `预测+${i + 1}`)
    : [];
  const allLabels = [...historicLabels, ...forecastLabels];

  const forecastPad = hasForecast ? forecastSeries.map(() => null) : [];

  const riskWithForecast = hasForecast
    ? [...riskSeries, ...forecastPad]
    : riskSeries;
  const forecastWithPad = hasForecast
    ? [...riskSeries.map(() => null), ...forecastSeries]
    : [];
  const leftAxisMax = resolveYAxisMax([riskWithForecast, forecastWithPad, [...auditSeries, ...forecastPad], [...aiCallSeries, ...forecastPad]]);
  const rightAxisMax = resolveYAxisMax([[...costSeries, ...forecastPad]]);
  const chartAnimation = !prefersReducedMotion.value && motionTier.value !== 'low';
  const chartAnimDuration = motionTier.value === 'high' ? 460 : 280;

  trendChart.setOption({
    animation: chartAnimation,
    animationDuration: chartAnimDuration,
    animationDurationUpdate: Math.round(chartAnimDuration * 0.8),
    backgroundColor: 'transparent',
    tooltip: { trigger: 'axis' },
    legend: {
      top: 0,
      textStyle: { color: '#b8c2d4' },
      data: hasForecast
        ? ['风险事件', '审计留痕', 'AI调用', '成本(分)', 'LSTM预测']
        : ['风险事件', '审计留痕', 'AI调用', '成本(分)']
    },
    grid: { left: 24, right: 28, top: 48, bottom: 24, containLabel: true },
    xAxis: {
      type: 'category',
      data: allLabels,
      axisLine: { lineStyle: { color: 'rgba(255,255,255,0.12)' } },
      axisLabel: { color: '#93a0b8' }
    },
    yAxis: [
      {
        type: 'value',
        min: 0,
        max: leftAxisMax,
        axisLine: { show: false },
        splitLine: { lineStyle: { color: 'rgba(255,255,255,0.08)' } },
        axisLabel: { color: '#93a0b8' }
      },
      {
        type: 'value',
        min: 0,
        max: rightAxisMax,
        axisLine: { show: false },
        splitLine: { show: false },
        axisLabel: { color: '#93a0b8' }
      }
    ],
    series: [
      {
        name: '风险事件',
        type: 'line',
        smooth: true,
        smoothMonotone: 'x',
        symbolSize: 8,
        data: riskWithForecast,
        lineStyle: { width: 3, color: '#ff7d66' },
        itemStyle: { color: '#ff7d66' },
        areaStyle: { color: 'rgba(255,125,102,0.12)' }
      },
      ...(hasForecast ? [{
        name: 'LSTM预测',
        type: 'line',
        smooth: true,
        smoothMonotone: 'x',
        symbolSize: 7,
        data: forecastWithPad,
        lineStyle: { width: 2, type: 'dashed', color: '#c77dff' },
        itemStyle: { color: '#c77dff' },
        areaStyle: { color: 'rgba(199,125,255,0.08)' }
      }] : []),
      {
        name: '审计留痕',
        type: 'line',
        smooth: true,
        smoothMonotone: 'x',
        symbolSize: 7,
        data: [...auditSeries, ...forecastPad],
        lineStyle: { width: 3, color: '#6aa6ff' },
        itemStyle: { color: '#6aa6ff' },
        areaStyle: { color: 'rgba(106,166,255,0.12)' }
      },
      {
        name: 'AI调用',
        type: 'bar',
        barMaxWidth: 18,
        data: [...aiCallSeries, ...forecastPad],
        itemStyle: {
          borderRadius: [10, 10, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: '#4fe3c1' },
            { offset: 1, color: '#1a8c74' }
          ])
        }
      },
      {
        name: '成本(分)',
        type: 'line',
        smooth: true,
        smoothMonotone: 'x',
        yAxisIndex: 1,
        symbolSize: 6,
        data: [...costSeries, ...forecastPad],
        lineStyle: { width: 2, type: 'dashed', color: '#f5d06f' },
        itemStyle: { color: '#f5d06f' }
      }
    ]
  });
}

async function renderRiskChart() {
  const echarts = await ensureEcharts();
  if (!riskChartRef.value) return;
  if (!riskChart) {
    riskChart = echarts.init(riskChartRef.value);
  }
  const chartAnimation = !prefersReducedMotion.value && motionTier.value !== 'low';
  const chartAnimDuration = motionTier.value === 'high' ? 420 : 220;
  riskChart.setOption({
    animation: chartAnimation,
    animationDuration: chartAnimDuration,
    animationDurationUpdate: Math.round(chartAnimDuration * 0.75),
    backgroundColor: 'transparent',
    tooltip: { trigger: 'item' },
    series: [
      {
        type: 'pie',
        radius: ['56%', '76%'],
        center: ['50%', '52%'],
        label: { show: false },
        labelLine: { show: false },
        itemStyle: {
          borderColor: '#111826',
          borderWidth: 6
        },
        data: overview.value.riskDistribution.map(item => ({
          value: item.value,
          name: item.level,
          itemStyle: {
            color: item.level === '高危'
              ? '#ff6b6b'
              : item.level === '中危'
                ? '#ffb454'
                : item.level === '低危'
                  ? '#2ecc71'
                  : '#7f8aa3'
          }
        }))
      }
    ]
  });
}


async function fetchData() {
  loading.value = true;
  // Start entrance sequence immediately; do not block on API success.
  playEntryScene();
  try {
    const bundle = await dashboardApi.getHomeBundle();
    const workbench = bundle?.workbench || {};
    const insightData = bundle?.insights || {};
    const pulseData = bundle?.trustPulse || {};
    const forecastData = bundle?.forecast || {};
    traceContext.value = bundle?.traceContext || traceContext.value;
    traceModules.value = bundle?.traceModules || {};
    const personalized = personalizeWorkbench(workbench, userStore.userInfo);

    if (forecastData?.forecast?.length) {
      const series = forecastData.forecast.map(v => Math.round(v * 10) / 10);
      personalized.trend = {
        ...personalized.trend,
        forecastSeries: series,
        forecastNextDay: Math.round(series[0] ?? personalized.trend.forecastNextDay),
      };
      forecastDataSource.value = forecastData._dataSource || 'real_db';
      forecastExplain.value = {
        method: forecastData.method || '',
        historyPoints: Number(forecastData.historyPoints || (forecastData.inputHistory?.length || 0)),
        note: forecastData.note || '',
        fallback: Boolean(forecastData.fallback),
        dataSource: forecastData._dataSource || 'real_db',
      };
    }

    overview.value = personalized;
    insights.value = insightData;
    trustPulse.value = pulseData;
    loading.value = false;

    schedulePrimaryChartRender();
  } catch (error) {
    if (!isPermissionDeniedError(error)) {
      ElMessage.error(error?.message || '首页工作台加载失败');
    }
  } finally {
    if (loading.value) {
      loading.value = false;
    }
  }
}

async function fetchModelGovernance() {
  modelGovernanceLoading.value = true;
  try {
    const [lineageResp, driftResp, releaseResp] = await Promise.all([
      dashboardApi.getModelLineage(),
      dashboardApi.getModelDriftStatus(),
      dashboardApi.getModelReleaseStatus(),
    ]);
    modelLineage.value = lineageResp?.lineage && typeof lineageResp.lineage === 'object'
      ? lineageResp.lineage
      : (lineageResp || modelLineage.value);
    modelDrift.value = driftResp?.drift && typeof driftResp.drift === 'object'
      ? driftResp.drift
      : (driftResp || modelDrift.value);
    modelRelease.value = releaseResp?.release && typeof releaseResp.release === 'object'
      ? releaseResp.release
      : (releaseResp || modelRelease.value);
  } catch (error) {
    modelDrift.value = {
      ...modelDrift.value,
      available: false,
      reason: error?.message || 'FETCH_FAILED',
    };
  } finally {
    modelGovernanceLoading.value = false;
  }
}

async function fetchAwardReadiness() {
  awardReadinessLoading.value = true;
  try {
    const report = await dashboardApi.getAwardReadinessReport();
    awardReadiness.value = report || awardReadiness.value;
  } catch (error) {
    if (!isPermissionDeniedError(error)) {
      ElMessage.warning(error?.message || '治理就绪度报告加载失败');
    }
  } finally {
    awardReadinessLoading.value = false;
  }
}

async function runAutoRemediationDryRun() {
  autoRemediationLoading.value = true;
  try {
    await dashboardApi.runAutoRemediationPlaybook({ dryRun: true });
    await fetchAwardReadiness();
    ElMessage.success('自动处置演练已执行');
  } catch (error) {
    ElMessage.error(error?.message || '自动处置演练失败');
  } finally {
    autoRemediationLoading.value = false;
  }
}

async function exportAwardEvidencePackage() {
  exportEvidenceLoading.value = true;
  try {
    const payload = {
      includePdf: false,
      includeJson: true,
    };
    const data = await dashboardApi.exportEvidencePackage(payload);
    const signature = String(data?.signature || '').trim();
    const sigSuffix = signature ? signature.slice(0, 10) : 'n/a';
    const warning = String(data?.warning || '').trim();
    if (warning) {
      ElMessage.warning(`证据包已导出（JSON），签名: ${sigSuffix}`);
    } else {
      ElMessage.success(`证据包已导出，签名: ${sigSuffix}`);
    }
  } catch (error) {
    ElMessage.error(error?.message || '证据包导出失败');
  } finally {
    exportEvidenceLoading.value = false;
  }
}

function schedulePrimaryChartRender() {
  if (primaryChartRenderTimer) {
    clearTimeout(primaryChartRenderTimer);
  }
  primaryChartRenderTimer = window.setTimeout(async () => {
    await nextTick();
    await Promise.all([renderTrendChart(), renderRiskChart()]);
  }, 180);
}

function scheduleHomeDeferredTask(task, delay = 0, idleTimeout = 1200) {
  const runTask = () => {
    if (typeof window.requestIdleCallback === 'function') {
      const idleId = window.requestIdleCallback(() => {
        task();
      }, { timeout: idleTimeout });
      homeDeferredIdleIds.push(idleId);
      return;
    }
    task();
  };
  const timerId = window.setTimeout(runTask, Math.max(0, delay));
  homeDeferredTaskTimers.push(timerId);
}

function resetHomeScrollPosition() {
  if (typeof window !== 'undefined') {
    window.scrollTo({ top: 0, left: 0, behavior: 'auto' });
  }
  const routeStage = document.querySelector('.route-stage');
  if (routeStage && typeof routeStage.scrollTop === 'number') {
    routeStage.scrollTop = 0;
  }
  const appMain = document.querySelector('.app-main');
  if (appMain && typeof appMain.scrollTop === 'number') {
    appMain.scrollTop = 0;
  }
}


watch(() => overview.value.trend, async () => {
  schedulePrimaryChartRender();
}, { deep: true });

onMounted(() => {
  evaluateMotionProfile();
  initHomeRevealPending();
  stabilizeHomeScrollContainers();
  resetHomeScrollPosition();
  window.setTimeout(() => {
    resetHomeScrollPosition();
  }, 40);
  if (typeof window.matchMedia === 'function') {
    reducedMotionQuery = window.matchMedia('(prefers-reduced-motion: reduce)');
    const handler = () => evaluateMotionProfile();
    if (typeof reducedMotionQuery.addEventListener === 'function') {
      reducedMotionQuery.addEventListener('change', handler);
    } else if (typeof reducedMotionQuery.addListener === 'function') {
      reducedMotionQuery.addListener(handler);
    }
    reducedMotionQuery.__aegisHandler = handler;
  }
  homeLoadFrameId = window.requestAnimationFrame(() => {
    fetchData();
    scheduleHomeDeferredTask(() => {
      refreshHomeAiHub();
      startHomeAiHubStream();
    }, 240, 900);
    scheduleHomeDeferredTask(() => {
      fetchModelGovernance();
      fetchAwardReadiness();
      loadAiAuditLogs();
    }, 820, 1800);
    homeLoadFrameId = null;
  });
  resizeHandler = () => {
    trendChart?.resize();
    riskChart?.resize();
  };
  window.addEventListener('resize', resizeHandler);
});

onBeforeUnmount(() => {
  restoreHomeScrollContainers();
  disposeHomeReveal();
  if (reducedMotionQuery && reducedMotionQuery.__aegisHandler) {
    const handler = reducedMotionQuery.__aegisHandler;
    if (typeof reducedMotionQuery.removeEventListener === 'function') {
      reducedMotionQuery.removeEventListener('change', handler);
    } else if (typeof reducedMotionQuery.removeListener === 'function') {
      reducedMotionQuery.removeListener(handler);
    }
  }
  window.removeEventListener('resize', resizeHandler);
  if (homeLoadFrameId != null) {
    window.cancelAnimationFrame(homeLoadFrameId);
    homeLoadFrameId = null;
  }
  if (primaryChartRenderTimer) clearTimeout(primaryChartRenderTimer);
  if (homeDeferredTaskTimers.length > 0) {
    homeDeferredTaskTimers.forEach(timerId => window.clearTimeout(timerId));
    homeDeferredTaskTimers = [];
  }
  if (homeDeferredIdleIds.length > 0 && typeof window.cancelIdleCallback === 'function') {
    homeDeferredIdleIds.forEach(id => window.cancelIdleCallback(id));
    homeDeferredIdleIds = [];
  }
  if (adversarialFinaleTimer) {
    window.clearTimeout(adversarialFinaleTimer);
    adversarialFinaleTimer = null;
  }
  stopHomeAiHubStream();
  stopAdversarialPlayback();
  stopAdversarialTaskPolling();
  trendChart?.dispose();
  riskChart?.dispose();
});
</script>

<style scoped>
.ai-model-select :deep(.el-select__input) {
  color: var(--color-text);
}

.ai-model-select :deep(.el-select__placeholder) {
  color: var(--color-text-subtle);
}

.ai-model-select :deep(.el-select-dropdown) {
  background: linear-gradient(160deg, rgba(12, 20, 34, 0.98), rgba(8, 14, 26, 0.96));
  border-color: rgba(169, 196, 255, 0.16);
  color: var(--color-text);
}

.ai-model-select :deep(.el-select-dropdown__item) {
  color: var(--color-text);
}

.ai-model-select :deep(.el-select-dropdown__item:hover) {
  background: rgba(95, 135, 255, 0.12);
}

.award-card {
  min-height: 260px;
}

.award-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.award-metric-card {
  border: 1px solid rgba(138, 182, 255, 0.2);
  border-radius: 14px;
  padding: 12px;
  background: linear-gradient(165deg, rgba(20, 35, 60, 0.5), rgba(15, 25, 46, 0.35));
}

.award-metric-card span {
  display: block;
  color: #a9bde8;
  font-size: 13px;
}

.award-metric-card strong {
  display: block;
  margin-top: 4px;
  color: #f5f7ff;
  font-size: 26px;
  line-height: 1.1;
}

.award-metric-card em {
  display: block;
  margin-top: 6px;
  color: #9ab1da;
  font-size: 12px;
  font-style: normal;
}

.award-actions-row {
  display: flex;
  gap: 10px;
  margin-bottom: 12px;
}

.award-evidence-line {
  color: #b3c3e7;
  font-size: 13px;
  margin-bottom: 6px;
}

.award-evidence-line strong {
  color: #f0f3ff;
}

.observability-card {
  min-height: 220px;
}

.web-vitals-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.web-vital-item {
  border: 1px solid rgba(133, 166, 243, 0.18);
  border-radius: 12px;
  padding: 10px;
  background: rgba(12, 22, 40, 0.42);
}

.web-vital-item strong {
  display: block;
  color: #f0f3ff;
  font-size: 16px;
}

.web-vital-item span,
.web-vital-item em {
  display: block;
  color: #a9bbdf;
  font-size: 12px;
  font-style: normal;
  margin-top: 4px;
}

.compare-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}

.compare-item {
  border: 1px dashed rgba(150, 185, 255, 0.26);
  border-radius: 12px;
  padding: 10px;
  background: rgba(11, 19, 35, 0.42);
}

.compare-item span {
  color: #9fb1d6;
  font-size: 12px;
}

.compare-item strong {
  display: block;
  color: #f8faff;
  font-size: 20px;
  margin-top: 4px;
}

@media (max-width: 1100px) {
  .trace-grid {
    grid-template-columns: 1fr;
  }

  .trace-grid.trace-grid-admin {
    grid-template-columns: 1fr;
  }

  .trace-module-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .award-grid {
    grid-template-columns: 1fr;
  }

  .award-actions-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .web-vitals-grid,
  .compare-grid {
    grid-template-columns: 1fr;
  }
}

.workbench-home {
  display: grid;
  grid-template-columns: repeat(12, minmax(0, 1fr));
  grid-auto-rows: minmax(120px, auto);
  gap: 14px;
  position: relative;
  width: 100%;
  min-height: 100vh;
  min-height: 100dvh;
  max-width: none;
  margin: 0;
  background:
    radial-gradient(circle at 18% 22%, rgba(109, 149, 255, 0.28), transparent 28%),
    radial-gradient(circle at 80% 16%, rgba(217, 231, 255, 0.12), transparent 22%),
    linear-gradient(rgba(255, 255, 255, 0.025) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.025) 1px, transparent 1px),
    linear-gradient(165deg, #03060b 0%, #06101d 45%, #0a1628 100%);
  background-size: auto, auto, 72px 72px, 72px 72px, auto;
  border: 1px solid rgba(169, 196, 255, 0.12);
  border-radius: 0;
  padding: 14px 24px 14px 14px;
  overflow-x: hidden;
  overflow-y: auto;
  touch-action: auto;
}

.home-cinematic-pending.home-cinematic-running.home-cinematic-overlay-active .reveal-phase {
  opacity: 0;
  transform: translateY(18px) scale(0.985);
  filter: blur(8px);
  pointer-events: none;
}

.home-cinematic-pending.home-cinematic-running.home-cinematic-overlay-active .hero-scene {
  opacity: 0;
  transform: translateY(14px);
  filter: blur(8px);
}

.home-reveal-overlay {
  position: fixed;
  inset: 0;
  width: 100vw;
  height: 100dvh;
  z-index: 9999;
  overflow: hidden;
  background:
    radial-gradient(circle at 20% 18%, rgba(109, 149, 255, 0.24), transparent 30%),
    linear-gradient(165deg, #03060b 0%, #06101d 45%, #0a1628 100%);
}

.home-preloader-backdrop,
.home-preloader-panel {
  position: fixed;
  inset: 0;
  width: 100%;
  height: 100%;
  min-height: 100dvh;
  will-change: clip-path;
}

.home-preloader-backdrop {
  background:
    radial-gradient(circle at 10% 16%, rgba(122, 170, 255, 0.16), transparent 35%),
    radial-gradient(circle at 86% 12%, rgba(220, 232, 255, 0.14), transparent 30%),
    #eef3f7;
  color: #5f6e80;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  z-index: 0;
  padding: 1.25rem;
}

.home-pb-row {
  width: 100%;
  min-height: 3.6rem;
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 0.75rem;
}

.home-pb-col {
  border: 1px dashed rgba(73, 119, 151, 0.34);
  border-radius: 10px;
  padding: 0.65rem 0.75rem;
  display: flex;
  align-items: flex-end;
}

.home-pb-row-meta .home-pb-col p {
  margin: 0;
  text-transform: uppercase;
  font-family: 'Geist Mono', ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.72rem;
  font-weight: 600;
  line-height: 1;
  letter-spacing: 0.08em;
}

.home-preloader-panel {
  background:
    radial-gradient(circle at 16% 18%, rgba(109, 149, 255, 0.18), transparent 44%),
    linear-gradient(160deg, #040913, #071021 38%, #0a1628 100%);
  color: #f8fbff;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  z-index: 2;
  padding: 1.25rem;
}

.home-p-row {
  width: 100%;
  display: flex;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 0.8rem 1.2rem;
}

.home-p-row-top {
  align-items: flex-start;
}

.home-p-row-bottom {
  align-items: flex-end;
}

.home-p-row p {
  margin: 0;
  overflow: hidden;
  text-transform: uppercase;
  font-family: 'Geist Mono', ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  font-size: 0.72rem;
  font-weight: 600;
  line-height: 1;
  letter-spacing: 0.08em;
}

.home-preloader-line {
  display: inline-block;
  transform: translateY(100%);
  will-change: transform;
}

.home-reveal-core {
  position: fixed;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: min(76vw, 20rem);
  aspect-ratio: 1;
  display: grid;
  place-items: center;
}

.home-pbc-svg-strokes,
.home-pbc-svg-strokes svg,
.home-reveal-logo-btn {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
}

.home-pbc-svg-strokes {
  width: 100%;
  height: 100%;
}

.home-pbc-svg-strokes svg {
  width: 100%;
  height: 100%;
  will-change: transform;
}

.home-stroke-track {
  stroke: rgba(120, 171, 208, 0.42);
  stroke-linecap: round;
  stroke-linejoin: round;
  vector-effect: non-scaling-stroke;
  shape-rendering: geometricPrecision;
}

.home-stroke-progress {
  stroke: rgba(233, 244, 255, 0.96);
  stroke-linecap: round;
  stroke-linejoin: round;
  vector-effect: non-scaling-stroke;
  shape-rendering: geometricPrecision;
}

.home-reveal-logo-btn {
  width: clamp(112px, 17vw, 164px);
  height: clamp(112px, 17vw, 164px);
  border: none;
  border-radius: 50%;
  background:
    radial-gradient(circle at 32% 28%, rgba(232, 242, 255, 0.36), rgba(95, 135, 255, 0.26) 42%, rgba(9, 18, 36, 0.96) 100%);
  box-shadow: 0 20px 44px rgba(4, 10, 24, 0.58), inset 0 0 0 1px rgba(190, 212, 255, 0.36);
  display: grid;
  place-items: center;
  cursor: pointer;
  color: #f6f8fc;
  z-index: 6;
  pointer-events: auto;
  transition: transform 0.2s ease, box-shadow 0.2s ease, filter 0.2s ease;
}

.home-reveal-logo-btn::before {
  content: '';
  position: absolute;
  inset: -10px;
  border-radius: 50%;
  border: 1px solid rgba(211, 233, 255, 0.34);
  opacity: 0.34;
  transform: scale(1);
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.home-reveal-logo-btn:hover {
  transform: translate(-50%, -50%) scale(1.045);
  box-shadow: 0 24px 56px rgba(4, 10, 24, 0.7), inset 0 0 0 1px rgba(217, 233, 255, 0.54);
  filter: brightness(1.06);
}

.home-reveal-logo-btn:hover::before {
  opacity: 0.72;
  transform: scale(1.08);
}

.home-reveal-logo-btn:active {
  transform: translate(-50%, -50%) scale(0.97);
}

.home-reveal-logo-btn:focus-visible {
  outline: 2px solid rgba(217, 234, 255, 0.86);
  outline-offset: 6px;
}

.home-reveal-logo {
  width: 50%;
  height: 50%;
  object-fit: contain;
  filter: drop-shadow(0 8px 20px rgba(76, 137, 255, 0.42));
}


.home-reveal-fade-enter-active,
.home-reveal-fade-leave-active {
  transition: opacity 0.22s ease;
}

.home-reveal-fade-enter-from,
.home-reveal-fade-leave-to {
  opacity: 0;
}

.home-reveal-fade-leave-active {
  pointer-events: none;
}

@media (max-width: 1000px) {
  .home-pb-row .home-pb-col:nth-child(1),
  .home-pb-row .home-pb-col:nth-child(2),
  .home-pb-row .home-pb-col:nth-child(5) {
    display: none;
  }

  .home-pb-row {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

}

.workbench-home.motion-tier-low * {
  will-change: auto !important;
}

.workbench-home.reduce-motion * {
  animation-duration: 0.01ms !important;
  animation-iteration-count: 1 !important;
  transition-duration: 0.01ms !important;
  scroll-behavior: auto !important;
}

.workbench-home::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    radial-gradient(circle at center, rgba(152, 188, 255, 0.18) 0.7px, transparent 0.7px);
  background-size: 4px 4px;
  opacity: 0.04;
  animation: homeGridShift 12s linear infinite;
  pointer-events: none;
}

.workbench-home > * {
  position: relative;
  z-index: 1;
}

.workbench-home.immersive-war-room > :not(.adversarial-floating-wrap) {
  filter: blur(2px) brightness(0.42) saturate(0.72);
  transform: scale(0.992);
  pointer-events: none;
  user-select: none;
  transition: filter 0.28s ease, transform 0.28s ease;
}

.workbench-home.immersive-war-room .adversarial-floating-wrap {
  right: 0;
  bottom: 0;
}

.trace-grid {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.trace-grid.trace-grid-admin {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.trace-card {
  min-height: 168px;
}

.trace-context-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 6px;
}

.trace-context-row span {
  padding: 6px 10px;
  border-radius: 10px;
  border: 1px solid rgba(118, 210, 255, 0.24);
  background: rgba(10, 28, 43, 0.46);
  color: #d3efff;
  font-size: 12px;
}

.trace-note {
  margin-top: 10px;
  color: #98bdd6;
  font-size: 13px;
  line-height: 1.6;
}

.trace-modules-card {
  grid-column: 1 / -1;
}

.trace-module-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}

.trace-module-item {
  text-align: left;
  border: 1px solid rgba(111, 208, 255, 0.24);
  background: linear-gradient(145deg, rgba(8, 24, 39, 0.76), rgba(9, 18, 30, 0.72));
  border-radius: 12px;
  padding: 10px;
  color: #dbe7ff;
  cursor: pointer;
}

.trace-module-item:hover {
  border-color: rgba(146, 228, 255, 0.52);
}

.trace-module-title {
  font-size: 14px;
  font-weight: 700;
}

.trace-module-meta {
  margin-top: 4px;
  color: #a2c7df;
  font-size: 12px;
}

.trace-module-count {
  margin-top: 8px;
  color: #f5f8ff;
  font-size: 18px;
  font-weight: 700;
}

.trace-dialog-head {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 10px;
}

.trace-dialog-head span {
  padding: 6px 10px;
  border: 1px solid rgba(140, 172, 239, 0.2);
  border-radius: 10px;
  background: rgba(11, 19, 35, 0.45);
  color: #dbe7ff;
  font-size: 12px;
}

.hub-detail-head {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 10px;
}

.hub-detail-head span {
  padding: 6px 10px;
  border: 1px solid rgba(140, 172, 239, 0.2);
  border-radius: 10px;
  background: rgba(11, 19, 35, 0.45);
  color: #dbe7ff;
  font-size: 12px;
}

.hub-detail-desc {
  margin: 0 0 10px;
  color: #a9bfdf;
  font-size: 12px;
}

.adversarial-compare-grid {
  margin-top: 12px;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.adversarial-compare-grid article {
  border: 1px solid rgba(140, 172, 239, 0.22);
  background: rgba(11, 19, 35, 0.45);
  border-radius: 10px;
  padding: 8px 10px;
}

.adversarial-compare-grid span {
  color: #9cb1d4;
  font-size: 12px;
}

.adversarial-compare-grid strong {
  display: block;
  margin-top: 5px;
  color: #ecf3ff;
  font-size: 13px;
}

.trace-record-list {
  display: grid;
  gap: 8px;
  max-height: none;
  overflow: visible;
}

.trace-record-item {
  border: 1px solid rgba(140, 172, 239, 0.2);
  border-radius: 10px;
  background: rgba(11, 19, 35, 0.45);
  padding: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 8px 14px;
  color: #d5e2fd;
  font-size: 12px;
}

.trace-record-item strong {
  color: #f5f8ff;
}

.verify-badge {
  font-size: 12px;
  border: 1px solid rgba(140, 172, 239, 0.2);
  border-radius: 999px;
  padding: 6px 10px;
}

.verify-badge.ok {
  color: #9ef2c4;
  border-color: rgba(46, 204, 113, 0.45);
  background: rgba(46, 204, 113, 0.1);
}

.verify-badge.warn {
  color: #ffd7a4;
  border-color: rgba(255, 180, 84, 0.45);
  background: rgba(255, 180, 84, 0.1);
}

.hero-scene {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 14px;
  min-height: clamp(260px, 39vh, 430px);
  padding: clamp(12px, 1.5vw, 18px);
  position: relative;
  overflow: visible;
  background:
    radial-gradient(circle at 12% 18%, rgba(116, 176, 255, 0.16), transparent 38%),
    linear-gradient(160deg, rgba(9, 16, 30, 0.96), rgba(8, 14, 24, 0.94));
  border: 1px solid rgba(169, 196, 255, 0.11);
  box-shadow: none;
}



.magazine-masthead .hero-stage {
  grid-template-columns: minmax(0, 1.36fr) minmax(380px, 0.64fr);
  align-items: start;
  gap: 16px;
}

.magazine-masthead .hero-copy {
  align-items: flex-start;
  text-align: left;
}

.magazine-masthead .hero-headline {
  font-size: clamp(84px, 12.4vw, 206px);
  font-weight: 600;
  line-height: 0.8;
  letter-spacing: 0.002em;
  margin: 6px 0 0;
  font-family: 'Sora', 'Space Grotesk', 'Segoe UI Variable Display', 'Bahnschrift', 'Microsoft YaHei UI', sans-serif;
  color: #f6f7fb;
  text-transform: uppercase;
  text-shadow: 0 8px 18px rgba(6, 11, 24, 0.36);
}

.magazine-masthead .hero-title-primary {
  gap: 0;
}

.hero-title-line {
  display: flex;
  width: 100%;
  justify-content: flex-start;
  position: relative;
}

.hero-title-line-aegis {
  overflow: visible;
}

.hero-title-line-workbench {
  margin-top: 4px;
}

.hero-char-first-aegis {
  position: relative;
  overflow: visible;
  z-index: 5;
}

.hero-planet-core {
  position: absolute;
  top: 0.12em;
  left: calc(100% + 0.06em);
  width: 0.3em;
  height: 0.3em;
  border-radius: 50%;
  pointer-events: none;
  z-index: 6;
  background:
    radial-gradient(circle at 28% 24%, rgba(237, 249, 255, 0.98), rgba(183, 221, 255, 0.9) 26%, rgba(107, 161, 225, 0.92) 62%, rgba(66, 117, 187, 0.9));
  box-shadow:
    0 0 0.07em rgba(209, 236, 255, 0.8),
    0 0 0.22em rgba(76, 140, 220, 0.45);
  transform: translateX(0.02em);
}

.hero-planet-core::before,
.hero-planet-core::after {
  content: '';
  position: absolute;
  pointer-events: none;
}

.hero-planet-core::before {
  inset: -16% -42%;
  border-radius: 50%;
  border: 1px solid rgba(189, 225, 255, 0.72);
  transform: rotate(-20deg);
  opacity: 0.88;
  animation: planet-ring-drift 9s linear infinite;
}

.hero-planet-core::after {
  width: 0.08em;
  height: 0.08em;
  border-radius: 50%;
  right: -0.03em;
  top: 0.03em;
  background: rgba(246, 252, 255, 0.92);
  box-shadow: 0 0 0.06em rgba(194, 232, 255, 0.9);
}

.hero-title-line-workbench {
  isolation: isolate;
}

.hero-bench-orbit-net {
  position: absolute;
  left: -0.08em;
  right: -0.2em;
  top: -0.15em;
  bottom: -0.18em;
  pointer-events: none;
  z-index: 1;
  opacity: 0.78;
  background:
    radial-gradient(circle at 18% 54%, rgba(196, 231, 255, 0.9) 0, rgba(196, 231, 255, 0.9) 0.018em, transparent 0.028em),
    radial-gradient(circle at 51% 28%, rgba(166, 214, 255, 0.82) 0, rgba(166, 214, 255, 0.82) 0.016em, transparent 0.026em),
    radial-gradient(circle at 84% 62%, rgba(228, 244, 255, 0.86) 0, rgba(228, 244, 255, 0.86) 0.018em, transparent 0.03em);
}

.hero-bench-orbit-net::before,
.hero-bench-orbit-net::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 50%;
  pointer-events: none;
}

.hero-bench-orbit-net::before {
  border: 1px solid rgba(128, 184, 242, 0.34);
  transform: rotate(-8deg) scaleY(0.62);
  animation: orbit-net-spin 14s linear infinite;
}

.hero-bench-orbit-net::after {
  inset: 10% 4%;
  border: 1px solid rgba(170, 214, 255, 0.28);
  transform: rotate(8deg) scaleY(0.58);
  animation: orbit-net-spin-reverse 20s linear infinite;
}

.hero-title-line-workbench .hero-char-mask {
  position: relative;
  z-index: 2;
}

@keyframes planet-ring-drift {
  from {
    transform: rotate(-20deg);
  }
  to {
    transform: rotate(340deg);
  }
}

@keyframes orbit-net-spin {
  from {
    transform: rotate(-8deg) scaleY(0.62);
  }
  to {
    transform: rotate(352deg) scaleY(0.62);
  }
}

@keyframes orbit-net-spin-reverse {
  from {
    transform: rotate(8deg) scaleY(0.58);
  }
  to {
    transform: rotate(-352deg) scaleY(0.58);
  }
}

@media (prefers-reduced-motion: reduce) {
  .hero-planet-core::before,
  .hero-bench-orbit-net::before,
  .hero-bench-orbit-net::after {
    animation: none;
  }
}

.hero-side-meta {
  display: grid;
  grid-template-rows: auto auto;
  gap: 14px;
  align-content: start;
}

.hero-side-pane {
  border: 1px solid rgba(255, 255, 255, 0.16);
  border-radius: 16px;
  background: linear-gradient(150deg, rgba(14, 24, 42, 0.58), rgba(10, 18, 32, 0.38));
  box-shadow: none;
  padding: 16px 18px;
  display: grid;
  gap: 10px;
  align-content: start;
}

.hero-side-meta-lines {
  padding: 8px 10px;
  border-radius: 12px;
  border: 1px solid rgba(154, 202, 255, 0.22);
  background: rgba(8, 18, 31, 0.5);
}

.hero-side-subline {
  margin: 0;
  color: #d7e8fb;
  font-size: 16px;
  line-height: 1.78;
}

.hero-pillar-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 2px;
}

.hero-pillar-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-height: 34px;
  padding: 0 16px;
  border-radius: 999px;
  border: 1px solid rgba(142, 206, 255, 0.28);
  background: linear-gradient(140deg, rgba(109, 149, 255, 0.2), rgba(70, 208, 255, 0.12));
  color: #e6f2ff;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.05em;
  white-space: nowrap;
}

.side-scene-tags {
  margin-top: 10px;
}

.hero-side-block {
  display: grid;
  gap: 8px;
}

.hero-side-label {
  font-size: 11px;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: rgba(208, 216, 232, 0.78);
  font-family: 'Geist Mono', ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}

.hero-side-value {
  font-size: 16px;
  color: #f7f9ff;
  font-weight: 700;
  line-height: 1.45;
}

.hero-side-block p {
  margin: 0;
  font-size: 13px;
  color: #c4d0e5;
  font-family: 'Geist Mono', ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
  line-height: 1.6;
}

.hero-quick-row {
  display: none;
}

.workbench-home :deep(.card-glass) {
  background: linear-gradient(160deg, rgba(11, 18, 32, 0.84), rgba(8, 14, 24, 0.78));
  border: 1px solid rgba(169, 196, 255, 0.1);
  backdrop-filter: blur(5px);
  box-shadow: none;
}

.workbench-home :deep(.card-glass:hover) {
  transform: none;
  box-shadow: none;
}




.hero-headline-wrap {
  position: relative;
  width: 100%;
  max-width: 1200px;
  display: flex;
  justify-content: center;
  padding: 26px 10px;
}

.hero-title-rings {
  position: absolute;
  top: 50%;
  left: calc(50% - 260px);
  width: min(1400px, calc(100% + 220px));
  height: clamp(240px, 34vw, 380px);
  transform: translate(-50%, -50%);
  pointer-events: none;
  opacity: 0.96;
  z-index: 0;
  mix-blend-mode: screen;
}

.hero-scene::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: linear-gradient(rgba(144, 223, 255, 0.08) 1px, transparent 1px), linear-gradient(90deg, rgba(104, 194, 255, 0.08) 1px, transparent 1px);
  background-size: 72px 72px;
  opacity: 0.22;
  mask-image: linear-gradient(180deg, rgba(0,0,0,0.88), transparent 100%);
}

.hero-stage {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 0;
  align-items: stretch;
}

.hero-copy,
.hero-quick-row {
  position: relative;
  z-index: 1;
}

.hero-copy {
  text-align: left;
  display: grid;
  justify-items: start;
  align-content: start;
  min-height: 100%;
}

.hero-quick-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-start;
}

.hero-quick-row span {
  padding: 7px 12px;
  border-radius: 999px;
  border: 1px solid rgba(121, 207, 255, 0.24);
  background: rgba(8, 25, 40, 0.5);
  color: #d4f2ff;
  font-size: 12px;
}

.eyebrow {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  border-radius: 999px;
  border: 1px solid rgba(122, 214, 255, 0.3);
  background: rgba(12, 35, 54, 0.76);
  color: #d8f2ff;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.16em;
}

.hero-headline {
  position: relative;
  z-index: 1;
  margin: 18px 0 14px;
  width: fit-content;
  max-width: 100%;
  font-size: clamp(36px, 4vw, 52px);
  line-height: 1.04;
  letter-spacing: -0.04em;
  color: #d9eaff;
  display: inline-flex;
  flex-direction: column;
  align-items: flex-start;
  justify-content: flex-start;
  gap: 0;
}

.hero-title-primary {
  display: inline-flex;
  flex-wrap: nowrap;
  gap: 0;
  color: #f6f8fe;
}

.hero-title-suffix {
  display: inline-flex;
  flex-wrap: wrap;
  gap: 0.01em;
}

.hero-char {
  display: inline-block;
  will-change: transform, opacity, filter;
  transform: scaleY(1.04);
  transform-origin: 50% 100%;
  font-weight: 700;
  letter-spacing: 0.002em;
}

.hero-char-mask {
  display: inline-flex;
  overflow: hidden;
  line-height: 0.94;
}

.hero-char-mask.hero-char-first-aegis {
  overflow: visible;
}

.hero-char-primary .hero-char {
  color: #eaf2ff;
  background: linear-gradient(180deg, #f4f8ff 0%, #d8e7ff 52%, #9fbeee 100%);
  -webkit-background-clip: text;
  background-clip: text;
  -webkit-text-fill-color: transparent;
  -webkit-text-stroke: 0.45px rgba(118, 157, 214, 0.34);
  text-shadow:
    0 8px 16px rgba(6, 16, 36, 0.4),
    0 0 10px rgba(78, 132, 212, 0.16);
  filter: drop-shadow(0 0 5px rgba(74, 132, 216, 0.14));
  position: relative;
}

.hero-copy p {
  max-width: 720px;
  margin: 0;
  color: #a8cfe4;
  font-size: 16px;
  line-height: 1.8;
}

.scene-tags {
  background: rgba(122, 166, 255, 0.12);
  border: 1px solid rgba(132, 182, 255, 0.22);
  color: #dce9ff;
  margin-top: 22px;
}

.scene-tag {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(121, 212, 255, 0.12);
  border: 1px solid rgba(129, 219, 255, 0.22);
  color: #dcf4ff;
  font-size: 12px;
}

.operator-ribbon {
  margin-top: 4px;
  padding: 18px 20px;
  width: 100%;
  border-radius: 22px;
  border: 1px solid rgba(126, 174, 255, 0.24);
  background: linear-gradient(135deg, rgba(120, 166, 255, 0.16), rgba(199, 221, 255, 0.08));
  box-shadow: inset 0 1px 0 rgba(212, 229, 255, 0.1);
  text-align: left;
}

.operator-label {
  font-size: 11px;
  letter-spacing: 0.14em;
  color: #8fb4cb;
  text-transform: uppercase;
}

.operator-name {
  margin-top: 8px;
  font-size: 28px;
  font-weight: 700;
  color: #ffffff;
}

.operator-meta {
  margin-top: 4px;
  color: #9dc0d3;
}

.stat-grid {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px;
}

.performance-verification-card {
  grid-column: 1 / -1;
  padding: 24px;
  border: 1px solid rgba(118, 164, 255, 0.16);
  background:
    radial-gradient(circle at top right, rgba(89, 133, 255, 0.18), transparent 44%),
    linear-gradient(180deg, rgba(8, 14, 26, 0.96) 0%, rgba(11, 19, 35, 0.96) 100%);
  box-shadow: 0 24px 60px rgba(3, 8, 16, 0.42), inset 0 1px 0 rgba(255, 255, 255, 0.03);
}

.performance-verification-head {
  align-items: center;
}

.performance-verification-badge {
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(136, 176, 255, 0.2);
  background: rgba(84, 124, 255, 0.08);
  color: #dbe7ff;
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.performance-verification-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-top: 18px;
}

.performance-verification-metric {
  min-height: 172px;
  padding: 20px 18px;
  border-radius: 24px;
  border: 1px solid rgba(123, 163, 255, 0.18);
  background: linear-gradient(180deg, rgba(15, 25, 44, 0.92) 0%, rgba(10, 18, 31, 0.96) 100%);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.03), 0 18px 40px rgba(2, 7, 15, 0.26);
}

.performance-verification-metric-label {
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #8fb4cb;
}

.performance-verification-metric-value-row {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin-top: 16px;
}

.performance-verification-metric-value {
  font-size: clamp(36px, 4.8vw, 64px);
  line-height: 0.92;
  font-weight: 800;
  letter-spacing: -0.04em;
  color: #f5fbff;
}

.performance-verification-metric-suffix {
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: #8ab5ff;
}

.performance-verification-metric-note {
  margin-top: 14px;
  color: #9ab1c7;
  font-size: 14px;
  line-height: 1.7;
}

.performance-verification-footer {
  margin: 16px 2px 0;
  color: #90a8bf;
  font-size: 13px;
  line-height: 1.6;
}

.pulse-grid {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 0.8fr);
  gap: 20px;
}

.pulse-card,
.pulse-signal-card {
  padding: 22px;
}

.pulse-chip {
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(118, 164, 255, 0.2);
  background: rgba(87, 127, 255, 0.08);
  color: #dce7ff;
  font-size: 12px;
}

.pulse-layout {
  display: grid;
  grid-template-columns: 180px 1fr;
  gap: 22px;
  align-items: center;
}

.pulse-score-ring {
  width: 180px;
  height: 180px;
  border-radius: 50%;
  display: grid;
  place-items: center;
  align-content: center;
  border: 1px solid rgba(118, 164, 255, 0.16);
  background:
    radial-gradient(circle at center, rgba(7, 12, 21, 0.86) 0 54%, transparent 55%),
    conic-gradient(from 180deg, #edf4ff, #85abff, #466de0, #edf4ff);
  box-shadow: inset 0 0 18px rgba(255,255,255,0.03), 0 10px 22px rgba(52, 93, 210, 0.1);
}

.pulse-score {
  font-size: 58px;
  font-weight: 800;
  line-height: 1;
  color: #f7fbff;
}

.pulse-score-ring em {
  margin-top: 6px;
  color: #98a9c8;
  font-style: normal;
}

.pulse-copy strong {
  color: #f7fbff;
  font-size: 20px;
  line-height: 1.55;
}

.pulse-dimensions {
  display: grid;
  gap: 14px;
  margin-top: 18px;
}

.pulse-dimension {
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(122, 214, 255, 0.16);
  background: rgba(98, 204, 255, 0.06);
}

.pulse-dimension-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.pulse-dimension-head span {
  color: #dfe8f7;
  font-weight: 700;
}

.pulse-dimension-head strong {
  font-size: 18px;
}

.pulse-bar {
  height: 8px;
  margin-top: 10px;
  border-radius: 999px;
  background: rgba(255,255,255,0.05);
  overflow: hidden;
}

.pulse-bar i {
  display: block;
  height: auto;
  min-height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #f1f6ff, #85adff 36%, #4469db 100%);
}

.pulse-dimension p,
.pulse-signal-item p {
  margin: 10px 0 0;
  color: #90a0b8;
  line-height: 1.7;
}

.pulse-signal-list {
  display: grid;
  gap: 12px;
}

.pulse-signal-item {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid rgba(122, 214, 255, 0.16);
  background: rgba(98, 204, 255, 0.06);
}

.pulse-signal-item.pulse-signal-new {
  animation: signalBlink 1s ease-in-out;
}

.pulse-signal-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.pulse-signal-top strong {
  color: #f6faff;
}

.pulse-tone {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.pulse-tone.danger {
  background: rgba(255, 107, 107, 0.12);
  color: #ffd8d8;
}

.pulse-tone.warning {
  background: rgba(118, 164, 255, 0.12);
  color: #dce8ff;
}

@keyframes signalBlink {
  0% {
    border-color: rgba(255, 128, 128, 0.2);
    box-shadow: 0 0 0 rgba(255, 128, 128, 0);
  }
  45% {
    border-color: rgba(255, 126, 126, 0.75);
    box-shadow: 0 0 16px rgba(255, 126, 126, 0.36);
  }
  100% {
    border-color: rgba(255,255,255,0.06);
    box-shadow: 0 0 0 rgba(255, 128, 128, 0);
  }
}

@keyframes homeGridShift {
  from {
    transform: translate3d(0, 0, 0);
  }
  to {
    transform: translate3d(42px, 42px, 0);
  }
}

.pulse-tone.safe {
  background: rgba(105, 169, 255, 0.12);
  color: #dcefff;
}

.chart-card,
.module-entry-card {
  padding: 22px;
}

.trend-card {
  grid-column: 1 / -1;
}

.risk-card {
  grid-column: 1 / span 6;
}

.module-entry-card {
  grid-column: 7 / span 6;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.panel-subtitle {
  margin: 8px 0 0;
  color: #96bad0;
  line-height: 1.7;
}

.panel-badge {
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(255, 191, 115, 0.32);
  background: rgba(255, 167, 71, 0.12);
  color: #ffe2bf;
  font-size: 12px;
  font-weight: 700;
}

.panel-actions {
  display: flex;
  align-items: center;
  gap: 10px;
}

.mini-refresh-btn {
  padding: 8px 12px;
  border-radius: 999px;
  border: 1px solid rgba(124, 214, 255, 0.36);
  background: rgba(89, 199, 255, 0.14);
  color: #d8f3ff;
  font-size: 12px;
  cursor: pointer;
}

.mini-refresh-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.chart-canvas {
  width: 100%;
}

.trend-canvas {
  height: 360px;
}

.risk-layout {
  display: grid;
  grid-template-columns: minmax(220px, 0.9fr) minmax(0, 1fr);
  align-items: center;
  gap: 8px;
}

.risk-canvas {
  height: 290px;
}

.risk-list {
  display: grid;
  gap: 12px;
}

.risk-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(97, 202, 255, 0.06);
  border: 1px solid rgba(125, 216, 255, 0.16);
}

.risk-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
}

.risk-dot.danger {
  background: #ff6b6b;
}

.risk-dot.warning {
  background: #ffb454;
}

.risk-dot.safe {
  background: #2ecc71;
}

.risk-dot.neutral {
  background: #7f8aa3;
}

.risk-copy {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.risk-copy strong,
.todo-copy strong {
  color: #f6f8fe;
}

.risk-copy span,
.todo-copy p {
  color: #90a0b8;
}

.module-entry-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.module-entry-item {
  display: grid;
  grid-template-columns: auto 1fr auto;
  gap: 16px;
  align-items: center;
  width: 100%;
  padding: 16px 18px;
  text-align: left;
  border: 1px solid rgba(129, 219, 255, 0.2);
  border-radius: 18px;
  background: linear-gradient(145deg, rgba(10, 28, 45, 0.72), rgba(8, 20, 32, 0.72));
  transition: border-color 0.15s ease, background 0.15s ease;
}

.module-entry-item:hover {
  border-color: rgba(144, 229, 255, 0.48);
  background: linear-gradient(145deg, rgba(12, 35, 55, 0.82), rgba(9, 24, 40, 0.82));
}

.module-entry-tag {
  min-width: 48px;
  padding: 8px 10px;
  border-radius: 12px;
  background: rgba(122, 166, 255, 0.18);
  color: #e1ecff;
  font-size: 12px;
  font-weight: 700;
  text-align: center;
}

.module-entry-copy p {
  margin: 6px 0 0;
  line-height: 1.6;
}

.module-entry-metric {
  color: #daf2ff;
  font-size: 13px;
  font-weight: 700;
}

.module-entry-copy strong {
  color: #f6f8fe;
}

.module-entry-copy p {
  color: #98bdd1;
}

@media (max-width: 1280px) {
  .workbench-home {
    grid-template-columns: repeat(8, minmax(0, 1fr));
    gap: 12px;
  }

  .magazine-masthead .hero-stage {
    grid-template-columns: 1fr;
  }

  .pulse-grid,
  .risk-layout,

  .pulse-layout {
    grid-template-columns: 1fr;
  }

  .hero-stage {
    grid-template-columns: 1fr;
  }

  .hero-copy p {
    max-width: 100%;
  }

  .stat-grid {
    grid-template-columns: 1fr;
  }

  .performance-verification-grid {
    grid-template-columns: 1fr;
  }

  .trend-card {
    grid-column: span 1;
  }

  .risk-card,
  .module-entry-card {
    grid-column: 1 / -1;
  }
}

@media (max-width: 768px) {
  .workbench-home {
    grid-template-columns: 1fr;
    width: 100%;
    margin: 0;
    padding: 10px;
  }

  .hero-scene {
    min-height: calc(100dvh - 20px);
  }

  .hero-scene,
  .chart-card,
  .module-entry-card {
    padding: 18px;
  }

  .module-entry-grid {
    grid-template-columns: 1fr;
  }

  .module-entry-item {
    grid-template-columns: 1fr;
  }

  .module-entry-metric {
    text-align: left;
  }

}

.workbench-home.motion-tier-low .hero-title-rings,
.workbench-home.reduce-motion .hero-title-rings {
  opacity: 0.45;
}

.workbench-home.reduce-motion::before {
  display: none;
}

.ai-workbench-card {
  grid-column: 1 / -1;
}

.ps-status-badge {
  padding: 6px 16px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
  white-space: nowrap;
  flex-shrink: 0;
}
.ps-status-badge.badge-on {
  background: rgba(16, 185, 129, 0.18);
  color: #34d399;
  border: 1px solid rgba(52, 211, 153, 0.3);
}
.ps-status-badge.badge-off {
  background: rgba(100, 116, 139, 0.14);
  color: #94a3b8;
  border: 1px solid rgba(100, 116, 139, 0.2);
}

.ai-input-row {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  margin-top: 14px;
}

.ai-config-row {
  display: grid;
  grid-template-columns: minmax(220px, 0.85fr) minmax(0, 1.15fr);
  gap: 12px;
  margin-top: 12px;
}

.ai-meta-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.ai-meta-pill {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.03em;
}

.ai-meta-pill.state-idle,
.ai-meta-pill.state-loading {
  color: #c0d4f8;
  background: rgba(73, 126, 233, 0.18);
  border: 1px solid rgba(106, 166, 255, 0.25);
}

.ai-meta-pill.state-ready,
.ai-meta-pill.state-selected {
  color: #c5f8e8;
  background: rgba(16, 185, 129, 0.14);
  border: 1px solid rgba(52, 211, 153, 0.25);
}

.ai-meta-pill.state-empty,
.ai-meta-pill.state-error {
  color: #ffd0d0;
  background: rgba(220, 38, 38, 0.14);
  border: 1px solid rgba(248, 113, 113, 0.24);
}

.ai-model-notice {
  margin: 8px 0 0;
  font-size: 12px;
  color: #fca5a5;
  line-height: 1.5;
}

.security-surface {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
  margin-top: 12px;
}

.security-card {
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.09);
  background: rgba(8, 17, 34, 0.56);
  padding: 12px;
}

.security-card-title {
  color: #dce8ff;
  font-size: 12px;
  font-weight: 700;
  margin-bottom: 8px;
}

.security-item-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
}

.security-item {
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.03);
  padding: 8px 10px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.security-item.security-item-wide {
  grid-column: 1 / -1;
}

.security-item span {
  color: #8ea1c4;
  font-size: 11px;
}

.security-item strong {
  color: #f6f8ff;
  font-size: 12px;
}

.security-origin-row {
  margin-top: 8px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.origin-chip {
  border-radius: 999px;
  border: 1px solid rgba(99, 179, 237, 0.25);
  background: rgba(59, 130, 246, 0.13);
  color: #b6d4ff;
  font-size: 11px;
  padding: 3px 8px;
}

.origin-chip.muted {
  border-color: rgba(148, 163, 184, 0.25);
  background: rgba(100, 116, 139, 0.18);
  color: #c0cad7;
}

.stack-placeholder {
  font-size: 12px;
  color: #8ea1c4;
  line-height: 1.6;
}

.stack-list {
  display: grid;
  gap: 8px;
}

.stack-item {
  border-radius: 10px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.03);
  padding: 8px 10px;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 8px;
  align-items: center;
}

.stack-item strong {
  color: #f5f8ff;
  font-size: 12px;
}

.stack-item span {
  color: #6ee7b7;
  font-size: 11px;
}

.stack-item em {
  color: #fcd34d;
  font-size: 11px;
  font-style: normal;
}

.ai-model-select,
.ai-reason-input {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  color: #e2e8f0;
  font-size: 13px;
  padding: 10px 12px;
  font-family: inherit;
  unicode-bidi: plaintext;
  text-rendering: optimizeLegibility;
}

.ai-model-select:focus,
.ai-reason-input:focus {
  outline: none;
  border-color: rgba(99, 179, 237, 0.5);
}

.ai-model-select option {
  color: #111827;
  background-color: #ffffff;
  font-family: inherit;
  font-size: 14px;
  padding: 8px 12px;
}

.ai-draft-input {
  flex: 1;
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 10px;
  color: #e2e8f0;
  font-size: 13px;
  padding: 10px 14px;
  resize: none;
  font-family: inherit;
  line-height: 1.55;
  transition: border-color 0.2s;
}
.ai-draft-input:focus {
  outline: none;
  border-color: rgba(99, 179, 237, 0.5);
}
.ai-draft-input::placeholder { color: rgba(255,255,255,0.28); }

.ai-send-btn {
  padding: 10px 22px;
  border-radius: 10px;
  border: none;
  background: linear-gradient(135deg, #3b82f6, #1d4ed8);
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  cursor: pointer;
  transition: opacity 0.15s, background 0.2s;
  white-space: nowrap;
}
.ai-send-btn:hover:not(:disabled) { opacity: 0.88; }
.ai-send-btn:disabled {
  background: linear-gradient(135deg, #7f1d1d, #991b1b);
  cursor: not-allowed;
}

.ai-block-notice {
  margin: 10px 0 0;
  font-size: 12px;
  color: #f87171;
  font-weight: 600;
  line-height: 1.5;
}
.ai-safe-notice {
  margin: 10px 0 0;
  font-size: 12px;
  color: #34d399;
  font-weight: 600;
}

.ai-response-panel {
  margin-top: 12px;
  border: 1px solid rgba(255, 255, 255, 0.09);
  border-radius: 12px;
  background: rgba(10, 20, 38, 0.65);
  padding: 12px;
}

.ai-response-title {
  font-size: 12px;
  font-weight: 700;
  color: #bfd7ff;
  margin-bottom: 8px;
}

.ai-response-content {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
  color: #d9e8ff;
  font-size: 12px;
  line-height: 1.55;
}

.adversarial-floating-wrap {
  position: fixed;
  right: 24px;
  bottom: 36px;
  z-index: 1100;
  display: none !important;
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
  margin-top: 0;
  padding: 18px;
  border-radius: 18px;
  border: 1px solid rgba(110, 162, 255, 0.24);
  background:
    radial-gradient(circle at 18% 16%, rgba(64, 121, 255, 0.16), transparent 34%),
    radial-gradient(circle at 82% 14%, rgba(63, 218, 196, 0.1), transparent 28%),
    linear-gradient(180deg, rgba(7, 14, 28, 0.96), rgba(9, 17, 33, 0.94));
  max-height: min(90vh, 1040px);
  overflow-x: hidden;
  overflow-y: hidden;
  display: flex;
  flex-direction: column;
}

.adversarial-panel.adversarial-panel-max {
  right: 0.8vw;
  bottom: 0.8vh;
  width: min(1760px, 99vw);
  height: min(96vh, 1160px);
  max-height: min(96vh, 1160px);
}

.adversarial-layout {
  margin-top: 12px;
  display: grid;
  grid-template-columns: minmax(0, 2.25fr) minmax(380px, 1fr);
  gap: 18px;
  flex: 1 1 auto;
  min-height: 0;
}

.adversarial-stage-column,
.adversarial-feed-column {
  display: grid;
  align-content: stretch;
  gap: 14px;
  min-height: 0;
}

.adversarial-stage-column {
  grid-template-rows: minmax(0, 1fr) auto;
}

.adversarial-feed-column {
  overflow-y: visible;
  overflow-x: hidden;
  padding-right: 0;
}

.adversarial-panel .adversarial-feed-column {
  overflow-y: visible !important;
  max-height: none !important;
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

.adversarial-close:hover {
  border-color: rgba(170, 214, 255, 0.46);
  background: rgba(57, 105, 184, 0.2);
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

.adversarial-scene-desc {
  margin-top: 10px;
  display: grid;
  gap: 4px;
  color: #a7b9d9;
  font-size: 12px;
}

.adversarial-scene-desc strong {
  color: #e6efff;
  font-size: 13px;
}

.adversarial-scene-desc em {
  color: #85a0cc;
  font-style: normal;
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

.adversarial-run:disabled {
  opacity: 0.68;
  cursor: not-allowed;
}

.adversarial-error {
  margin: 10px 0 0;
  color: #ffb9b9;
  font-size: 12px;
}

.adversarial-summary-grid {
  margin-top: 0;
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  align-self: end;
}

.adversarial-log-head {
  display: flex;
  gap: 16px;
  flex-wrap: wrap;
  color: #9bb0d5;
  font-size: 12px;
  margin-bottom: 10px;
}

.adversarial-log-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.adversarial-log-item {
  border: 1px solid rgba(136, 170, 221, 0.2);
  border-radius: 10px;
  background: rgba(10, 19, 36, 0.52);
  padding: 10px;
  display: grid;
  gap: 4px;
  margin-bottom: 8px;
}

.adversarial-log-item strong {
  color: #e6f1ff;
  font-size: 12px;
}

.adversarial-log-item span {
  color: #8fa9d2;
  font-size: 11px;
}

.adversarial-log-item p {
  color: #bacbe8;
  font-size: 12px;
  margin: 0;
  line-height: 1.5;
}

.adversarial-summary-grid article {
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.03);
  padding: 9px;
  display: grid;
  gap: 4px;
}

.adversarial-summary-grid span {
  color: #90a5c8;
  font-size: 11px;
}

.adversarial-summary-grid strong {
  color: #f4f8ff;
  font-size: 13px;
}

.adversarial-curve-panel {
  margin-top: 10px;
  border: 1px solid rgba(129, 173, 232, 0.2);
  border-radius: 12px;
  background: rgba(12, 23, 43, 0.52);
  padding: 10px;
}

.adversarial-curve-panel h4 {
  margin: 0 0 8px;
  font-size: 12px;
  color: #dce8ff;
}

.adversarial-curve-svg {
  width: 100%;
  height: 120px;
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.04), rgba(255, 255, 255, 0.01));
}

.adversarial-curve-svg .curve {
  fill: none;
  stroke-width: 1.8;
}

.adversarial-curve-svg .curve-attack {
  stroke: #ff8f70;
}

.adversarial-curve-svg .curve-defense {
  stroke: #72b8ff;
}

.adversarial-curve-legend {
  margin-top: 8px;
  display: flex;
  gap: 12px;
  font-size: 11px;
}

.adversarial-curve-legend .attack {
  color: #ffbfaa;
}

.adversarial-curve-legend .defense {
  color: #a9d3ff;
}

.adversarial-cinematic-stage {
  margin-top: 0;
  border: 1px solid rgba(117, 177, 255, 0.22);
  border-radius: 14px;
  background:
    radial-gradient(circle at 26% 40%, rgba(255, 116, 76, 0.18), transparent 42%),
    radial-gradient(circle at 78% 28%, rgba(74, 188, 255, 0.2), transparent 40%),
    linear-gradient(135deg, rgba(7, 14, 28, 0.94), rgba(11, 23, 46, 0.92));
  overflow: hidden;
  position: relative;
  min-height: 0;
  height: 100%;
}

.adversarial-panel.adversarial-panel-max .adversarial-cinematic-stage {
  min-height: 0;
}

.adversarial-cinematic-stage::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image:
    linear-gradient(rgba(129, 171, 253, 0.12) 1px, transparent 1px),
    linear-gradient(90deg, rgba(129, 171, 253, 0.1) 1px, transparent 1px);
  background-size: 34px 34px;
  opacity: 0.2;
  pointer-events: none;
}

.adversarial-cinematic-stage::after {
  content: '';
  position: absolute;
  inset: 0;
  background: radial-gradient(circle at 50% 46%, rgba(194, 228, 255, 0.14), transparent 46%);
  opacity: 0;
  transition: opacity 0.22s ease;
  pointer-events: none;
}

.stage-hud,
.stage-field,
.stage-narrative {
  position: relative;
  z-index: 1;
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

.stage-field {
  margin-top: 12px;
  display: grid;
  grid-template-columns: minmax(160px, 1fr) minmax(160px, 0.9fr) minmax(160px, 1fr);
  gap: 10px;
  align-items: center;
}

.adversarial-panel.adversarial-panel-max .stage-field {
  grid-template-columns: minmax(220px, 1fr) minmax(220px, 0.9fr) minmax(220px, 1fr);
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

.actor-badge {
  font-size: 10px;
  letter-spacing: 0.12em;
  color: #9ec6ff;
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
  opacity: 0.5;
  pointer-events: none;
}

.attacker-asset {
  filter: drop-shadow(0 0 10px rgba(255, 130, 90, 0.4));
}

.defender-asset {
  filter: drop-shadow(0 0 10px rgba(120, 199, 255, 0.34));
}

.adversarial-panel.adversarial-panel-max .actor-avatar {
  width: 132px;
  height: 132px;
}

.battle-actor strong {
  color: #f0f6ff;
  font-size: 13px;
}

.battle-actor p {
  margin: 0;
  color: #9fb8dd;
  font-size: 11px;
  line-height: 1.45;
}

.attacker-mark-grid {
  width: 66px;
  height: 66px;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 5px;
}

.attacker-mark-grid i {
  border-radius: 4px;
  background: linear-gradient(160deg, rgba(255, 122, 72, 0.95), rgba(255, 82, 112, 0.8));
  box-shadow: 0 0 8px rgba(255, 124, 72, 0.46);
}

.openclaw-mark {
  width: 78px;
  height: 78px;
  position: relative;
}

.attacker-helix-mark,
.attacker-shard-mark,
.attacker-swarm-mark {
  width: 70px;
  height: 70px;
  position: relative;
}

.attacker-helix-mark i {
  position: absolute;
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
  position: absolute;
  width: 22px;
  height: 8px;
  left: 50%;
  top: 50%;
  margin-left: -11px;
  margin-top: -4px;
  border-radius: 99px;
  background: linear-gradient(90deg, rgba(255, 168, 83, 0.94), rgba(255, 99, 136, 0.88));
  transform-origin: center;
}

.attacker-shard-mark i:nth-child(1) { transform: rotate(0deg) translateY(-22px); }
.attacker-shard-mark i:nth-child(2) { transform: rotate(60deg) translateY(-22px); }
.attacker-shard-mark i:nth-child(3) { transform: rotate(120deg) translateY(-22px); }
.attacker-shard-mark i:nth-child(4) { transform: rotate(180deg) translateY(-22px); }
.attacker-shard-mark i:nth-child(5) { transform: rotate(240deg) translateY(-22px); }
.attacker-shard-mark i:nth-child(6) { transform: rotate(300deg) translateY(-22px); }

.attacker-swarm-mark i {
  position: absolute;
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

.openclaw-mark i {
  position: absolute;
  display: block;
}

.shrimp-core {
  width: 26px;
  height: 34px;
  left: 16px;
  top: 10px;
  border-radius: 50% 50% 40% 40%;
  background: linear-gradient(180deg, #ff8748, #ff4f75);
  box-shadow: 0 0 10px rgba(255, 116, 75, 0.52);
}

.shrimp-claw {
  width: 15px;
  height: 15px;
  top: 14px;
  border-radius: 50% 50% 50% 10%;
  border: 2px solid rgba(255, 145, 105, 0.92);
  border-left: none;
  border-bottom: none;
}

.shrimp-claw.claw-left {
  left: 3px;
  transform: rotate(-132deg);
}

.shrimp-claw.claw-right {
  right: 2px;
  transform: rotate(46deg);
}

.shrimp-tail {
  width: 20px;
  height: 16px;
  left: 20px;
  bottom: 3px;
  border-radius: 20px 20px 40% 40%;
  border: 2px solid rgba(255, 190, 148, 0.95);
  border-top: none;
}

.actor-attacker {
  box-shadow: inset 0 0 28px rgba(255, 88, 94, 0.12);
}

.actor-attacker.attacker-openclaw .actor-avatar {
  animation: openclawWave 1.2s ease-in-out infinite;
}

.actor-attacker.attacker-pattern-claw-a .shrimp-core {
  filter: hue-rotate(0deg);
}

.actor-attacker.attacker-pattern-claw-b .shrimp-core {
  filter: hue-rotate(24deg) saturate(1.2);
}

.actor-attacker.attacker-pattern-claw-c .shrimp-core {
  filter: hue-rotate(-20deg) saturate(1.25);
}

.actor-attacker.attacker-pattern-claw-c .shrimp-tail {
  width: 24px;
  left: 18px;
}

.actor-attacker.attacker-openclaw .attacker-asset {
  opacity: 0.34;
}

.actor-attacker.attacker-pattern-helix .actor-avatar {
  animation: attackerSpin 4.8s linear infinite;
}

.actor-attacker.attacker-pattern-shard .actor-avatar {
  box-shadow: inset 0 0 20px rgba(255, 122, 84, 0.24), 0 0 16px rgba(255, 122, 84, 0.22);
}

.actor-attacker.attacker-pattern-swarm .actor-avatar {
  animation: swarmPulse 1.6s ease-in-out infinite;
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
  height: auto;
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

.battle-impact strong {
  display: block;
  color: #eff6ff;
  font-size: 12px;
}

.battle-impact span {
  display: block;
  margin-top: 4px;
  color: #9fc0ea;
  font-size: 11px;
}

.impact-hit {
  border-color: rgba(255, 113, 93, 0.48);
  box-shadow: 0 0 12px rgba(255, 106, 93, 0.34);
}

.impact-block {
  border-color: rgba(73, 209, 159, 0.45);
  box-shadow: 0 0 12px rgba(73, 209, 159, 0.3);
}

.impact-charge {
  border-color: rgba(124, 186, 255, 0.48);
  box-shadow: 0 0 12px rgba(124, 186, 255, 0.28);
}

.impact-dash {
  border-color: rgba(255, 166, 90, 0.5);
  box-shadow: 0 0 14px rgba(255, 166, 90, 0.34);
}

.defender-avatar {
  background: radial-gradient(circle at 50% 26%, rgba(113, 195, 255, 0.3), rgba(9, 20, 44, 0.92));
}

.defender-core-logo {
  width: 30px;
  height: 30px;
  position: relative;
  z-index: 2;
  filter: drop-shadow(0 0 6px rgba(120, 199, 255, 0.6));
}

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

.defender-shield-rings i:nth-child(1) {
  width: 42px;
  height: 42px;
}

.defender-shield-rings i:nth-child(2) {
  width: 62px;
  height: 62px;
}

.defender-shield-rings i:nth-child(3) {
  width: 82px;
  height: 82px;
  border-color: rgba(132, 203, 255, 0.22);
}

.defender-side-panels {
  position: absolute;
  inset: 0;
}

.defender-side-panels i {
  position: absolute;
  top: 42px;
  width: 18px;
  height: 28px;
  border-radius: 8px;
  background: linear-gradient(180deg, rgba(118, 201, 255, 0.85), rgba(72, 147, 255, 0.76));
}

.defender-side-panels .panel-left {
  left: 14px;
  transform: skewY(12deg);
}

.defender-side-panels .panel-right {
  right: 14px;
  transform: skewY(-12deg);
}

.defender-fortress .defender-shield-rings i {
  animation: defenderRing 1.6s ease-in-out infinite;
}

.defender-vector .defender-side-panels i {
  animation: defenderWing 1s ease-in-out infinite;
}

.defender-prism {
  box-shadow: inset 0 0 26px rgba(141, 226, 255, 0.2), 0 0 16px rgba(117, 214, 255, 0.24);
}

.defender-fortress .defender-asset {
  opacity: 0.62;
}

.defender-prism .defender-asset {
  opacity: 0.58;
}

.guard-torso {
  width: 12px;
  height: 26px;
  top: 26px;
  left: 33px;
}

.actor-defender.pose-counter .defender-avatar {
  animation: guardianCounter 1.4s ease-in-out infinite;
}

.actor-defender.pose-guard .defender-avatar {
  animation: guardianGuard 0.9s ease-in-out infinite;
}

.stage-running .actor-attacker .actor-avatar {
  animation: attackerDash 0.7s ease-in-out infinite;
}

.stage-defense .actor-defender .actor-avatar {
  box-shadow: 0 0 16px rgba(78, 220, 169, 0.35);
}

.stage-breach .actor-attacker .actor-avatar {
  box-shadow: 0 0 18px rgba(255, 109, 94, 0.4);
}

.stage-narrative {
  margin: 10px 0 0;
  color: #d7e8ff;
  font-size: 12px;
  line-height: 1.72;
  min-height: 38px;
}

.stage-subtitle {
  margin: 6px 0 0;
  color: #9ec2ec;
  font-size: 11px;
  line-height: 1.68;
  min-height: 18px;
}

.stage-breach {
  box-shadow: inset 0 0 34px rgba(255, 89, 89, 0.12);
}

.stage-defense {
  box-shadow: inset 0 0 34px rgba(70, 224, 169, 0.12);
}

.stage-running {
  box-shadow: inset 0 0 34px rgba(100, 179, 255, 0.1);
}

.stage-finale-active {
  animation: stageFinaleFlash 0.52s ease-in-out 1;
}

.stage-finale-active::after {
  opacity: 1;
}

.stage-finale-active .lane-pulse {
  animation-duration: 3.2s;
  opacity: 0.72;
}

.stage-finale-active .battle-actor {
  transform: scale(1.02);
  filter: saturate(1.15) contrast(1.06);
}

.stage-hardening-overlay {
  margin-top: 10px;
  border: 1px solid rgba(125, 184, 255, 0.2);
  border-radius: 11px;
  background: rgba(9, 18, 35, 0.74);
  padding: 9px;
  display: grid;
  gap: 8px;
}

.overlay-title {
  color: #d7e8ff;
  font-size: 11px;
  font-weight: 700;
}

.stage-hardening-overlay article {
  display: grid;
  gap: 6px;
}

.overlay-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 10px;
}

.overlay-head strong {
  color: #eff5ff;
  font-size: 12px;
}

.overlay-head span {
  color: #9eb8dc;
  font-size: 11px;
}

.overlay-meter {
  height: 7px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.07);
  position: relative;
  overflow: hidden;
}

.overlay-meter i {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  border-radius: inherit;
  transition: width 0.36s ease;
}

.overlay-meter i.before {
  background: linear-gradient(90deg, rgba(255, 145, 112, 0.85), rgba(255, 96, 128, 0.72));
}

.overlay-meter i.after {
  background: linear-gradient(90deg, rgba(108, 205, 255, 0.95), rgba(73, 214, 168, 0.82));
  opacity: 0.9;
}

.overlay-meter i.before.defense {
  background: linear-gradient(90deg, rgba(134, 173, 255, 0.8), rgba(95, 136, 224, 0.72));
}

.overlay-meter i.after.defense {
  background: linear-gradient(90deg, rgba(74, 222, 190, 0.9), rgba(49, 196, 136, 0.84));
}

.adversarial-stream {
  margin-top: 0;
  display: grid;
  gap: 8px;
  max-height: none;
  overflow: visible;
  padding-right: 2px;
}

.adversarial-round {
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.03);
  padding: 10px;
}

.adversarial-round-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.adversarial-round-top strong {
  color: #ecf3ff;
  font-size: 13px;
}

.adversarial-round-top span {
  border-radius: 999px;
  padding: 2px 8px;
  font-size: 11px;
  font-weight: 700;
}

.adversarial-round-top span.hit {
  background: rgba(255, 92, 92, 0.16);
  color: #ffd7d7;
}

.adversarial-round-top span.block {
  background: rgba(49, 196, 136, 0.16);
  color: #c8ffe6;
}

.adversarial-round p {
  margin: 8px 0 0;
  color: #a4b7d8;
  font-size: 12px;
  line-height: 1.68;
}

.adversarial-round em {
  margin-top: 6px;
  color: #d9e7ff;
  font-style: normal;
  display: block;
  line-height: 1.72;
  font-size: 12px;
}

.adversarial-recommendations {
  margin-top: 10px;
  border-radius: 12px;
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.03);
  padding: 10px;
}

.adversarial-recommendations h4 {
  margin: 0 0 8px;
  color: #ecf3ff;
  font-size: 13px;
}

.adversarial-recommendations p {
  margin: 6px 0 0;
  color: #9cb1d4;
  line-height: 1.7;
  font-size: 12px;
}

.adversarial-recommendation-actions {
  margin-top: 10px;
  display: flex;
  align-items: center;
  gap: 10px;
}

.adversarial-harden-btn {
  font-weight: 700;
  letter-spacing: 0.04em;
  box-shadow: 0 0 16px rgba(255, 172, 89, 0.28);
}

.adversarial-feed-column .adversarial-compare-grid {
  margin-top: 0;
}

@keyframes laneRush {
  0% {
    transform: translateX(-120%);
  }
  100% {
    transform: translateX(260%);
  }
}

@keyframes openclawWave {
  0%,
  100% {
    transform: translateY(0) rotate(0deg);
  }
  50% {
    transform: translateY(-4px) rotate(-3deg);
  }
}

@keyframes guardianCounter {
  0%,
  100% {
    transform: translateY(0) scale(1);
  }
  50% {
    transform: translateY(-3px) scale(1.03);
  }
}

@keyframes guardianGuard {
  0%,
  100% {
    transform: translateX(0);
  }
  50% {
    transform: translateX(-3px);
  }
}

@keyframes attackerDash {
  0%,
  100% {
    transform: translateX(0) scale(1);
  }
  50% {
    transform: translateX(3px) scale(1.04);
  }
}

@keyframes attackerSpin {
  0% {
    transform: rotate(0deg);
  }
  100% {
    transform: rotate(360deg);
  }
}

@keyframes swarmPulse {
  0%,
  100% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.06);
  }
}

@keyframes defenderRing {
  0%,
  100% {
    opacity: 0.55;
    transform: scale(0.96);
  }
  50% {
    opacity: 0.95;
    transform: scale(1.04);
  }
}

@keyframes defenderWing {
  0%,
  100% {
    transform: translateY(0);
  }
  50% {
    transform: translateY(-2px);
  }
}

@keyframes stageFinaleFlash {
  0% {
    filter: brightness(1);
  }
  40% {
    filter: brightness(1.2);
  }
  100% {
    filter: brightness(1);
  }
}

@media (max-width: 768px) {
  .ai-config-row,
  .security-surface {
    grid-template-columns: 1fr;
  }

  .ai-input-row {
    display: grid;
    grid-template-columns: 1fr;
    align-items: stretch;
  }

  .ai-send-btn {
    width: 100%;
  }

  .adversarial-floating-wrap {
    right: 10px;
    bottom: 14px;
  }

  .adversarial-panel {
    right: 10px;
    bottom: 10px;
    width: calc(100vw - 20px);
    height: calc(100vh - 20px);
    max-height: calc(100vh - 20px);
  }

  .adversarial-panel.adversarial-panel-max {
    width: calc(100vw - 12px);
    height: calc(100vh - 12px);
    max-height: calc(100vh - 12px);
  }

  .adversarial-layout {
    grid-template-columns: 1fr;
  }

  .adversarial-feed-column {
    max-height: min(42vh, 360px);
    overflow-y: visible;
    overflow-x: hidden;
    padding-right: 0;
  }

  .adversarial-panel .adversarial-feed-column {
    overflow-y: visible !important;
    max-height: none !important;
  }

  .adversarial-config,
  .adversarial-summary-grid {
    grid-template-columns: 1fr;
  }

  .stage-field {
    grid-template-columns: 1fr;
  }

  .battle-mid {
    order: 3;
  }

  .adversarial-compare-grid {
    grid-template-columns: 1fr;
  }

  .adversarial-log-grid {
    grid-template-columns: 1fr;
  }
}
</style>
