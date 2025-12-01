import { check, sleep } from 'k6';
import ws from 'k6/ws';

export const options = {
  vus: 50,
  duration: '60s'
};

// ENV vars: BASE_URL (e.g. ws://localhost:8080), GAME_CODE, TOKEN
const BASE = __ENV.BASE_URL || 'ws://localhost:8080';
const GAME = __ENV.GAME_CODE || 'TEST01';
const TOKEN = __ENV.TOKEN || 'guest';

export default function () {
  const url = `${BASE}/colorcraze-websocket`; // adapt path if sockJS endpoint differs
  const params = { headers: { Authorization: `Bearer ${TOKEN}` } };

  ws.connect(url, params, socket => {
    socket.on('open', () => {
      // Subscribe frame (STOMP style minimal) could be added; here we spam movement endpoint via fallback REST if exposed.
      for (let i = 0; i < 40; i++) {
        // Simulate alternating moves
        const dir = i % 2 === 0 ? 'RIGHT' : 'DOWN';
        try {
          socket.send(JSON.stringify({ type: 'MOVE', game: GAME, direction: dir }));
        } catch (e) {}
        sleep(0.05); // 50ms between moves
      }
    });
    socket.on('message', msg => {
      // Basic validation
      try {
        const data = JSON.parse(msg);
        if (data.success === false && data.rateLimited) {
          // Count rate limited events
        }
      } catch (_) {}
    });
    socket.on('close', () => {});
    socket.on('error', () => {});
    sleep(1);
    socket.close();
  });
}
