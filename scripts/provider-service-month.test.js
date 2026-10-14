/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/oscarReport/provider_service_report_form.jsp'), 'utf8');
// Execute the real initializer and capture the options actually passed to the picker.
const fields = ['startDate', 'endDate'].map(id => ({id, addEventListener() {}}));
const form = {elements: {startDate: fields[0], endDate: fields[1]}, addEventListener() {}};
const pickers = [];
const initializer = jsp.match(/<script>\s*([\s\S]*?)<\/script>/i)[1];
vm.runInNewContext(initializer, {
  document: {getElementById: id => id === 'psrForm' ? form : undefined},
  flatpickr: (field, options) => pickers.push({field, options}),
});
assert.equal(pickers.length, 2);
assert.deepEqual(pickers.map(picker => picker.field.id), ['startDate', 'endDate']);
for (const picker of pickers) assert.equal(typeof picker.options.parseDate, 'function');
const parseMonth = value => {
  const parsed = pickers.map(picker => picker.options.parseDate(value));
  assert.equal(parsed[0]?.getTime(), parsed[1]?.getTime());
  return parsed[0];
};
for (let month = 1; month <= 12; month++) {
  const value = `${String(month).padStart(2, '0')}/1953`;
  test(`The report picker parses ${value} without changing its month`, () => {
    const date = parseMonth(value);
    assert.equal(date.getFullYear(), 1953);
    assert.equal(date.getMonth(), month - 1);
    assert.equal(date.getDate(), 1);
  });
}
for (const value of ['', '2/1953', '00/1953', '13/1953', '02/53', '02/1953extra', ' 02/1953', '02/1953 ', '02/0000']) {
  test(`The report picker refuses malformed month ${JSON.stringify(value)}`, () => {
    assert.equal(parseMonth(value), undefined);
  });
}

test('The picker preserves year 0001 instead of applying the JavaScript 1900 offset', () => {
  const date = parseMonth('02/0001');
  assert.equal(date.getFullYear(), 1);
  assert.equal(date.getMonth(), 1);
});

test('Both month pickers retain the month parser on mobile devices', () => {
  for (const picker of pickers) assert.equal(picker.options.disableMobile, true);
});
