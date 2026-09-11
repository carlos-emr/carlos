import assert from "node:assert/strict";
import { mkdtemp, readFile, rm, writeFile } from "node:fs/promises";
import { tmpdir } from "node:os";
import { join } from "node:path";
import test from "node:test";
import { configureAndroidSecurity } from "./configure-android-security.mjs";

test("Android backup exclusions are applied exactly once", async () => {
  const directory = await mkdtemp(join(tmpdir(), "mycarlos-android-security-"));
  try {
    const manifestPath = join(directory, "AndroidManifest.xml");
    await writeFile(
      manifestPath,
      '<manifest xmlns:android="http://schemas.android.com/apk/res/android"><application android:allowBackup="true" android:label="myCarlos" /></manifest>',
    );

    await configureAndroidSecurity(manifestPath);
    await configureAndroidSecurity(manifestPath);
    const manifest = await readFile(manifestPath, "utf8");
    assert.equal((manifest.match(/android:allowBackup="false"/g) ?? []).length, 1);
    assert.equal((manifest.match(/android:fullBackupContent="false"/g) ?? []).length, 1);
    assert.doesNotMatch(manifest, /android:allowBackup="true"/);
  } finally {
    await rm(directory, { recursive: true, force: true });
  }
});
