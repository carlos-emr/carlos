/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const {
  MENU_ITEMS, MINIMUM_PDF_BYTES, assertIsPdf,
} = require('./demographic-labels-playwright-checks');

const SOURCE = fs.readFileSync(path.join(__dirname, 'demographic-labels-playwright-checks.js'), 'utf8');
const MENU_JSP = fs.readFileSync(path.join(
  __dirname, '..', 'src', 'main', 'webapp', 'WEB-INF', 'jsp', 'demographic', 'edit-form-clinical.jsp',
), 'utf8');
const BUNDLE = fs.readFileSync(path.join(
  __dirname, '..', 'src', 'main', 'resources', 'oscarResources_en.properties',
), 'utf8');

/** A minimal but structurally honest PDF, for the positive case. */
function samplePdf(size = MINIMUM_PDF_BYTES + 100) {
  const head = '%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\n';
  const tail = '\ntrailer<</Root 1 0 R>>\n%%EOF\n';
  return Buffer.from(head + 'x'.repeat(Math.max(0, size - head.length - tail.length)) + tail, 'latin1');
}

/*
 * These labels are printed onto a physical chart and an envelope that goes in
 * the post, so "the popup opened" is not the question. CLAUDE.md's
 * direct-response section exists because actions that stream bytes have twice
 * shipped an HTML error page inside a download; these tests keep the check
 * honest about looking at the bytes.
 */

test('every menu item this check drives is one the page actually renders', () => {
  // The labels come from the bundle, so a renamed key would otherwise leave the
  // check silently looking for an item that no longer exists.
  for (const item of MENU_ITEMS) {
    assert.ok(BUNDLE.includes(`=${item.label}`),
      `"${item.label}" is not a label in oscarResources_en.properties`);
  }
});

test('the check covers the whole Print / Labels menu, not a sample of it', () => {
  // Per line, not one sweeping pattern: each item's onclick contains "%>", so
  // an attribute-spanning [^>]* stops inside it and matches nothing.
  const rendered = MENU_JSP.split('\n')
    .filter((line) => line.includes('class="dropdown-item"'))
    .map((line) => {
      const keys = [...line.matchAll(/<fmt:message key="([\w.]+)"\s*\/>/g)].map((found) => found[1]);
      return keys.length ? keys[keys.length - 1] : null;
    })
    .filter(Boolean);
  assert.ok(rendered.length >= 6, `expected the print menu to render several items, found ${rendered.length}`);
  const values = rendered.map((key) => {
    const line = BUNDLE.split('\n').find((candidate) => candidate.startsWith(`${key}=`));
    return line ? line.slice(key.length + 1).trim() : key;
  });
  const checked = MENU_ITEMS.map((item) => item.label);
  const missed = values.filter((value) => !checked.includes(value));
  assert.deepEqual(missed, [], `these menu items are rendered but unchecked: ${missed.join(', ')}`);
});

test('the settings item is not asserted to be a PDF, because it is not one', () => {
  // ViewDemographicLabelPrintSetting is an HTML page the user picks a layout on.
  // Asserting %PDF on it would fail on entirely correct behaviour.
  const settings = MENU_ITEMS.find((item) => item.label === 'Print Label');
  assert.ok(settings, 'the label-layout settings item must stay in the list');
  assert.equal(settings.pdf, false);
  assert.match(MENU_JSP, /printHtmlLbl = demoPath \+ "ViewDemographicLabelPrintSetting/);
  const pdfItems = MENU_ITEMS.filter((item) => item.pdf);
  assert.ok(pdfItems.length >= 5, 'most of this menu is generated PDFs and must be asserted as such');
});

test('an optional item says why it may be absent', () => {
  for (const item of MENU_ITEMS.filter((candidate) => candidate.optional)) {
    assert.match(item.optional, /property/,
      `${item.label} must name the property that gates it`);
  }
  // And that property really is what gates it in the JSP.
  assert.match(MENU_JSP, /getProperty\("showSexualHealthLabel", "false"\)/);
});

test('a real PDF passes every gate', () => {
  assert.doesNotThrow(() => assertIsPdf(
    { label: 'PDF Chart Label' }, 200, 'application/pdf', samplePdf(),
  ));
});

test('an HTML error page wearing a PDF content type is caught', () => {
  // The PR #2043 shape: the action failed after the container set the type, so
  // a content-type check alone passes it straight through.
  const errorPage = Buffer.from(
    '<html><head><title>CARLOS Error</title></head><body>CARLOS Error: 0</body></html>', 'latin1',
  );
  assert.throws(
    () => assertIsPdf({ label: 'PDF Envelope' }, 200, 'application/pdf', errorPage),
    /returned HTML where a PDF was expected/,
  );
});

test('a truncated PDF is caught, not accepted for starting correctly', () => {
  // A stream cut off by a post-write exception still starts with %PDF and still
  // carries the right Content-Type; it opens as a corrupt file in the clinic.
  const truncated = Buffer.from(`%PDF-1.4\n${'x'.repeat(1000)}`, 'latin1');
  assert.throws(
    () => assertIsPdf({ label: 'PDF Label' }, 200, 'application/pdf', truncated),
    /no %%EOF trailer/,
  );
});

test('bytes that are not a PDF are caught whatever the Content-Type claims', () => {
  const notPdf = Buffer.from(`GIF89a${'x'.repeat(1000)}%%EOF`, 'latin1');
  assert.throws(
    () => assertIsPdf({ label: 'PDF Label' }, 200, 'application/pdf', notPdf),
    /does not start with the %PDF magic bytes/,
  );
});

test('a PDF served under the wrong Content-Type is caught', () => {
  assert.throws(
    () => assertIsPdf({ label: 'PDF Label' }, 200, 'text/html;charset=UTF-8', samplePdf()),
    /right bytes but Content-Type/,
  );
});

test('a non-200 is a failure, with the CARLOS Error: 0 shape named', () => {
  assert.throws(
    () => assertIsPdf({ label: 'PDF Label' }, 500, 'application/pdf', samplePdf()),
    /answered HTTP 500/,
  );
});

test('an implausibly small file is caught', () => {
  const tiny = Buffer.from('%PDF-1.4\n%%EOF\n', 'latin1');
  assert.throws(
    () => assertIsPdf({ label: 'PDF Label' }, 200, 'application/pdf', tiny),
    /too small to be a rendered label/,
  );
});

test('the menu is opened by clicking its toggle, and the items by clicking them', () => {
  assert.match(SOURCE, /button\.dropdown-toggle/);
  assert.match(SOURCE, /a\.dropdown-item/);
  assert.ok(!/page\.goto\(/.test(SOURCE),
    'entering by address would skip the Bootstrap dropdown this check exists to exercise');
  // The bytes are fetched from the URL the CLICK produced, never one built here.
  assert.match(SOURCE, /context\.request\.get\(produced\.url/);
  assert.ok(!/ViewPrintEnvelope|printDemoChartLabelAction|ViewPrintAddressLabel/.test(SOURCE),
    'the check must name the menu item a user clicks, not the route behind it');
});

test('the check writes nothing', () => {
  for (const statement of [/\bINSERT\s+INTO\b/i, /\bUPDATE\s+\w/i, /\bDELETE\s+FROM\b/i, /createSqlRunner/]) {
    assert.ok(!statement.test(SOURCE), `generating a label is read-only; found ${statement}`);
  }
});
