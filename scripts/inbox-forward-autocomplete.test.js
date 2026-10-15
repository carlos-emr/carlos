/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const webapp = path.join(__dirname, '../src/main/webapp');
const inbox = fs.readFileSync(path.join(webapp, 'WEB-INF/jsp/web/inboxhub/Inboxhub.jsp'), 'utf8');
const dialog = fs.readFileSync(path.join(webapp, 'WEB-INF/jsp/oscarMDS/SelectProvider.jsp'), 'utf8');
const start = dialog.indexOf('    initProviderAutocomplete(');
const end = dialog.indexOf('    function removeProvider(', start);
assert.ok(start >= 0 && end > start);
const initialize = dialog.slice(start, end).replace('<%= request.getContextPath() %>', '/carlos');

for (const results of [[], [{ providerNo: '42', firstName: 'Synthetic', lastName: 'Provider' }]]) {
  test(`Inbox-loaded dependencies let the Forward fragment search and select (${results.length} results)`, () => {
    let options;
    const requests = [];
    const selected = [];
    const input = { value: 'Provider', focus() {} };
    const widget = { length: 1, autocomplete(value) {
      if (value === 'instance') return {};
      options = value;
      return this;
    } };
    const jQuery = selector => {
      assert.equal(selector, '#autocompleteprov');
      return widget;
    };
    jQuery.ajax = request => { requests.push(request); request.success({ results }); };
    const context = vm.createContext({ jQuery, console: { error(message) { assert.fail(message); } }, document: {
      getElementById(id) {
        if (id === 'autocompleteprov') return input;
        if (id === 'fwdProviders') return { add(option) { selected.push(option); } };
        return null;
      },
      createElement(tag) {
        assert.ok(['div', 'option'].includes(tag));
        // Plain fixture names need no HTML escaping; hostile-name behavior has separate shared-helper tests.
        return { textContent: '', get innerHTML() { return this.textContent; } };
      },
    } });
    // The sanitized dialog only executes inline scripts. Load its dependencies from the parent page's
    // actual script list, so omitting either import reproduces the live missing-helper/formatter failure.
    for (const match of inbox.matchAll(/<script\b[^>]*\bsrc="([^"]+)"[^>]*>/g)) {
      const file = match[1].split('/').pop();
      if (['demographicProviderAutocomplete.js', 'carlosAutocomplete.js'].includes(file)) {
        vm.runInContext(fs.readFileSync(path.join(webapp, 'js', file), 'utf8'), context);
      }
    }
    vm.runInContext(initialize, context);
    let offered;
    options.source({ term: 'Provider' }, items => { offered = items; });
    assert.equal(requests.length, 1);
    assert.equal(requests[0].url, '/carlos/provider/SearchProvider');
    assert.equal(requests[0].type, 'POST');
    assert.equal(requests[0].data.query, 'Provider');
    assert.equal(offered.length, results.length);
    if (offered.length) {
      options.select({}, { item: offered[0] });
      assert.equal(selected.length, 1);
      assert.equal(selected[0].value, '42');
      assert.equal(selected[0].text, 'Provider, Synthetic');
      assert.equal(input.value, '');
    } else assert.equal(selected.length, 0);
  });
}
