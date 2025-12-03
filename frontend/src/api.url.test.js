import { describe, it, expect, vi } from 'vitest'

describe('api base URL selection', () => {
  it('uses production Railway URL when PROD', async () => {
    const env = { PROD: true }
    const mod = await vi.dynamicImport(() => import('./api'), { meta: { env } })
    const instance = mod.default
    expect(instance.defaults.baseURL).toBe('https://color-craze-production.up.railway.app')
  })

  it('uses VITE_API_URL when provided in dev', async () => {
    const env = { PROD: false, VITE_API_URL: 'http://dev-backend:8080' }
    const mod = await vi.dynamicImport(() => import('./api'), { meta: { env } })
    const instance = mod.default
    expect(instance.defaults.baseURL).toBe('http://dev-backend:8080')
  })
})
