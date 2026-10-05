/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/oscarReport/provider_service_report_form.jsp'), 'utf8');
const body = jsp.match(/function parseMonth\(value\) \{([\s\S]*?)\n        \}/)[1];
const parseMonth = vm.runInNewContext(`(function parseMonth(value) {${body}})`);
for (let month = 1; month <= 12; month++) {
  const value = `${String(month).padStart(2, '0')}/1953`;
  test(`The report picker parses ${value} without changing its month`, () => {
    const date = parseMonth(value);
    assert.equal(date.getFullYear(), 1953);
    assert.equal(date.getMonth(), month - 1);
    assert.equal(date.getDate(), 1);
  });
}
for (const value of ['', '2/1953', '00/1953', '13/1953', '02/53', '02/1953extra', ' 02/1953', '02/1953 ']) {
  test(`The report picker refuses malformed month ${JSON.stringify(value)}`, () => {
    assert.equal(parseMonth(value), undefined);
  });
}
