/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { checkNoteBrowserProgramContext } = require('./lib/note-browser-program-context-checks');
const { ownedPdfDocuments } = require('./lib/stored-pdf-documents');

for (const location of ['source', 'refile']) {
  for (const kind of ['regular file', 'dangling symlink']) {
    test(`failed Browse Notes ${location} staging preserves a preexisting ${kind}`, async () => {
      const base = fs.mkdtempSync(path.join(os.tmpdir(), 'carlos-note-collision-'));
      const store = path.join(base, 'stored');
      const incoming = path.join(base, 'incoming');
      fs.mkdirSync(store);
      fs.mkdirSync(path.join(incoming, '1', 'Refile'), { recursive: true });
      const marker = 'FAKE-PW0123456789abcdef';
      const docs = ownedPdfDocuments(store, `${marker}-NB`, [{ key: 'delete' }, { key: 'refile' }]);
      const collided = location === 'source' ? docs[0].file
        : path.join(incoming, '1', 'Refile', `R${docs[1].filename.substring(14)}`);
      const existing = Buffer.from('Preexisting evidence must survive failed staging.');
      if (kind === 'regular file') fs.writeFileSync(collided, existing);
      else fs.symlinkSync('missing-original-target', collided);
      const identity = fs.lstatSync(collided);
      const prior = { DOCUMENT_DIR: process.env.DOCUMENT_DIR, INCOMINGDOCUMENT_DIR: process.env.INCOMINGDOCUMENT_DIR };
      const cleanups = [];
      let writes = 0;
      process.env.DOCUMENT_DIR = store;
      process.env.INCOMINGDOCUMENT_DIR = incoming;
      const s = {
        patient: '100', provider: '123456', marker, config: { baseUrl: 'https://example.invalid/carlos' },
        sql: {
          value: query => /SELECT COUNT\(\*\) FROM demographic/.test(query) ? '1' : '0',
          rows: () => [], execute: () => { writes++; },
        },
        cleanup: callback => cleanups.push(callback),
        step: async () => { throw new Error('A browser action ran before file-collision validation'); },
      };
      try {
        await assert.rejects(checkNoteBrowserProgramContext(s, {
          context: {}, charts: [{}, {}], tokens: ['origin-context', 'other-context'],
          programs: ['7', '8'], provider: '123456', role: '2',
        }), /fixture file already exists/);
        for (const cleanup of cleanups.reverse()) await cleanup();
        const retained = fs.lstatSync(collided);
        assert.equal(retained.dev, identity.dev);
        assert.equal(retained.ino, identity.ino);
        if (kind === 'regular file') assert.deepEqual(fs.readFileSync(collided), existing);
        else assert.equal(fs.readlinkSync(collided), 'missing-original-target');
        assert.equal(writes, 0, 'A collision must not trigger document mutations or cleanup writes');
      } finally {
        for (const [key, value] of Object.entries(prior)) {
          if (value === undefined) delete process.env[key];
          else process.env[key] = value;
        }
        fs.rmSync(base, { recursive: true, force: true });
      }
    });
  }
}
