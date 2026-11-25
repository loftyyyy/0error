import { defineConfig, devices } from '@playwright/test'

const backendCommand =
  process.platform === 'win32'
    ? 'cmd /c mvnw.cmd -o spring-boot:run'
    : './mvnw -o spring-boot:run'

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    ...devices['Desktop Chrome'],
  },
  webServer: [
    {
      command: 'npm run dev -- --port 5173 --strictPort',
      url: 'http://localhost:5173',
      reuseExistingServer: true,
      timeout: 120_000,
    },
    {
      command: backendCommand,
      cwd: '../backend',
      url: 'http://localhost:8080/actuator/health',
      reuseExistingServer: true,
      timeout: 180_000,
      env: {
        DB_HOST: 'localhost',
        DB_PORT: '55432',
        DB_NAME: '0error',
        DB_USER: '0error',
        DB_PASSWORD: '0error_dev',
        SERVER_PORT: '8080',
        DRILL_AI_ENABLED: 'false',
      },
    },
  ],
})
