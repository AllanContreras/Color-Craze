import { describe, it, expect, vi } from 'vitest'
import { createStompClient } from './ws'

vi.mock('@stomp/stompjs', () => {
  return {
    Client: vi.fn().mockImplementation((opts) => {
      // expose opts for assertions
      return {
        opts,
        activate: vi.fn(),
      }
    })
  }
})

vi.mock('sockjs-client', () => ({ default: vi.fn(() => ({}) ) }))

describe('ws client options', () => {
  it('sets heartbeat and reconnect options', () => {
    const c = createStompClient(() => {})
    const { Client } = require('@stomp/stompjs')
    const args = Client.mock.calls[0][0]
    expect(args.reconnectDelay).toBe(2000)
    expect(args.heartbeatIncoming).toBe(5000)
    expect(args.heartbeatOutgoing).toBe(5000)
  })
})
