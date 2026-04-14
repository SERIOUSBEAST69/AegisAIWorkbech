import request from './request';

export function fetchTodoPage(params) {
  return request.get('/governance-change/todo-page', { params });
}

export function fetchMyPage(params) {
  return request.get('/governance-change/my-page', { params });
}

export function fetchApprovalDetail(id) {
  return request.get(`/governance-change/detail/${id}`);
}

export function fetchApprovalDiff(id) {
  return request.get(`/governance-change/diff/${id}`);
}

export function approveRequest(payload) {
  return request.post('/governance-change/approve', payload);
}

export function revokeRequest(payload) {
  return request.post('/governance-change/revoke', payload);
}

export function deleteDraftRequest(id) {
  return request.delete(`/governance-change/draft/${id}`);
}

export function submitGovernanceChange(payload) {
  return request.post('/governance-change/submit', payload);
}
