import request from './request';
import { isMockSession } from '../utils/auth';
import { getSessionUserFallback, shouldUseApiFallback } from './fallback';

const FALLBACK_STATS = {
  dataAsset: 128,
  aiModel: 14,
  user: 62,
  riskEvent: 7,
};

const FALLBACK_TREND = {
  perHour: {
    8: 3,
    10: 5,
    12: 2,
    14: 7,
    16: 4,
    18: 6,
  },
  forecastNextHour: 5,
};

const EMPTY_INSIGHTS = {
  postureScore: 86,
  summary: {
    highSensitivityAssets: 12,
    openRiskEvents: 3,
    highRiskEvents: 1,
    highRiskModels: 2,
    pendingSubjectRequests: 2,
    todayAuditCount: 48,
    totalAiCalls: 320,
    totalCostCents: 12600,
  },
  highlights: [
    { title: '高敏资产纳管', value: '12 项', description: '演示环境数据，模拟校园与中小企业的核心高敏资产。' },
    { title: 'AI 模型治理', value: '2 个高风险模型', description: '展示模型分级接入与成本纳管后的治理视角。' },
  ],
  recommendations: [
    { code: 'demo-risk-gate', priority: 'P0', title: '优先闭环高风险模型调用', description: '当前为演示降级数据，可用于展示风险闭环流程。', route: '/risk-event-manage', metric: '2 个重点模型' },
  ],
};

const EMPTY_TRUST_PULSE = {
  score: 82,
  pulseLevel: '可控推进',
  mission: '当前处于可控推进区间，建议把资源集中在风险压降和流程提速。',
  innovationLabel: '治理脉冲引擎 · Governance Pulse Engine',
  dimensions: [
    { code: 'data', label: '数据边界', score: 78, description: '高敏资产持续纳管，需保持共享与脱敏边界一致。' },
    { code: 'model', label: '模型可信', score: 84, description: '模型准入与额度处于受控状态。' },
    { code: 'process', label: '流程闭环', score: 76, description: '审批与主体履约仍有压缩空间。' },
    { code: 'audit', label: '审计准备度', score: 90, description: '审计留痕足够支撑抽查与复盘。' },
  ],
  signals: [
    { title: '高危风险', value: '3', tone: 'danger', action: '优先压降高危事件' },
    { title: '模型接入', value: '14 / 2', tone: 'warning', action: '检查模型额度与准入条件' },
    { title: '流程积压', value: '4', tone: 'warning', action: '缩短共享审批与主体履约时延' },
  ],
};

const MOCK_WORKBENCH = {
  operator: {
    displayName: '演示管理员',
    roleName: '演示管理员',
    department: '可信AI治理中心',
    avatar: ''
  },
  headline: '可信AI数据治理与隐私合规工作台',
  subheadline: '演示环境下，平台统一聚合高敏资产、模型准入、审计证据链与主体权利履约态势。',
  sceneTags: ['审计证据链', '高敏资产纳管', '模型分级准入', '主体权利履约'],
  metrics: [
    { key: 'assets', label: '高敏资产纳管', value: 12, suffix: '项', delta: 28, hint: '近7日新增高敏资产纳管规模' },
    { key: 'alerts', label: '待闭环告警', value: 5, suffix: '条', delta: 18, hint: '仍需人工处置的风险与告警压力' },
    { key: 'aiCalls', label: '7日AI调用', value: 320, suffix: '次', delta: 14, hint: '模型真实调用量与治理压力' },
    { key: 'audits', label: '今日审计留痕', value: 48, suffix: '条', delta: 9, hint: '面向监管抽查的证据链产出' },
  ],
  trend: {
    labels: ['3/05', '3/06', '3/07', '3/08', '3/09', '3/10', '3/11'],
    riskSeries: [3, 4, 6, 5, 7, 6, 8],
    auditSeries: [22, 26, 28, 31, 30, 35, 40],
    aiCallSeries: [38, 44, 42, 48, 53, 46, 49],
    costSeries: [1200, 1440, 1360, 1580, 1710, 1620, 1800],
    forecastNextDay: 7,
  },
  riskDistribution: [
    { level: '高危', value: 3 },
    { level: '中危', value: 4 },
    { level: '低危', value: 2 },
    { level: '待研判', value: 1 },
  ],
  todos: [
    { priority: 'P0', title: '闭环高风险事件', description: '优先压降高风险事件，避免平台只监测不处置。', route: '/risk-event-manage', metric: '3 个高危事件' },
    { priority: 'P1', title: '履约主体权利请求', description: '访问、删除、导出类工单仍在队列中，影响隐私履约体验。', route: '/subject-request', metric: '2 个待处理工单' },
    { priority: 'P1', title: '巡检启用AI能力', description: '核验高风险模型额度、状态与绑定资产是否仍符合最新策略。', route: '/ai/risk-rating', metric: '14 个启用模型' },
  ],
  feeds: [
    { level: 'high', title: '风险事件 · 敏感数据异常导出', description: '状态：open，处置日志：待法务与安全联合复核。', route: '/risk-event-manage', timeLabel: '03-11 10:15' },
    { level: 'medium', title: '风险态势 · AI调用阈值逼近', description: '状态：claimed，处置说明：正在核验高风险模型额度。', route: '/risk-event-manage', timeLabel: '03-11 09:30' },
    { level: 'processing', title: '主体权利 · export', description: '请求导出个人数据副本，当前状态：processing', route: '/subject-request', timeLabel: '03-11 08:40' },
  ]
};

function buildWorkbenchFallback() {
  const user = getSessionUserFallback();
  return {
    ...MOCK_WORKBENCH,
    operator: {
      ...MOCK_WORKBENCH.operator,
      displayName: user?.nickname || user?.realName || user?.username || MOCK_WORKBENCH.operator.displayName,
      roleName: user?.roleName || user?.roleCode || MOCK_WORKBENCH.operator.roleName,
      department: user?.department || MOCK_WORKBENCH.operator.department,
      avatar: user?.avatar || MOCK_WORKBENCH.operator.avatar,
    },
  };
}

const FALLBACK_FORECAST = {
  forecast: [5.2, 6.0, 4.8, 7.1, 6.5, 5.9, 8.0],
  horizon: 7,
};

export const dashboardApi = {
  async getStats() {
    if (isMockSession()) {
      return { ...FALLBACK_STATS, _mock: true };
    }
    try {
      return await request.get('/dashboard/stats');
    } catch (error) {
      if (shouldUseApiFallback(error)) {
        return { ...FALLBACK_STATS, _mock: true };
      }
      throw error;
    }
  },

  async getRiskTrend() {
    if (isMockSession()) {
      return { ...FALLBACK_TREND, _mock: true };
    }
    try {
      return await request.get('/risk/trend');
    } catch (error) {
      if (shouldUseApiFallback(error)) {
        return { ...FALLBACK_TREND, _mock: true };
      }
      throw error;
    }
  },

  async getInsights() {
    if (isMockSession()) {
      return { ...EMPTY_INSIGHTS, _mock: true };
    }
    try {
      return await request.get('/dashboard/insights');
    } catch (error) {
      if (shouldUseApiFallback(error)) {
        return { ...EMPTY_INSIGHTS, _mock: true };
      }
      throw error;
    }
  },

  async getWorkbench() {
    if (isMockSession()) {
      return { ...buildWorkbenchFallback(), _mock: true };
    }
    try {
      return await request.get('/dashboard/workbench');
    } catch (error) {
      if (shouldUseApiFallback(error)) {
        return { ...buildWorkbenchFallback(), _mock: true };
      }
      throw error;
    }
  },

  async getTrustPulse() {
    if (isMockSession()) {
      return { ...EMPTY_TRUST_PULSE, _mock: true };
    }
    try {
      return await request.get('/dashboard/trust-pulse');
    } catch (error) {
      if (shouldUseApiFallback(error)) {
        return { ...EMPTY_TRUST_PULSE, _mock: true };
      }
      throw error;
    }
  },

  /**
   * LSTM 风险预测：获取未来 7 天的风险事件数预测序列。
   * 对应后端 /api/risk/forecast，底层调用 Python 微服务 LSTM 模型。
   */
  async getForecast() {
    if (isMockSession()) {
      return { ...FALLBACK_FORECAST, _mock: true };
    }
    try {
      return await request.get('/risk/forecast');
    } catch (error) {
      if (shouldUseApiFallback(error)) {
        return { ...FALLBACK_FORECAST, _mock: true };
      }
      throw error;
    }
  },
};
