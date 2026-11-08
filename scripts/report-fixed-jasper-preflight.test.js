/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { preflight } = require('./report-fixed-jasper-playwright-checks');

test('bundled qualification refuses configured overrides and override qualification requires its own marker', () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'jasper-preflight-'));
  const previous = { home: process.env.CARLOS_REPORT_SERVER_HOME, text: process.env.CARLOS_FIXED_JASPER_OVERRIDE_TEXT };
  try {
    process.env.CARLOS_REPORT_SERVER_HOME = directory;
    delete process.env.CARLOS_FIXED_JASPER_OVERRIDE_TEXT;
    assert.doesNotThrow(() => preflight());
    fs.writeFileSync(path.join(directory, 'Addresslabel.xml'), '<jasperReport/>');
    assert.throws(() => preflight(), /requires no Addresslabel.xml/);
    assert.throws(() => preflight('override'), /OVERRIDE_TEXT/);
    process.env.CARLOS_FIXED_JASPER_OVERRIDE_TEXT = 'Synthetic override';
    assert.doesNotThrow(() => preflight('override'));
    assert.throws(() => preflight(), /requires no Addresslabel.xml/);
  } finally {
    for (const [key, value] of [['CARLOS_REPORT_SERVER_HOME', previous.home], ['CARLOS_FIXED_JASPER_OVERRIDE_TEXT', previous.text]]) {
      if (value === undefined) delete process.env[key]; else process.env[key] = value;
    }
    fs.rmSync(directory, { recursive: true, force: true });
  }
});
