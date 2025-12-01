// Simple STOMP relay smoke test: subscribe and publish to verify broker relay across instances
// Usage: node scripts/smoke/stomp-relay-check.js "http://backend-host:8080" ROOMCODE

const SockJS = require('sockjs-client')
const { Client } = require('@stomp/stompjs')

const args = process.argv.slice(2)
const baseUrl = args[0]
const roomCode = args[1]
if (!baseUrl || !roomCode) {
  console.error('Usage: node scripts/smoke/stomp-relay-check.js <baseUrl> <roomCode>')
  process.exit(1)
}

function createClient() {
  return new Client({
    webSocketFactory: () => new SockJS(`${baseUrl}/color-craze/ws`),
    debug: (m) => console.log('[STOMP]', m),
    reconnectDelay: 3000,
  })
}

async function run() {
  const subClient = createClient()
  const pubClient = createClient()

  let received = false

  await new Promise((resolve) => {
    subClient.onConnect = () => {
      console.log('Subscriber connected')
      subClient.subscribe(`/topic/board/${roomCode}/state`, (msg) => {
        console.log('Received message:', msg.body)
        received = true
        resolve()
      })
      // After subscriber is ready, connect publisher
      pubClient.activate()
    }
    subClient.activate()
  })

  // When publisher connects, send a test frame
  pubClient.onConnect = () => {
    console.log('Publisher connected; sending test message')
    const payload = JSON.stringify({ code: roomCode, status: 'TEST', ts: Date.now() })
    pubClient.publish({ destination: `/topic/board/${roomCode}/state`, body: payload })
  }

  // Wait briefly to confirm reception
  await new Promise((r) => setTimeout(r, 2000))
  try { subClient.deactivate() } catch {}
  try { pubClient.deactivate() } catch {}

  if (!received) {
    console.error('Smoke test failed: no message received on subscription')
    process.exit(2)
  }
  console.log('Smoke test passed: relay delivering messages')
}

run().catch((err) => { console.error('Error:', err); process.exit(3) })
