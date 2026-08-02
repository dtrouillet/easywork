import { test, expect, type Page } from "@playwright/test";

/**
 * ADR 0003: end-to-end coverage for the auto-classification suggest/confirm
 * flow — specifically the "progressive learning" path (README: "a tag
 * applied once is automatically suggested for similar documents
 * afterwards"), which only real cross-request, cross-document state can
 * exercise; unit/integration tests already cover the heuristic matching and
 * service logic in isolation.
 */

const RUN_ID = Date.now();

async function createTag(page: Page, name: string) {
  await page.goto("/settings/taxonomy");
  await page.getByRole("button", { name: "Tags" }).click();
  await page.getByPlaceholder("New tag name").fill(name);
  await page.getByRole("button", { name: "Add" }).click();
  await expect(page.getByRole("button", { name: `Edit ${name}` })).toBeVisible();
}

async function createCorrespondent(page: Page, name: string) {
  await page.goto("/settings/taxonomy");
  await page.getByRole("button", { name: "Correspondents" }).click();
  await page.getByPlaceholder("New correspondent name").fill(name);
  await page.getByRole("button", { name: "Add" }).click();
  await expect(page.getByRole("button", { name: `Edit ${name}` })).toBeVisible();
}

/** Uploads a plain-text file (native text extraction, no OCR) and returns its title/filename. */
async function uploadTextDocument(page: Page, filename: string, content: string) {
  await page.goto("/documents");
  await page.getByRole("button", { name: "Upload" }).click();
  await page
    .getByTestId("file-input")
    .setInputFiles({ name: filename, mimeType: "text/plain", buffer: Buffer.from(content) });
  await expect(page.getByText("1 uploaded")).toBeVisible({ timeout: 30_000 });
  await page.getByText("Close").click();
}

async function openDocumentAndWaitReady(page: Page, filename: string) {
  await page.goto("/documents");
  await Promise.all([
    page.waitForURL(/\/documents\/[0-9a-fA-F-]{36}$/),
    page.getByRole("link", { name: filename }).click(),
  ]);
  await expect(page.getByText("READY")).toBeVisible({ timeout: 30_000 });
}

test.describe("Classification suggestion — confirm", () => {
  test("learned tag association is suggested and confirming applies it to the document", async ({ page }) => {
    const correspondent = `E2E EDF Confirm ${RUN_ID}`;
    const tag = `e2e-energy-tag-${RUN_ID}`;

    await createCorrespondent(page, correspondent);
    await createTag(page, tag);

    // Doc 1: text contains both names, so the heuristic matches both directly.
    // (Deliberately avoids words like "Facture"/"Contrat" that collide with
    // pre-existing DocumentType names in the shared dev database.)
    const doc1 = `e2e-confirm-1-${RUN_ID}.txt`;
    await uploadTextDocument(
      page, doc1,
      `Correspondence from ${correspondent} — merci pour votre paiement mensuel. Tag associe: ${tag}.`
    );
    await openDocumentAndWaitReady(page, doc1);
    await expect(page.getByText(`This looks like it's from ${correspondent}.`)).toBeVisible();
    await page.getByRole("button", { name: "Confirm", exact: true }).click();
    await expect(page.getByText(`This looks like it's from ${correspondent}.`)).not.toBeVisible();
    await expect(page.getByText(`/No type/${correspondent}/No date`)).toBeVisible();

    // Doc 2: only the correspondent appears in the text — the tag must come from
    // the learned association recorded when doc 1 was confirmed, not the heuristic.
    const doc2 = `e2e-confirm-2-${RUN_ID}.txt`;
    await uploadTextDocument(
      page, doc2,
      `Second statement from ${correspondent} regarding an unrelated matter entirely.`
    );
    await openDocumentAndWaitReady(page, doc2);
    await expect(page.getByText(`This looks like it's from ${correspondent}.`)).toBeVisible();
    await expect(page.getByText(`Suggested tags: ${tag}`)).toBeVisible();

    await page.getByRole("button", { name: "Confirm", exact: true }).click();

    await expect(page.getByText(`/No type/${correspondent}/No date`)).toBeVisible();
    await expect(page.getByRole("button", { name: tag })).toHaveClass(/border-transparent/);
  });
});

test.describe("Classification suggestion — reject", () => {
  test("rejecting a suggestion leaves the document unclassified", async ({ page }) => {
    const correspondent = `E2E EDF Reject ${RUN_ID}`;
    await createCorrespondent(page, correspondent);

    const doc = `e2e-reject-1-${RUN_ID}.txt`;
    await uploadTextDocument(
      page, doc,
      `Invoice from ${correspondent} — please find your monthly statement attached below.`
    );
    await openDocumentAndWaitReady(page, doc);

    await expect(page.getByText(`This looks like it's from ${correspondent}.`)).toBeVisible();
    await page.getByRole("button", { name: "Dismiss" }).click();

    await expect(page.getByText(`This looks like it's from ${correspondent}.`)).not.toBeVisible();
    await expect(page.getByText("/No type/No correspondent/No date")).toBeVisible();
  });
});
