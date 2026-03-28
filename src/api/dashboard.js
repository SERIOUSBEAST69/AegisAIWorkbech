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
};
