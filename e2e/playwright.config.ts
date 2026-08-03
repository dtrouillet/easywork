import { defineConfig, devices } from "@playwright/test";

const BASE_URL = process.env.E2E_BASE_URL ?? "http://localhost:3000";

/**
 * Runs against the full local Docker Compose stack (see CLAUDE.md /
 * README.md "Running locally"): `docker compose up -d` then
 * `cd frontend && npm run dev` before `npm test` here.
 */
export default defineConfig({
  testDir: "./tests",
  fullyParallel: false, // tests share taxonomy/document state against one backend
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 1 : 0,
  workers: 1,
  reporter: process.env.CI ? "line" : "html",
  timeout: 60_000,
  // `next dev` (unoptimized, HMR-instrumented) is noticeably slower to fetch/render
  // than a production build — the default 5s assertion timeout is too tight for it.
  expect: { timeout: 10_000 },
  globalSetup: "./global-setup.ts",
  use: {
    baseURL: BASE_URL,
    storageState: "./.auth/user.json",
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
});
