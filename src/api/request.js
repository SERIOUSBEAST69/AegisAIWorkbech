import axios from 'axios';
import { clearSession, getAuthHeaderToken, getSession } from '../utils/auth';
import { assessOutboundRisk } from '../utils/clientRiskGuard';

const service = axios.create({
  baseURL: '/api',
  timeout: 12000,
  headers: {
    'Content-Type': 'application/json;charset=utf-8'
  }
});

function createClientError(message, extra = {}) {
  const error = new Error(message || '请求失败');
  Object.assign(error, extra);
  return error;
}

function rejectWith(message, extra = {}) {
  return Promise.reject(createClientError(message, extra));
}

function redirectToLogin() {
  const redirect = encodeURIComponent(window.location.pathname + window.location.search);
  window.location.assign(`/login?reason=session-expired&redirect=${redirect}`);
}

function shouldSuppressUnauthorizedRedirect(requestPath, message) {
  const path = String(requestPath || '').toLowerCase();
  const text = String(message || '').toLowerCase();
  if (path.includes('/governance-change/')) {
    return true;
  }
  if (text.includes('二次密码') || text.includes('password')) {
    return true;
  }
  return false;
}

function handleUnauthorized(message, data, requestPath) {
  const session = getSession();
  const hasRealSession = Boolean(session?.token);
  const error = createClientError(message || '未登录或会话已失效', {
    code: 40100,
    sessionExpired: hasRealSession,
    detail: data || null,
  });

  if (shouldSuppressUnauthorizedRedirect(requestPath, message)) {
    return Promise.reject(error);
  }

  // Default behavior for real session expiration.
  clearSession('expired');
  if (window.location.pathname !== '/login') {
    redirectToLogin();
  }

  return Promise.reject(error);
}

service.interceptors.request.use(config => {
  const token = getAuthHeaderToken();
  const requestPath = String(config.url || '').split('?')[0];
  const authBypassPaths = new Set([
    '/auth/login',
    '/auth/login-phone',
    '/auth/login-wechat',
    '/auth/register',
    '/auth/phone-code',
    '/auth/registration-options',
    '/public/roles',
    '/security/cross-site/status',
  ]);
  if (token && !authBypassPaths.has(requestPath)) {
    config.headers['Authorization'] = 'Bearer ' + token;
  }
  const session = getSession();
  const companyId = session?.user?.companyId;
  if (companyId !== undefined && companyId !== null && companyId !== '') {
    config.headers['X-Company-Id'] = String(companyId);
  }

  const risk = assessOutboundRisk({
    url: config.url,
    method: config.method,
    data: config.data,
    params: config.params,
  });
  config.headers['X-Aegis-Trace-Id'] = risk.traceId;
  config.headers['X-Aegis-Client-Risk'] = risk.level;
  config.headers['X-Aegis-Client-Risk-Action'] = risk.action;
  config.headers['X-Aegis-Client-Risk-Category'] = risk.category;
  if (risk.reasons.length > 0) {
    config.headers['X-Aegis-Client-Risk-Reasons'] = risk.reasons.slice(0, 6).join(',');
  }
  if (risk.level === 'high') {
    return Promise.reject(createClientError('客户端风控拦截：检测到高风险请求特征', {
      code: 49901,
      blockedByClientRisk: true,
      risk,
    }));
  }
  if (risk.level === 'medium' && risk.action === 'challenge' && typeof window !== 'undefined' && typeof window.confirm === 'function') {
    const approved = window.confirm('检测到中风险安全请求，是否继续发送？');
    if (!approved) {
      return Promise.reject(createClientError('用户取消了中风险请求发送', {
        code: 49902,
        blockedByClientRisk: true,
        risk,
      }));
    }
  }

  return config;
});

service.interceptors.response.use(
  res => {
    const body = res.data;
    const requestPath = String(res?.config?.url || '').split('?')[0];
    // 后端统一返回 R { code, msg, data, timestamp }
    if (body && body.code === 20000) return body.data;
    if (body && body.code === 40100) {
      return handleUnauthorized(body.msg, body.data, requestPath);
    }
    if (body && body.code === 40300) {
      return rejectWith(body.msg || '无权限访问当前资源', { code: 40300 });
    }
    if (body && body.code === 40310) {
      return rejectWith(body.msg || '跨站请求已被拦截', {
        code: 40310,
        crossSiteBlocked: true,
        detail: body.data || null,
      });
    }
    return rejectWith((body && body.msg) || '请求失败', { code: body?.code });
  },
  err => {
    if (err?.blockedByClientRisk) {
      return rejectWith(err.message || '客户端风控拦截请求', {
        code: err.code || 49901,
        blockedByClientRisk: true,
        risk: err.risk || null,
      });
    }
    if (err.code === 'ECONNABORTED') {
      return rejectWith('请求超时，请稍后重试', { timeout: true });
    }
    if (err.response && err.response.status === 401) {
      const requestPath = String(err?.config?.url || '').split('?')[0];
      return handleUnauthorized(err.response.data?.msg || '未登录或会话已失效', err.response.data?.data, requestPath);
    }
    if (err.response && err.response.status === 403) {
      if (err.response.data?.code === 40310) {
        return rejectWith(err.response.data?.msg || '跨站请求已被拦截', {
          code: 40310,
          status: 403,
          crossSiteBlocked: true,
          detail: err.response.data?.data || null,
        });
      }
      return rejectWith(err.response.data?.msg || '无权限访问当前资源', { code: 40300, status: 403 });
    }
    if (!err.response) {
      return rejectWith('网络连接失败，请检查后端服务、代理配置或跨域设置', { network: true });
    }
    return rejectWith(err.response.data?.msg || err.message || '请求失败', { status: err.response.status });
  }
);

export default service;
