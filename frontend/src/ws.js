import SockJS from 'sockjs-client'
import { Client } from '@stomp/stompjs'

// Resolve WS base URL
function resolveWsBase() {
  const envUrl = import.meta.env.VITE_WS_URL
  if (envUrl) return envUrl
  if (import.meta.env.PROD && typeof window !== 'undefined' && window.location) {
    return window.location.origin
  }
  return 'http://localhost:8080'
}

function readToken() {
  if (typeof window === 'undefined') return null
  // Try common keys to avoid mismatches
  return (
    window.localStorage.getItem('cc_token') ||
    window.localStorage.getItem('token') ||
    window.localStorage.getItem('jwt') ||
    null
  )
}

const WS_URL = resolveWsBase()

// Basic STOMP client with reconnect support
export function createStompClient(onConnect){
  const socketFactory = () => new SockJS(`${WS_URL}/color-craze/ws`)
  const token = readToken()

  const client = new Client({
    webSocketFactory: socketFactory,
    debug: (m) => console.log('[STOMP]', m),
    // Reconnect after delay when connection drops
    reconnectDelay: 2000,
    // Heartbeats: detect broken connections faster
    heartbeatIncoming: 5000,
    heartbeatOutgoing: 5000,
    connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
  })

  client.onConnect = () => {
    try { onConnect(client) } catch (e) { console.error('onConnect error', e) }
  }

  client.activate()
  return client
}

// Managed STOMP client: caches subscriptions and re-subscribes on reconnect
export function createManagedStompClient(){
  const socketFactory = () => new SockJS(`${WS_URL}/color-craze/ws`)
  const token = readToken()
  const baseHeaders = token ? { Authorization: `Bearer ${token}` } : {}

  const client = new Client({
    webSocketFactory: socketFactory,
    debug: (m) => console.log('[STOMP]', m),
    reconnectDelay: 2000,
    heartbeatIncoming: 5000,
    heartbeatOutgoing: 5000,
    connectHeaders: baseHeaders,
  })

  // Store subscription requests to replay after reconnect
  const subscriptions = []

  const subscribe = (destination, callback, headers = {}) => {
    const mergedHeaders = { ...baseHeaders, ...headers }
    subscriptions.push({ destination, callback, headers: mergedHeaders })
    if (client.connected) {
      return client.subscribe(destination, callback, mergedHeaders)
    }
    return { unsubscribe: () => {} }
  }

  const resubscribeAll = () => {
    subscriptions.forEach(({ destination, callback, headers }) => {
      client.subscribe(destination, callback, headers)
    })
  }

  client.onConnect = () => {
    resubscribeAll()
  }

  client.onStompError = (frame) => {
    console.error('STOMP error', frame.headers['message'], frame.body)
  }

  client.onWebSocketClose = () => {
    console.warn('WebSocket closed; will attempt reconnect')
  }

  client.activate()

  return {
    client,
    subscribe,
    publish: (destination, body, headers = {}) => client.publish({ destination, body, headers: { ...baseHeaders, ...headers } }),
    disconnect: () => client.deactivate(),
  }
}

// Helper used in tests to build a native ws/wss URL when needed
// (SockJS internally uses http/https but for direct websocket code we expose this utility)
export function createWsUrl(path){
  const proto = (typeof window !== 'undefined' && window.location && window.location.protocol === 'https:') ? 'wss://' : 'ws://'
  const host = (typeof window !== 'undefined' && window.location && window.location.host) ? window.location.host : 'localhost'
  return `${proto}${host}${path}`
}
