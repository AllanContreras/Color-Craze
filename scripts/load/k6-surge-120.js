import http from 'k6/http';
import { sleep, check } from 'k6';

// Simple HTTP surge from 20 -> 120 VUs within 2 minutes
export const options = {
  stages: [
    { duration: '30s', target: 20 },
    { duration: '60s', target: 120 },
    { duration: '30s', target: 120 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<150'],
  },
};

const BASE = __ENV.API_BASE || 'http://localhost:8080';
const AUTH = __ENV.JWT || '';

export default function () {
  const params = AUTH
    ? { headers: { Authorization: `Bearer ${AUTH}` } }
    : {};

  // Health
  const h = http.get(`${BASE}/actuator/health`);
  check(h, {
    'health 200': (r) => r.status === 200,
  });

  // List games (adjust path to your API)
  const g = http.get(`${BASE}/api/games`, params);
  check(g, {
    'games 200': (r) => r.status === 200,
  });

  sleep(1);
}
