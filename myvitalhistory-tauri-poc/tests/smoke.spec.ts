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

test("shows the purpose of the library navigation sections", async ({ page }, testInfo) => {
  await page.goto("/");

  async function openSection(value: string, label: string) {
    if (testInfo.project.name === "phone") {
      await page.getByRole("combobox", { name: "Section" }).selectOption(value);
    } else {
      await page.getByRole("button", { name: new RegExp(`^${label}`) }).click();
    }
  }

  await openSection("recent", "Recent");
  await expect(page.getByRole("heading", { name: "Recent" })).toBeVisible();

  await openSection("starred", "Starred");
  await expect(page.getByRole("heading", { name: "Starred" })).toBeVisible();
  await expect(page.getByText("Prescription — ramipril 5mg")).not.toBeVisible();

  await openSection("trash", "Trash");
  await expect(page.getByText("Gone in 27 days")).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath("trash.png"), fullPage: true });
  await page.getByRole("button", { name: "Restore" }).first().click();
  await expect(page.getByText("Bloodwork — thyroid", { exact: true })).not.toBeVisible();

  await openSection("security", "Security & backup");
  const cloudBackup = page.getByRole("button", { name: "Backup to iCloud demo" });
  await expect(cloudBackup).toHaveAttribute("aria-pressed", "true");
  await page.screenshot({ path: testInfo.outputPath("security.png"), fullPage: true });
  await cloudBackup.click();
  await expect(cloudBackup).toHaveAttribute("aria-pressed", "false");

  await openSection("health", "Health data");
  await expect(page.getByText("Nothing connected")).toBeVisible();
  await page.getByRole("button", { name: "Connect demo" }).first().click();
  await expect(page.getByRole("heading", { name: "Sample health snapshot" })).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath("health.png"), fullPage: true });
});

test("carries a record through starred, trash, and restore", async ({ page }, testInfo) => {
  await page.goto("/");

  async function openSection(value: string, label: string) {
    if (testInfo.project.name === "phone") {
      await page.getByRole("combobox", { name: "Section" }).selectOption(value);
    } else {
      await page.getByRole("button", { name: new RegExp(`^${label}`) }).click();
    }
  }

  const title = "Prescription — ramipril 5mg";
  await page.getByRole("button", { name: `More options for ${title}` }).click();
  await expect(page.getByRole("dialog", { name: title })).toBeVisible();
  await page.screenshot({ path: testInfo.outputPath("record-preview.png"), fullPage: true });
  await page.getByRole("button", { name: "Add to Starred" }).click();
  await page.getByRole("button", { name: "Close document preview" }).click();

  await openSection("starred", "Starred");
  await expect(page.getByText(title, { exact: true })).toBeVisible();
  await page.getByRole("button", { name: `More options for ${title}` }).click();
  await page.getByRole("button", { name: "Move to Trash" }).click();
  await expect(page.getByText(title, { exact: true })).not.toBeVisible();

  await openSection("trash", "Trash");
  const deletedRow = page.locator("article").filter({ hasText: title });
  await expect(page.getByText("Gone in 30 days")).toBeVisible();
  await deletedRow.getByRole("button", { name: "Restore" }).click();

  await openSection("records", "My records");
  await expect(page.getByText(title, { exact: true })).toBeVisible();
});
