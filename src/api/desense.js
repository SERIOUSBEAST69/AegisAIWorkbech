import request from './request';

export const desenseApi = {
  /**
   * 动态脱敏策略推荐
   * @param {string} dataCategory  数据类别（如 id_card, phone, email …）
   * @param {string} userRole      调用方角色
   * @param {string} sensitivityLevel  敏感级别（low / medium / high / critical）
   */
  async recommend(dataCategory, userRole, sensitivityLevel) {
    return request.post('/desense/recommend', { dataCategory, userRole, sensitivityLevel });
  },

  /** 获取所有脱敏规则 */
  async listRules() {
    return request.get('/desense/rules');
  },

  /** 预览脱敏效果 */
  async preview(payload) {
    return request.post('/desense/preview', payload);
  },

  /** 一键脱敏执行 */
  async execute(payload) {
    return request.post('/desense/execute', payload);
  },

  /** 保存或更新脱敏规则 */
  async saveRule(rule) {
    return request.post('/desense/save', rule);
  },

  /** 删除脱敏规则 */
  async deleteRule(id) {
    return request.post('/desense/delete', { id });
  }
};
