import { describe, it, expect, vi, beforeEach } from 'vitest'
import api from './api'

describe('api client', () => {
  beforeEach(() => {
    localStorage.clear()
  })

  it('adds Authorization header when token present', async () => {
    localStorage.setItem('cc_token', 'abc123')
    const cfg = await api.interceptors.request.handlers[0].fulfilled({ headers: {} })
    expect(cfg.headers.Authorization).toBe('Bearer abc123')
  })

  it('does not add Authorization when token missing', async () => {
    const cfg = await api.interceptors.request.handlers[0].fulfilled({ headers: {} })
    expect(cfg.headers.Authorization).toBeUndefined()
  })
})
