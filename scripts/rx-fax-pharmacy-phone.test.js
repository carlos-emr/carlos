/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

// Issue #3974 (port of Open-O PR #2494): the "[Rx faxed to ...]" encounter note carries the
// pharmacy phone. Runs the real printPaste2Parent from rx/ViewScript2.jsp with its JSP output
// expressions replaced by what the server would render, so the composed note, the placement of
// the Tel: segment and the as-is retry path are asserted without a browser or server.
// scripts/rx-fax-pharmacy-phone-playwright-checks.js covers the same behaviour end to end.

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/rx/ViewScript2.jsp'), 'utf8');

const PHONE_EXPR = '<%= SafeEncode.forJavaScript(pharmacyPhoneSegment) %>';
const NAME_EXPR = '<%= pharmacy!=null?SafeEncode.forJavaScript(pharmacy.getName()):""%>';
const FAX_EXPR = '<%= pharmacy!=null?SafeEncode.forJavaScript(pharmacy.getFax()):""%>';
const TIMESTAMP_EXPR = '<%= timeStamp %>';

/** Enough of OWASP Encode.forJavaScript for these fixtures: every character that ends a JS string or a script block. */
function forJavaScript(value) {
  return String(value).replace(/[\\'"&/\r\n]/g, (c) => ({
    '\\': '\\\\', '\'': '\\x27', '"': '\\x22', '&': '\\x26', '/': '\\/', '\r': '\\r', '\n': '\\n',
  })[c]);
}

/** Mirrors the scriptlet in ViewScript2.jsp: " Tel: <phones>" or "" (composePharmacyPhone is unit-tested in Java). */
function segmentFor(phones) {
  return phones ? ` Tel: ${phones}` : '';
}

function printPaste2ParentSource({ name, fax, phones }) {
  const start = jsp.indexOf('function printPaste2Parent(');
  const end = jsp.indexOf('function writeToEncounter(', start);
  assert.ok(start >= 0 && end > start, 'printPaste2Parent is missing from ViewScript2.jsp');
  let source = jsp.slice(start, end).replace(/<%--[\s\S]*?--%>/g, '');
  for (const [expr, value] of [[PHONE_EXPR, forJavaScript(segmentFor(phones))], [NAME_EXPR, forJavaScript(name)],
    [FAX_EXPR, forJavaScript(fax)], [TIMESTAMP_EXPR, '26-Sep-2026 10:15 AM']]) {
    assert.ok(source.includes(expr), `printPaste2Parent no longer contains ${expr}`);
    source = source.split(expr).join(value);
  }
  // Every other scriptlet (property switches, the provider name, the demographic number) is
  // irrelevant here; replace it the same way scripts/rx-fax-notes-lock.test.js does.
  return source.replace(/<%[\s\S]*?%>/g, '1').replace(/<carlos:encode\b[^>]*\/>/g, 'Dr Example');
}

function run(pharmacy) {
  const writes = [];
  const context = vm.createContext({
    faxSubmissionUncertain: false, faxPreviewReloading: false, isReprint: false, hasPreview: true,
    faxQueued: false, faxSubmissionPending: false, faxPasteCanRetry: false, lastFaxPasteText: null,
    window: { parent: { opener: null } },
    document: { getElementById: () => null, all: undefined },
    alert: (message) => { throw new Error(String(message)); },
    Promise,
    writeToEncounter: (print, text) => { writes.push(text); return Promise.resolve(true); },
  });
  vm.runInContext(printPaste2ParentSource(pharmacy), context);
  return { context, writes };
}

const faxedLine = (text) => text.split('\n').find((line) => line.startsWith('[Rx faxed to '));

test('the note carries both numbers straight after the fax number', async () => {
  const { context, writes } = run({ name: 'Main St Pharmacy', fax: '5550101', phones: '555-0100 555-0102' });
  assert.equal(await context.printPaste2Parent(false, true, true, 'Amoxicillin 500 mg\n'), true);
  assert.equal(faxedLine(writes[0]),
    '[Rx faxed to Main St Pharmacy Fax#: 5550101 Tel: 555-0100 555-0102 prescribed by Dr Example, 26-Sep-2026 10:15 AM]');
});

test('with no phone on file the note is exactly the pre-feature text', async () => {
  const { context, writes } = run({ name: 'Main St Pharmacy', fax: '5550101', phones: '' });
  await context.printPaste2Parent(false, true, true, 'Amoxicillin 500 mg\n');
  const line = faxedLine(writes[0]);
  assert.equal(line, '[Rx faxed to Main St Pharmacy Fax#: 5550101 prescribed by Dr Example, 26-Sep-2026 10:15 AM]');
  assert.doesNotMatch(line, /Tel:|null| {2}/);
});

test('script-significant phone text stays inside the JavaScript string and arrives verbatim', async () => {
  const hostile = '\'"</script><b>1';
  const { context, writes } = run({ name: 'Main St Pharmacy', fax: '5550101', phones: hostile });
  await context.printPaste2Parent(false, true, true, 'Amoxicillin 500 mg\n');
  assert.ok(faxedLine(writes[0]).includes(`Fax#: 5550101 Tel: ${hostile} prescribed by `));
});

test('a paste retry reuses the captured text as-is and never appends the segment twice', async () => {
  const { context, writes } = run({ name: 'Main St Pharmacy', fax: '5550101', phones: '555-0100' });
  await context.printPaste2Parent(false, true, true, 'Amoxicillin 500 mg\n');
  const captured = context.lastFaxPasteText;
  assert.equal(captured, writes[0]);
  await context.printPaste2Parent(false, true, true, captured, true);
  assert.equal(writes[1], writes[0]);
  assert.equal((writes[1].match(/ Tel: /g) || []).length, 1);
});

test('the print path does not gain a Tel: segment', async () => {
  const { context, writes } = run({ name: 'Main St Pharmacy', fax: '5550101', phones: '555-0100' });
  await context.printPaste2Parent(true, false, true, 'Amoxicillin 500 mg\n');
  assert.doesNotMatch(writes[0], /Tel:|Rx faxed to/);
});

test('the segment is built server-side from composePharmacyPhone and JavaScript-encoded', () => {
  assert.match(jsp, /String pharmacyPhone = RxPharmacyData\.composePharmacyPhone\(pharmacy\);/);
  assert.match(jsp, /String pharmacyPhoneSegment = pharmacyPhone\.isEmpty\(\) \? "" : " Tel: " \+ pharmacyPhone;/);
  // One output of the segment, and only in the single-quoted JS string after the Fax#: part.
  assert.equal(jsp.split('pharmacyPhoneSegment').length - 1, 2); // its declaration and its one output
  assert.ok(jsp.includes(`text += '${PHONE_EXPR}';`));
  assert.ok(jsp.indexOf(`text += '${PHONE_EXPR}';`) > jsp.indexOf(`" Fax#: " + '${FAX_EXPR}';`));
});
