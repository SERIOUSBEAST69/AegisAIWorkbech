import axios from 'axios';
import { clearSession, getAuthHeaderToken, getSession } from '../utils/auth';
import { assessOutboundRisk } from '../utils/clientRiskGuard';
import { isClientLiteMode } from '../utils/runtimeProfile';

const service = axios.create({
  baseURL: '/api',
  timeout: 22000,
  headers: {
    'Content-Type': 'application/json;charset=utf-8'
  }
});

const TRANSIENT_RETRY_MAX = 2;
const TRANSIENT_RETRY_BASE_MS = 450;

function createClientError(message, extra = {}) {
  const error = new Error(message || '请求失败');
  Object.assign(error, extra);
  return error;
}

function rejectWith(message, extra = {}) {
  return Promise.reject(createClientError(message, extra));
}

function wait(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function isIdempotentRequest(config) {
  const method = String(config?.method || 'get').toLowerCase();
  if (method === 'get' || method === 'head' || method === 'options') {
    return true;
  }
  // Login-style endpoints are safe to retry on gateway transient failures.
  if (method === 'post') {
    const requestPath = String(config?.url || '').split('?')[0];
    return requestPath === '/auth/login'
      || requestPath === '/auth/login-phone'
      || requestPath === '/auth/login-wechat';
  }
  return false;
}

function shouldRetryTransientError(err) {
  if (isClientLiteMode()) {
    return false;
  }
  if (!err || !err.config || !isIdempotentRequest(err.config)) {
    return false;
  }
  const status = Number(err?.response?.status || 0);
  if (err.code === 'ECONNABORTED' || !err.response) {
    return true;
  }
  return status === 502 || status === 503 || status === 504;
}

async function retryTransientRequest(err) {
  const config = err?.config;
  if (!config) return Promise.reject(err);
  const retryCount = Number(config.__retryCount || 0);
  if (retryCount >= TRANSIENT_RETRY_MAX) {
    return Promise.reject(err);
  }
  config.__retryCount = retryCount + 1;
  config.timeout = Math.max(Number(config.timeout || 0), 22000);
  const delay = TRANSIENT_RETRY_BASE_MS * Math.pow(2, retryCount);
  await wait(delay);
  return service(config);
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
  if (isClientLiteMode()) {
    // Electron lite mode prefers fast failover over long blocking retries.
    config.timeout = Math.min(Number(config.timeout || 22000), 9000);
  }
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
    if (res?.config?.responseType === 'blob' || res?.config?.responseType === 'arraybuffer') {
      return res;
    }
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
    if (shouldRetryTransientError(err)) {
      return retryTransientRequest(err);
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
    if (err.response && (err.response.status === 502 || err.response.status === 503 || err.response.status === 504)) {
      return rejectWith('服务网关暂时不可用，请等待后端服务就绪后重试', {
        status: err.response.status,
        transientGateway: true,
      });
    }
    if (!err.response) {
      return rejectWith('网络连接失败，请检查后端服务、代理配置或跨域设置', { network: true });
    }
    return rejectWith(err.response.data?.msg || err.message || '请求失败', { status: err.response.status });
  }
);

export default service;
