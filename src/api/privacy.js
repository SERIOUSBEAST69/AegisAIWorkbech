import request from './request';

export const privacyApi = {
  listEvents(params = {}) {
    const query = new URLSearchParams();
    Object.entries(params).forEach(([k, v]) => {
      if (v !== undefined && v !== null && String(v) !== '') {
        query.append(k, String(v));
      }
    });
    const suffix = query.toString() ? `?${query.toString()}` : '';
    return request.get(`/privacy/events${suffix}`);
  },

  reportEvent(payload) {
    return request.post('/privacy/events', payload);
  },

  getConfig() {
    return request.get('/privacy/config');
  },

  updateConfig(payload) {
    return request.post('/privacy/config', payload);
  },
};
