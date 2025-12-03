import { describe, it, expect, vi } from 'vitest'
import { createManagedStompClient } from './ws'

vi.mock('@stomp/stompjs', () => {
  return {
    Client: vi.fn().mockImplementation(() => {
      const client = {
        connected: false,
        onConnect: undefined,
        activate: vi.fn(() => { if (typeof client.onConnect === 'function') client.onConnect() }),
        subscribe: vi.fn(),
        publish: vi.fn(),
        deactivate: vi.fn(),
      }
      return client
    }),
  }
})

vi.mock('sockjs-client', () => ({ default: vi.fn(() => ({}) ) }))

describe('createManagedStompClient', () => {
  it('caches subscriptions and resubscribes on reconnect', () => {
    const { client, subscribe } = createManagedStompClient()
    const cb = vi.fn()
    // subscribe before connected
    subscribe('/topic/test', cb)
    // simulate second connect (reconnect)
    client.onConnect()
    expect(client.subscribe).toHaveBeenCalledWith('/topic/test', cb, expect.any(Object))
  })
})
