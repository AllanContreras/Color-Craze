import { describe, it, expect } from 'vitest'
import { getApiBase } from './api'

describe('api base URL selection', () => {
  it('uses production Railway URL when PROD', () => {
    const env = { PROD: true }
    const base = getApiBase(env)
    expect(base).toBe('https://color-craze-production.up.railway.app')
  })

  it('uses VITE_API_URL when provided in dev', () => {
    const env = { PROD: false, VITE_API_URL: 'http://dev-backend:8080' }
    const base = getApiBase(env)
    expect(base).toBe('http://dev-backend:8080')
  })
})
