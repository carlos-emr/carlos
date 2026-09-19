/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const vm = require('node:vm');
const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/scratch/index.jsp'), 'utf8');
const start = jsp.indexOf('let dirty = false;');
assert.ok(start >= 0);
const script = jsp.slice(start, jsp.indexOf('</script>', start));

function fixture(text = 'original') {
  const nodes = Object.fromEntries(['dirty', 'savebutton', 'scratch', 'curr_id', 'thetext',
    'lastSavedTimestamp', 'saveError', 'saveErrorText', 'retryScratch', 'openCurrentScratch']
    .map(id => [id, { value: '', textContent: '', disabled: false, hidden: true }]));
  nodes.thetext.value = text;
  nodes.curr_id.value = '7';
  nodes.scratchVersions = { options: [], add(option) { this.options.push(option); } };
  const requests = [];
  const listeners = {};
  let reloads = 0;
  const jquery = () => ({ serialize: () => nodes.thetext.value });
  jquery.ajax = options => requests.push(options);
  const context = vm.createContext({
    document: { getElementById: id => { assert.ok(nodes[id], `Unexpected DOM access: ${id}`); return nodes[id]; } },
    window: { addEventListener: (event, callback) => { listeners[event] = callback; },
      setInterval() {}, location: { reload() { reloads++; } } },
    Option: function(text, value) { this.text = text; this.value = String(value); },
    $: jquery,
  });
  vm.runInContext(script, context);
  vm.runInContext("lastSavedText = document.getElementById('thetext').value; setDirty();", context);
  return { nodes, requests, context, listeners, reloads: () => reloads,
    edit(value) { nodes.thetext.value = value; context.setDirty(); },
    save() { context.checkScratch(); return requests.at(-1); },
  };
}

for (const [oldText, newText] of [
  ['alpha beta', 'alpha+beta'], ['dose A', 'dose%20A'], ['&', '&amp;'],
  ['note', ' note\n'], ['note', ''], ['100%', '100% + 1'],
]) {
  test(`autosave preserves literal edit ${JSON.stringify(newText)}`, () => {
    const f = fixture(oldText);
    f.edit(newText); f.context.autoSave();
    assert.equal(f.requests.length, 1);
    assert.equal(f.requests[0].data, newText);
    f.requests[0].success({ success: true, id: '8', text: newText });
    assert.equal(f.nodes.thetext.value, newText);
    assert.equal(f.nodes.dirty.value, 'false');
    assert.match(f.nodes.lastSavedTimestamp.textContent, /^Last saved:/);
    f.context.autoSave(); assert.equal(f.requests.length, 1);
  });
}

test('acknowledging an earlier save never erases edits typed while it was pending', () => {
  const f = fixture(); f.edit('submitted'); const request = f.save();
  f.edit('newer edit'); request.success({ success: true, id: '8', text: 'submitted' });
  assert.equal(f.nodes.thetext.value, 'newer edit');
  assert.equal(f.nodes.curr_id.value, '8');
  assert.equal(f.nodes.dirty.value, 'true');
  assert.equal(f.nodes.lastSavedTimestamp.textContent, 'Unsaved changes');
  f.context.autoSave(); assert.equal(f.requests.length, 2);
  assert.equal(f.requests[1].data, 'newer edit');
});

for (const status of ['error', 'timeout', 'parsererror']) {
  test(`${status} preserves unsaved text and revision and supports retry`, () => {
    const f = fixture(); f.edit('unsaved'); f.save().error({ status: 500 }, status);
    assert.equal(f.nodes.thetext.value, 'unsaved');
    assert.equal(f.nodes.curr_id.value, '7');
    assert.equal(f.nodes.saveError.hidden, false);
    assert.equal(f.nodes.retryScratch.hidden, false);
    assert.equal(f.nodes.lastSavedTimestamp.textContent, 'Not saved');
    f.save().success({ success: true, id: '8', text: 'unsaved' });
    assert.equal(f.nodes.dirty.value, 'false');
    assert.equal(f.nodes.saveError.hidden, true);
  });
}

test('conflict preserves unsaved text and blocks retries and autosave until reconciled in another editor', () => {
  const f = fixture(); f.edit('local note'); f.save().error({ status: 409 }, 'error');
  assert.equal(f.nodes.thetext.value, 'local note');
  assert.equal(f.nodes.curr_id.value, '7');
  assert.equal(f.nodes.savebutton.disabled, true);
  assert.equal(f.nodes.openCurrentScratch.hidden, false);
  assert.equal(f.nodes.retryScratch.hidden, true);
  f.edit('more local notes'); f.context.autoSave(); f.context.checkScratch();
  assert.equal(f.requests.length, 1);
  assert.equal(f.nodes.curr_id.value, '7');
});

for (const response of [null, {}, { success: false },
  { success: true, id: '8', text: 'different text' },
  { success: true, id: '6', text: 'submitted' },
  { success: true, id: 'NaN', text: 'submitted' }]) {
  test(`invalid save acknowledgment cannot mark notes saved: ${JSON.stringify(response)}`, () => {
    const f = fixture(); f.edit('submitted'); f.save().success(response);
    assert.equal(f.nodes.curr_id.value, '7');
    assert.equal(f.nodes.dirty.value, 'true');
    assert.equal(f.nodes.thetext.value, 'submitted');
    assert.equal(f.nodes.saveError.hidden, false);
    assert.equal(f.nodes.lastSavedTimestamp.textContent, 'Not saved');
  });
}

test('duplicate clicks do not start parallel saves', () => {
  const f = fixture(); f.edit('new note'); f.save(); f.save(); f.context.autoSave();
  assert.equal(f.requests.length, 1);
});

test('history-window updates and navigation cannot silently discard an unsaved note', () => {
  const f = fixture(); f.context.scratchpadVersionChanged(); assert.equal(f.reloads(), 1);
  f.edit('unsaved'); f.context.scratchpadVersionChanged(); assert.equal(f.reloads(), 1);
  let prevented = false;
  const event = { preventDefault() { prevented = true; } };
  f.listeners.beforeunload(event);
  assert.equal(prevented, true); assert.equal(event.returnValue, '');
});

test('a failed in-flight save remains dirty even if typing was undone while awaiting it', () => {
  const f = fixture('original');
  f.edit('submitted'); const request = f.save(); f.edit('original');
  request.error({status: 0}, 'timeout');
  assert.equal(f.nodes.thetext.value, 'original');
  assert.equal(f.nodes.dirty.value, 'true');
  f.context.scratchpadVersionChanged(); assert.equal(f.reloads(), 0);
  f.context.autoSave(); assert.equal(f.requests.length, 2);
  assert.equal(f.requests[1].data, 'original');
});
