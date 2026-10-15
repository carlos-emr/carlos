/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const web = path.join(__dirname, '../src/main/webapp/WEB-INF/jsp');
const child = fs.readFileSync(path.join(web, 'billing/CA/ON/billingDigSearch.jsp'), 'utf8');
const parent = fs.readFileSync(path.join(web, 'provider/providerpreference.jsp'), 'utf8');
const callbackStart = parent.indexOf('var dxSearchModalShown');
const callbackEnd = parent.indexOf('</script>', callbackStart);
assert(callbackStart >= 0 && callbackEnd > callbackStart);
const childStart = child.indexOf('function CodeAttach(File2)');
const childEnd = child.indexOf('function setfocus()', childStart);
assert(childStart >= 0 && childEnd > childStart);
// Render the default popup branch of the JSP choose; the iframe route executes before it.
const childFunction = child.slice(childStart, childEnd).replace(/<c:choose>[\s\S]*?<c:otherwise>([\s\S]*?)<\/c:otherwise>[\s\S]*?<\/c:choose>/, '$1');

function parentWindow(shown = true) {
  const box = { value: '' };
  const events = new Map();
  const modal = { addEventListener(name, handler) { events.set(name, handler); } };
  const frame = { src: '' };
  const fire = name => events.get(name).call(modal);
  let hidden = 0;
  const context = vm.createContext({
    document: { getElementById: id => id === 'dxCode' ? box : id === 'dxSearchFrame' ? frame : modal },
    bootstrap: { Modal: { getInstance: node => { assert.equal(node, modal); return { hide() { hidden++; } }; } } },
  });
  vm.runInContext(parent.slice(callbackStart, callbackEnd), context);
  if (shown) fire('shown.bs.modal');
  return { context, box, frame, fire, hidden: () => hidden };
}

test('single result reaches the preference callback during child parsing, without an opener or onload override', () => {
  const parent = parentWindow();
  const self = { parent: parent.context, opener: null };
  const context = vm.createContext({ self, alert: () => assert.fail('unexpected alert'), setTimeout: () => assert.fail('must not close iframe') });
  vm.runInContext(childFunction + "CodeAttach('250.1|Diabetes type 1');", context);
  assert.equal(parent.box.value, '250.1');
  assert.equal(parent.hidden(), 1);
});

test('preference selection keeps the whole code and excludes its description', () => {
  const parent = parentWindow();
  for (const code of ['2', '401', '250.1', 'ZR123']) {
    parent.context.selectDefaultDiagnosticCode(code + '|Description with | punctuation');
    assert.equal(parent.box.value, code);
  }
  parent.context.selectDefaultDiagnosticCode(null);
  assert.equal(parent.hidden(), 4);
});

test('standalone popup continues writing the complete diagnostic detail to its opener', () => {
  const detail = { value: '' };
  let changed = 0;
  let closed = false;
  const self = { opener: { document: { forms: [{}, { xml_diagnostic_detail: detail }] }, callChangeCodeDesc() { changed++; } }, close() { closed = true; } };
  self.parent = self;
  const context = vm.createContext({ self, alert: () => assert.fail('unexpected alert'), setTimeout(code) { vm.runInContext(code, context); } });
  vm.runInContext(childFunction + "CodeAttach('401|Hypertension');", context);
  assert.equal(detail.value, '401|Hypertension');
  assert.equal(changed, 1);
  assert.equal(closed, true);
});

for (const opener of [null, { closed: true }]) {
  test(`missing or closed popup opener reports an explicit transfer failure (${opener === null ? 'missing' : 'closed'})`, () => {
    let alerts = 0;
    const self = { opener };
    self.parent = self;
    const context = vm.createContext({ self, alert: () => alerts++, setTimeout: () => assert.fail('must keep failed selection visible') });
    vm.runInContext(childFunction + "CodeAttach('401|Hypertension');", context);
    assert.equal(alerts, 1);
  });
}

test('selection during the opening transition closes exactly after shown', () => {
  const parent = parentWindow(false);
  parent.fire('show.bs.modal');
  parent.context.selectDefaultDiagnosticCode('250.1|Diabetes type 1');
  assert.equal(parent.box.value, '250.1');
  assert.equal(parent.hidden(), 0);
  parent.fire('shown.bs.modal');
  assert.equal(parent.hidden(), 1);
  parent.fire('hidden.bs.modal');
  assert.equal(parent.frame.src, 'about:blank');
  parent.fire('show.bs.modal');
  parent.fire('shown.bs.modal');
  assert.equal(parent.hidden(), 1, 'the previous selection must not close a reopened modal');
});
