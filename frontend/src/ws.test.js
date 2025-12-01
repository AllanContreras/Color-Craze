import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'

// Assuming ws.js exports a function createWsUrl and connect
import * as wsmod from './ws'

describe('ws.js', () => {
  const originalLocation = globalThis.location
  let mockWS

  beforeEach(() => {
    mockWS = vi.fn()
    globalThis.WebSocket = vi.fn(() => ({ close: vi.fn() }))
    // minimal location for URL building
    // @ts-ignore
    globalThis.location = { protocol: 'http:', host: 'example.com' }
  })

  afterEach(() => {
    globalThis.WebSocket = mockWS
    // @ts-ignore
    globalThis.location = originalLocation
    vi.restoreAllMocks()
  })

  it('builds ws url based on http protocol', () => {
    const url = wsmod.createWsUrl('/ws')
    expect(url.startsWith('ws://')).toBe(true)
    expect(url.endsWith('/ws')).toBe(true)
  })

  it('builds wss url when https', () => {
    // @ts-ignore
    globalThis.location = { protocol: 'https:', host: 'example.com' }
    const url = wsmod.createWsUrl('/ws')
    expect(url.startsWith('wss://')).toBe(true)
  })
})
