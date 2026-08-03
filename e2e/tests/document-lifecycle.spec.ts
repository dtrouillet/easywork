import { test, expect, type Page } from "@playwright/test";

const RUN_ID = Date.now();

/** Uploads a plain-text file (native text extraction, no OCR) and returns its title/filename. */
async function uploadTextDocument(page: Page, filename: string, content: string) {
  await page.goto("/documents");
  // Wait for the session to hydrate — the upload scheduler silently no-ops
  // without an access token and nothing re-triggers it once one arrives.
  await expect(page.getByRole("button", { name: "Sign out" })).toBeVisible();
  await page.getByRole("button", { name: "Upload" }).click();
  await page
    .getByTestId("file-input")
    .setInputFiles({ name: filename, mimeType: "text/plain", buffer: Buffer.from(content) });
  await expect(page.getByText("1 uploaded")).toBeVisible({ timeout: 30_000 });
  await page.getByText("Close").click();
}

/**
 * The "Active" filter only shows READY documents (see documents/page.tsx), so a
 * just-uploaded document is invisible there until processing completes — reload
 * until it shows up, then open it.
 */
async function openDocumentAndWaitReady(page: Page, filename: string) {
  await page.goto("/documents");
  await expect(async () => {
    await page.reload();
    await expect(page.getByRole("link", { name: filename })).toBeVisible();
  }).toPass({ timeout: 30_000 });

  await Promise.all([
    page.waitForURL(/\/documents\/[0-9a-fA-F-]{36}$/),
    page.getByRole("link", { name: filename }).click(),
  ]);
  await expect(page.getByText("READY")).toBeVisible({ timeout: 10_000 });
}

test.describe("Document lifecycle — Active / Archived / Trash filter", () => {
  test("archive, trash and restore move a document between lifecycle filters", async ({ page }) => {
    const filename = `lifecycle-${RUN_ID}.txt`;
    await uploadTextDocument(
      page, filename,
      `Document lifecycle filter e2e test content (run ${RUN_ID}), well over the native text extraction threshold.`
    );
    await openDocumentAndWaitReady(page, filename);

    // Archive: leaves "Active", appears in "Archived".
    await page.getByRole("button", { name: "Archive" }).click();
    await expect(page.getByRole("button", { name: "Restore" })).toBeVisible();

    await page.goto("/documents");
    await expect(page.getByRole("link", { name: filename })).not.toBeVisible();
    await page.getByRole("button", { name: "Archived" }).click();
    await expect(page.getByRole("link", { name: filename })).toBeVisible();

    // Trash from the archived state: leaves "Archived", appears in "Trash".
    await page.getByRole("link", { name: filename }).click();
    await expect(page).toHaveURL(/\/documents\/[0-9a-fA-F-]{36}$/);
    await page.getByRole("button", { name: "Move to trash" }).click();
    await expect(page.getByRole("button", { name: "Restore" })).toBeVisible();

    await page.goto("/documents");
    await page.getByRole("button", { name: "Archived" }).click();
    await expect(page.getByRole("link", { name: filename })).not.toBeVisible();
    await page.getByRole("button", { name: "Trash" }).click();
    await expect(page.getByRole("link", { name: filename })).toBeVisible();

    // Restore from trash: back to "Active".
    await page.getByRole("link", { name: filename }).click();
    await page.getByRole("button", { name: "Restore" }).click();
    await expect(page.getByRole("button", { name: "Archive" })).toBeVisible();

    await page.goto("/documents");
    await page.getByRole("button", { name: "Active" }).click();
    await expect(page.getByRole("link", { name: filename })).toBeVisible();
  });
});
