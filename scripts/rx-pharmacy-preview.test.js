/* SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');

const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/rx/ViewScript2.jsp'), 'utf8');
const start = jsp.indexOf('var initialPharmacy =');
const end = jsp.indexOf('</script>', start);
assert.ok(start >= 0 && end > start);
const source = jsp.slice(start, end).replace(/<fmt:message\b[^>]*\/>/g, 'paper-size warning');

function setup(hasFrame = true, hasWarning = true, initialPharmacy = null) {
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
  const rendered = source.replace('<carlos:encode value="<%= pharmacyPreviewJson %>" context="javaScript"/>',
    JSON.stringify(JSON.stringify(initialPharmacy)).slice(1, -1).replace(/'/g, '\\x27'))
    .replace('${carlos:forJavaScript(msg_removePharmacyInfo)}', 'Remove');
  vm.runInContext(rendered, context);
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


test('the selected pharmacy is ready before the iframe loads without an asynchronous lookup', () => {
  const fixture = setup(true, true, { id: '71', name: '<unsafe & pharmacy>', phone1: "x'1", phone2: null });
  const target = { innerHTML: '' };
  fixture.setTarget(target);
  fixture.load();
  assert.ok(target.innerHTML.includes('&lt;unsafe &amp; pharmacy&gt;'));
  assert.ok(target.innerHTML.includes("name='pharmacyInfo' value='71'"));
  assert.ok(target.innerHTML.includes('x&#39;1'));
  assert.ok(!target.innerHTML.includes('null'));
  fixture.context.reducePreview();
  fixture.load();
  assert.equal(target.innerHTML, '', 'an explicit remove must survive iframe reload');
});

for (const missing of [null, undefined, '', '   ']) {
  test(`pharmacy preview omits empty lines, separators and labels (${String(missing)})`, () => {
    const fixture = setup(true, true, { id: '71', name: ' Pharmacy ', address: missing,
      city: missing, province: ' ON ', postalCode: missing, phone1: missing,
      phone2: ' 555-0100 ', fax: missing, email: missing, notes: missing });
    const target = { innerHTML: '' };
    fixture.setTarget(target);
    fixture.load();
    assert.equal(target.innerHTML.split('<br><br>')[0], 'Pharmacy<br>ON<br>Tel:555-0100');
    assert.ok(target.innerHTML.includes("name='pharmacyInfo' value='71'"));
    assert.doesNotMatch(target.innerHTML, /Fax:|Email:|Note:|null|undefined|, /);
  });
}

test('pharmacy preview keeps and encodes every populated contact field', () => {
  const fixture = setup(true, true, { id: '71', name: 'A&B', address: '<Road>', city: 'City',
    province: 'ON', postalCode: 'A1A 1A1', phone1: '111', phone2: '222', fax: '333',
    email: 'a@example.test', notes: '<note>' });
  const target = { innerHTML: '' };
  fixture.setTarget(target);
  fixture.load();
  assert.equal(target.innerHTML.split('<br><br>')[0],
    'A&amp;B<br>&lt;Road&gt;<br>City, ON, A1A 1A1<br>Tel:111 222<br>Fax:333<br>Email:a@example.test<br>Note:&lt;note&gt;');
});
