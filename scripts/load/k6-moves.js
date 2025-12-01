import { check, sleep } from 'k6';
import ws from 'k6/ws';
import { Trend, Counter } from 'k6/metrics';

export const options = {
  vus: 50,
  duration: '60s',
  thresholds: {
    'move_latency{type:move}': ['p(95)<200'],
    'rate_limited': ['count<500']
  }
};

const moveLatency = new Trend('move_latency', true);
const rateLimited = new Counter('rate_limited');
const movesSent = new Counter('moves_sent');

// ENV vars: BASE_URL (e.g. ws://localhost:8080), GAME_CODE, TOKEN
const BASE = __ENV.BASE_URL || 'ws://localhost:8080';
const GAME = __ENV.GAME_CODE || 'TEST01';
const TOKEN = __ENV.TOKEN || 'guest';

export default function () {
  const url = `${BASE}/colorcraze-websocket`; // adapt path if sockJS endpoint differs
  const params = { headers: { Authorization: `Bearer ${TOKEN}` } };

  ws.connect(url, params, socket => {
    socket.on('open', () => {
      // Fire a burst of simulated moves
      for (let i = 0; i < 40; i++) {
        // Simulate alternating moves
        const dir = i % 2 === 0 ? 'RIGHT' : 'DOWN';
        const start = Date.now();
        try {
          socket.send(JSON.stringify({ type: 'MOVE', game: GAME, direction: dir }));
          movesSent.add(1);
        } catch (e) {}
        sleep(0.05); // 50ms between moves
        moveLatency.add(Date.now() - start, { type: 'move' });
      }
    });
    socket.on('message', msg => {
      // Basic validation
      try {
        const data = JSON.parse(msg);
        if (data.success === false && data.rateLimited) {
          rateLimited.add(1);
        }
      } catch (_) {}
    });
    socket.on('close', () => {});
    socket.on('error', () => {});
    sleep(1);
    socket.close();
  });
}

export function handleSummary(data) {
  return {
    'summary.json': JSON.stringify(data),
  };
}
