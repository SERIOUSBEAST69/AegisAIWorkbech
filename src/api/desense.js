import request from './request';

export const desenseApi = {
  async getGlobalConfig() {
    return request.get('/privacy/config');
  },

  async saveGlobalConfig(payload) {
    return request.post('/privacy/config', payload);
  }
};
