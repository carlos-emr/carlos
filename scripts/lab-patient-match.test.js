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
