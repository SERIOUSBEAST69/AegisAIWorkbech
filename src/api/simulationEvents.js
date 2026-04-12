import request from './request';
import { getAuthHeaderToken } from '../utils/auth';

export function fetchSimulationPending(afterId = 0, limit = 20) {
  return request.get('/simulation-events/pending', {
    params: { afterId, limit },
  });
}

export function markSimulationProcessed(eventIds) {
  return request.post('/simulation-events/mark-processed', {
    eventIds: Array.isArray(eventIds) ? eventIds : [],
  });
}

export function openSimulationEventStream({ lastEventId = 0, limit = 20, onEvent, onError }) {
  const token = getAuthHeaderToken();
  if (!token) {
    throw new Error('未找到登录令牌，无法建立演示事件连接');
  }

  const params = new URLSearchParams({
    token,
    lastEventId: String(lastEventId),
    limit: String(limit),
  });
  const source = new EventSource(`/api/simulation-events/stream?${params.toString()}`);

  source.addEventListener('ready', event => {
    try {
      const payload = JSON.parse(event.data || '{}');
      onEvent && onEvent({ type: 'ready', ...payload });
    } catch (err) {
      onError && onError(err);
    }
  });

  source.addEventListener('simulation', event => {
    try {
      const payload = JSON.parse(event.data || '{}');
      onEvent && onEvent({ type: 'simulation', ...payload });
    } catch (err) {
      onError && onError(err);
    }
  });

  source.addEventListener('ping', event => {
    try {
      const payload = JSON.parse(event.data || '{}');
      onEvent && onEvent({ type: 'ping', ...payload });
    } catch (err) {
      onError && onError(err);
    }
  });

  source.addEventListener('error', err => {
    onError && onError(err);
  });

  return source;
}
