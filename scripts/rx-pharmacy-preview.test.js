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

function setup(hasFrame = true, hasWarning = true) {
  let target = null;
  let load;
  const frame = {
    style: {}, contentWindow: { document: { getElementById: () => target } },
    addEventListener: (_event, callback) => { load = callback; },
  };
  const warning = { hidden: true };
  const context = vm.createContext({
    document: { getElementById: (id) => id === 'preview' ? (hasFrame ? frame : null) : (hasWarning ? warning : null) },
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
  assert.equal(fixture.warning.hidden, false);
});

test('removing pharmacy details before the iframe loads does not restore stale content', () => {
  const fixture = setup();
  fixture.context.expandPreview('stale details');
  fixture.context.reducePreview();
  const target = { innerHTML: 'server content' };
  fixture.setTarget(target);
  fixture.load();
  assert.equal(target.innerHTML, '');
  assert.equal(fixture.warning.hidden, true);
  assert.equal(fixture.frame.style.width, '460px');
});

test('pharmacy text escapes HTML and attribute delimiters, including missing values', () => {
  const { context } = setup();
  assert.equal(context.pharmacyText('<img src=x onerror="bad()"> & \'injection\''),
    '&lt;img src=x onerror=&quot;bad()&quot;&gt; &amp; &#39;injection&#39;');
  assert.equal(context.pharmacyText(null), '');
  assert.equal(context.pharmacyText(undefined), '');
});

test('pharmacy warning exists in the real JSP rather than only in the mock DOM', () => {
  assert.match(jsp, /<p id="selectedPharmacy" role="status" hidden><fmt:message key="oscarRx.printPharmacyInfo.paperSizeWarning"\/><\/p>/);
});

for (const [hasFrame, hasWarning] of [[false, true], [true, false], [false, false]]) {
  test(`pharmacy preview tolerates absent optional DOM (frame=${hasFrame}, warning=${hasWarning})`, () => {
    const fixture = setup(hasFrame, hasWarning);
    assert.doesNotThrow(() => fixture.context.expandPreview('safe pharmacy'));
    assert.doesNotThrow(() => fixture.context.reducePreview());
    assert.equal(fixture.warning.hidden, true);
  });
}
