<template>
  <div class="threat-monitor-page">

    <!-- 页头 -->
    <div class="page-header scene-block">
      <div class="page-header-copy">
        <div class="page-eyebrow">REAL-TIME THREAT MONITOR</div>
        <h1 class="page-title">实时威胁监控</h1>
        <p class="page-subtitle">
          检测并响应恶意AI模拟攻击与代理窃取行为。
          实时告警、手动阻拦或忽略，并可配置敏感文件检测规则。
        </p>
      </div>
      <div class="page-header-actions">
        <el-tag :type="autoRefresh ? 'success' : 'info'" size="large">
          {{ autoRefresh ? '实时刷新中' : '已暂停刷新' }}
        </el-tag>
        <el-button
          :type="autoRefresh ? 'warning' : 'success'"
          @click="toggleAutoRefresh"
        >
          <el-icon><component :is="autoRefresh ? 'VideoPause' : 'VideoPlay'" /></el-icon>
          {{ autoRefresh ? '暂停' : '开启实时刷新' }}
        </el-button>
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

    <!-- 主内容标签页 -->
    <el-tabs v-model="activeTab" class="main-tabs">

      <!-- ── 事件列表 Tab ── -->
      <el-tab-pane label="威胁事件" name="events">
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

            <el-table-column label="文件路径" min-width="200">
              <template #default="{ row }">
                <span class="file-path" :title="row.filePath">{{ truncate(row.filePath, 45) }}</span>
              </template>
            </el-table-column>

            <el-table-column prop="hostname" label="主机" width="180" />
            <el-table-column prop="employeeId" label="员工标识" width="110" />

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
                  v-if="canHandleThreats && (row.status === 'pending' || row.status === 'reviewing')"
                  size="small"
                  type="danger"
                  :loading="actionLoading === row.id + '-block'"
                  @click="blockEvent(row)"
                >阻拦</el-button>
                <el-button
                  v-if="canHandleThreats && (row.status === 'pending' || row.status === 'reviewing')"
                  size="small"
                  :loading="actionLoading === row.id + '-ignore'"
                  @click="ignoreEvent(row)"
                >忽略</el-button>
                <el-tag v-if="row.status === 'blocked'" type="danger" size="small">已阻拦</el-tag>
                <el-tag v-if="row.status === 'ignored'" type="info" size="small">已忽略</el-tag>
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

      <el-tab-pane label="告警闭环" name="alertCenter">
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
            <el-table-column prop="severity" label="级别" width="100">
              <template #default="{ row }">
                <el-tag :type="severityTagType(row.severity)" size="small">{{ severityLabel(row.severity) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="title" label="告警标题" min-width="220" />
            <el-table-column prop="username" label="关联用户" width="120" />
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
                  v-if="canHandleThreats && (row.status === 'pending' || row.status === 'reviewing')"
                  size="small"
                  type="danger"
                  @click="openDispose(row, 'blocked')"
                >阻断并验证</el-button>
                <el-button
                  v-if="canHandleThreats && (row.status === 'pending' || row.status === 'reviewing')"
                  size="small"
                  @click="openDispose(row, 'ignored')"
                >忽略</el-button>
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
      <el-tab-pane v-if="canRunThreatDrill" label="攻防演练" name="drill">
        <el-card class="card-glass" style="margin-top: 0">
          <div class="simulator-info">
            <h3>真实安全态势攻防演练</h3>
            <p>
              当前演练不再注入模拟攻击数据，完全基于真实审计日志、风险事件与威胁监控事件进行状态检测。
              点击“立即检测”将实时刷新当前公司的安全态势评分。
            </p>

            <div class="drill-actions">
              <el-button type="primary" :loading="drillLoading" @click="runImmediateThreatDrill">立即检测</el-button>
              <el-button type="success" :loading="simDrillLoading" @click="runPythonBattleDrill">模拟攻防</el-button>
              <el-tag :type="threatDrill.threatLevel === 'high' ? 'danger' : (threatDrill.threatLevel === 'medium' ? 'warning' : 'success')" size="large">
                当前态势：{{ threatDrill.threatLevel || 'unknown' }}
              </el-tag>
            </div>

            <div class="drill-config-row">
              <el-select v-model="simDrillConfig.scenario" style="width: 280px" :disabled="simDrillLoading">
                <el-option
                  v-for="scene in simulationScenarios"
                  :key="scene.code"
                  :label="scene.description || scene.code"
                  :value="scene.code"
                />
              </el-select>
              <el-input-number v-model="simDrillConfig.rounds" :min="1" :max="100" :disabled="simDrillLoading" />
              <el-input v-model="simDrillConfig.seed" style="width: 180px" placeholder="seed(可选)" :disabled="simDrillLoading" />
            </div>

            <p v-if="simDrillError" class="sim-error">{{ simDrillError }}</p>

            <div class="code-block" style="margin-top: 14px">
              <div class="code-label">风险评分</div>
              <pre>{{ threatDrill.riskScore ?? 0 }}</pre>
            </div>

            <div class="code-block">
              <div class="code-label">检测信号</div>
              <pre>{{ JSON.stringify(threatDrill.signals || {}, null, 2) }}</pre>
            </div>

            <el-table :data="threatDrill.recentSecurityEvents || []" style="margin-top: 12px">
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

    <button
      v-if="canHandleThreats"
      class="adversarial-fab"
      type="button"
      :disabled="floatingDrillLoading"
      @click="runFloatingDrill"
      :title="floatingDrillLoading ? '攻防模拟进行中' : '启动攻防模拟'"
    >
      <span class="fab-icon">⚔</span>
      <span class="fab-label">{{ floatingDrillLoading ? '模拟中' : '模拟攻防' }}</span>
    </button>

    <!-- 规则编辑弹窗 -->
    <el-drawer
      v-model="relatedVisible"
      title="关联事件链路"
      size="50%"
      :with-header="true"
    >
      <div v-if="relatedCurrent" class="related-head">
        <p>当前事件: #{{ relatedCurrent.id }} / {{ relatedCurrent.title || '未命名告警' }}</p>
      </div>
      <el-skeleton v-if="relatedLoading" rows="6" animated />
      <template v-else>
        <div class="related-tags">
          <el-tag v-for="(count, key) in relatedTypeCount" :key="key" size="small">{{ centerEventTypeLabel(key) }}: {{ count }}</el-tag>
        </div>
        <el-table :data="relatedEvents" style="margin-top: 10px">
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
import { computed, ref, onMounted, onUnmounted } from 'vue';
import {
  Refresh, Search, Plus, VideoPause, VideoPlay,
} from '@element-plus/icons-vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import request from '../api/request';
import { alertCenterApi } from '../api/alertCenter';
import { useUserStore } from '../store/user';

const userStore = useUserStore();

function hasAnyRole(...roleCodes) {
  const currentRole = String(userStore.userInfo?.roleCode || '').toUpperCase();
  return roleCodes.some(role => role === currentRole);
}

const canViewThreatMonitor = computed(() => hasAnyRole('ADMIN', 'SECOPS', 'EXECUTIVE'));
const canHandleThreats = computed(() => hasAnyRole('ADMIN', 'SECOPS'));
const canManageThreatRules = computed(() => hasAnyRole('ADMIN', 'SECOPS'));
const canRunThreatDrill = computed(() => hasAnyRole('ADMIN', 'SECOPS'));

// ── 统计 ──────────────────────────────────────────────────────────────────────
const stats = ref({});

async function fetchStats() {
  try {
    stats.value = await alertCenterApi.stats();
  } catch (e) {
    try {
      stats.value = await request.get('/security/stats');
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
  triggerSimulation: true,
  rounds: 12,
});
const disposeResult = ref(null);

// ── 事件列表 ──────────────────────────────────────────────────────────────────
const events = ref([]);
const loading = ref(false);
const actionLoading = ref(null);

const filter = ref({ status: '', severity: '', keyword: '' });
const pagination = ref({ page: 1, pageSize: 20, total: 0 });

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
    events.value = data.list || [];
    pagination.value.total = data.total || 0;
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
  actionLoading.value = row.id + '-block';
  try {
    await request.post('/security/block', { id: row.id });
    ElMessage.success('已阻拦该事件');
    row.status = 'blocked';
    fetchStats();
  } catch (e) {
    ElMessage.error('操作失败：' + (e.message || '未知错误'));
  } finally {
    actionLoading.value = null;
  }
}

async function ignoreEvent(row) {
  actionLoading.value = row.id + '-ignore';
  try {
    await request.post('/security/ignore', { id: row.id });
    ElMessage.success('已忽略该事件');
    row.status = 'ignored';
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

// ── 自动刷新 ──────────────────────────────────────────────────────────────────
const autoRefresh = ref(true);
let refreshTimer = null;

const drillLoading = ref(false);
const floatingDrillLoading = ref(false);
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
const battleInsights = ref({ analysis: '', suggestions: [] });
const simDrillConfig = ref({
  scenario: 'random',
  rounds: 12,
  seed: '',
});
let battlePlaybackTimer = null;

const simulationScenarios = computed(() => {
  const all = Array.isArray(adversarialMeta.value?.scenarios) ? adversarialMeta.value.scenarios : [];
  return all.filter(scene => String(scene?.code || '').trim().toLowerCase() !== 'real-threat-check');
});

function startAutoRefresh() {
  stopAutoRefresh();
  refreshTimer = setInterval(() => {
    refreshEvents();
    if (activeTab.value === 'alertCenter') {
      refreshCenterEvents();
    }
    fetchStats();
  }, 5000);
}

function stopAutoRefresh() {
  if (refreshTimer) {
    clearInterval(refreshTimer);
    refreshTimer = null;
  }
}

function toggleAutoRefresh() {
  autoRefresh.value = !autoRefresh.value;
  if (autoRefresh.value) {
    startAutoRefresh();
    ElMessage.success('已开启实时刷新（每5秒）');
  } else {
    stopAutoRefresh();
    ElMessage.info('已暂停自动刷新');
  }
}

// ── 标签页 ────────────────────────────────────────────────────────────────────
const activeTab = ref('events');

// ── 总刷新 ────────────────────────────────────────────────────────────────────
async function refresh() {
  await Promise.all([refreshEvents(), refreshCenterEvents(), fetchStats()]);
}

async function refreshCenterEvents() {
  centerLoading.value = true;
  try {
    const params = {
      page: centerPagination.value.page,
      pageSize: centerPagination.value.pageSize,
    };
    if (centerFilter.value.status) params.status = centerFilter.value.status;
    if (centerFilter.value.eventType) params.eventType = centerFilter.value.eventType;
    if (centerFilter.value.keyword) params.keyword = centerFilter.value.keyword;

    const data = await alertCenterApi.list(params);
    centerEvents.value = data.list || [];
    centerPagination.value.total = data.total || 0;
    if (data.stats) {
      stats.value = data.stats;
    }
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
    relatedEvents.value = data.related || [];
    relatedTypeCount.value = data.typeCount || {};
  } catch (e) {
    ElMessage.error('加载关联事件失败：' + (e.message || '未知错误'));
  } finally {
    relatedLoading.value = false;
  }
}

function openDispose(row, status) {
  disposeForm.value = {
    id: row.id,
    status,
    note: '',
    triggerSimulation: true,
    rounds: 12,
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
    disposeResult.value = data.validation || null;
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
}

function startBattlePlayback(rounds) {
  stopBattlePlayback();
  visibleBattleRounds.value = [];
  if (!Array.isArray(rounds) || rounds.length === 0) {
    return;
  }
  let cursor = 0;
  battlePlaybackTimer = window.setInterval(() => {
    visibleBattleRounds.value = rounds.slice(0, cursor + 1);
    cursor += 1;
    if (cursor >= rounds.length) {
      stopBattlePlayback();
    }
  }, 320);
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

async function runFloatingDrill() {
  if (!canHandleThreats.value || floatingDrillLoading.value) {
    return;
  }
  floatingDrillLoading.value = true;
  try {
    await runPythonBattleDrill();
    activeTab.value = 'drill';
  } catch (e) {
    ElMessage.error('攻防模拟触发失败：' + (e.message || '未知错误'));
  } finally {
    floatingDrillLoading.value = false;
  }
}

// ── 格式化工具 ────────────────────────────────────────────────────────────────
function severityTagType(s) {
  return { critical: 'danger', high: 'warning', medium: '', low: 'info' }[s] ?? '';
}

function severityLabel(s) {
  return { critical: '严重', high: '高危', medium: '中危', low: '低危' }[s] ?? s;
}

function statusTagType(s) {
  return { pending: 'warning', blocked: 'danger', ignored: 'info', reviewing: '' }[s] ?? '';
}

function statusLabel(s) {
  return { pending: '待处理', blocked: '已阻拦', ignored: '已忽略', reviewing: '审查中' }[s] ?? s;
}

function eventTypeLabel(t) {
  const map = {
    FILE_STEAL: '文件窃取',
    SUSPICIOUS_UPLOAD: '可疑上传',
    BATCH_COPY: '批量复制',
    EXFILTRATION: '数据外泄',
    DATA_SCRAPE: '数据抓取',
    CREDENTIAL_DUMP: '凭证转储',
  };
  return map[t] ?? t;
}

function centerEventTypeLabel(t) {
  const map = {
    PRIVACY_ALERT: '隐私告警',
    ANOMALY_ALERT: '行为异常',
    SHADOW_AI_ALERT: '影子AI',
    SECURITY_ALERT: '安全威胁',
  };
  return map[t] ?? (t || '未知');
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
  if (row.severity === 'critical') return { background: 'rgba(255,50,50,0.06)' };
  if (row.severity === 'high') return { background: 'rgba(255,150,50,0.05)' };
  return {};
}

// ── 生命周期 ──────────────────────────────────────────────────────────────────
onMounted(() => {
  if (!canViewThreatMonitor.value) {
    ElMessage.error('当前身份无权访问实施威胁检测模块');
    return;
  }
  refresh();
  if (canManageThreatRules.value) {
    fetchRules();
  }
  if (autoRefresh.value) startAutoRefresh();
  if (canRunThreatDrill.value) {
    fetchThreatDrillMeta();
  }
});

onUnmounted(() => {
  stopBattlePlayback();
  stopAutoRefresh();
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

/* ── 统计卡片 ── */
.stats-row {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 16px;
  margin-bottom: 24px;
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
  color: #e8f4ff;
  margin-bottom: 12px;
}

.simulator-info p {
  color: rgba(200, 220, 255, 0.7);
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
  overflow: auto;
}

.battle-round-item {
  border: 1px solid rgba(100, 180, 255, 0.16);
  border-radius: 10px;
  background: rgba(13, 28, 52, 0.6);
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

.adversarial-fab {
  position: fixed;
  right: 28px;
  bottom: 34px;
  width: 128px;
  height: 56px;
  border-radius: 28px;
  border: 1px solid rgba(120, 210, 255, 0.45);
  background: radial-gradient(circle at 20% 20%, rgba(80, 220, 255, 0.82), rgba(24, 88, 180, 0.92));
  color: #f2f9ff;
  box-shadow: 0 14px 34px rgba(32, 118, 224, 0.45);
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  cursor: pointer;
  z-index: 41;
  transition: transform 0.18s ease, box-shadow 0.18s ease, opacity 0.18s ease;
}

.adversarial-fab:hover:not(:disabled) {
  transform: translateY(-2px) scale(1.02);
  box-shadow: 0 18px 38px rgba(32, 118, 224, 0.52);
}

.adversarial-fab:disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.fab-icon {
  font-size: 19px;
}

.fab-label {
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.04em;
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
  background: rgba(12, 24, 48, 0.7);
  border: 1px solid rgba(100, 180, 255, 0.12);
  border-radius: 16px;
}

@media (max-width: 900px) {
  .stats-row {
    grid-template-columns: repeat(2, 1fr);
  }

  .battle-summary-grid {
    grid-template-columns: repeat(2, minmax(120px, 1fr));
  }

  .adversarial-fab {
    right: 18px;
    bottom: 22px;
    width: 114px;
    height: 50px;
  }
}
</style>
