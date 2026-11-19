/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
function exportCsv(values, tagName = 'TD') {
  const table = {rows: [{cells: values.map(textContent => ({textContent, tagName}))}]};
  const context = {document: {getElementById: () => table}, btoa: s => Buffer.from(s, 'binary').toString('base64')};
  vm.runInNewContext(fs.readFileSync('src/main/webapp/js/table-export.js', 'utf8'), context);
  const anchor = {};
  assert.equal(context.TableExport.csv(anchor, 'report'), true);
  return Buffer.from(anchor.href.split(',')[1], 'base64').toString('utf8');
}
test('includes a UTF-8 BOM and preserves quotes, commas, line breaks and accents', () => {
  assert.equal(exportCsv(['Zoë', 'a,b', 'a"b', 'a\nb']), '\uFEFFZoë,"a,b","a""b","a\nb"\r\n');
});
test('normalizes header whitespace without changing line breaks in data', () => {
  assert.equal(exportCsv([' SERVICE\n  DATE '], 'TH'), '\uFEFFSERVICE DATE\r\n');
  assert.equal(exportCsv(['line one\nline two']), '\uFEFF"line one\nline two"\r\n');
});
test('exports negative adjustments as numeric CSV values', () => {
  assert.equal(exportCsv(['-12.50', '-0.01', '-123']), '\uFEFF-12.50,-0.01,-123\r\n');
});
for (const value of ['=1+1', '+SUM(A1)', '-1+2', '-1E3+2', '@SUM(A1)', '-cmd|x']) {
  test(`neutralizes formula ${value}`, () => assert.equal(exportCsv([value]), `\uFEFF"\t${value}"\r\n`));
}
// Issue #5846: the browser export and the server's SpreadsheetCsv.sanitizeCell guard the same
// cells. SpreadsheetCsvUnitTest reads this fixture too, so the two policies cannot drift apart.
const sharedCases = JSON.parse(fs.readFileSync('scripts/fixtures/spreadsheet-csv-cell-cases.json', 'utf8')).cases;
function cellPolicy() {
  const context = {document: {getElementById: () => null}, btoa: s => Buffer.from(s, 'binary').toString('base64')};
  vm.runInNewContext(fs.readFileSync('src/main/webapp/js/table-export.js', 'utf8'), context);
  return context.TableExport.sanitizeCell;
}
test('applies the shared spreadsheet cell policy to every fixture case', () => {
  const sanitizeCell = cellPolicy();
  assert.ok(sharedCases.length > 0);
  for (const {input, expected} of sharedCases) {
    assert.equal(sanitizeCell(input), expected, JSON.stringify(input));
  }
});
for (const value of ['＝1+1', '＋SUM(A1)', '＠SUM(A1)']) {
  test(`neutralizes full-width formula ${JSON.stringify(value)} in an exported cell`, () =>
    assert.equal(exportCsv([value]), `﻿"\t${value}"\r\n`));
}
