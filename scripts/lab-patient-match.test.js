/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const test = require('node:test');
const vm = require('node:vm');
const source = fs.readFileSync('src/main/webapp/share/javascript/oscarMDSIndex.js', 'utf8');

function load() {
  let channel;
  const panels = { DemoTable42: { style: { backgroundColor: 'yellow' } },
    DemoTable43: { style: { backgroundColor: 'yellow' } } };
  const context = {
    contextpath: '/carlos',
    document: { getElementById: id => panels[id] },
    BroadcastChannel: class {
      constructor(name) { this.name = name; channel = this; }
    },
  };
  vm.runInNewContext(source, context);
  // This fixture isolates notification dispatch; the real refresh is exercised against
  // standalone and inline server-rendered panels by the lab patient-link browser workflow.
  context.refreshMatchedLabPanel = (id, panel) => { panel.style.backgroundColor = '#FFF'; };
  assert.equal(channel.name, 'lab-patient-match-/carlos');
  return { panels, channel };
}

test('shared lab script updates the matching panel without an opener or standalone callback', () => {
  const { panels, channel } = load();
  channel.onmessage({ data: { type: 'patient-matched', labType: 'HL7', labNo: '42' } });
  assert.equal(panels.DemoTable42.style.backgroundColor, '#FFF');
  assert.equal(panels.DemoTable43.style.backgroundColor, 'yellow');
});

test('shared lab listener ignores other report types, unknown panels, and malformed messages', () => {
  const { panels, channel } = load();
  for (const data of [null, {}, { type: 'other', labType: 'HL7', labNo: '42' },
    { type: 'patient-matched', labType: 'HRM', labNo: '42' },
    { type: 'patient-matched', labType: 'HL7', labNo: '42x' },
    { type: 'patient-matched', labType: 'HL7', labNo: 42 },
    { type: 'patient-matched', labType: 'HL7', labNo: '99' }]) channel.onmessage({ data });
  assert.equal(panels.DemoTable42.style.backgroundColor, 'yellow');
  assert.equal(panels.DemoTable43.style.backgroundColor, 'yellow');
});

test('shared lab script loads when BroadcastChannel is unavailable', () => {
  assert.doesNotThrow(() => vm.runInNewContext(source, { contextpath: '/carlos' }));
});

test('shared lab script remains usable when channel construction is blocked', () => {
  const warnings = [];
  assert.doesNotThrow(() => vm.runInNewContext(source, {
    contextpath: '/carlos',
    BroadcastChannel: class { constructor() { throw new Error('unavailable'); } },
    console: { warn: message => warnings.push(message) },
  }));
  assert.equal(warnings.length, 1);
});

const searchPage = fs.readFileSync('src/main/webapp/WEB-INF/jsp/oscarMDS/PatientSearch.jsp', 'utf8');
const selectPatient = searchPage.slice(searchPage.indexOf('function selectPatient(demoNo)'),
  searchPage.indexOf('// Legacy addName compatibility'));
for (const failure of ['none', 'constructor', 'post']) {
  test(`saved match consumes confirmation and closes despite ${failure} notification failure`, async () => {
    let consumed = false;
    let closed = false;
    const warnings = [];
    const errors = [];
    const messages = [];
    const context = {
      document: { querySelector: () => ({ value: 'fixture-token' }), getElementById: () => null },
      window: { opener: null, close() { assert.equal(consumed, true); closed = true; } },
      fetch: async () => ({ ok: true, async json() { await Promise.resolve(); consumed = true; return { success: true }; } }),
      console: { warn: message => warnings.push(message), error: message => errors.push(message) },
      BroadcastChannel: class {
        constructor(name) {
          this.name = name;
          if (name.startsWith('lab-patient-match-') && failure === 'constructor') throw new Error('unavailable');
        }
        postMessage(message) {
          if (this.name.startsWith('lab-patient-match-') && failure === 'post') throw new Error('unavailable');
          messages.push([this.name, message]);
        }
        close() { }
      },
    };
    vm.createContext(context);
    vm.runInContext(selectPatient, context);
    await context.selectPatient('100');
    assert.equal(closed, true);
    assert.deepEqual(errors, []);
    assert.equal(warnings.length, failure === 'none' ? 0 : 1);
    assert.ok(messages.some(([name, message]) => name === 'inboxhub-refresh' && message === 'refresh'));
  });
}

test('legacy MDS report reloads only for its own successful MDS match', () => {
  const jsp = fs.readFileSync('src/main/webapp/WEB-INF/jsp/oscarMDS/SegmentDisplay.jsp', 'utf8');
  const script = jsp.slice(jsp.indexOf('// Refresh only this MDS report'), jsp.indexOf('function getComment()'))
    .replace("${carlos:forJavaScript(pageContext.request.contextPath)}", '/carlos')
    .replace("${carlos:forJavaScript(param.segmentID)}", '42');
  let channel;
  let reloads = 0;
  let closed = false;
  vm.runInNewContext(script, {
    BroadcastChannel: class { constructor(name) { this.name = name; channel = this; } close() { closed = true; } },
    window: { location: { reload() { reloads++; } } },
  });
  assert.equal(channel.name, 'lab-patient-match-/carlos');
  for (const data of [null, {}, { type: 'patient-matched', labType: 'HL7', labNo: '42' },
    { type: 'patient-matched', labType: 'MDS', labNo: '43' }]) channel.onmessage({ data });
  assert.equal(reloads, 0);
  channel.onmessage({ data: { type: 'patient-matched', labType: 'MDS', labNo: '42' } });
  assert.equal(reloads, 1);
  assert.equal(closed, true);
});
