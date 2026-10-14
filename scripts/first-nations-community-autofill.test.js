/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Runs the First Nations module's own page script (manageFirstNationsModule.jsp) against a
 * minimal DOM to pin the status-number auto-fill of the community: it fills a community that was
 * never set, but never overwrites one the user cleared, in this page or in a saved record that is
 * reloaded (a saved 10-digit status number with an empty community holds a deliberate clear).
 */
'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const JSP = path.join(__dirname, '..', 'src/main/webapp/WEB-INF/jsp/demographic/manageFirstNationsModule.jsp');

/** The page script that holds the auto-fill, without the JSP's HTML comment wrapper lines. */
function pageScript() {
  const source = fs.readFileSync(JSP, 'utf8');
  const blocks = [...source.matchAll(/<script type="text\/javascript">([\s\S]*?)<\/script>/g)].map(m => m[1]);
  const script = blocks.find(block => block.includes('communityClearedByUser'));
  assert.ok(script, 'the First Nations page script was not found in the JSP');
  return script.replace(/^\s*\/\/<!--\s*$/m, '').replace(/^\s*\/\/-->\s*$/m, '');
}

function element(props = {}) {
  const listeners = {};
  return Object.assign({
    value: '',
    addEventListener(type, fn) { (listeners[type] = listeners[type] || []).push(fn); },
    fire(type) { (listeners[type] || []).forEach(fn => fn()); },
  }, props);
}

/**
 * Renders the module as the JSP would for a record with {@code savedStatusNumber} and
 * {@code savedCommunity}, runs its DOMContentLoaded handler, and returns the controls.
 */
function render({ savedStatusNumber = '', savedCommunity = '' } = {}) {
  const options = [{ value: '', text: '--' }, { value: '123', text: 'Band 123 ' }];
  const community = element({ options, selectedIndex: 0 });
  Object.defineProperty(community, 'value', {
    get() { return options[this.selectedIndex].value; },
    set(v) { const i = options.findIndex(o => o.value === v); this.selectedIndex = i < 0 ? 0 : i; },
  });
  community.value = savedCommunity;
  const byId = {
    statusNum: element({ value: savedStatusNumber }),
    fNationCom: community,
    labelfNationCom: element(),
    fNationFamilyNumber: element(),
    fNationFamilyPosition: element(),
  };
  const byName = { statusNumOrig: element({ value: savedStatusNumber }), aboriginal: element() };
  let ready;
  const document = {
    getElementById: id => byId[id] || null,
    querySelector: selector => byName[(selector.match(/\[name='([^']+)'\]/) || [])[1]] || null,
    addEventListener: (type, fn) => { if (type === 'DOMContentLoaded') ready = fn; },
  };
  vm.runInNewContext(pageScript(), { document, toggleFirstNationFields() {} });
  ready();
  return byId;
}

function blurWith(controls, statusNumber) {
  controls.statusNum.value = statusNumber;
  controls.statusNum.fire('blur');
}

test('shouldFillCommunityFromBand_whenCommunityWasNeverSet', () => {
  const controls = render();
  blurWith(controls, '1230000101');
  assert.equal(controls.fNationCom.value, '123');
  assert.equal(controls.fNationFamilyNumber.value, '00001');
  assert.equal(controls.fNationFamilyPosition.value, '01');
});

test('shouldKeepCommunityEmpty_whenUserClearsItInThisPage', () => {
  const controls = render({ savedStatusNumber: '1230000101', savedCommunity: '123' });
  controls.fNationCom.value = '';
  controls.fNationCom.fire('change');
  blurWith(controls, '1230000101');
  assert.equal(controls.fNationCom.value, '');
});

test('shouldKeepSavedClear_whenRecordIsReloaded', () => {
  // A saved 10-digit status number with an empty community: the clear survived the save.
  for (const statusNumber of ['1230000101', '1230000202']) {
    const controls = render({ savedStatusNumber: '1230000101', savedCommunity: '' });
    blurWith(controls, statusNumber);
    assert.equal(controls.fNationCom.value, '', `blur with ${statusNumber} restored the band`);
  }
});

test('shouldFillAgain_whenUserPicksACommunityAfterReload', () => {
  const controls = render({ savedStatusNumber: '1230000101', savedCommunity: '' });
  controls.fNationCom.value = '123';
  controls.fNationCom.fire('change');
  controls.fNationCom.value = '';
  blurWith(controls, '1230000101');
  // Choosing a community and then emptying it without a change event is not a clear.
  assert.equal(controls.fNationCom.value, '123');
});
