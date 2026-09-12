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

const commentStart = jsp.indexOf('function setComment(');
const commentEnd = jsp.indexOf('function setDefaultAddr(', commentStart);
assert(commentStart >= 0 && commentEnd > commentStart);
const literalComment = '  <img src=x onerror=alert(1)> & "clinical"\nnext line  ';
const commentSource = jsp.slice(commentStart, commentEnd)
  .replace("'<%=SafeEncode.forJavaScript(comment)%>'", JSON.stringify(literalComment));

for (const scenario of ['empty', 'missing-frame', 'missing-document', 'missing-fields', 'inaccessible', 'ready']) {
  test(`initial prescription comment safely handles ${scenario} preview and stays literal`, () => {
    const notes = { style: {}, set innerHTML(value) { throw new Error('Clinical text must not be parsed as HTML'); } };
    const hidden = {};
    const frames = {};
    if (!['empty', 'missing-frame'].includes(scenario)) {
      frames.preview = {};
      if (scenario === 'inaccessible') Object.defineProperty(frames.preview, 'document', {
        get() { throw new Error('Preview document unavailable'); },
      });
      else if (scenario !== 'missing-document') frames.preview.document = {
        getElementById: () => scenario === 'ready' ? notes : null,
        getElementsByName: () => scenario === 'ready' ? [hidden] : [],
      };
    }
    const context = vm.createContext({ frames, hasPreview: scenario !== 'empty' });
    vm.runInContext(commentSource, context);
    assert.doesNotThrow(() => context.setComment());
    if (scenario === 'ready') {
      assert.equal(notes.textContent, literalComment);
      assert.equal(notes.style.whiteSpace, 'pre-wrap');
      assert.equal(hidden.value, literalComment.replace(/\n/g, '\r\n'));
    } else {
      assert.equal(notes.textContent, undefined);
      assert.equal(hidden.value, undefined);
    }
  });
}

test('empty prescription address initialization returns before default-address and iframe access', () => {
  const addressStart = jsp.indexOf('function addressSelect(');
  const addressEnd = jsp.indexOf('function popupPrint(', addressStart);
  const source = jsp.slice(addressStart, addressEnd);
  assert.match(source, /function addressSelect\(\)\s*\{\s*if \(typeof hasPreview === 'undefined' \|\| !hasPreview \|\| !frames\['preview'\]\) return;/);
});
