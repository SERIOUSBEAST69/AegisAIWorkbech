import request from './request';

export const dashboardApi = {
  async getStats() {
    return request.get('/dashboard/stats');
  },

  async getRiskTrend() {
    return request.get('/risk/trend');
  },

  async getInsights() {
    return request.get('/dashboard/insights');
  },

  async getWorkbench() {
    return request.get('/dashboard/workbench');
  },

  async getTrustPulse() {
    return request.get('/dashboard/trust-pulse');
  },

  /**
   * LSTM 风险预测：获取未来 7 天的风险事件数预测序列。
   * 对应后端 /api/risk/forecast，底层调用 Python 微服务 LSTM 模型。
   */
  async getForecast() {
    return request.get('/risk/forecast');
  },

  async getAwardSummary() {
    return request.get('/award/summary');
  },

  async getExperimentReport(params) {
    return request.get('/award/experiment-report', { params });
  },

  async generateComplianceEvidence(payload) {
    return request.post('/award/compliance-evidence/generate', payload || {});
  },

  async runReliabilityDrill(payload) {
    return request.post('/award/reliability/drill/run', payload || {});
  },

  async getWebVitalSummary(days = 7) {
    return request.get('/ops-metrics/web-vitals/summary', { params: { days } });
  },

  async getHttpHistory(days = 7, api = '') {
    return request.get('/ops-metrics/http-history', { params: { days, api } });
  },

  async getInnovationReport(params) {
    return request.get('/award/innovation-report', { params });
  },
};
