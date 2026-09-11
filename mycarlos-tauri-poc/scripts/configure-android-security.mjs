import { readFile, writeFile } from "node:fs/promises";
import { pathToFileURL } from "node:url";

export async function configureAndroidSecurity(manifestPath) {
  let manifest = await readFile(manifestPath, "utf8");
  if (!/<application\b/.test(manifest)) {
    throw new Error(`Android application element not found in ${manifestPath}`);
  }

  manifest = manifest
    .replace(/\s+android:allowBackup="[^"]*"/g, "")
    .replace(/\s+android:fullBackupContent="[^"]*"/g, "")
    .replace(
      /<application\b/,
      '<application android:allowBackup="false" android:fullBackupContent="false"',
    );

  await writeFile(manifestPath, manifest, "utf8");
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  await configureAndroidSecurity(
    process.argv[2] ?? "src-tauri/gen/android/app/src/main/AndroidManifest.xml",
  );
}
