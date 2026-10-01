/* SPDX-License-Identifier: GPL-2.0-or-later */
// Pins the #3977 response contract that lab-embedded-pdf-playwright-checks.js asserts on a
// live deployment, so a weakened assertion fails here without one.
const test = require('node:test');
const assert = require('node:assert/strict');
const {
  buildMessage, headerMap, assertInlinePdfResponse, assertRefusal, PDF, HTML_PAYLOAD, ownPreferences,
} = require('./lab-embedded-pdf-playwright-checks');

const GOOD_HEADERS = {
  'content-type': 'application/pdf',
  'content-disposition': 'inline; filename="Lab-12.pdf"',
  'x-content-type-options': 'nosniff',
  'cache-control': 'no-store',
  'content-security-policy': "default-src 'none'; frame-ancestors 'self'; sandbox",
};

test('accepts the hardened inline PDF response', () => {
  assertInlinePdfResponse(200, GOOD_HEADERS, PDF);
});

test('rejects an inline response missing any hardening header', () => {
  for (const header of ['x-content-type-options', 'cache-control', 'content-security-policy']) {
    const headers = { ...GOOD_HEADERS };
    delete headers[header];
    assert.throws(() => assertInlinePdfResponse(200, headers, PDF), undefined, header);
  }
  assert.throws(() => assertInlinePdfResponse(200,
    { ...GOOD_HEADERS, 'content-security-policy': "default-src 'none'; frame-ancestors 'self'" }, PDF), /sandbox/);
});

test('rejects an inline response missing any single CSP directive', () => {
  // Each directive individually, so dropping one from the live check's directive loop fails here.
  for (const missing of ["default-src 'none'", "frame-ancestors 'self'", 'sandbox']) {
    const csp = GOOD_HEADERS['content-security-policy'].replace(missing, '');
    assert.notEqual(csp, GOOD_HEADERS['content-security-policy'], missing);
    assert.throws(() => assertInlinePdfResponse(200, { ...GOOD_HEADERS, 'content-security-policy': csp }, PDF),
      new RegExp(`lacks ${missing}`), missing);
  }
});

test('rejects an attachment, a wrong type or non-PDF bytes on the inline route', () => {
  assert.throws(() => assertInlinePdfResponse(200,
    { ...GOOD_HEADERS, 'content-disposition': 'attachment; filename="Lab-12.pdf"' }, PDF), /inline/);
  assert.throws(() => assertInlinePdfResponse(200, { ...GOOD_HEADERS, 'content-type': 'text/html' }, PDF), /application\/pdf/);
  assert.throws(() => assertInlinePdfResponse(200, GOOD_HEADERS, HTML_PAYLOAD), /not a PDF/);
  assert.throws(() => assertInlinePdfResponse(404, GOOD_HEADERS, PDF), /404/);
});

test('a refusal must have the expected status, no body and no PDF type', () => {
  assertRefusal(415, {}, Buffer.alloc(0), 415, 'x');
  assert.throws(() => assertRefusal(200, {}, Buffer.alloc(0), 415, 'x'), /answered 200/);
  assert.throws(() => assertRefusal(415, { 'content-type': 'application/pdf' }, Buffer.alloc(0), 415, 'x'), /application\/pdf/);
  assert.throws(() => assertRefusal(415, {}, Buffer.from('<html>'), 415, 'x'), /body/);
});

test('the seeded lab mixes a text result, the PDF and a non-PDF ED payload', () => {
  const message = buildMessage('PW3977-abc', 'FAKE-PWX');
  const segments = message.split('\r').filter(Boolean);
  assert.equal(segments.filter(line => line.startsWith('OBR|')).length, 3);
  assert.ok(segments.some(line => line.startsWith('OBX|1|NM|GLU^')));
  assert.ok(message.includes(`^TEXT^PDF^Base64^${PDF.toString('base64')}`));
  assert.ok(message.includes(`^TEXT^HTML^Base64^${HTML_PAYLOAD.toString('base64')}`));
  assert.equal(PDF.subarray(0, 5).toString('ascii'), '%PDF-');
});

test('keeps both CSP headers the front door sends', () => {
  const headers = headerMap([
    { name: 'Content-Type', value: 'application/pdf' },
    { name: 'Content-Security-Policy', value: "default-src 'none'; frame-ancestors 'self'; sandbox" },
    { name: 'content-security-policy', value: "frame-ancestors 'self'; base-uri 'self'; object-src 'none'" },
  ]);
  assert.match(headers['content-security-policy'], /sandbox/);
  assert.match(headers['content-security-policy'], /object-src 'none'/);
  assertInlinePdfResponse(200, { ...GOOD_HEADERS, 'content-security-policy': headers['content-security-policy'] }, PDF);
});

/**
 * A stand-in for the two SystemPreferences rows: rows() reads the current state in the column
 * shape ownPreferences() selects, and execute() applies exactly the DELETE/UPDATE shapes its
 * cleanup emits (anything else throws), so the cleanup's final re-fetch sees what really ran.
 */
function fakePreferenceTable(initial) {
  const table = new Map(initial.map(row => [row.id, { ...row }]));
  const executed = [];
  const rows = () => [...table.values()].sort((a, b) => Number(a.id) - Number(b.id)).map(row => [
    row.id, row.name, row.valueNull ? '' : row.hex, row.valueNull ? '1' : '0',
    row.updatedNull ? '' : row.updated, row.updatedNull ? '1' : '0']);
  const execute = query => {
    executed.push(query);
    const del = /^DELETE FROM SystemPreferences WHERE name IN \([^)]*\)(?: AND id NOT IN\(([\d,]+)\))?$/.exec(query);
    if (del) {
      const keep = del[1] ? del[1].split(',') : [];
      for (const id of [...table.keys()]) if (!keep.includes(id)) table.delete(id);
      return;
    }
    const upd = /^UPDATE SystemPreferences SET value=(?:NULL|UNHEX\('([0-9A-F]*)'\)),\s+updateDate=(?:NULL|'([^']*)') WHERE id=(\d+)$/
      .exec(query);
    if (upd) {
      const row = table.get(upd[3]);
      if (row) {
        Object.assign(row, {
          valueNull: upd[1] === undefined, hex: upd[1] ?? '',
          updatedNull: upd[2] === undefined, updated: upd[2] ?? '',
        });
      }
      return;
    }
    throw new Error(`the fake preference table does not model: ${query}`);
  };
  return { table, executed, rows, execute };
}

test('restores NULL preference values and dates as SQL NULL, not the string null', () => {
  // Rows as the harness returns them: the IS NULL flags carry nullness, never a NULL token.
  const fake = fakePreferenceTable([
    { id: '7', name: 'lab_pdf_max_size', hex: '', valueNull: true, updated: '', updatedNull: true },
    { id: '8', name: 'lab_pdf_inline_preview', hex: '66616C7365', valueNull: false,
      updated: '2026-09-30 10:00:00', updatedNull: false },
  ]);
  const { executed } = fake;
  let cleanup;
  const s = { sql: { rows: fake.rows, execute: fake.execute }, cleanup: fn => { cleanup = fn; } };
  ownPreferences(s);
  // What the check does in between: both rows rewritten and stamped, and a third row inserted.
  Object.assign(fake.table.get('7'), { hex: '31303438353736', valueNull: false, updated: '2026-10-01 09:00:00', updatedNull: false });
  Object.assign(fake.table.get('8'), { hex: '74727565', updated: '2026-10-01 09:00:00' });
  fake.table.set('9', { id: '9', name: 'lab_pdf_max_size', hex: '313030', valueNull: false, updated: '2026-10-01 09:00:00', updatedNull: false });
  cleanup();
  const restores = executed.filter(query => query.startsWith('UPDATE'));
  assert.equal(restores.length, 2);
  assert.match(restores[0], /SET value=NULL,\s+updateDate=NULL WHERE id=7$/);
  assert.match(restores[1], /SET value=UNHEX\('66616C7365'\),\s+updateDate='2026-09-30 10:00:00' WHERE id=8$/);
  assert.ok(executed.every(query => !/'null'|'NULL'/.test(query)), 'no NULL may be restored as a string');
  assert.deepEqual(fake.rows(), [
    ['7', 'lab_pdf_max_size', '', '1', '', '1'],
    ['8', 'lab_pdf_inline_preview', '66616C7365', '0', '2026-09-30 10:00:00', '0'],
  ]);
});

test('fails the cleanup when a restore does not bring a row back', () => {
  const fake = fakePreferenceTable([
    { id: '8', name: 'lab_pdf_inline_preview', hex: '66616C7365', valueNull: false,
      updated: '2026-09-30 10:00:00', updatedNull: false },
  ]);
  let cleanup;
  // Drop every restore UPDATE: the re-fetch then still shows the check's value.
  const s = {
    sql: { rows: fake.rows, execute: query => (query.startsWith('UPDATE') ? undefined : fake.execute(query)) },
    cleanup: fn => { cleanup = fn; },
  };
  ownPreferences(s);
  Object.assign(fake.table.get('8'), { hex: '74727565' });
  assert.throws(() => cleanup(), /not restored exactly/);
});

test('refuses a preference snapshot whose NULL arrived as a JS null', () => {
  const s = {
    sql: { rows: () => [['7', 'lab_pdf_max_size', null, '1', null, '1']], execute: () => {} },
    cleanup: () => {},
  };
  assert.throws(() => ownPreferences(s), /Unexpected preference snapshot/);
});
