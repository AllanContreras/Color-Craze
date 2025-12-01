import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    environment: 'jsdom',
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov', 'html', 'cobertura'],
    },
    include: ['src/**/*.{test,spec}.{js,jsx,ts,tsx}'],
  },
})
