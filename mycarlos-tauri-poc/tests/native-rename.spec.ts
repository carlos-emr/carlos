import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";

test("renames native-library folders and documents with accessible dialogs", async ({ page }, testInfo) => {
  // Exercise the native UI and IPC argument wiring in a real browser. Rust tests
  // separately verify persistence, encryption, and unchanged document contents.
  await page.addInitScript(() => {
    const snapshot = {
      profiles: [{ id: "profile", displayName: "FAKE Patient", createdAtMs: 1 }],
      folders: [{ id: "folder", profileId: "profile", parentId: null, name: "FAKE Old folder", createdAtMs: 1 }],
      records: [{ id: "record", profileId: "profile", folderIds: [], displayName: "FAKE Old.pdf", sourceLabel: "Manual import — unverified", mediaType: "application/octet-stream", plaintextSize: 200, importedAtMs: 1 }],
      degraded: false,
    };
    Reflect.set(window, "__TAURI_INTERNALS__", {
      invoke: async (command: string, args?: { request: { folderId?: string; recordId?: string; parentId?: string | null; name: string } }) => {
        if (command === "vault_status") return "unlocked";
        if (command === "vault_snapshot") return structuredClone(snapshot);
        if (command === "vault_update_folder" && args?.request.folderId === "folder" && args.request.parentId === null) {
          snapshot.folders[0].name = args.request.name;
          return;
        }
        if (command === "vault_rename_record" && args?.request.recordId === "record") {
          snapshot.records[0].displayName = args.request.name;
          return;
        }
        throw new Error(`Unexpected IPC command: ${command}`);
      },
    });
  });
  await page.goto("/");
  await page.getByRole("button", { name: "Rename folder FAKE Old folder" }).click();
  const folderDialog = page.getByRole("dialog", { name: "Rename folder" });
  await expect(folderDialog.getByLabel("Folder name")).toBeFocused();
  const dialogAccessibility = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
  expect(dialogAccessibility.violations).toEqual([]);
  await folderDialog.getByLabel("Folder name").fill("FAKE Results");
  await page.screenshot({ path: testInfo.outputPath("rename-folder.png"), fullPage: true });
  await folderDialog.getByRole("button", { name: "Save name" }).click();
  await expect(page.getByRole("article", { name: "FAKE Results folder" })).toBeVisible();
  await page.getByRole("button", { name: "Grid view" }).click();
  await page.getByRole("button", { name: "Rename folder FAKE Results" }).click();
  await page.getByRole("button", { name: "Cancel", exact: true }).click();
  await page.getByRole("article", { name: "FAKE Old.pdf document" }).getByRole("button").click();
  await page.getByRole("button", { name: "Rename document" }).click();
  const documentDialog = page.getByRole("dialog", { name: "Rename document" });
  await documentDialog.getByLabel("File name").fill("FAKE Bloodwork.pdf");
  await documentDialog.getByRole("button", { name: "Save name" }).click();
  await expect(page.getByRole("dialog", { name: "FAKE Bloodwork.pdf" })).toBeVisible();
  await page.getByRole("button", { name: "Close document details" }).click();
  await expect(page.getByRole("article", { name: "FAKE Bloodwork.pdf document" })).toBeVisible();
  const libraryAccessibility = await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21a", "wcag21aa"]).analyze();
  expect(libraryAccessibility.violations).toEqual([]);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath("renamed-library.png"), fullPage: true });
});
