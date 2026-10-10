import { defineConfig } from '@playwright/test'

// The journey runs against its own database (popcorn_e2e) and ports, so it never
// touches the real journal. Uses the installed Microsoft Edge: no browser download.
const API_PORT = 8081
const WEB_PORT = 5181

export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: `http://127.0.0.1:${WEB_PORT}`,
    channel: process.env.PLAYWRIGHT_CHANNEL ?? 'msedge',
    trace: 'retain-on-failure',
  },
  webServer: [
    {
      command: 'mvn -q -f ../backend/pom.xml spring-boot:run',
      url: `http://127.0.0.1:${API_PORT}/api/v1/health`,
      timeout: 180_000,
      reuseExistingServer: false,
      env: { DB_NAME: 'popcorn_e2e', SERVER_PORT: String(API_PORT) },
    },
    {
      command: 'npm run dev',
      url: `http://127.0.0.1:${WEB_PORT}`,
      timeout: 60_000,
      reuseExistingServer: false,
      env: { VITE_PORT: String(WEB_PORT), VITE_API_PROXY_TARGET: `http://127.0.0.1:${API_PORT}` },
    },
  ],
})
