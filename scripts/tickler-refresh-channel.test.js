/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

// The task list's shipped refresh listener: a popup save is broadcast and the list reloads once
// (#4993). Chromium refuses unload listeners, so the list closes its channel on pagehide, and a page
// restored from the back/forward cache reopens it and catches up on a save it missed (#6346).
const jsp = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/tickler/ticklerMain.jsp'), 'utf8');
const start = jsp.indexOf('var ticklerChannel = null;');
const end = jsp.indexOf('</script>', start);
assert.ok(start >= 0 && end > start, 'The task-list refresh channel must be exercised');
// Render the channel name as the provider-wide list does.
const block = jsp.slice(start, end).replace(/<carlos:encode\b[^\n]*?\/>/, 'all');
assert.ok(!block.includes('<carlos:'), 'The channel name tag must be rendered');

function taskList() {
  const channels = [];
  const listeners = {};
  const list = { reloads: 0, pageReloads: 0 };
  class Channel {
    constructor(name) { this.name = name; this.closed = false; channels.push(this); }
    close() { this.closed = true; }
  }
  vm.runInNewContext(block, {
    BroadcastChannel: Channel,
    ticklerResultsTable: { ajax: { reload: () => { list.reloads++; } } },
    location: { reload: () => { list.pageReloads++; } },
    window: { addEventListener: (type, listener) => { (listeners[type] ||= []).push(listener); } },
    console: { error() {}, warn() {} },
  });
  const open = () => channels.filter(channel => !channel.closed);
  return {
    list, listeners, open,
    dispatch: (type, event) => (listeners[type] || []).forEach(listener => listener(event)),
    // A closed channel receives nothing, exactly as a real BroadcastChannel.
    save: () => open().forEach(channel => channel.onmessage({ data: { action: 'refresh' } })),
  };
}

test('the task list registers no unload listener and reloads once per save on its one channel', () => {
  const page = taskList();
  assert.deepEqual(Object.keys(page.listeners).sort(), ['pagehide', 'pageshow']);
  assert.deepEqual(page.open().map(channel => channel.name), ['carlos_tickler_refresh_all']);
  page.save();
  assert.deepEqual(page.list, { reloads: 1, pageReloads: 0 });
});

test('pagehide closes the channel, so a hidden list takes no save', () => {
  const page = taskList();
  page.dispatch('pagehide', { persisted: true });
  assert.equal(page.open().length, 0);
  page.save();
  assert.deepEqual(page.list, { reloads: 0, pageReloads: 0 });
});

test('a list restored from the back/forward cache reopens one channel and reloads once', () => {
  const page = taskList();
  page.dispatch('pagehide', { persisted: true });
  page.dispatch('pageshow', { persisted: true });
  assert.deepEqual(page.open().map(channel => channel.name), ['carlos_tickler_refresh_all']);
  assert.deepEqual(page.list, { reloads: 1, pageReloads: 0 });
  page.save();
  assert.deepEqual(page.list, { reloads: 2, pageReloads: 0 });
});

test('a pageshow that restores nothing opens no second channel and does not reload', () => {
  const page = taskList();
  page.dispatch('pageshow', { persisted: false });
  page.dispatch('pageshow', { persisted: true });
  assert.equal(page.open().length, 1);
  page.save();
  assert.deepEqual(page.list, { reloads: 1, pageReloads: 0 });
});
