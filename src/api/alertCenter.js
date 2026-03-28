import request from './request';

export const alertCenterApi = {
  list(params = {}) {
    return request.get('/alert-center/list', { params });
  },
  stats() {
    return request.get('/alert-center/stats');
  },
  detail(id) {
    return request.get(`/alert-center/${id}`);
  },
  related(id, params = {}) {
    return request.get(`/alert-center/${id}/related`, { params });
  },
  userHistory(params = {}) {
    return request.get('/alert-center/user-history', { params });
  },
  dispose(payload) {
    return request.post('/alert-center/dispose', payload);
  },
};
