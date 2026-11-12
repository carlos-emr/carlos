/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { homeDirFromProperties } = require('./packaged-home-dir');

test('packaged HOME_DIR uses the installed base, or its explicit override', () => {
  assert.equal(homeDirFromProperties('BASE_DOCUMENT_DIR = /srv/clinic files/'), '/srv/clinic files/carlos/billing/download');
  assert.equal(homeDirFromProperties('BASE_DOCUMENT_DIR=/srv/clinic\nHOME_DIR: /srv/teleplan/'), '/srv/teleplan');
  assert.equal(homeDirFromProperties('HOME_DIR=/old\n# HOME_DIR=/comment\nHOME_DIR=/new'), '/new');
});

test('packaged HOME_DIR cannot silently fall back to a guessed or relative path', () => {
  for (const properties of ['', '# BASE_DOCUMENT_DIR=/comment', 'BASE_DOCUMENT_DIR=',
    'BASE_DOCUMENT_DIR=relative', 'BASE_DOCUMENT_DIR=/valid\nHOME_DIR=', 'HOME_DIR=relative']) {
    assert.throws(() => homeDirFromProperties(properties), /must name an absolute directory/);
  }
});

test('packaged HOME_DIR command requires the actual output folder to exist', t => {
  const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-home-dir-'));
  t.after(() => fs.rmSync(temporary, { recursive: true, force: true }));
  const properties = path.join(temporary, 'carlos.properties');
  const directory = path.join(temporary, 'Teleplan output');
  fs.writeFileSync(properties, `HOME_DIR=${directory}\n`);
  const run = () => spawnSync(process.execPath, [path.join(__dirname, 'packaged-home-dir.js'), properties], { encoding: 'utf8' });
  assert.equal(run().status, 1);
  fs.writeFileSync(directory, 'not a folder');
  assert.equal(run().status, 1);
  fs.unlinkSync(directory);
  fs.mkdirSync(directory);
  const result = run();
  assert.equal(result.status, 0, result.stderr);
  assert.equal(result.stdout, directory);
});
