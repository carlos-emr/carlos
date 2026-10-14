/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/documentManager/incomingDocs.jsp'), 'utf8');
const start = jsp.indexOf('        function extractPagePdf(');
const end = jsp.indexOf('        function printPdf(', start);
assert.ok(start >= 0 && end > start, 'Actual JSP extraction handler must be present');
const handler = jsp.slice(start, end).replaceAll('<%=numOfPage%>', '3')
  .replace(/<fmt:message key="([^"]+)"\s*\/>/g, '$1');

function run(range, totalPage = 3) {
  const alerts = [];
  let submissions = 0;
  const form = Object.fromEntries(['pdfNo', 'pdfDir', 'pdfName', 'pdfAction', 'pdfPageNumber', 'pdfExtractPageNumber']
    .map(name => [name, { value: '' }]));
  form.submit = () => submissions++;
  const context = { totalPage, curPage: 1, prompt: () => range, alert: message => alerts.push(message),
    trim: value => value.replace(/^\s+|\s+$/g, ''), document: { PdfInfoForm: form } };
  vm.runInNewContext(`${handler}\nextractPagePdf(1, 'File', 'synthetic.pdf');`, context, { timeout: 100 });
  return { alerts, submissions, form };
}

test('Cancel leaves the document and form untouched without an error or alert', () => {
  const result = run(null);
  assert.equal(result.submissions, 0);
  assert.deepEqual(result.alerts, []);
  assert.equal(result.form.pdfAction.value, '');
});
for (const range of ['', ' ', '0', '4', '2-1', '1-99999999999', '9007199254740993', '1e2', '1.5', '-1', '1,,2', '1-2-3', '1-3', '1,2,3']) {
  test(`Invalid or whole-document selection ${JSON.stringify(range)} is visibly refused without submitting`, () => {
    const result = run(range);
    assert.equal(result.submissions, 0);
    assert.deepEqual(result.alerts, ['dms.incomingDocs.invalidPages']);
    assert.equal(result.form.pdfAction.value, '');
  });
}
for (const range of ['2', '1-2', '1,3', '2,2', ' 2 ', '1, 3']) {
  test(`Valid selection ${JSON.stringify(range)} submits the intended PDF exactly once`, () => {
    const result = run(range);
    assert.equal(result.submissions, 1);
    assert.deepEqual(result.alerts, []);
    assert.equal(result.form.pdfAction.value, 'ExtractPagePDF');
    assert.equal(result.form.pdfName.value, 'synthetic.pdf');
    assert.equal(result.form.pdfExtractPageNumber.value, range.trim());
  });
}
test('A single-page PDF is refused without opening an extraction operation', () => {
  const result = run('1', 1);
  assert.equal(result.submissions, 0);
  assert.deepEqual(result.alerts, ['dms.incomingDocs.nothingToExtract']);
});
