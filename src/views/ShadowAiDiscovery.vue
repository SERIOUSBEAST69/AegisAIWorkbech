<template>
  <div class="shadow-ai-page">

    <!-- 页头 -->
    <div class="page-header scene-block">
      <div class="page-header-copy">
        <div class="page-eyebrow">SHADOW AI DISCOVERY</div>
        <h1 class="page-title">影子AI发现与治理</h1>
        <p class="page-subtitle">
          <template v-if="isEmployeeView">
            员工模式下仅展示当前账号设备相关检测信息，基础检测默认保持开启。
          </template>
          <template v-else>
          实时监控终端设备上员工私自使用的AI服务，自动识别 ChatGPT、Midjourney、Claude
          等未受管控的"影子AI"，防止核心数据与隐私泄露。
          </template>
        </p>
      </div>
      <div class="page-header-actions">
        <el-tag v-if="isMock" type="warning" size="large">演示数据</el-tag>
        <el-tag v-if="isElectron" type="success" size="large">客户端已连接</el-tag>
        <el-tag v-else type="info" size="large">Web 模式</el-tag>
        <el-button
          type="success"
          :loading="localScanning"
          @click="runLocalScan"
        >
          <el-icon><Monitor /></el-icon>
          扫描本机
        </el-button>
        <el-button type="primary" :loading="loading" @click="refresh">
          <el-icon><Refresh /></el-icon>
          刷新扫描结果
        </el-button>
      </div>
    </div>

    <!-- 演示数据免责提示（仅当后端不可用时显示） -->
    <el-alert
      v-if="isMock"
      type="warning"
      show-icon
      :closable="false"
      style="margin-bottom: 16px;"
    >
      <template #title>
        <strong>当前显示的是演示数据，非真实员工信息</strong>
      </template>
      <template #default>
        设备名称（如 WIN-WORKSTATION-01）和用户标识（如 employee_a）均为占位示例。
        真实数据需在员工终端上安装 Aegis 客户端（见下方下载区），客户端会将扫描结果上报至本系统。
        <strong>本系统无法在未安装客户端的情况下监控员工的桌面 AI 应用程序。</strong>
      </template>
    </el-alert>

    <!-- 统计卡片 -->
    <div v-if="!isEmployeeView" class="stats-grid scene-block">
      <article class="stat-tile card-glass">
        <div class="stat-tile-icon clients">
          <el-icon><Monitor /></el-icon>
        </div>
        <div class="stat-tile-body">
          <span class="stat-tile-label">在线客户端</span>
          <strong class="stat-tile-value">{{ stats.totalClients }}</strong>
          <span class="stat-tile-hint">已部署客户端总数</span>
        </div>
      </article>

      <article class="stat-tile card-glass">
        <div class="stat-tile-icon shadow">
          <el-icon><Warning /></el-icon>
        </div>
        <div class="stat-tile-body">
          <span class="stat-tile-label">发现影子AI</span>
          <strong class="stat-tile-value">{{ stats.totalShadowAi }}</strong>
          <span class="stat-tile-hint">未经授权的AI服务</span>
        </div>
      </article>

      <article class="stat-tile card-glass">
        <div class="stat-tile-icon danger">
          <el-icon><AlarmClock /></el-icon>
        </div>
        <div class="stat-tile-body">
          <span class="stat-tile-label">高风险终端</span>
          <strong class="stat-tile-value danger-text">{{ stats.highRiskClients }}</strong>
          <span class="stat-tile-hint">需优先处置的设备</span>
        </div>
      </article>

      <article class="stat-tile card-glass">
        <div class="stat-tile-icon reports">
          <el-icon><Document /></el-icon>
        </div>
        <div class="stat-tile-body">
          <span class="stat-tile-label">近7日报告</span>
          <strong class="stat-tile-value">{{ stats.recentReports }}</strong>
          <span class="stat-tile-hint">客户端上报次数</span>
        </div>
      </article>
    </div>

    <!-- 本机实时扫描面板（Web 和 Electron 均可使用） -->
    <div class="local-scan-panel scene-block card-glass">
      <div class="local-scan-header">
        <div>
          <div class="card-header">📡 本机实时扫描</div>
          <p class="panel-subtitle">
            <template v-if="isElectron">
              以下为当前设备的真实扫描结果，通过分析进程列表、网络连接和浏览器访问历史获得。
            </template>
            <template v-else>
              Web 模式下通过服务端 API 触发扫描，获取当前已注册客户端的最新状态汇总。
              如需扫描本机真实进程，请安装下方的 Aegis 客户端。
            </template>
          </p>
        </div>
        <div class="local-scan-status">
          <span v-if="localScanResult" :class="['risk-badge', localScanResult.riskLevel]">
            本机风险：{{ riskLabel(localScanResult.riskLevel) }}
          </span>
          <el-button
            type="success"
            :loading="localScanning"
            size="small"
            @click="runLocalScan"
          >
            <el-icon><Refresh /></el-icon>
            立即扫描
          </el-button>
        </div>
      </div>

      <div v-if="localScanning" class="local-scan-loading">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>正在扫描本机 AI 服务…（检测进程、网络连接和浏览器历史）</span>
      </div>

      <div v-else-if="!localScanResult" class="local-scan-empty">
        <span>尚未执行本机扫描，请点击「立即扫描」或等待定时扫描。</span>
      </div>

      <div v-else>
        <div class="local-scan-meta">
          <span>🕐 扫描时间：{{ formatTime(localScanResult.time) }}</span>
          <span>🔍 发现影子AI：<strong>{{ localScanResult.shadowAiCount }}</strong> 个</span>
        </div>

        <div v-if="localScanResult.shadowAiCount === 0" class="local-scan-empty">
          ✅ 未发现未经授权的AI服务，本机合规。
        </div>

        <div v-else class="local-services-grid">
          <div
            v-for="svc in localScanResult.services"
            :key="svc.name + svc.source"
            :class="['local-service-card', svc.riskLevel]"
          >
            <div class="local-service-head">
              <span :class="['risk-badge', svc.riskLevel]">{{ riskLabel(svc.riskLevel) }}</span>
              <strong>{{ svc.name }}</strong>
              <el-tag size="small" type="info">{{ categoryLabel(svc.category) }}</el-tag>
            </div>
            <div class="local-service-meta">
              <span>🌐 {{ svc.domain }}</span>
              <span>📡 {{ sourceLabel(svc.source) }}</span>
              <span v-if="svc.lastSeen">🕐 {{ formatTime(svc.lastSeen) }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 风险分布 + 客户端列表 -->
    <div v-if="!isEmployeeView" class="main-grid scene-block">

      <!-- 左侧：风险等级分布 -->
      <el-card class="risk-dist-card card-glass">
        <div class="panel-head">
          <div class="card-header">风险分布</div>
          <p class="panel-subtitle">各终端影子AI风险等级占比。</p>
        </div>
        <div class="risk-dist-list">
          <div
            v-for="item in riskDistItems"
            :key="item.level"
            class="risk-dist-item"
          >
            <div class="risk-dist-row">
              <span :class="['risk-badge', item.level]">{{ item.label }}</span>
              <strong>{{ item.count }}</strong>
            </div>
            <div class="risk-dist-bar">
              <i
                :class="['risk-dist-fill', item.level]"
                :style="{ width: `${item.pct}%` }"
              ></i>
            </div>
          </div>
        </div>

        <div class="download-section">
          <div class="card-header" style="margin-top: 28px;">客户端下载</div>
          <p class="panel-subtitle">
            部署 Aegis 轻量客户端到员工电脑，实现持续监控。
          </p>
          <div class="download-btns">
            <el-button
              type="primary"
              plain
              size="small"
              :loading="downloading === 'windows'"
              @click="downloadClient('windows')"
            >
              <el-icon><Download /></el-icon>
              Windows 安装包
            </el-button>
            <el-button
              type="primary"
              plain
              size="small"
              :loading="downloading === 'macos'"
              @click="downloadClient('macos')"
            >
              <el-icon><Download /></el-icon>
              macOS DMG
            </el-button>
            <el-button
              plain
              size="small"
              :loading="downloading === 'linux'"
              @click="downloadClient('linux')"
            >
              <el-icon><Download /></el-icon>
              Linux DEB/RPM
            </el-button>
          </div>
          <div class="deploy-tip">
            下载后双击安装，客户端将自动连接到本平台并开始扫描。
            部署文档见
            <a
              href="https://github.com/SERIOUSBEAST69/AegisAI/blob/main/electron/README.md"
              target="_blank"
            >electron/README.md</a>。
          </div>
        </div>

        <!-- 局域网多机部署指引 -->
        <div class="lan-guide-section">
          <div class="card-header" style="margin-top: 28px;">🌐 局域网多机检测部署指引</div>
          <p class="panel-subtitle" style="margin-bottom: 12px;">
            将 Aegis 客户端部署到同一局域网内的多台设备，统一汇报至本平台进行集中合规审查。
          </p>
          <ol class="lan-guide-steps">
            <li>
              <strong>启动 Aegis 服务端</strong>：在本机（或局域网内某台服务器）运行
              <code>docker-compose up</code>，确保服务端监听在 <code>0.0.0.0:8080</code>。
            </li>
            <li>
              <strong>确认本机 IP</strong>：运行 <code>ipconfig</code>（Windows）或
              <code>ifconfig</code>（macOS/Linux）获取局域网 IP，如 <code>192.168.1.100</code>。
            </li>
            <li>
              <strong>分发客户端安装包</strong>：将 Windows 安装包或 macOS DMG 分发给需要检测的设备。
              每台设备安装后，在托盘菜单 → 「服务器设置」中填写服务器地址，例如
              <code>http://192.168.1.100:8080</code>。
            </li>
            <li>
              <strong>客户端自动扫描上报</strong>：Aegis 客户端将每 30 分钟自动扫描本机 AI 服务，
              并将结果上报到此平台。在「终端设备清单」中可查看所有已接入设备的扫描报告。
            </li>
            <li>
              <strong>隐私合规声明</strong>：请确保每台设备的使用者已了解并同意 Aegis 的扫描行为。
              Aegis 仅读取进程列表、活跃网络连接和浏览器历史记录，不上传任何文件内容。
            </li>
          </ol>
        </div>
      </el-card>

      <!-- 右侧：客户端设备列表 -->
      <el-card class="clients-card card-glass">
        <div class="panel-head">
          <div>
            <div class="card-header">终端设备清单</div>
            <p class="panel-subtitle">每台设备的最新扫描结果与发现的影子AI服务。</p>
          </div>
          <el-input
            v-model="searchKeyword"
            placeholder="搜索主机名或用户"
            size="small"
            style="width: 200px;"
            clearable
          >
            <template #prefix><el-icon><Search /></el-icon></template>
          </el-input>
        </div>

        <div v-if="loading" class="loading-state">
          <el-icon class="is-loading"><Loading /></el-icon>
          <span>正在加载扫描数据…</span>
        </div>

        <div v-else-if="filteredClients.length === 0" class="empty-state">
          <el-icon><Monitor /></el-icon>
          <span>暂无客户端数据。请先部署客户端。</span>
        </div>

        <div v-else class="client-list">
          <article
            v-for="client in filteredClients"
            :key="client.clientId"
            :class="['client-card', 'card-glass', client.riskLevel]"
            @click="selectClient(client)"
          >
            <div class="client-card-head">
              <div class="client-info">
                <div class="client-hostname">
                  <el-icon><Monitor /></el-icon>
                  {{ client.hostname }}
                </div>
                <div class="client-meta">
                  {{ client.osUsername }} · {{ client.osType }} · v{{ client.clientVersion }}
                </div>
              </div>
              <div class="client-risk">
                <span :class="['risk-badge', client.riskLevel]">
                  {{ riskLabel(client.riskLevel) }}
                </span>
                <span class="client-ai-count">
                  {{ client.shadowAiCount }} 个影子AI
                </span>
              </div>
            </div>

            <div class="client-services" v-if="parseServices(client).length > 0">
              <span
                v-for="svc in parseServices(client).slice(0, 6)"
                :key="svc.domain"
                :class="['service-chip', svc.riskLevel]"
                :title="svc.domain + ' · 发现来源: ' + svc.source"
              >
                {{ svc.name }}
              </span>
              <span v-if="parseServices(client).length > 6" class="service-chip more">
                +{{ parseServices(client).length - 6 }}
              </span>
            </div>

            <div class="client-footer">
              <span class="client-scan-time">
                最近扫描：{{ formatTime(client.scanTime) }}
              </span>
            </div>
          </article>
        </div>
      </el-card>
    </div>

    <!-- 云端扫描队列（下载触发时自动入队） -->
    <div v-if="!isEmployeeView && (scanQueue.length > 0 || localScanEnabled)" class="cloud-queue-panel scene-block card-glass">
      <div class="cloud-queue-header">
        <div>
          <div class="card-header">☁️ 云端扫描队列</div>
          <p class="panel-subtitle">
            每次下载客户端安装包时自动入队；客户端安装完成并执行首次扫描后，状态将更新为"已完成"。
          </p>
        </div>
        <div class="cloud-queue-actions">
          <el-tag v-if="localScanEnabled" type="success" size="small">本地扫描已开启</el-tag>
          <el-button size="small" :loading="queueLoading" @click="refreshQueue">
            <el-icon><Refresh /></el-icon>
            刷新队列
          </el-button>
        </div>
      </div>

      <div v-if="queueLoading" class="loading-state">
        <el-icon class="is-loading"><Loading /></el-icon>
        <span>加载队列中…</span>
      </div>
      <div v-else-if="scanQueue.length === 0" class="empty-state">
        <el-icon><Document /></el-icon>
        <span>暂无下载记录。点击上方客户端下载按钮后，任务将自动出现在此队列中。</span>
      </div>
      <div v-else class="cloud-queue-list">
        <div
          v-for="item in scanQueue"
          :key="item.id"
          :class="['cloud-queue-item', 'card-glass', item.status]"
        >
          <div class="cloud-queue-item-head">
            <span :class="['queue-platform-badge', item.platform]">
              {{ platformLabel(item.platform) }}
            </span>
            <span :class="['queue-status-badge', item.status]">
              {{ queueStatusLabel(item.status) }}
            </span>
            <span class="queue-hostname" v-if="item.hostname">{{ item.hostname }}</span>
          </div>
          <div class="cloud-queue-item-meta">
            <span>⬇️ 下载时间：{{ formatTime(item.downloadTime) }}</span>
            <span v-if="item.osUsername">👤 {{ item.osUsername }}</span>
            <span v-if="item.userAgent" class="ua-hint" :title="item.userAgent">
              🌐 {{ shortenUA(item.userAgent) }}
            </span>
          </div>
          <div v-if="item.scanResult" class="cloud-queue-scan-result">
            <el-icon><Document /></el-icon>
            <span>扫描结果已就绪</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 详情抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      :title="`设备详情 · ${selectedClient?.hostname}`"
      direction="rtl"
      size="520px"
    >
      <div v-if="selectedClient" class="drawer-content">
        <div class="drawer-meta">
          <div class="drawer-meta-row">
            <span>主机名</span><strong>{{ selectedClient.hostname }}</strong>
          </div>
          <div class="drawer-meta-row">
            <span>用户</span><strong>{{ selectedClient.osUsername }}</strong>
          </div>
          <div class="drawer-meta-row">
            <span>系统</span><strong>{{ selectedClient.osType }}</strong>
          </div>
          <div class="drawer-meta-row">
            <span>客户端版本</span><strong>v{{ selectedClient.clientVersion }}</strong>
          </div>
          <div class="drawer-meta-row">
            <span>风险等级</span>
            <span :class="['risk-badge', selectedClient.riskLevel]">
              {{ riskLabel(selectedClient.riskLevel) }}
            </span>
          </div>
          <div class="drawer-meta-row">
            <span>最近扫描</span><strong>{{ formatTime(selectedClient.scanTime) }}</strong>
          </div>
        </div>

        <div class="drawer-section-title">发现的影子AI服务</div>

        <div v-if="parseServices(selectedClient).length === 0" class="drawer-empty">
          未发现未授权AI服务 ✓
        </div>

        <div
          v-for="svc in parseServices(selectedClient)"
          :key="svc.domain"
          :class="['drawer-service-item', svc.riskLevel]"
        >
          <div class="drawer-service-head">
            <span :class="['risk-badge', svc.riskLevel]">{{ riskLabel(svc.riskLevel) }}</span>
            <strong>{{ svc.name }}</strong>
            <el-tag size="small" type="info">{{ categoryLabel(svc.category) }}</el-tag>
          </div>
          <div class="drawer-service-meta">
            <span class="meta-item">🌐 {{ svc.domain }}</span>
            <span class="meta-item">📡 {{ sourceLabel(svc.source) }}</span>
            <span class="meta-item" v-if="svc.lastSeen">🕐 {{ formatTime(svc.lastSeen) }}</span>
          </div>
        </div>
      </div>
    </el-drawer>

  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  Refresh, Monitor, Warning, AlarmClock, Document,
  Download, Search, Loading,
} from '@element-plus/icons-vue';
import { shadowAiApi } from '../api/shadowAi';
import request from '../api/request';
import { useUserStore } from '../store/user';

// ── 检测是否在 Electron 客户端中运行 ──────────────────────────────────────────
const isElectron = typeof window !== 'undefined' && !!window.aegisClient;
const userStore = useUserStore();

// ── 状态 ──────────────────────────────────────────────────────────────────────
const loading        = ref(false);
const localScanning  = ref(false);
const isMock         = ref(false);
const stats          = ref({ totalClients: 0, highRiskClients: 0, totalShadowAi: 0, recentReports: 0, riskDistribution: {} });
const clients        = ref([]);
const searchKeyword  = ref('');
const drawerVisible  = ref(false);
const selectedClient = ref(null);

// 本地扫描结果（Electron 客户端专属或 Web 模式下使用服务端摘要）
const localScanResult = ref(null);
const localClientInfo = ref(null);

// 本地扫描是否已开启（用于决定下载时是否入云端队列）
const localScanEnabled = ref(false);

// 下载状态（按平台跟踪）
const downloading = ref(null);

// 云端扫描队列
const scanQueue   = ref([]);
const queueLoading = ref(false);
const isEmployeeView = computed(() => String(userStore.userInfo?.roleCode || '').toUpperCase() === 'EMPLOYEE');

// ── 计算属性 ──────────────────────────────────────────────────────────────────
const filteredClients = computed(() => {
  const kw = searchKeyword.value.toLowerCase();
  if (!kw) return clients.value;
  return clients.value.filter(c =>
    (c.hostname || '').toLowerCase().includes(kw) ||
    (c.osUsername || '').toLowerCase().includes(kw)
  );
});

const riskDistItems = computed(() => {
  const dist = stats.value.riskDistribution || {};
  const total = Object.values(dist).reduce((a, b) => a + b, 0) || 1;
  const order = ['high', 'medium', 'low', 'none'];
  return order.map(level => ({
    level,
    label: riskLabel(level),
    count: dist[level] || 0,
    pct: Math.round(((dist[level] || 0) / total) * 100),
  }));
});

// ── 工具函数 ──────────────────────────────────────────────────────────────────
function parseServices(client) {
  if (!client?.discoveredServices) return [];
  try {
    if (typeof client.discoveredServices === 'string') {
      return JSON.parse(client.discoveredServices);
    }
    return client.discoveredServices;
  } catch (e) {
    console.warn('[ShadowAI] Failed to parse discoveredServices:', e.message);
    return [];
  }
}

function riskLabel(level) {
  const map = { high: '高风险', medium: '中风险', low: '低风险', none: '安全' };
  return map[level] || level || '未知';
}

function categoryLabel(cat) {
  const map = {
    chat: '对话AI',
    image: '图像AI',
    search: 'AI搜索',
    code: '代码AI',
    local_llm: '本地LLM',
    embedding: '向量模型',
    other: '其他',
  };
  return map[cat] || cat || '未知';
}

function sourceLabel(src) {
  const map = {
    browser_history: '浏览器历史',
    network: '网络连接',
    process: '运行进程',
    dns: 'DNS记录',
  };
  return map[src] || src || '未知';
}

function formatTime(t) {
  if (!t) return '—';
  const d = new Date(t);
  if (isNaN(d)) return t;
  return d.toLocaleString('zh-CN', { hour12: false }).replace(/\//g, '-');
}

// ── 本地 Electron 扫描 ────────────────────────────────────────────────────────

/**
 * 将本地扫描结果（来自 Electron IPC）转换为客户端卡片格式，
 * 以便与服务端数据统一展示。
 */
function buildLocalClient(info, result) {
  return {
    clientId: info?.clientId || 'local',
    hostname: info?.hostname || '本机',
    osUsername: info?.osUsername || '',
    osType: info?.osType || '',
    clientVersion: '1.0.0',
    shadowAiCount: result?.shadowAiCount ?? 0,
    riskLevel: result?.riskLevel ?? 'none',
    scanTime: result?.time ?? new Date().toISOString(),
    discoveredServices: result?.services ?? [],
    _local: true,
  };
}

async function syncElectronAuthGate() {
  if (!isElectron || !window.aegisClient?.setAuthState) {
    return;
  }
  try {
    await window.aegisClient.setAuthState({
      authenticated: Boolean(userStore.token && userStore.userInfo),
      user: userStore.userInfo || null,
    });
  } catch (error) {
    console.warn('[ShadowAI] 同步 Electron 登录态失败:', error?.message || error);
  }
}

async function runLocalScan() {
  localScanning.value = true;
  localScanEnabled.value = true;
  try {
    if (isElectron) {
      await syncElectronAuthGate();
      // Electron 客户端模式：直接调用本机扫描 IPC
      let result = await window.aegisClient.runScan();
      if (result?.skipped) {
        // 兼容首次登录后主进程尚未刷新鉴权态的场景：自动重试一次。
        await syncElectronAuthGate();
        result = await window.aegisClient.runScan();
      }
      if (result?.skipped) {
        ElMessage.warning(result.reason || '请先登录后再开始扫描');
        return;
      }
      localScanResult.value = result;
      updateLocalClientEntry(result);
      ElMessage.success(`本机扫描完成，发现 ${result?.shadowAiCount ?? 0} 个影子AI服务`);
    } else {
      if (isEmployeeView.value) {
        ElMessage.info('员工 Web 模式仅可查看本机客户端检测结果，请在客户端执行扫描。');
        return;
      }
      // Web 模式：从服务端拉取最新扫描摘要作为"本机"结果展示
      const [s, c] = await Promise.all([
        shadowAiApi.getStats(),
        shadowAiApi.getClients(),
      ]);
      stats.value   = s;
      clients.value = c;
      isMock.value  = !!(s?._mock || c?.[0]?._mock);
      // 构造一个汇总结果显示在本地扫描面板
      const totalShadow = Number(s?.totalShadowAi ?? 0);
      const riskLevel   = s?.highRiskClients > 0 ? 'high' : totalShadow > 0 ? 'medium' : 'none';
      localScanResult.value = {
        time: new Date().toISOString(),
        shadowAiCount: totalShadow,
        riskLevel,
        services: (c ?? []).flatMap(client => {
          try {
            const svcs = typeof client.discoveredServices === 'string'
              ? JSON.parse(client.discoveredServices)
              : (client.discoveredServices ?? []);
            return Array.isArray(svcs) ? svcs : [];
          } catch { return []; }
        }).slice(0, 20),
      };
      ElMessage.success(`扫描完成（Web 模式），发现 ${totalShadow} 个影子AI服务`);
      // 同时刷新云端队列
      await refreshQueue();
    }
  } catch (err) {
    ElMessage.error('扫描失败：' + (err.message || '未知错误'));
  } finally {
    localScanning.value = false;
  }
}

function updateLocalClientEntry(result) {
  const localClient = buildLocalClient(localClientInfo.value, result);
  // 替换或插入本地客户端条目（排在最前面）
  const idx = clients.value.findIndex(c => c._local);
  if (idx >= 0) {
    clients.value.splice(idx, 1, localClient);
  } else {
    clients.value.unshift(localClient);
  }
  // 同步更新统计数据
  rebuildStats();
}

function rebuildStats() {
  const all = clients.value;
  const dist = { high: 0, medium: 0, low: 0, none: 0 };
  let totalShadowAi = 0;
  let highRiskClients = 0;
  for (const c of all) {
    dist[c.riskLevel] = (dist[c.riskLevel] || 0) + 1;
    totalShadowAi += c.shadowAiCount || 0;
    if (c.riskLevel === 'high') highRiskClients++;
  }
  stats.value = {
    ...stats.value,
    totalClients: all.length,
    highRiskClients,
    totalShadowAi,
    riskDistribution: dist,
  };
}

// ── 服务端数据加载 ────────────────────────────────────────────────────────────
async function refresh() {
  if (isEmployeeView.value) {
    loading.value = false;
    clients.value = [];
    stats.value = {
      totalClients: 1,
      highRiskClients: 0,
      totalShadowAi: localScanResult.value?.shadowAiCount || 0,
      recentReports: 0,
      riskDistribution: {},
    };
    if (isElectron) {
      try {
        localClientInfo.value = await window.aegisClient.getClientInfo();
        const lastResult = localClientInfo.value?.lastScanResult;
        if (lastResult && !lastResult.skipped) {
          localScanResult.value = lastResult;
        }
      } catch (e) {
        console.warn('[ShadowAI] Unable to load local client info:', e.message);
      }
    }
    return;
  }

  loading.value = true;
  try {
    const [s, c] = await Promise.all([
      shadowAiApi.getStats(),
      shadowAiApi.getClients(),
    ]);
    stats.value   = s;
    clients.value = c;
    isMock.value  = !!(s?._mock || c?.[0]?._mock);
  } catch (err) {
    ElMessage.error('加载失败：' + (err.message || '网络异常'));
  } finally {
    loading.value = false;
  }

  // 如果在 Electron 中运行，加载本地扫描历史并附加到列表
  if (isElectron) {
    try {
      localClientInfo.value = await window.aegisClient.getClientInfo();
      const lastResult = localClientInfo.value?.lastScanResult;
      if (lastResult) {
        localScanResult.value = lastResult;
        updateLocalClientEntry(lastResult);
      } else {
        // 无历史结果时添加占位条目，触发首次扫描
        const placeholder = buildLocalClient(localClientInfo.value, null);
        const idx = clients.value.findIndex(c => c._local);
        if (idx < 0) clients.value.unshift(placeholder);
        rebuildStats();
      }
    } catch (e) {
      console.warn('[ShadowAI] Unable to load local client info:', e.message);
    }
  }
}

function selectClient(client) {
  selectedClient.value = client;
  drawerVisible.value  = true;
}

// ── 客户端下载 ────────────────────────────────────────────────────────────────

/**
 * 触发客户端安装包下载。
 * 1. 通过 <a> 标签向 /api/download/client/{platform} 发起下载请求。
 * 2. 若本地扫描已开启，同时向云端队列提交一条入队记录。
 */
async function downloadClient(platform) {
  downloading.value = platform;
  try {
    // 使用隐藏 <a> 标签触发真实文件下载
    const link = document.createElement('a');
    link.href  = `/api/download/client/${platform}`;
    link.download = '';
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);

    ElMessage.success(`${platformLabel(platform)} 安装包已开始下载`);

    // 无论本地扫描是否开启，都将此次下载记录加入云端队列（以便追踪部署情况）
    try {
      await request.post('/client/queue', {
        platform,
      });
      localScanEnabled.value = true;
      await refreshQueue();
      ElMessage.info('下载任务已加入云端扫描队列');
    } catch (qErr) {
      console.warn('[ShadowAI] 队列入队失败:', qErr?.message);
    }
  } catch (err) {
    ElMessage.error(`下载失败：${err.message || '未知错误'}`);
  } finally {
    downloading.value = null;
  }
}

// ── 云端扫描队列 ──────────────────────────────────────────────────────────────

async function refreshQueue() {
  if (isEmployeeView.value) {
    scanQueue.value = [];
    return;
  }
  queueLoading.value = true;
  try {
    const data = await request.get('/client/queue');
    scanQueue.value = Array.isArray(data) ? data : [];
  } catch (err) {
    console.warn('[ShadowAI] 队列加载失败:', err?.message);
    scanQueue.value = [];
  } finally {
    queueLoading.value = false;
  }
}

// ── 辅助标签函数 ──────────────────────────────────────────────────────────────

function platformLabel(platform) {
  const map = { windows: '🪟 Windows', macos: '🍎 macOS', linux: '🐧 Linux' };
  return map[platform] || platform || '未知平台';
}

function queueStatusLabel(status) {
  const map = { queued: '等待安装', scanning: '扫描中', done: '已完成', failed: '失败' };
  return map[status] || status || '未知';
}

function shortenUA(ua) {
  if (!ua) return '';
  // 提取浏览器和OS的简短描述
  const match = ua.match(/(Chrome|Firefox|Safari|Edge|Opera)[/\s][\d.]+/i);
  return match ? match[0] : ua.slice(0, 40);
}

// 监听 Electron 扫描完成事件（后台定时扫描完成时自动更新）
function onScanComplete(result) {
  localScanResult.value = result;
  updateLocalClientEntry(result);
}

onMounted(() => {
  syncElectronAuthGate();
  refresh();
  refreshQueue();
  if (isElectron) {
    window.aegisClient.onScanComplete(onScanComplete);
  }
});

onUnmounted(() => {
  if (isElectron) {
    window.aegisClient.offScanComplete(onScanComplete);
  }
});
</script>

<style scoped>
.shadow-ai-page {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* ── 页头 ─────────────────────────────────────────────────────────────────── */
.page-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24px;
  flex-wrap: wrap;
  padding: 28px 32px;
  border-radius: var(--radius-lg);
  background: rgba(8, 16, 28, 0.6);
  border: 1px solid rgba(169, 196, 255, 0.1);
}

.page-eyebrow {
  font-size: 11px;
  font-weight: 800;
  letter-spacing: 0.24em;
  color: #64acff;
  text-transform: uppercase;
  margin-bottom: 6px;
}

.page-title {
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text);
  margin: 0 0 8px;
}

.page-subtitle {
  color: var(--color-text-muted);
  font-size: 14px;
  line-height: 1.6;
  margin: 0;
  max-width: 600px;
}

.page-header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
  padding-top: 4px;
}

/* ── 统计卡片 ─────────────────────────────────────────────────────────────── */
.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

@media (max-width: 1100px) {
  .stats-grid { grid-template-columns: repeat(2, 1fr); }
}
@media (max-width: 640px) {
  .stats-grid { grid-template-columns: 1fr; }
}

.stat-tile {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 20px 24px;
  border-radius: var(--radius-lg);
}

.stat-tile-icon {
  width: 48px;
  height: 48px;
  border-radius: 14px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22px;
  flex-shrink: 0;
}

.stat-tile-icon.clients  { background: rgba(34, 116, 255, 0.18); color: #64acff; }
.stat-tile-icon.shadow   { background: rgba(255, 198, 0, 0.18);  color: #ffc600; }
.stat-tile-icon.danger   { background: rgba(255, 77, 77, 0.18);  color: #ff6b6b; }
.stat-tile-icon.reports  { background: rgba(27, 217, 180, 0.18); color: #1bd9b4; }

.stat-tile-body {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.stat-tile-label {
  font-size: 12px;
  color: var(--color-text-muted);
  font-weight: 600;
  letter-spacing: 0.04em;
}

.stat-tile-value {
  font-size: 30px;
  font-weight: 700;
  color: var(--color-text);
  line-height: 1.1;
}

.stat-tile-value.danger-text { color: #ff6b6b; }

.stat-tile-hint {
  font-size: 11px;
  color: var(--color-text-muted);
}

/* ── 主网格 ───────────────────────────────────────────────────────────────── */
.main-grid {
  display: grid;
  grid-template-columns: 280px 1fr;
  gap: 20px;
  align-items: start;
}

@media (max-width: 1000px) {
  .main-grid { grid-template-columns: 1fr; }
}

.risk-dist-card, .clients-card {
  background: rgba(8, 16, 28, 0.5) !important;
  border: 1px solid rgba(169, 196, 255, 0.1) !important;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
}

.card-header {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text);
}

.panel-subtitle {
  font-size: 12px;
  color: var(--color-text-muted);
  margin: 4px 0 0;
  line-height: 1.5;
}

/* ── 风险分布 ─────────────────────────────────────────────────────────────── */
.risk-dist-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.risk-dist-item {}

.risk-dist-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.risk-dist-bar {
  height: 6px;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 3px;
  overflow: hidden;
}

.risk-dist-fill {
  display: block;
  height: 100%;
  border-radius: 3px;
  transition: width 0.6s ease;
}

.risk-dist-fill.high   { background: #ff6b6b; }
.risk-dist-fill.medium { background: #ffc600; }
.risk-dist-fill.low    { background: #64acff; }
.risk-dist-fill.none   { background: #1bd9b4; }

/* ── 下载区域 ─────────────────────────────────────────────────────────────── */
.download-btns {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-top: 10px;
}

.deploy-tip {
  margin-top: 10px;
  font-size: 11px;
  color: var(--color-text-muted);
  line-height: 1.6;
}

.deploy-tip a {
  color: #64acff;
  text-decoration: none;
}

/* ── 客户端列表 ───────────────────────────────────────────────────────────── */
.client-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
  max-height: 640px;
  overflow-y: auto;
  padding-right: 4px;
}

.client-card {
  padding: 16px 20px;
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all var(--transition-fast);
  border: 1px solid rgba(169, 196, 255, 0.08);
}

.client-card:hover {
  border-color: rgba(100, 172, 255, 0.3);
  transform: translateY(-1px);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
}

.client-card.high   { border-left: 3px solid #ff6b6b; }
.client-card.medium { border-left: 3px solid #ffc600; }
.client-card.low    { border-left: 3px solid #64acff; }
.client-card.none   { border-left: 3px solid #1bd9b4; }

.client-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}

.client-hostname {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text);
}

.client-meta {
  font-size: 11px;
  color: var(--color-text-muted);
  margin-top: 2px;
}

.client-risk {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex-shrink: 0;
}

.client-ai-count {
  font-size: 11px;
  color: var(--color-text-muted);
}

.client-services {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 10px;
}

.client-footer {
  font-size: 11px;
  color: var(--color-text-muted);
}

/* ── 风险标签 ─────────────────────────────────────────────────────────────── */
.risk-badge {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 999px;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
}

.risk-badge.high   { background: rgba(255, 77, 77, 0.18);  color: #ff8f8f; border: 1px solid rgba(255, 77, 77, 0.3); }
.risk-badge.medium { background: rgba(255, 198, 0, 0.18);  color: #ffd557; border: 1px solid rgba(255, 198, 0, 0.3); }
.risk-badge.low    { background: rgba(34, 116, 255, 0.18); color: #7ab8ff; border: 1px solid rgba(34, 116, 255, 0.3); }
.risk-badge.none   { background: rgba(27, 217, 180, 0.18); color: #1bd9b4; border: 1px solid rgba(27, 217, 180, 0.3); }

/* ── 服务标签 ─────────────────────────────────────────────────────────────── */
.service-chip {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}

.service-chip.high   { background: rgba(255, 77, 77, 0.14);  color: #ff8f8f; }
.service-chip.medium { background: rgba(255, 198, 0, 0.14);  color: #ffd557; }
.service-chip.low    { background: rgba(34, 116, 255, 0.14); color: #7ab8ff; }
.service-chip.more   { background: rgba(255, 255, 255, 0.06); color: var(--color-text-muted); }

/* ── 加载/空状态 ──────────────────────────────────────────────────────────── */
.loading-state, .empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
  padding: 48px 24px;
  color: var(--color-text-muted);
  font-size: 14px;
}

.loading-state .el-icon { font-size: 28px; }
.empty-state   .el-icon { font-size: 36px; }

/* ── 详情抽屉 ─────────────────────────────────────────────────────────────── */
.drawer-content {
  padding: 0 4px;
}

.drawer-meta {
  background: rgba(255, 255, 255, 0.03);
  border-radius: var(--radius-md);
  padding: 16px 20px;
  margin-bottom: 24px;
}

.drawer-meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.05);
  font-size: 13px;
}

.drawer-meta-row:last-child { border-bottom: none; }
.drawer-meta-row span { color: var(--color-text-muted); }
.drawer-meta-row strong { color: var(--color-text); }

.drawer-section-title {
  font-size: 14px;
  font-weight: 700;
  color: var(--color-text);
  margin-bottom: 12px;
}

.drawer-empty {
  padding: 24px;
  text-align: center;
  color: #1bd9b4;
  font-size: 14px;
  background: rgba(27, 217, 180, 0.06);
  border-radius: var(--radius-md);
}

.drawer-service-item {
  padding: 14px 16px;
  border-radius: var(--radius-md);
  margin-bottom: 10px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(169, 196, 255, 0.08);
}

.drawer-service-item.high   { border-left: 3px solid #ff6b6b; }
.drawer-service-item.medium { border-left: 3px solid #ffc600; }
.drawer-service-item.low    { border-left: 3px solid #64acff; }

.drawer-service-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.drawer-service-head strong {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.drawer-service-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.meta-item {
  font-size: 12px;
  color: var(--color-text-muted);
}

.scan-time { font-size: 11px; }

/* ── 本机实时扫描面板 ─────────────────────────────────────────────────────── */
.local-scan-panel {
  padding: 24px 28px;
  border-radius: var(--radius-lg);
  background: rgba(8, 28, 18, 0.65);
  border: 1px solid rgba(27, 217, 180, 0.25);
}

.local-scan-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 20px;
}

.local-scan-status {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.local-scan-loading,
.local-scan-empty {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 20px;
  color: var(--color-text-muted);
  font-size: 14px;
  background: rgba(255, 255, 255, 0.02);
  border-radius: var(--radius-md);
}

.local-scan-meta {
  display: flex;
  gap: 24px;
  font-size: 13px;
  color: var(--color-text-muted);
  margin-bottom: 16px;
}

.local-scan-meta strong {
  color: var(--color-text);
}

.local-services-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 12px;
}

.local-service-card {
  padding: 14px 16px;
  border-radius: var(--radius-md);
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(169, 196, 255, 0.08);
}

.local-service-card.high   { border-left: 3px solid #ff6b6b; }
.local-service-card.medium { border-left: 3px solid #ffc600; }
.local-service-card.low    { border-left: 3px solid #64acff; }

.local-service-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}

.local-service-head strong {
  flex: 1;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text);
}

.local-service-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 12px;
  color: var(--color-text-muted);
}

/* ── 局域网部署指引 ───────────────────────────────────────────────────────── */
.lan-guide-section {
  margin-top: 8px;
  padding-top: 20px;
  border-top: 1px solid rgba(169, 196, 255, 0.08);
}

.lan-guide-steps {
  margin: 0;
  padding-left: 20px;
  list-style: decimal;
}

.lan-guide-steps li {
  font-size: 13px;
  color: var(--color-text-muted);
  line-height: 1.7;
  margin-bottom: 8px;
}

.lan-guide-steps li strong {
  color: var(--color-text);
}

.lan-guide-steps li code {
  background: rgba(100, 172, 255, 0.12);
  color: #64acff;
  padding: 1px 6px;
  border-radius: 4px;
  font-size: 12px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
}

/* ── 云端扫描队列 ─────────────────────────────────────────────────────────── */
.cloud-queue-panel {
  padding: 24px 28px;
  border-radius: var(--radius-lg);
  background: rgba(8, 16, 28, 0.6);
  border: 1px solid rgba(169, 196, 255, 0.1);
}

.cloud-queue-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.cloud-queue-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.cloud-queue-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.cloud-queue-item {
  padding: 14px 18px;
  border-radius: var(--radius-md, 10px);
  border: 1px solid rgba(169, 196, 255, 0.08);
  transition: border-color 0.2s;
}

.cloud-queue-item.queued   { border-left: 3px solid #64acff; }
.cloud-queue-item.scanning { border-left: 3px solid #ffc600; }
.cloud-queue-item.done     { border-left: 3px solid #1bd9b4; }
.cloud-queue-item.failed   { border-left: 3px solid #ff6b6b; }

.cloud-queue-item-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
  flex-wrap: wrap;
}

.queue-platform-badge {
  font-size: 12px;
  font-weight: 700;
  padding: 2px 8px;
  border-radius: 6px;
  background: rgba(100, 172, 255, 0.15);
  color: #64acff;
}

.queue-status-badge {
  font-size: 11px;
  font-weight: 600;
  padding: 2px 8px;
  border-radius: 6px;
}
.queue-status-badge.queued   { background: rgba(100,172,255,0.15); color: #64acff; }
.queue-status-badge.scanning { background: rgba(255,198,0,0.15);   color: #ffc600; }
.queue-status-badge.done     { background: rgba(27,217,180,0.15);  color: #1bd9b4; }
.queue-status-badge.failed   { background: rgba(255,107,107,0.15); color: #ff6b6b; }

.queue-hostname {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text);
}

.cloud-queue-item-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: var(--color-text-muted);
}

.ua-hint {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.cloud-queue-scan-result {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 8px;
  font-size: 12px;
  color: #1bd9b4;
}

.loading-state,
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 24px;
  color: var(--color-text-muted);
  font-size: 13px;
}
</style>
