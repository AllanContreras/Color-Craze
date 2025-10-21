import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'

export function createStompClient(onConnect){
  const socket = new SockJS('http://localhost:8080/color-craze/ws')
  const client = new Client({
    webSocketFactory: () => socket,
    onConnect: () => onConnect(client),
    debug: (m) => console.log('[STOMP]', m),
    reconnectDelay: 5000
  })
  client.activate()
  return client
}
