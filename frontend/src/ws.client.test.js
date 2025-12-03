import { describe, it, expect, beforeEach, vi } from 'vitest'
import { createStompClient } from './ws'

// Mock @stomp/stompjs Client
vi.mock('@stomp/stompjs', () => {
  return {
    Client: vi.fn().mockImplementation((opts) => {
      return {
        connected: false,
        activate: vi.fn(() => {
          // simulate immediate connect
          if (opts && typeof opts.onConnect === 'function') {
            opts.onConnect()
          }
        }),
        subscribe: vi.fn(),
        publish: vi.fn(),
        deactivate: vi.fn(),
      }
    }),
  }
})

// Mock sockjs-client
vi.mock('sockjs-client', () => ({ default: vi.fn(() => ({}) ) }))

describe('createStompClient', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('sets Authorization header when token present', () => {
    localStorage.setItem('token', 't123')
    const onConnect = vi.fn()
    const client = createStompClient(onConnect)
    expect(onConnect).toHaveBeenCalled()
    // Verify Client was constructed with connectHeaders containing Authorization
    const Client = (await import('@stomp/stompjs')).Client
    const call = Client.mock.calls[0][0]
    expect(call.connectHeaders.Authorization).toBe('Bearer t123')
    expect(client).toBeTruthy()
  })

  it('does not set Authorization when token missing', async () => {
    const onConnect = vi.fn()
    createStompClient(onConnect)
    const Client = (await import('@stomp/stompjs')).Client
    const call = Client.mock.calls[Client.mock.calls.length - 1][0]
    expect(call.connectHeaders).toEqual({})
  })
})
