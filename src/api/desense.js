import request from './request';
import { isMockSession } from '../utils/auth';
import { shouldUseApiFallback } from './fallback';

const MOCK_STRATEGIES = {
  id_card: [
    { ruleId: 1, name: '身份证脱敏', pattern: '\\d{18}', mask: '***', strategy: 'mask', priority: 1, reason: '身份证号属于最高级敏感字段，建议全字段遮蔽' }
  ],
  phone: [
    { ruleId: 2, name: '手机号中间位脱敏', pattern: '1[3-9]\\d{9}', mask: '138****8000', strategy: 'partial', priority: 1, reason: '手机号保留首3位与末4位，兼顾可用性与合规' }
  ],
  bank_card: [
    { ruleId: 3, name: '银行卡号脱敏', pattern: '\\d{12,19}', mask: '****', strategy: 'mask', priority: 1, reason: '银行卡号属于金融敏感字段，建议全字段遮蔽' }
  ],
  email: [
    { ruleId: 4, name: '邮箱用户名脱敏', pattern: '[^@]+@.+', mask: '***@domain.com', strategy: 'partial', priority: 2, reason: '邮箱保留域名部分有助于溯源，用户名部分脱敏' }
  ],
  address: [
    { ruleId: 5, name: '地址模糊化', pattern: '.+', mask: '**省**市**区', strategy: 'generalize', priority: 2, reason: '地址字段建议泛化到区级，保留统计价值' }
  ],
  name: [
    { ruleId: 6, name: '姓名首字保留', pattern: '.{2,}', mask: '张**', strategy: 'partial', priority: 2, reason: '姓名保留首字，其余星号替代，满足数据可用性' }
  ],
  default: [
    { ruleId: 7, name: '通用字段脱敏', pattern: '.+', mask: '***', strategy: 'mask', priority: 3, reason: '未识别字段类型，建议默认全字段遮蔽' }
  ]
};

function mockRecommend(dataCategory) {
  const category = (dataCategory || '').toLowerCase();
  return MOCK_STRATEGIES[category] || MOCK_STRATEGIES.default;
}

export const desenseApi = {
  /**
   * 动态脱敏策略推荐
   * @param {string} dataCategory  数据类别（如 id_card, phone, email …）
   * @param {string} userRole      调用方角色
   * @param {string} sensitivityLevel  敏感级别（low / medium / high / critical）
   */
  async recommend(dataCategory, userRole, sensitivityLevel) {
    if (isMockSession()) {
      return mockRecommend(dataCategory);
    }
    try {
      return await request.post('/desense/recommend', { dataCategory, userRole, sensitivityLevel });
    } catch (error) {
      if (shouldUseApiFallback(error)) {
        return mockRecommend(dataCategory);
      }
      throw error;
    }
  },

  /** 获取所有脱敏规则 */
  async listRules() {
    if (isMockSession()) {
      return Object.values(MOCK_STRATEGIES).flat();
    }
    try {
      return await request.get('/desense/rules');
    } catch (error) {
      if (shouldUseApiFallback(error)) {
        return Object.values(MOCK_STRATEGIES).flat();
      }
      throw error;
    }
  },

  /** 预览脱敏效果 */
  async preview(payload) {
    const raw = payload?.text || '';
    const maskChar = (payload?.mask || '*').charAt(0) || '*';
    const masked = raw.replace(/./g, maskChar);
    if (isMockSession()) {
      return { raw, masked };
    }
    try {
      return await request.post('/desense/preview', payload);
    } catch (error) {
      if (shouldUseApiFallback(error)) {
        return { raw, masked };
      }
      throw error;
    }
  },

  /** 一键脱敏执行 */
  async execute(payload) {
    const raw = payload?.text || payload?.sample || '';
    const maskChar = (payload?.mask || '*').charAt(0) || '*';
    const masked = raw.replace(/./g, maskChar);
    if (isMockSession()) {
      return { raw, masked, executedAt: Date.now(), ruleId: payload?.ruleId || null };
    }
    try {
      return await request.post('/desense/execute', payload);
    } catch (error) {
      if (shouldUseApiFallback(error)) {
        return { raw, masked, executedAt: Date.now(), ruleId: payload?.ruleId || null };
      }
      throw error;
    }
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
