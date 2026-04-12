import request from './request';

export const clientSimulationApi = {
  triggerShadowAi(payload = {}) {
    return request.post('/client/simulation/shadow-ai/trigger', payload);
  },

  triggerEmployeeAnomaly(payload = {}) {
    return request.post('/client/simulation/employee-anomaly/trigger', payload);
  },

  replay(payload = {}) {
    return request.post('/client/simulation/replay', payload);
  },

  list(params = {}) {
    return request.get('/client/simulation/events', { params });
  },
};
