import request from './request';
import { isMockSession } from '../utils/auth';
import { shouldUseApiFallback } from './fallback';

// ── Mock数据 ──────────────────────────────────────────────────────────────────

const MOCK_STATS = {
  totalClients: 8,
  highRiskClients: 2,
  totalShadowAi: 14,
  recentReports: 23,
  riskDistribution: { none: 3, low: 2, medium: 1, high: 2 },
  _mock: true,
};

/**
 * 演示用模拟数据。
 * 当后端服务不可用时（如 404 / 网络故障），展示此数据以演示界面能力。
 * 所有设备名与用户名均为占位示例，不代表真实人员。
 * 真实数据来源：已安装 Aegis 客户端的终端通过 /api/client/report 接口上报。
 */
const MOCK_CLIENTS = [
  {
    id: 1,
    clientId: 'demo-client-001',
    hostname: 'WIN-WORKSTATION-01',
    osUsername: 'employee_a',
    osType: 'Windows',
    clientVersion: '1.0.0',
    shadowAiCount: 4,
    riskLevel: 'high',
    scanTime: '2026-03-14T08:30:00',
    _isMockData: true,  // 标记此为演示数据，UI 可据此展示免责说明
    discoveredServices: JSON.stringify([
      { name: 'ChatGPT', domain: 'chat.openai.com', category: 'chat', source: 'browser_history', riskLevel: 'high', lastSeen: '2026-03-14T08:25:00' },
      { name: 'Claude', domain: 'claude.ai', category: 'chat', source: 'browser_history', riskLevel: 'high', lastSeen: '2026-03-14T07:50:00' },
      { name: 'Midjourney', domain: 'midjourney.com', category: 'image', source: 'browser_history', riskLevel: 'medium', lastSeen: '2026-03-13T16:20:00' },
      { name: 'Perplexity', domain: 'perplexity.ai', category: 'search', source: 'network', riskLevel: 'medium', lastSeen: '2026-03-14T08:10:00' },
    ]),
  },
  {
    id: 2,
    clientId: 'demo-client-002',
    hostname: 'WIN-NOTEBOOK-02',
    osUsername: 'employee_b',
    osType: 'Windows',
    clientVersion: '1.0.0',
    shadowAiCount: 3,
    riskLevel: 'medium',
    scanTime: '2026-03-14T09:00:00',
    _isMockData: true,
    discoveredServices: JSON.stringify([
      { name: 'Kimi', domain: 'kimi.moonshot.cn', category: 'chat', source: 'browser_history', riskLevel: 'medium', lastSeen: '2026-03-14T08:55:00' },
      { name: 'Doubao', domain: 'doubao.com', category: 'chat', source: 'browser_history', riskLevel: 'medium', lastSeen: '2026-03-14T09:00:00' },
      { name: 'Ollama', domain: 'localhost:11434', category: 'local_llm', source: 'process', riskLevel: 'medium', lastSeen: '2026-03-14T09:00:00' },
    ]),
  },
  {
    id: 3,
    clientId: 'demo-client-003',
    hostname: 'MAC-WORKSTATION-03',
    osUsername: 'employee_c',
    osType: 'macOS',
    clientVersion: '1.0.0',
    shadowAiCount: 1,
    riskLevel: 'low',
    scanTime: '2026-03-14T07:00:00',
    _isMockData: true,
    discoveredServices: JSON.stringify([
      { name: 'Gemini', domain: 'gemini.google.com', category: 'chat', source: 'browser_history', riskLevel: 'low', lastSeen: '2026-03-13T20:00:00' },
    ]),
  },
  {
    id: 4,
    clientId: 'demo-client-004',
    hostname: 'WIN-WORKSTATION-04',
    osUsername: 'employee_d',
    osType: 'Windows',
    clientVersion: '1.0.0',
    shadowAiCount: 0,
    riskLevel: 'none',
    scanTime: '2026-03-14T09:10:00',
    _isMockData: true,
    discoveredServices: JSON.stringify([]),
  },
];

// ── API ───────────────────────────────────────────────────────────────────────

export const shadowAiApi = {
  /**
   * 获取影子AI治理统计摘要（供工作台和视图使用）。
   */
  async getStats() {
    if (isMockSession()) {
      return { ...MOCK_STATS, _mock: true };
    }
    try {
      return await request.get('/client/stats');
    } catch (error) {
      if (shouldUseApiFallback(error)) {
        return { ...MOCK_STATS, _mock: true };
      }
      throw error;
    }
  },

  /**
   * 获取所有客户端最新扫描报告列表。
   */
  async getClients() {
    if (isMockSession()) {
      return MOCK_CLIENTS.map(c => ({ ...c, _mock: true }));
    }
    try {
      return await request.get('/client/list');
    } catch (error) {
      if (shouldUseApiFallback(error)) {
        return MOCK_CLIENTS.map(c => ({ ...c, _mock: true }));
      }
      throw error;
    }
  },

  /**
   * 获取指定客户端的历史扫描记录。
   */
  async getHistory(clientId) {
    if (isMockSession()) {
      return MOCK_CLIENTS.filter(c => c.clientId === clientId).map(c => ({ ...c, _mock: true }));
    }
    try {
      return await request.get('/client/history', { params: { clientId } });
    } catch (error) {
      if (shouldUseApiFallback(error)) {
        return [];
      }
      throw error;
    }
  },
};
