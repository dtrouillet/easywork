import { chromium, type FullConfig } from "@playwright/test";

const BASE_URL = process.env.E2E_BASE_URL ?? "http://localhost:3000";
const USER = process.env.E2E_USER ?? "dev";
const PASSWORD = process.env.E2E_PASSWORD ?? "dev";

/**
 * Logs in once via Keycloak SSO (the `dev` / `dev` seeded test user — see
 * keycloak/realm-easywork.json) and saves the session so individual specs
 * don't each pay for a full OAuth round trip.
 */
export default async function globalSetup(_config: FullConfig) {
  const browser = await chromium.launch();
  const page = await browser.newPage();

  await page.goto(`${BASE_URL}/login`);
  await page.getByRole("button", { name: "Sign in with SSO" }).click();
  await page.getByRole("textbox", { name: "Username or email" }).fill(USER);
  await page.getByRole("textbox", { name: "Password" }).fill(PASSWORD);
  await page.getByRole("button", { name: "Sign In" }).click();
  await page.waitForURL(`${BASE_URL}/documents`);

  await page.context().storageState({ path: "./.auth/user.json" });
  await browser.close();
}
