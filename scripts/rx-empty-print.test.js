/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/rx/ViewScript2.jsp'), 'utf8');
const start = jsp.indexOf('function printIframe(');
const end = jsp.indexOf('function printPaste2Parent(', start);
assert(start >= 0 && end > start);
const printSource = jsp.slice(start, end);

for (const scenario of ['uninitialized', 'empty', 'missing-frame', 'ready', 'legacy-browser']) {
  test(`ordinary prescription print handles ${scenario} preview without a missing-frame exception`, () => {
    const calls = [];
    const unload = () => {};
    const context = vm.createContext({
      frames: {}, navigator: { appName: scenario === 'legacy-browser' ? 'Microsoft Internet Explorer' : 'Netscape' },
      window: { onbeforeunload: unload }, alert() { calls.push('alert'); },
      self: { focus() { calls.push('self-focus'); } },
    });
    if (scenario !== 'uninitialized') context.hasPreview = scenario !== 'empty';
    if (['ready', 'legacy-browser'].includes(scenario)) {
      context.frames.preview = { focus() { calls.push('preview-focus'); }, print() { calls.push('print'); } };
    }
    vm.runInContext(printSource, context);
    assert.doesNotThrow(() => context.printIframe());
    if (scenario === 'ready') {
      assert.deepEqual(calls, ['preview-focus', 'print', 'self-focus']);
      assert.equal(context.window.onbeforeunload, null);
      assert.equal(typeof context.self.onfocus, 'function');
    } else {
      assert.deepEqual(calls, scenario === 'legacy-browser' ? ['alert'] : []);
      assert.equal(context.window.onbeforeunload, unload);
    }
  });
}

test('ordinary Print is server-rendered disabled when no prescription preview exists', () => {
  assert.match(jsp, /id="printButton"\s+<%= !previewAvailable \? "disabled='true'" : "" %>/);
});
