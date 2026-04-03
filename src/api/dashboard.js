import request from './request';

export const dashboardApi = {
  async getStats() {
    return request.get('/dashboard/stats');
  },

  async getRiskTrend() {
    return request.get('/risk/trend');
  },

  async getInsights() {
    return request.get('/dashboard/insights');
  },

  async getWorkbench() {
    return request.get('/dashboard/workbench');
  },

  async getHomeBundle() {
    return request.get('/dashboard/home-bundle');
  },

  async getTrustPulse() {
    return request.get('/dashboard/trust-pulse');
  },

  async getForecast() {
    return request.get('/risk/forecast');
  },

  async getAwardSummary() {
    return request.get('/award/summary');
  },

  async getExperimentReport(params) {
    return request.get('/award/experiment-report', { params });
  },

  async generateComplianceEvidence(payload) {
    return request.post('/award/compliance-evidence/generate', payload || {});
  },

  async runReliabilityDrill(payload) {
    return request.post('/award/reliability/drill/run', payload || {});
  },

  async getWebVitalSummary(days = 7) {
    return request.get('/ops-metrics/web-vitals/summary', { params: { days } });
  },

  async getHttpHistory(days = 7, api = '') {
    return request.get('/ops-metrics/http-history', { params: { days, api } });
  },

  async getInnovationReport(params) {
    return request.get('/award/innovation-report', { params });
  },

  async getModelLineage() {
    return request.get('/ai/model-lineage');
  },

  async getModelDriftStatus() {
    return request.get('/ai/model-drift-status');
  },

  async getModelExplainability() {
    return request.get('/ai/model-explainability');
  },

  async buildTrainingDataFactory(payload) {
    return request.post('/ai/data-factory/build', payload || {});
  },

  async trainFromFactory(payload) {
    return request.post('/ai/train/factory', payload || {});
  },

  async trainFromAdversarialFeedback(payload) {
    return request.post('/ai/train/adversarial-feedback', payload || {});
  },

  async getModelReleaseStatus() {
    return request.get('/ai/model-release/status');
  },

  async getModelReleaseTrafficStats() {
    return request.get('/ai/model-release/traffic-stats');
  },

  async registerModelReleaseCandidate(payload) {
    return request.post('/ai/model-release/register-candidate', payload || {});
  },

  async promoteModelReleaseCanary(payload) {
    return request.post('/ai/model-release/promote-canary', payload || {});
  },

  async promoteModelReleaseStable(payload) {
    return request.post('/ai/model-release/promote-stable', payload || {});
  },

  async rollbackModelRelease(payload) {
    return request.post('/ai/model-release/rollback', payload || {});
  },

  async getAwardReadinessReport() {
    return request.get('/award/readiness/report');
  },

  async runAutoRemediationPlaybook(payload) {
    return request.post('/award/readiness/auto-remediate', payload || {});
  },

  async getLastAutoRemediationRun() {
    return request.get('/award/readiness/auto-remediate/last');
  },

  async exportEvidencePackage(payload) {
    return request.post('/award/export', payload || {});
  },
};
