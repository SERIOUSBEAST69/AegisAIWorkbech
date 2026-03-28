import axios from 'axios';
import { clearSession, getAuthHeaderToken, getSession } from '../utils/auth';

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

function handleUnauthorized(message, data) {
  const session = getSession();
  const hasRealSession = Boolean(session?.token);
  const error = createClientError(message || '未登录或会话已失效', {
    code: 40100,
    sessionExpired: hasRealSession,
    detail: data || null,
  });

  // Always clear the stale session and redirect to the login page so that
  // the user is never stuck seeing raw JSON error responses.
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
  return config;
});

service.interceptors.response.use(
  res => {
    const body = res.data;
    // 后端统一返回 R { code, msg, data, timestamp }
    if (body && body.code === 20000) return body.data;
    if (body && body.code === 40100) {
      return handleUnauthorized(body.msg, body.data);
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
    if (err.code === 'ECONNABORTED') {
      return rejectWith('请求超时，请稍后重试', { timeout: true });
    }
    if (err.response && err.response.status === 401) {
      return handleUnauthorized(err.response.data?.msg || '未登录或会话已失效', err.response.data?.data);
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
