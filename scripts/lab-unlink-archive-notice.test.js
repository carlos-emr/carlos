/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
/*
 * The notice a confirmed lab unlink leaves for the reloaded lab page (#6214): unlink archives the
 * readings the report filed to the chart, the page reloads, and the reloaded page tells the
 * clinician once, in a Bootstrap alert that closes itself after the window has had focus for a
 * while. Runs the page's own unlink script from labDisplay.jsp against a minimal DOM.
 */
const {test} = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');

const jsp = fs.readFileSync('src/main/webapp/WEB-INF/jsp/lab/CA/ALL/labDisplay.jsp', 'utf8');
const start = jsp.indexOf('        function unlinkDemographic(labNo)');
const end = jsp.indexOf('        function addComment(');
assert.ok(start >= 0 && end > start, 'unlinkDemographic markers missing or out of order in labDisplay.jsp');
const TEMPLATE = 'Synthetic unlink notice. Readings archived: {0}';
const CLOSE_LABEL = 'Synthetic close';
const source = jsp.slice(start, end)
  .replaceAll('${carlos:forJavaScript(msgUnlinkArchived)}', TEMPLATE)
  .replaceAll('${carlos:forJavaScript(msgUnlinkNoticeClose)}', CLOSE_LABEL);
const KEY = 'carlos.lab.unlinkArchivedNotice';

function element(tag) {
  const listeners = {};
  return {
    tagName: tag.toUpperCase(), className: '', type: '', textContent: '', style: {}, attributes: {}, children: [],
    parent: null, isConnected: false,
    setAttribute(name, value) { this.attributes[name] = String(value); },
    appendChild(child) { this.children.push(child); child.parent = this; return child; },
    addEventListener(type, handler) { (listeners[type] = listeners[type] || []).push(handler); },
    click() { (listeners.click || []).forEach(handler => handler()); },
    remove() {
      if (this.parent) this.parent.children = this.parent.children.filter(child => child !== this);
      this.parent = null;
      this.isConnected = false;
    }
  };
}

function fixture({kept, storage = 'working', focused = true, bootstrap = false, answer} = {}) {
  const store = new Map();
  if (kept !== undefined) store.set(KEY, typeof kept === 'string' ? kept : JSON.stringify(kept));
  const timers = new Map();
  let nextTimer = 1;
  const windowListeners = {focus: [], blur: []};
  const body = element('body');
  body.isConnected = true;
  body.insertBefore = function (child) {
    this.children.unshift(child);
    child.parent = this;
    child.isConnected = true;
    return child;
  };
  const closedByBootstrap = [];
  const alerts = [];
  let reloads = 0;
  const window = {
    sessionStorage: {
      getItem(key) {
        if (storage === 'unavailable') throw new Error('synthetic storage failure');
        return store.has(key) ? store.get(key) : null;
      },
      setItem(key, value) {
        if (storage !== 'working') throw new Error('synthetic storage failure');
        store.set(key, value);
      },
      removeItem(key) {
        if (storage === 'unavailable') throw new Error('synthetic storage failure');
        store.delete(key);
      }
    },
    location: {reload() { reloads++; }},
    setTimeout(handler, delay) { const id = nextTimer++; timers.set(id, {handler, delay}); return id; },
    clearTimeout(id) { timers.delete(id); },
    addEventListener(type, handler) { windowListeners[type].push(handler); },
    removeEventListener(type, handler) { windowListeners[type] = windowListeners[type].filter(h => h !== handler); }
  };
  if (bootstrap) {
    window.bootstrap = {Alert: {getOrCreateInstance(box) {
      return {close() { closedByBootstrap.push(box); box.remove(); }};
    }}};
  }
  const context = {
    window,
    document: {body, createElement: element, hasFocus: () => focused},
    top: {opener: null},
    prompt() { return 'synthetic reason'; },
    alert(message) { alerts.push(message); },
    console: {error() {}},
    getCsrfToken() { return 'synthetic token'; },
    fetch() { return Promise.resolve({ok: true, json: async () => answer}); }
  };
  vm.runInNewContext(source, context);
  return {
    context, store, timers, body, alerts, closedByBootstrap, windowListeners,
    reloads() { return reloads; },
    notices() { return body.children.filter(child => 'data-carlos-lab-unlink-notice' in child.attributes); },
    async unlink(labNo) { context.unlinkDemographic(labNo); await new Promise(setImmediate); },
    fire(type) { windowListeners[type].slice().forEach(handler => handler()); },
    runTimers() { const due = [...timers.values()]; timers.clear(); due.forEach(timer => timer.handler()); }
  };
}

test('a confirmed unlink that archived readings keeps their count for the reloaded page', async () => {
  const f = fixture({answer: {success: true, archivedMeasurementCount: 3}});
  await f.unlink(17);
  const kept = JSON.parse(f.store.get(KEY));
  assert.equal(kept.labNo, '17');
  assert.equal(kept.archived, 3);
  assert.ok(Math.abs(Date.now() - kept.at) < 60000);
  assert.equal(f.reloads(), 1);
  assert.deepEqual(f.alerts, []);
});

test('an unlink that archived nothing leaves no notice', async () => {
  for (const answer of [{success: true, archivedMeasurementCount: 0}, {success: true}]) {
    const f = fixture({answer});
    await f.unlink(17);
    assert.equal(f.store.has(KEY), false);
    assert.equal(f.reloads(), 1);
  }
});

test('storage that refuses the notice does not undo a confirmed unlink', async () => {
  const f = fixture({storage: 'full', answer: {success: true, archivedMeasurementCount: 3}});
  await f.unlink(17);
  assert.equal(f.reloads(), 1);
  assert.deepEqual(f.alerts, []);
  assert.equal(f.context.unlinkDemographic.reasons['17'], undefined);
});

test('the reloaded report shows the notice once as a Bootstrap status alert', () => {
  const f = fixture({kept: {labNo: '17', archived: 3, at: Date.now()}});
  f.context.showUnlinkNotice('17');
  const notices = f.notices();
  assert.equal(notices.length, 1);
  const box = notices[0];
  for (const name of ['alert', 'alert-info', 'alert-dismissible', 'fade', 'show', 'DoNotPrint']) {
    assert.ok(box.className.split(' ').includes(name), `missing ${name}`);
  }
  assert.equal(box.attributes.role, 'status');
  assert.equal(box.attributes['aria-live'], 'polite');
  assert.equal(box.children[0].textContent, 'Synthetic unlink notice. Readings archived: 3');
  assert.equal(box.children[1].className, 'btn-close');
  assert.equal(box.children[1].type, 'button');
  assert.equal(box.children[1].attributes['aria-label'], CLOSE_LABEL);
  assert.equal(f.store.has(KEY), false);
  f.context.showUnlinkNotice('17');
  assert.equal(f.notices().length, 1);
});

test('the notice closes itself once the focused window has shown it long enough', () => {
  for (const bootstrap of [false, true]) {
    const f = fixture({kept: {labNo: '17', archived: 2, at: Date.now()}, bootstrap});
    f.context.showUnlinkNotice('17');
    assert.deepEqual([...f.timers.values()].map(timer => timer.delay), [10000]);
    f.runTimers();
    assert.equal(f.notices().length, 0);
    assert.equal(f.closedByBootstrap.length, bootstrap ? 1 : 0);
    assert.equal(f.windowListeners.focus.length + f.windowListeners.blur.length, 0);
  }
});

test('the notice waits while another window has the focus', () => {
  const f = fixture({kept: {labNo: '17', archived: 2, at: Date.now()}, focused: false});
  f.context.showUnlinkNotice('17');
  assert.equal(f.timers.size, 0);
  f.fire('focus');
  assert.equal(f.timers.size, 1);
  f.fire('blur');
  assert.equal(f.timers.size, 0);
  assert.equal(f.notices().length, 1);
  f.fire('focus');
  f.runTimers();
  assert.equal(f.notices().length, 0);
});

test('the close button dismisses the notice once and stops its timer', () => {
  const f = fixture({kept: {labNo: '17', archived: 2, at: Date.now()}, bootstrap: true});
  f.context.showUnlinkNotice('17');
  const box = f.notices()[0];
  box.children[1].click();
  box.children[1].click();
  assert.equal(f.notices().length, 0);
  assert.equal(f.closedByBootstrap.length, 1);
  assert.equal(f.timers.size, 0);
  f.fire('focus');
  assert.equal(f.timers.size, 0);
});

test('a notice for another report, an old notice or a malformed one is dropped unseen', () => {
  const kept = [
    {labNo: '23', archived: 2, at: Date.now()},
    {labNo: '17', archived: 2, at: Date.now() - 10 * 60 * 1000},
    {labNo: '17', archived: 2, at: Date.now() + 10 * 60 * 1000},
    {labNo: '17', archived: 0, at: Date.now()},
    {labNo: '17', archived: 2.5, at: Date.now()},
    {labNo: '17', archived: '2', at: Date.now()},
    {labNo: '17', archived: 2},
    'not json',
    'null'
  ];
  for (const value of kept) {
    const f = fixture({kept: value});
    f.context.showUnlinkNotice('17');
    assert.equal(f.notices().length, 0, JSON.stringify(value));
    assert.equal(f.store.has(KEY), false, JSON.stringify(value));
    assert.equal(f.timers.size, 0);
  }
});

test('unavailable storage shows nothing and raises nothing on load', () => {
  const f = fixture({storage: 'unavailable'});
  assert.doesNotThrow(() => f.context.showUnlinkNotice('17'));
  assert.equal(f.notices().length, 0);
});

test('the lab page shows a kept notice after its other load handlers', () => {
  const body = jsp.match(/<body onLoad="([^"]+)"/);
  assert.ok(body, 'labDisplay.jsp body onLoad missing');
  assert.match(body[1], /loadHL7hx\(\); showUnlinkNotice\(labNo\);$/);
  assert.match(jsp, /<fmt:message key="oscarMDS\.segmentDisplay\.msgUnlinkArchived" var="msgUnlinkArchived"\/>/);
  assert.match(jsp, /<fmt:message key="global\.btnClose" var="msgUnlinkNoticeClose"\/>/);
});
