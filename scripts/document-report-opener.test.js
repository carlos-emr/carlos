/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const source = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/documentManager/documentReport.jsp'), 'utf8');
const setup = source.match(/function setup\(\) \{[\s\S]*?\n            \}/)[0];
const ready = source.slice(source.indexOf('jQuery(document).ready('), source.indexOf('</script>', source.indexOf('jQuery(document).ready(')))
  .replace(/<%=request.getContextPath\(\) %>/g, '/carlos')
  .replace(/<fmt:message key="global.i18n.datatablescode"\/>/g, 'en');

function load({ update = 'true', parentId = 'docs', opener, modern = false } = {}) {
  const refreshed = [];
  const warnings = [];
  const liveOpener = { closed: false, URLs: { docs: '/carlos/encounter/displayDocs?demographic_no=1' },
    popLeftColumn(...args) { refreshed.push(args); } };
  if (modern) {
    liveOpener.document = { getElementById(id) { return id === 'docs' ? {} : null; } };
    liveOpener.reloadNav = (...args) => refreshed.push(args);
  }
  let tables = 0;
  const renderedSetup = setup
    .replace(/"<carlos:encode value='<%= updateParent %>' context="javaScriptBlock"\/>"/, JSON.stringify(update))
    .replace(/"<carlos:encode value='<%= parentAjaxId %>' context="javaScriptBlock"\/>"/, JSON.stringify(parentId));
  vm.runInNewContext(`${renderedSetup}\n${ready}`, {
    document: {}, window: { opener: opener === undefined ? liveOpener : opener },
    console: { warn(message) { warnings.push(message); } },
    jQuery() { return { ready(callback) { callback(); }, DataTable() { tables++; } }; },
  });
  return { refreshed, warnings, tables };
}

test('document list initialization refreshes the requested chart module after upload', () => {
  const result = load();
  assert.deepEqual(result.refreshed, [['/carlos/encounter/displayDocs?demographic_no=1', 'docs', 'docs']]);
  assert.equal(result.tables, 1);
  assert.deepEqual(result.warnings, []);
});

test('document list uses the current E-Chart refresh API when available', () => {
  const result = load({ modern: true });
  assert.deepEqual(result.refreshed, [['docs']]);
  assert.equal(result.tables, 1);
  assert.deepEqual(result.warnings, []);
});

test('the current chart callback cannot target an arbitrary module', () => {
  const result = load({ modern: true, parentId: 'not-a-chart-module' });
  assert.deepEqual(result.refreshed, []);
  assert.equal(result.warnings.length, 1);
});

test('an incomplete legacy opener does not break document list initialization', () => {
  const result = load({ opener: { closed: false, URLs: { docs: '/legacy-docs' } } });
  assert.deepEqual(result.refreshed, []);
  assert.equal(result.tables, 1);
  assert.equal(result.warnings.length, 1);
});

test('opening the list normally does not refresh the chart', () => {
  const result = load({ update: 'false' });
  assert.deepEqual(result.refreshed, []);
  assert.deepEqual(result.warnings, []);
  assert.equal(result.tables, 1);
});

for (const [name, options] of [['missing opener', { opener: null }], ['closed opener', { opener: { closed: true } }],
  ['unknown module', { parentId: 'not-a-chart-module' }]]) {
  test(`document list initialization remains usable with ${name}`, () => {
    const result = load(options);
    assert.deepEqual(result.refreshed, []);
    assert.equal(result.warnings.length, 1);
    assert.equal(result.tables, 1);
  });
}
