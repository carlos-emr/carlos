import { expect, test } from "@playwright/test";

test("renders the mock-aligned patient library at each target viewport", async ({ page }, testInfo) => {
  await page.goto("/");

  await expect(page.getByRole("heading", { name: "My records" })).toBeVisible();
  await expect(page.getByText("Heart & blood pressure")).toBeVisible();
  await expect(page.getByText("Bloodwork — cholesterol and liver panel")).toBeVisible();
  await expect(page.getByText("Technology evaluation only")).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath("library.png"), fullPage: true });

  await page.getByText("Evaluation details").click();
  await expect(page.getByText("Browser preview", { exact: true }).first()).toBeVisible();
});

test("imports browser-selected PDF metadata for the session", async ({ page }) => {
  await page.goto("/");

  const chooserPromise = page.waitForEvent("filechooser");
  await page.getByRole("button", { name: "New document — choose a sample PDF" }).click();
  const chooser = await chooserPromise;
  await chooser.setFiles({
    name: "fictional-browser-record.pdf",
    mimeType: "application/pdf",
    buffer: Buffer.from("%PDF-1.4\n% synthetic test file\n"),
  });

  await expect(page.getByText("fictional-browser-record.pdf", { exact: true })).toBeVisible();
  await expect(page.getByText("fictional-browser-record.pdf was added for this session only.")).toBeVisible();

  await page.getByText("Evaluation details").click();
  await page.getByRole("button", { name: "Reset session" }).click();
  await expect(page.getByText("fictional-browser-record.pdf", { exact: true })).not.toBeVisible();
  await expect(page.getByText("Evaluation reset. Only the built-in sample records are shown.")).toBeVisible();
});
