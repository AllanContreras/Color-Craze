import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'

// Use environment variable or fallback to localhost for development
const WS_URL = import.meta.env.VITE_WS_URL || 'http://localhost:8080'

export function createStompClient(onConnect){
  const socket = new SockJS(`${WS_URL}/color-craze/ws`)
  const client = new Client({
    webSocketFactory: () => socket,
    onConnect: () => onConnect(client),
    debug: (m) => console.log('[STOMP]', m),
    reconnectDelay: 5000
  })
  client.activate()
  return client
}
