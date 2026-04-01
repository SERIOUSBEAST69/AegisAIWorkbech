import request from './request';

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

function realRegistrationOptions(companyId) {
  if (companyId) {
    return request.get('/auth/registration-options', { params: { companyId } });
  }
  return request.get('/auth/registration-options');
}

function realPublicRoles(companyId) {
  return request.get('/public/roles', { params: { companyId } });
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
  const response = await request.get('/auth/me');
  return normalizeCurrentUserResponse(response);
}

// ---------- 导出 ----------
export const authApi = {
  /**
   * 登录
   * @param {{ username: string, password: string, captcha?: string }} payload
   * @returns {Promise<{ token: string, userInfo: object }>}
   */
  login(payload) {
    return realLogin(payload).then(response => normalizeAuthResponse(response, 'real'));
  },

  loginByPhone(payload) {
    return realPhoneLogin(payload).then(response => normalizeAuthResponse(response, 'real'));
  },

  loginByWechat(payload) {
    return realWechatLogin(payload).then(response => normalizeAuthResponse(response, 'real'));
  },

  register(payload) {
    return realRegister(payload).then(response => normalizeAuthResponse(response, 'real'));
  },

  sendPhoneCode(payload) {
    return realSendPhoneCode(payload);
  },

  getRegistrationOptions(companyId) {
    return realRegistrationOptions(companyId);
  },

  getPublicRoles(companyId) {
    return realPublicRoles(companyId);
  },

  /**
   * 退出登录
   * @returns {Promise<void>}
   */
  logout() {
    return realLogout();
  },

  getCurrentUser,
};
