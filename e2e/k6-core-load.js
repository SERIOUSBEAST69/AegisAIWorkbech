import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: Number(__ENV.VUS || 8),
  duration: __ENV.DURATION || '45s',
  thresholds: {
    http_req_failed: ['rate<0.02'],
    http_req_duration: ['p(95)<900', 'p(99)<1400'],
  },
};

const BASE = __ENV.BASE_URL || 'http://localhost:8080';

export default function () {
  const reg = http.get(`${BASE}/api/auth/registration-options`);
  check(reg, {
    'registration-options status 200': (r) => r.status === 200,
    'registration-options has code': (r) => String(r.body || '').includes('20000'),
  });

  const vitalsPayload = JSON.stringify({
    name: 'LCP',
    value: Math.random() * 3000,
    rating: 'good',
    id: `k6-${__VU}-${__ITER}`,
    navigationType: 'navigate',
    path: '/k6',
  });
  const vitals = http.post(`${BASE}/api/ops-metrics/web-vitals`, vitalsPayload, {
    headers: { 'Content-Type': 'application/json' },
  });
  check(vitals, {
    'web-vitals status 200': (r) => r.status === 200,
  });

  const slow = http.get(`${BASE}/api/ops-metrics/http`);
  check(slow, {
    'ops metrics endpoint reachable': (r) => r.status === 200 || r.status === 401 || r.status === 403,
  });

  sleep(0.4);
}
