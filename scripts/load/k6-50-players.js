import http from 'k6/http';
import { sleep, check } from 'k6';

// Steady 50 users scenario
export const options = {
  vus: 50,
  duration: '2m',
  thresholds: {
    http_req_duration: ['p(95)<120'],
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

  // Touch game APIs to approximate typical traffic
  const g = http.get(`${BASE}/api/games`, params);
  check(g, {
    'games 200': (r) => r.status === 200,
  });

  // If you have a specific game code, set __ENV.GAME_CODE
  const code = __ENV.GAME_CODE;
  if (code) {
    const s = http.get(`${BASE}/api/games/${code}/lite`, params);
    check(s, {
      'state 200': (r) => r.status === 200,
    });
  }

  sleep(1);
}
