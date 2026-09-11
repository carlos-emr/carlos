/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/rx/ViewScript2.jsp'), 'utf8');
const start = jsp.indexOf('function pharmacyText(');
const end = jsp.indexOf('</script>', start);
assert.ok(start >= 0 && end > start);
const source = jsp.slice(start, end).replace(/<fmt:message\b[^>]*\/>/g, 'paper-size warning');

function setup() {
  let target = null;
  let load;
  const frame = {
    style: {}, contentWindow: { document: { getElementById: () => target } },
    addEventListener: (_event, callback) => { load = callback; },
  };
  const warning = { innerHTML: '' };
  const context = vm.createContext({
    document: { getElementById: (id) => id === 'preview' ? frame : warning },
    parent: { document: { querySelector: () => null } },
  });
  vm.runInContext(source, context);
  return { context, frame, warning, setTarget: (value) => { target = value; }, load: () => load() };
}

test('pharmacy details arriving before the iframe are applied after its load', () => {
  const fixture = setup();
  fixture.context.expandPreview('safe pharmacy details');
  assert.equal(fixture.context.applyPharmacyPreview(), false);
  const target = { innerHTML: '' };
  fixture.setTarget(target);
  fixture.load();
  assert.equal(target.innerHTML, 'safe pharmacy details');
  assert.equal(fixture.frame.style.width, '600px');
});

test('removing pharmacy details before the iframe loads does not restore stale content', () => {
  const fixture = setup();
  fixture.context.expandPreview('stale details');
  fixture.context.reducePreview();
  const target = { innerHTML: 'server content' };
  fixture.setTarget(target);
  fixture.load();
  assert.equal(target.innerHTML, '');
  assert.equal(fixture.warning.innerHTML, '');
  assert.equal(fixture.frame.style.width, '460px');
});

test('pharmacy text escapes HTML and attribute delimiters, including missing values', () => {
  const { context } = setup();
  assert.equal(context.pharmacyText('<img src=x onerror="bad()"> & \'injection\''),
    '&lt;img src=x onerror=&quot;bad()&quot;&gt; &amp; &#39;injection&#39;');
  assert.equal(context.pharmacyText(null), '');
  assert.equal(context.pharmacyText(undefined), '');
});
