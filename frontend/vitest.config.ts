import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: {
    environment: 'jsdom',
    coverage: {
      provider: 'v8',
      reporter: ['text', 'lcov', 'html'],
    },
    include: ['src/**/*.{js,jsx,ts,tsx}'],
  },
})
