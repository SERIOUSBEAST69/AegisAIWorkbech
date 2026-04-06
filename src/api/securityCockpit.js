import request from './request';
import { getAuthHeaderToken } from '../utils/auth';

export function fetchCockpitOverview() {
  return request.get('/security-cockpit/overview');
}

export function fetchDepartmentHeatmap(days = 7) {
  return request.get('/security-cockpit/heatmap/department', { params: { days } });
}

export function fetchHourlyTrend(hours = 24) {
  return request.get('/security-cockpit/trend/hourly', { params: { hours } });
}

export function fetchRiskTopology(days = 7) {
  return request.get('/security-cockpit/topology', { params: { days } });
}

export function fetchRecentAlerts(limit = 40) {
  return request.get('/security-cockpit/alerts/recent', { params: { limit } });
}

export function fetchDepartmentDetail(department, days = 7, limit = 80) {
  return request.get('/security-cockpit/department/detail', {
    params: { department, days, limit },
  });
}

export function fetchHourDetail(hour, limit = 120) {
  return request.get('/security-cockpit/hour/detail', {
    params: { hour, limit },
  });
}

export function fetchTopologyDetail(sourceIp, target, days = 7, limit = 80) {
  return request.get('/security-cockpit/topology/detail', {
    params: { sourceIp, target, days, limit },
  });
}

export function openCockpitAlertStream({ lastEventId = 0, limit = 30, onAlerts, onError }) {
  const token = getAuthHeaderToken();
  if (!token) {
    throw new Error('未找到登录令牌，无法建立实时告警连接');
  }

  const params = new URLSearchParams({
    token,
    lastEventId: String(lastEventId),
    limit: String(limit),
  });
  const source = new EventSource(`/api/security-cockpit/alerts/stream?${params.toString()}`);

  source.addEventListener('open', event => {
    onAlerts && onAlerts({ type: 'open', nativeEvent: event });
  });

  source.addEventListener('ready', event => {
    try {
      const payload = JSON.parse(event.data || '{}');
      onAlerts && onAlerts({ type: 'ready', ...payload });
    } catch (err) {
      onError && onError(err);
    }
  });

  source.addEventListener('alerts', event => {
    try {
      const payload = JSON.parse(event.data || '{}');
      onAlerts && onAlerts({ type: 'alerts', ...payload });
    } catch (err) {
      onError && onError(err);
    }
  });

  source.addEventListener('ping', event => {
    try {
      const payload = JSON.parse(event.data || '{}');
      onAlerts && onAlerts({ type: 'ping', ...payload });
    } catch (err) {
      onError && onError(err);
    }
  });

  source.addEventListener('error', error => {
    onError && onError(error);
  });

  return source;
}
