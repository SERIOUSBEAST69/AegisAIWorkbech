/**
 * auth.js — 认证 API 服务层
 *
 * 通过环境变量 VITE_USE_MOCK=true 启用 Mock 模式：
 *   任意非空用户名 + 密码长度 ≥ 4 即视为登录成功。
 * 生产环境会调用真实后端接口。
 */
import request from './request';
import { getSessionUserFallback, shouldUseApiFallback } from './fallback';

const USE_MOCK = import.meta.env.VITE_USE_MOCK === 'true';

function normalizeAuthResponse(response, mode) {
  return {
    token: response?.token,
    user: response?.user || response?.userInfo || null,
    pendingApproval: Boolean(response?.pendingApproval),
    accountStatus: response?.accountStatus,
    message: response?.message,
    mode,
  };
}

// ---------- Mock 实现 ----------
function mockLogin({ username, password }) {
  return new Promise((resolve, reject) => {
    // 模拟网络延迟 600ms
    setTimeout(() => {
      if (!username || !password || password.length < 4) {
        reject({ message: '用户名或密码不正确（Mock 模式）' });
      } else {
        resolve({
          token: 'mock-jwt-token-' + Date.now(),
          user: {
            id: 1,
            companyId: 1,
            username,
            nickname: '管理员',
            roles: ['ADMIN'],
          },
        });
      }
    }, 600);
  });
}

function mockLogout() {
  return Promise.resolve();
}

function buildMockSession(identity = 'ADMIN', overrides = {}) {
  return {
    token: 'mock-jwt-token-' + Date.now(),
    user: {
      id: 1,
      companyId: Number(overrides.companyId || 1),
      username: overrides.username || overrides.phone || 'demo-user',
      nickname: overrides.nickname || '演示用户',
      realName: overrides.realName || overrides.nickname || '演示用户',
      phone: overrides.phone || '13800138000',
      department: overrides.department || '体验中心',
      roleName: overrides.roleName || overrides.roleCode || identity,
      roleCode: overrides.roleCode || identity,
      organizationType: overrides.organizationType || 'enterprise',
      loginType: overrides.loginType || 'mock',
      accountType: overrides.accountType || 'demo',
      accountStatus: overrides.accountStatus || 'active',
    },
  };
}

// ---------- 真实 API ----------
function realLogin(payload) {
  return request.post('/auth/login', payload);
}

function realLogout() {
  return request.post('/auth/logout');
}

function realPhoneLogin(payload) {
  return request.post('/auth/login-phone', payload);
}

function realWechatLogin(payload) {
  return request.post('/auth/login-wechat', payload);
}

function realRegister(payload) {
  return request.post('/auth/register', payload);
}

function realSendPhoneCode(payload) {
  return request.post('/auth/phone-code', payload);
}

function realRegistrationOptions() {
  return request.get('/auth/registration-options');
}

function normalizeCurrentUserResponse(response) {
  if (response?.user) {
    return response;
  }
  return {
    user: response || null,
  };
}

async function getCurrentUser() {
  try {
    const response = await request.get('/auth/me');
    return normalizeCurrentUserResponse(response);
  } catch (error) {
    if (!USE_MOCK && shouldUseApiFallback(error)) {
      try {
        const profile = await request.get('/user/profile');
        return normalizeCurrentUserResponse(profile);
      } catch (profileError) {
        if (shouldUseApiFallback(profileError)) {
          return normalizeCurrentUserResponse(getSessionUserFallback());
        }
        throw profileError;
      }
    }
    throw error;
  }
}

// ---------- 导出 ----------
export const authApi = {
  /**
   * 登录
   * @param {{ username: string, password: string, captcha?: string }} payload
   * @returns {Promise<{ token: string, userInfo: object }>}
   */
  login(payload) {
    if (USE_MOCK) {
      return mockLogin(payload).then(response => normalizeAuthResponse(response, 'mock'));
    }
    return realLogin(payload).then(response => normalizeAuthResponse(response, 'real'));
  },

  loginByPhone(payload) {
    if (USE_MOCK) {
      return Promise.resolve(normalizeAuthResponse(buildMockSession(payload.roleCode || 'BUSINESS_OWNER', {
        phone: payload.phone,
        username: payload.phone,
        loginType: 'phone',
      }), 'mock'));
    }
    return realPhoneLogin(payload).then(response => normalizeAuthResponse(response, 'real'));
  },

  loginByWechat(payload) {
    if (USE_MOCK) {
      return Promise.resolve(normalizeAuthResponse(buildMockSession(payload.roleCode || 'BUSINESS_OWNER', {
        username: payload.wechatOpenId || payload.nickname || 'wechat-user',
        nickname: payload.nickname || '微信用户',
        phone: payload.phone,
        loginType: 'wechat',
        organizationType: payload.organizationType,
      }), 'mock'));
    }
    return realWechatLogin(payload).then(response => normalizeAuthResponse(response, 'real'));
  },

  register(payload) {
    if (USE_MOCK) {
      const accountType = payload.accountType || 'real';
      if (accountType === 'real') {
        return Promise.resolve(normalizeAuthResponse({
          pendingApproval: true,
          accountStatus: 'pending',
          message: '注册申请已提交，等待管理员审批',
          user: {
            username: payload.username || payload.phone || payload.wechatOpenId || 'new-user',
            realName: payload.realName,
            nickname: payload.nickname || payload.realName,
            accountType: 'real',
            accountStatus: 'pending',
            companyName: payload.companyName || '',
          }
        }, 'mock'));
      }
      return Promise.resolve(normalizeAuthResponse(buildMockSession(payload.roleCode || 'BUSINESS_OWNER', {
        username: payload.username || payload.phone || payload.wechatOpenId || 'new-user',
        nickname: payload.nickname || payload.realName,
        realName: payload.realName,
        phone: payload.phone,
        department: payload.department,
        organizationType: payload.organizationType,
        loginType: payload.loginType || 'password',
        accountType,
        accountStatus: 'active',
      }), 'mock'));
    }
    return realRegister(payload).then(response => normalizeAuthResponse(response, 'real'));
  },

  sendPhoneCode(payload) {
    if (USE_MOCK) {
      return Promise.resolve({
        phone: payload.phone,
        codeHint: '123456',
        message: 'Mock 模式验证码固定为 123456',
      });
    }
    return realSendPhoneCode(payload);
  },

  getRegistrationOptions() {
    if (USE_MOCK) {
      return Promise.resolve({
        identities: [
          { code: 'ADMIN', label: '治理管理员' },
          { code: 'EXECUTIVE', label: '管理层' },
          { code: 'SECOPS', label: '安全运维' },
          { code: 'DATA_ADMIN', label: '数据管理员' },
          { code: 'AI_BUILDER', label: 'AI应用开发者' },
          { code: 'BUSINESS_OWNER', label: '业务负责人' },
          { code: 'EMPLOYEE', label: '普通员工' },
        ],
        organizations: [
          { code: 'enterprise', label: '企业' },
          { code: 'school', label: '学校' },
          { code: 'ai-team', label: 'AI应用团队' },
          { code: 'public-sector', label: '政企/公共机构' },
        ],
      });
    }
    return realRegistrationOptions();
  },

  /**
   * 退出登录
   * @returns {Promise<void>}
   */
  logout() {
    return USE_MOCK ? mockLogout() : realLogout();
  },

  getCurrentUser,
};
