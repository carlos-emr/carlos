/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/rx/SearchDrug3.jsp'), 'utf8');
const source = jsp.slice(jsp.indexOf('function checkIfInactive('), jsp.indexOf('function Discontinue('));
function check(response, failure = false) {
  const target = { textContent: 'old warning', setAttribute() {} };
  let sent;
  const context = { document: { getElementById: () => target }, ctx: '/carlos', CarlosAjax: {
    request(url, options) {
      sent = {url, options};
      assert.equal(target.textContent, 'Checking drug status…');
      if (failure) options.onFailure();
      else options.onSuccess({responseText: response});
    },
  }};
  vm.runInNewContext(source, context);
  context.checkIfInactive('row&1', '02245547');
  return {target, sent};
}
test('inactive date remains a calendar date, independent of browser timezone', () => {
  assert.equal(check(JSON.stringify({checked: true, inactiveDate: '2018-07-24'})).target.textContent,
    'Inactive Drug Since: 2018-07-24');
});
test('only a checked empty result clears the warning', () => {
  assert.equal(check(JSON.stringify({checked: true, inactiveDate: null})).target.textContent, '');
});
for (const [name, body] of Object.entries({empty: '', legacyEmpty: '{}', malformed: '<html>error</html>',
  unchecked: '{"checked":false}', missingDate: '{"checked":true}', invalidDate: '{"checked":true,"inactiveDate":42}'})) {
  test(`${name} result visibly reports that status was not checked`, () => {
    assert.match(check(body).target.textContent, /could not be checked/);
  });
}
test('HTTP failure does not clear the warning', () => {
  assert.match(check('', true).target.textContent, /could not be checked/);
});
test('request values are encoded individually', () => {
  const {sent} = check('{"checked":true,"inactiveDate":null}');
  assert.equal(new URLSearchParams(sent.options.postBody).get('id'), 'row&1');
  assert.equal(new URLSearchParams(sent.options.postBody).get('din'), '02245547');
});

for (const date of ['2026-02-31', '2026-99-99', '2026-02-29', '1900-02-29', '2100-02-29',
  '2026-04-31', '2026-00-10', '2026-13-01', '2026-01-00', '2026-01-32']) {
  test(`impossible calendar date ${date} shows the unavailable warning`, () => {
    assert.match(check(JSON.stringify({checked: true, inactiveDate: date})).target.textContent, /could not be checked/);
  });
}
for (const date of ['2024-02-29', '2000-02-29', '1900-02-28', '2026-04-30', '2026-01-31', '2026-12-31']) {
  test(`valid calendar date ${date} is preserved exactly`, () => {
    assert.equal(check(JSON.stringify({checked: true, inactiveDate: date})).target.textContent,
      'Inactive Drug Since: ' + date);
  });
}
