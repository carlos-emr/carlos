// Licensed under the repository LICENSE. Executes the production js-popup click handler.
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const source = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/share/javascript/popupLink.js'), 'utf8');
function fixture({ blocked = false } = {}) {
  const opened = [];
  let handler;
  const link = { href: 'https://clinic.test/carlos/encounter/ViewLicense',
    getAttribute: name => ({ 'data-popup-width': '400', 'data-popup-height': '300' })[name] };
  const window = {
    open: (url, name, features) => { opened.push({ url, name, features }); return blocked ? null : { opener: null }; }
  };
  const context = vm.createContext({ window,
    document: { addEventListener: (type, fn) => { if (type === 'click') handler = fn; } } });
  vm.runInContext(source, context);
  return { opened, click(modifiers = {}) {
    const event = { button: 0, defaultPrevented: false, metaKey: false, ctrlKey: false, shiftKey: false, altKey: false, ...modifiers,
      target: { closest: selector => (selector === 'a.js-popup' ? link : null) },
      preventDefault() { this.defaultPrevented = true; } };
    handler(event);
    return event;
  } };
}
test('plain click opens the popup and stops same-window navigation', () => {
  const f = fixture(); const event = f.click();
  assert.equal(f.opened.length, 1);
  assert.equal(f.opened[0].url, 'https://clinic.test/carlos/encounter/ViewLicense');
  assert.match(f.opened[0].features, /height=300,width=400/);
  assert.match(f.opened[0].features, /,noopener$/);
  assert.equal(f.opened[0].name, '_blank');
  assert.equal(event.defaultPrevented, true);
});
for (const key of ['ctrlKey', 'metaKey', 'shiftKey', 'altKey']) {
  test(`${key} click is left to native browser handling`, () => {
    const f = fixture(); const event = f.click({ [key]: true });
    assert.equal(f.opened.length, 0);
    assert.equal(event.defaultPrevented, false);
  });
}
test('blocked popup still stops same-window navigation', () => {
  const f = fixture({ blocked: true }); const event = f.click();
  assert.equal(f.opened.length, 1);
  assert.equal(event.defaultPrevented, true);
});

for (const button of [1, 2]) {
  test(`button ${button} preserves native behavior`, () => {
    const f = fixture(); const event = f.click({ button });
    assert.equal(f.opened.length, 0);
    assert.equal(event.defaultPrevented, false);
  });
}
test('an already-handled click does not open another window', () => {
  const f = fixture(); f.click({ defaultPrevented: true });
  assert.equal(f.opened.length, 0);
});
