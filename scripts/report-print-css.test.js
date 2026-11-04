/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
// Static guard for issue #3342: the report pages must load the shared print
// stylesheet, mark their interactive controls report-print-hide, and keep
// result rows out of <thead> so a printed header row repeats alone.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const webapp = path.join(__dirname, '../src/main/webapp');
const read = rel => fs.readFileSync(path.join(webapp, rel), 'utf8');
const css = read('css/report-print.css');

const PAGES = {
  visit: 'WEB-INF/jsp/oscarReport/oscarReportVisitControl.jsp',
  pcn: 'WEB-INF/jsp/oscarReport/oscarReportCatchment.jsp',
  phcp: 'WEB-INF/jsp/report/reportonbilledphcp.jsp',
  reconciliation: 'WEB-INF/jsp/billing/CA/ON/onGenRA.jsp',
};

test('the print stylesheet exists and only hides inside @media print', () => {
  assert.match(css, /@media print\s*\{/);
  // Outside the print block only the screen-hidden criteria rule and @page may appear.
  const outside = css.replace(/\/\*[\s\S]*?\*\//g, '').split('@media print')[0];
  assert.equal(outside.replace(/\s+/g, ''), '.report-print-only{display:none;}@page{margin:12mm;}');
  assert.match(css, /\.report-print-hide\s*\{\s*display:\s*none\s*!important/);
  assert.match(css, /thead\s*\{[^}]*table-header-group/);
  assert.match(css, /break-inside:\s*avoid/);
  assert.match(css, /^@page\s*\{[^}]*margin:/m, '@page must be a top-level rule');
});

for (const [name, page] of Object.entries(PAGES)) {
  test(`${name} loads the shared print stylesheet`, () => {
    assert.match(read(page), /\/css\/report-print\.css/);
  });
}

test('Visit Report hides its Print button, provider link and filter form in print', () => {
  const src = read(PAGES.visit);
  assert.match(src, /<button name="print"[^>]*report-print-hide/);
  assert.match(src, /class="[^"]*report-print-hide[^"]*" id="visitForm"/);
  assert.match(src, /<div class="d-print-none report-print-hide" style="float:right;">/);
  assert.match(src, /id="visitReportPrintCriteria"/);
});

test('Visit Report result table keeps rows in tbody, outside the repeating thead', () => {
  const src = read('WEB-INF/jsp/oscarReport/oscarReportVisit_vr.jspf');
  assert.match(src, /<table[^>]*report-print-table/);
  const head = src.indexOf('<thead>');
  const headEnd = src.indexOf('</thead>');
  const bodyStart = src.indexOf('<tbody>');
  assert.ok(head >= 0 && headEnd > head && bodyStart > headEnd, 'thead must close before tbody opens');
  const theadHtml = src.slice(head, headEnd);
  assert.equal((theadHtml.match(/<tr>/g) || []).length, 1, 'thead holds only the column header row');
  assert.ok(src.lastIndexOf('</tbody>') > src.indexOf('TOTAL'), 'tbody closes after the totals row');
});

test('PCN hides Print and pagination in print', () => {
  const src = read(PAGES.pcn);
  assert.match(src, /name='print'[^>]*report-print-hide/);
  assert.match(src, /<nav class="[^"]*report-print-hide[^"]*" id="pcnPagination">/);
  assert.match(src, /<table class="[^"]*report-print-table[^"]*"/);
});

test('PHCP hides the filter form and Print/Exit buttons and repeats its header rows', () => {
  const src = read(PAGES.phcp);
  assert.match(src, /<form name="myform" class="report-print-hide"/);
  assert.match(src, /<th[^>]*class="report-print-hide"><input type="button" name="Button"/);
  assert.match(src, /<table class="report-print-table report-print-wide"/);
  const start = src.indexOf('<table class="report-print-table report-print-wide"');
  const end = src.indexOf('</table>', start);
  const block = src.slice(start, end);
  assert.match(block, /<thead>[\s\S]*<\/thead>\s*<tbody>[\s\S]*<\/tbody>\s*$/);
});

test('billing reconciliation hides its own Print button in print', () => {
  const src = read(PAGES.reconciliation);
  assert.match(src, /name='print'[^>]*/);
  assert.match(src, /class="btn btn-primary float-end d-print-none report-print-hide" type='button' name='print'/);
  // Row actions are workflow controls: the Action column and the Settle/S35 links.
  assert.match(src, /<th class="report-print-hide">Action<\/th>/);
  assert.match(src, /<td align="center" class="report-print-hide">\s*<a href="[^"]*ViewOnGenRAError/);
  const settleLinks = src.match(/<span class="report-print-hide">\s*<a href="#" onclick="checkReconcile\(/g) || [];
  assert.equal(settleLinks.length, 2, 'both Settle/S35 link groups are print-hidden');
});

test('administration shell removes the schedule menu and full-width pane in print', () => {
  const src = read('WEB-INF/jsp/administration/index.jsp');
  const print = src.slice(src.indexOf('@media print'));
  assert.match(print, /#firstTable,\s*\.noprint\s*\{[^}]*display:\s*none/);
  assert.match(print, /#dynamic-content\s*\{[^}]*max-width:\s*100%/);
});
