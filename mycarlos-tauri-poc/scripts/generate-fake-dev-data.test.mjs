import assert from "node:assert/strict";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import test from "node:test";
import { generateFakeDevData } from "./generate-fake-dev-data.mjs";

test("generates clearly prefixed valid PDF fixtures and an exact duplicate", async () => {
  const outputDirectory = await mkdtemp(path.join(os.tmpdir(), "mycarlos-fake-data-"));
  try {
    const generated = await generateFakeDevData(outputDirectory);
    assert.equal(generated.length, 6);
    assert.ok(generated.every((name) => name.startsWith("FAKE")));

    const original = await readFile(path.join(outputDirectory, "FAKE_Avery_Patient_Bloodwork.pdf"));
    const duplicate = await readFile(path.join(outputDirectory, "FAKE_Avery_Patient_Bloodwork_DUPLICATE.pdf"));
    assert.deepEqual(duplicate, original);
    assert.match(original.toString("ascii"), /^%PDF-1\.4/);
    assert.match(original.toString("ascii"), /Patient: FAKE Avery Patient/);
    assert.match(original.toString("ascii"), /%%EOF\n$/);

    const manifest = JSON.parse(await readFile(path.join(outputDirectory, "FAKE_MANIFEST.json"), "utf8"));
    assert.equal("suggestedPassphrase" in manifest, false);
    assert.ok(manifest.profiles.every((profile) => profile.name.startsWith("FAKE ")));
    assert.ok(manifest.profiles.flatMap((profile) => profile.folders)
      .every((folder) => folder.startsWith("FAKE ")));
  } finally {
    await rm(outputDirectory, { recursive: true, force: true });
  }
});
