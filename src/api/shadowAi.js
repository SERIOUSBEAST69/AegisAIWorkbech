import request from './request';

export const shadowAiApi = {
  /**
   * 获取影子AI治理统计摘要（供工作台和视图使用）。
   */
  async getStats() {
    return request.get('/client/stats');
  },

  /**
   * 获取所有客户端最新扫描报告列表。
   */
  async getClients() {
    return request.get('/client/list');
  },

  /**
   * 获取指定客户端的历史扫描记录。
   */
  async getHistory(clientId) {
    return request.get('/client/history', { params: { clientId } });
  },
};
