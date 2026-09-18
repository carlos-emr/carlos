/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const {
  MARKERS, PRINT_CONTROLS_CSS, auditJsp, definesMarker, hasMarker,
  printCallOffsets, resolveElements, stripJspNoise,
} = require('./lib/print-control-audit');

const REPO = path.join(__dirname, '..');
const RA_DIR = path.join(REPO, 'src', 'main', 'webapp', 'WEB-INF', 'jsp', 'billing', 'CA', 'ON');

/*
 * Issue #3278: Admin > Billing > billing reconciliation printed the page as-is,
 * so the Print button the operator had just clicked appeared in the printout.
 *
 * The fix is print CSS, and print CSS is the kind of fix that rots silently:
 * nothing fails, nothing logs, and nobody notices until a clinic files a
 * reconciliation report with a button drawn on it. These tests pin both halves
 * of it - the marker class on the controls, and a stylesheet that actually
 * defines that class on the page - for the whole RA page family, not just the
 * one page the issue named.
 */

/**
 * Every page reachable from Admin > Billing > billing reconciliation that
 * prints itself. onGenRA.jsp is the entry page the issue reports; the rest are
 * the popups its Error / Summary / Report links open, which have the same
 * button and the same defect.
 */
const RECONCILIATION_PAGES = [
  'onGenRA.jsp',
  'onGenRAError.jsp',
  'onGenRASummary.jsp',
  'genRADesc.jsp',
  'genRASummary.jsp',
  'genRASummaryDetail.jsp',
];

test('the rule this enforces is the one CLAUDE.md states', () => {
  // Doc and enforcement drift apart silently; this fails the build when they do.
  const claudeMd = fs.readFileSync(path.join(REPO, 'CLAUDE.md'), 'utf8');
  assert.match(claudeMd, /Print CSS on Pages That Print Themselves/);
  assert.match(claudeMd, /css\/print-controls\.css/);
  for (const marker of MARKERS) assert.ok(claudeMd.includes(marker));
});

test('the shared print stylesheet hides the marker classes, and only in print', () => {
  const css = fs.readFileSync(PRINT_CONTROLS_CSS, 'utf8');
  const printBlock = /@media\s+print\s*\{([\s\S]*)\}/.exec(css);
  assert.ok(printBlock, 'css/print-controls.css must scope its rules to @media print');
  for (const marker of MARKERS) {
    assert.ok(printBlock[1].includes(`.${marker}`),
      `css/print-controls.css must hide .${marker} so pages can mark controls with it`);
  }
  assert.match(printBlock[1], /display:\s*none\s*!important/,
    'the rule needs !important to beat the inline and id styles these legacy pages carry');
});

test('every reconciliation page that prints itself is covered by this audit', () => {
  // Guards against the audit quietly narrowing: a page that grows a Print
  // button must be listed here, and a listed page that loses one must be
  // removed deliberately rather than by the audit silently skipping it.
  const printing = fs.readdirSync(RA_DIR)
    .filter((name) => /^(onGenRA|genRA)/.test(name) && name.endsWith('.jsp'))
    .filter((name) => printCallOffsets(stripJspNoise(
      fs.readFileSync(path.join(RA_DIR, name), 'utf8'),
    )).length > 0);
  assert.deepEqual(printing.sort(), [...RECONCILIATION_PAGES].sort());
});

test('no reconciliation page prints its own Print button', () => {
  const offenders = [];
  for (const name of RECONCILIATION_PAGES) {
    const report = auditJsp(path.join(RA_DIR, name));
    assert.ok(report, `${name} no longer calls window.print(); update RECONCILIATION_PAGES`);
    for (const control of report.unmarked) offenders.push(`${name}: ${control}`);
  }
  assert.deepEqual(offenders, [],
    'a control that fires window.print() is not inside an element marked '
    + `${MARKERS.map((m) => `.${m}`).join(' or ')}, so it prints itself`);
});

test('every reconciliation page actually defines the marker class it uses', () => {
  // The half that is easy to forget. Marking a button d-print-none on a page
  // that loads neither Bootstrap nor css/print-controls.css hides nothing, and
  // looks exactly like a page that is fixed.
  const missing = RECONCILIATION_PAGES.filter(
    (name) => !definesMarker(fs.readFileSync(path.join(RA_DIR, name), 'utf8')),
  );
  assert.deepEqual(missing, [],
    'page marks controls d-print-none but loads no stylesheet that defines it');
});

test('the Close buttons beside Print are hidden too', () => {
  // Same toolbar, same paper. A printout showing "Close" is the same defect
  // wearing a different label.
  const offenders = [];
  for (const name of RECONCILIATION_PAGES) {
    const source = stripJspNoise(fs.readFileSync(path.join(RA_DIR, name), 'utf8'));
    const offsets = [];
    const pattern = /window\s*\.\s*close\s*\(/g;
    let match = pattern.exec(source);
    while (match !== null) {
      offsets.push(match.index);
      match = pattern.exec(source);
    }
    const resolved = resolveElements(source, offsets);
    for (const offset of offsets) {
      const entry = resolved.get(offset);
      if (!entry) continue;
      const marked = hasMarker(entry.element)
        || entry.ancestors.some((ancestor) => hasMarker(ancestor.tag));
      if (!marked) offenders.push(`${name}: ${entry.element.replace(/\s+/g, ' ').trim()}`);
    }
  }
  assert.deepEqual(offenders, [], 'a Close button is left visible in the printout');
});

test('the provider picker toolbars are hidden', () => {
  // The Generate/provider-select toolbars are navigation: on paper they are a
  // dropdown frozen on one value and a button that does nothing.
  const withToolbars = ['onGenRAError.jsp', 'onGenRASummary.jsp', 'genRASummary.jsp', 'genRASummaryDetail.jsp'];
  for (const name of withToolbars) {
    const source = stripJspNoise(fs.readFileSync(path.join(RA_DIR, name), 'utf8'));
    const offset = source.indexOf('Generate');
    assert.notEqual(offset, -1, `${name} should still have its Generate control`);
    const entry = resolveElements(source, [offset]).get(offset);
    assert.ok(entry, `${name}: could not resolve the element holding the Generate control`);
    const marked = hasMarker(entry.element)
      || entry.ancestors.some((ancestor) => hasMarker(ancestor.tag));
    assert.ok(marked, `${name}: the Generate toolbar is not marked, so it prints`);
  }
});

test("the RA list's Action column is hidden but its Status column is not", () => {
  // The Action column is a strip of Error/Summary/Report links - chrome, on
  // paper. Status is not: "Processed" versus a Settle action is the one thing
  // the printed list says about where each RA stands, so hiding the pair
  // together would trade one defect for a worse one.
  const source = stripJspNoise(fs.readFileSync(path.join(RA_DIR, 'onGenRA.jsp'), 'utf8'));
  const marked = source.match(/<t[hd][^>]*\bd-print-none\b[^>]*>/g) || [];
  assert.ok(marked.length >= 2,
    'expected the Action header and its cells to carry the marker');
  const statusHeader = /<th[^>]*>\s*Status\s*<\/th>/.exec(source);
  assert.ok(statusHeader, 'the Status column header should still be rendered');
  assert.ok(!hasMarker(statusHeader[0]), 'the Status column must still print');
});

/*
 * THE FAILURE MODE TO GUARD AGAINST IS THE AUDIT FINDING NOTHING. If the tag
 * scanner or the class matcher breaks, every test above passes on a webapp full
 * of self-printing buttons. These two run the detector against markup whose
 * answer is known, including the malformed nesting these legacy pages really
 * contain: <form> directly inside <table>, and <td> left unclosed.
 */

test('the detector flags an unmarked print control', () => {
  const page = [
    '<html><head><link rel="stylesheet" href="/css/print-controls.css"/></head><body>',
    '<table><form action="/x" method="post"><tr>',
    '<th><input type="button" value="Print" onClick="window.print()"></th>',
    '<td>cell left unclosed',
    '</tr></form></table></body></html>',
  ].join('\n');
  const file = path.join(REPO, 'target', 'print-audit-unmarked.jsp');
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, page);
  try {
    assert.equal(auditJsp(file).unmarked.length, 1);
  } finally {
    fs.rmSync(file, { force: true });
  }
});

test('the detector accepts a control hidden by an ancestor, not just by itself', () => {
  // How the real pages are written: the marker sits on the <th> that holds the
  // button, so ancestor resolution has to work through the malformed nesting.
  const page = [
    '<html><head><link rel="stylesheet" href="/css/print-controls.css"/></head><body>',
    '<table><form action="/x" method="post"><tr>',
    '<th class="d-print-none"><input type="button" value="Print" onClick="window.print()"></th>',
    '<td>cell left unclosed',
    '</tr></form></table></body></html>',
  ].join('\n');
  const file = path.join(REPO, 'target', 'print-audit-marked.jsp');
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, page);
  try {
    assert.deepEqual(auditJsp(file).unmarked, []);
  } finally {
    fs.rmSync(file, { force: true });
  }
});

test('a print() in a script body is not mistaken for a rendered control', () => {
  // Some pages print from JavaScript rather than from a button. There is no
  // element to hide, and reporting one would be a false positive that pushes
  // someone to mark the wrong thing.
  const page = '<html><body><script>function go(){ window.print(); }</script></body></html>';
  const file = path.join(REPO, 'target', 'print-audit-script.jsp');
  fs.mkdirSync(path.dirname(file), { recursive: true });
  fs.writeFileSync(file, page);
  try {
    assert.deepEqual(auditJsp(file).unmarked, []);
  } finally {
    fs.rmSync(file, { force: true });
  }
});
