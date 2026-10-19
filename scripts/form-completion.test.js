/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const {completeEncounterForm} = require('../src/main/webapp/form/form-completion');

function fixture({globalId = '123', query = '?demographicNo=123', hidden = ['123'], legacy = false} = {}) {
  const actions = [];
  const editor = {value: 'Existing draft', scrollHeight: 90, scrollTop: 0,
    dispatchEvent(event) { actions.push(['event', event.type, event.bubbles, this.value]); }};
  const otherEditor = {value: 'Other editor'};
  const opener = {
    demographicNo: globalId, location: new URL(`https://fixture.invalid/carlos/casemgmt/CaseManagementView${query}`),
    ctx: '/carlos', Colour: {forms: '#123abc', measurements: '#def456'}, Event,
    document: {
      querySelectorAll() { return hidden.map(value => ({value})); },
      querySelector(selector) {
        if (selector === 'textarea[name="caseNote_note"]') return legacy ? null : editor;
        if (selector === 'form[name="encForm"] textarea[name="enTextarea"]') return legacy ? editor : otherEditor;
        throw new Error(`Unexpected selector ${selector}`);
      },
      getElementById(id) { return ['forms', 'measurements'].includes(id) ? {} : null; },
    },
    loadDiv(panel, url, limit) { actions.push(['refresh', panel, url, limit]); },
  };
  const popup = {opener, close() { actions.push(['close']); }};
  return {popup, opener, editor, otherEditor, actions};
}

test('completion appends literal text to the modern editor and dispatches dirty events before refresh and close', () => {
  const f = fixture();
  const payload = '\n</textarea><script>throw new Error("no execution")</script>\n"\' & <b>literal</b>\u2028\u2029';
  // nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag -- isolated Node fixture; the script payload must remain literal textarea.value text, with no DOM execution.
  completeEncounterForm(f.popup, '123', payload);
  // nosemgrep: javascript.lang.security.audit.unknown-value-with-script-tag.unknown-value-with-script-tag -- assertion on a plain mock object, not an HTML/script output sink.
  assert.equal(f.editor.value, `Existing draft\n${payload}`);
  assert.equal(f.otherEditor.value, 'Other editor');
  assert.deepEqual(f.actions.slice(0, 2), ['input', 'change'].map(type => ['event', type, true, f.editor.value]));
  assert.equal(f.editor.scrollTop, 90);
  assert.deepEqual(f.actions.slice(2, 4).map(action => action[1]), ['forms', 'measurements']);
  for (const [, panel, address, limit] of f.actions.slice(2, 4)) {
    const url = new URL(address);
    assert.equal(url.origin, 'https://fixture.invalid');
    assert.equal(url.searchParams.get('demographicNo'), '123');
    assert.equal(url.searchParams.get('cmd'), panel);
    assert.equal(url.searchParams.get('hC'), f.opener.Colour[panel]);
    // Same page size as the chart's popup-close reloads, so the panel is not left expanded.
    assert.equal(url.searchParams.get('numToDisplay'), '6');
    assert.equal(limit, 0);
  }
  assert.deepEqual(f.actions.at(-1), ['close']);
});

test('the legacy editor receives literal text when the URL identifies the patient without modern globals', () => {
  const f = fixture({globalId: undefined, query: '?demographic_no=123', hidden: [], legacy: true});
  delete f.opener.demographicNo;
  delete f.opener.loadDiv;
  completeEncounterForm(f.popup, '123', '<literal>');
  assert.equal(f.editor.value, 'Existing draft\n<literal>');
  assert.deepEqual(f.actions.map(action => action[0]), ['event', 'event', 'close']);
});

for (const [name, options] of [
  ['foreign patient', {globalId: '456', query: '?demographicNo=456', hidden: ['456']}],
  ['stale global after navigation', {query: '?demographicNo=456'}],
  ['stale hidden field', {hidden: ['456']}],
  ['conflicting hidden fields', {hidden: ['123', '456']}],
  ['conflicting URL aliases', {query: '?demographicNo=123&demographic_no=456'}],
  ['conflicting repeated URL IDs', {query: '?demographicNo=123&demographicNo=456'}],
  ['malformed URL identity', {query: '?demographicNo=bad'}],
  ['empty URL identity', {query: '?demographicNo='}],
  ['invalid global identity', {globalId: null}],
]) {
  test(`completion leaves the draft and panels untouched for ${name}`, () => {
    const f = fixture(options);
    completeEncounterForm(f.popup, '123', 'Do not append');
    assert.equal(f.editor.value, 'Existing draft');
    assert.deepEqual(f.actions, [['close']]);
  });
}

test('an opener without any explicit identity cannot receive completion text', () => {
  const f = fixture({query: '', hidden: []});
  delete f.opener.demographicNo;
  completeEncounterForm(f.popup, '123', 'Do not append');
  assert.equal(f.editor.value, 'Existing draft');
  assert.deepEqual(f.actions, [['close']]);
});

test('matching canonical IDs are accepted across aliases, numeric globals and leading zeroes', () => {
  const f = fixture({globalId: 123, query: '?demographicNo=00123&demographic_no=123&demographicNo=123', hidden: ['123']});
  f.opener.demographic_no = '00123';
  completeEncounterForm(f.popup, '00123', 'Saved');
  assert.equal(f.editor.value, 'Existing draft\nSaved');
});

test('invalid completion patient IDs never mutate a chart', () => {
  for (const id of [null, undefined, '', '0', '-1', '1.5', '123x', ' 123', '2147483648', {}, true]) {
    const f = fixture();
    completeEncounterForm(f.popup, id, 'Do not append');
    assert.equal(f.editor.value, 'Existing draft');
    assert.deepEqual(f.actions, [['close']]);
  }
});

test('missing, closed and cross-origin openers close safely without reading or writing a draft', () => {
  for (const state of ['missing', 'closed', 'inaccessible']) {
    const f = fixture();
    if (state === 'missing') f.popup.opener = null;
    if (state === 'closed') f.opener.closed = true;
    if (state === 'inaccessible') Object.defineProperty(f.opener, 'location', {get() { throw new Error('SecurityError'); }});
    assert.doesNotThrow(() => completeEncounterForm(f.popup, '123', 'Do not append'));
    assert.equal(f.editor.value, 'Existing draft');
    assert.deepEqual(f.actions, [['close']]);
  }
});

test('missing and readonly editors still allow supported own-patient panel refreshes and closing', () => {
  for (const state of ['missing', 'readOnly', 'disabled']) {
    const f = fixture();
    if (state === 'missing') f.opener.document.querySelector = () => null;
    else f.editor[state] = true;
    completeEncounterForm(f.popup, '123', 'Saved');
    assert.equal(f.editor.value, 'Existing draft');
    assert.deepEqual(f.actions.map(action => action[0]), ['refresh', 'refresh', 'close']);
  }
});

test('absent, failed and cross-origin refresh hooks do not prevent safe completion and closing', () => {
  for (const state of ['absent', 'throws', 'foreign']) {
    const f = fixture();
    if (state === 'absent') delete f.opener.loadDiv;
    if (state === 'throws') f.opener.loadDiv = () => { throw new Error('Panel unavailable'); };
    if (state === 'foreign') f.opener.ctx = 'https://other.invalid/carlos';
    assert.doesNotThrow(() => completeEncounterForm(f.popup, '123', 'Saved'));
    assert.equal(f.editor.value, 'Existing draft\nSaved');
    assert.deepEqual(f.actions.map(action => action[0]), ['event', 'event', 'close']);
  }
});

test('empty or absent completion text does not insert a spurious newline', () => {
  for (const text of ['', null, undefined]) {
    const f = fixture();
    completeEncounterForm(f.popup, '123', text);
    assert.equal(f.editor.value, 'Existing draft');
    assert.deepEqual(f.actions.map(action => action[0]), ['refresh', 'refresh', 'close']);
  }
});

test('the completion JSP encodes each request attribute once in JavaScript context', () => {
  const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/form/formSaveAndExit.jsp'), 'utf8');
  for (const name of ['demographicNo', 'diagnosisVT']) {
    assert.equal(jsp.split(`\u0024{carlos:forJavaScript(requestScope.${name})}`).length - 1, 1);
  }
  assert.match(jsp, /\/form\/form-completion\.js/);
  assert.doesNotMatch(jsp, /write2Parent|self\.open\.window|onunload|request\.getAttribute\("diagnosisVT"\)/);
});

test('Save and Exit refreshes only the owning patient Forms list and never reloads another chart', () => {
  for (const patient of ['123', '456']) {
    const f = fixture({globalId: undefined, query: `?demographic_no=${patient}`, hidden: []});
    f.opener.location = new URL(`https://fixture.invalid/clinic/encounter/ViewFormlist?demographic_no=${patient}`);
    let reloaded = false;
    f.opener.location.reload = () => { reloaded = true; };
    completeEncounterForm(f.popup, '123', '');
    assert.equal(reloaded, patient === '123');
    assert.equal(f.editor.value, 'Existing draft');
    assert.deepEqual(f.actions, [['close']]);
  }
});

test('ordinary Save refreshes the owning chart without closing the form or changing its draft', () => {
  const {refreshEncounterForms} = require('../src/main/webapp/form/form-completion');
  for (const patient of ['123', '456']) {
    const f = fixture();
    refreshEncounterForms(f.popup, patient);
    assert.equal(f.editor.value, 'Existing draft');
    assert.deepEqual(f.actions.map(action => action[0]), patient === '123' ? ['refresh', 'refresh'] : []);
  }
});

test('the legacy chart refresh uses encounterConfig and rejects conflicting configured patients', () => {
  const {refreshEncounterForms} = require('../src/main/webapp/form/form-completion');
  for (const patient of ['123', '456']) {
    const f = fixture({globalId: undefined, query: '', hidden: []});
    delete f.opener.ctx;
    f.opener.encounterConfig = {ctx: '/clinic', demographicNo: patient};
    refreshEncounterForms(f.popup, '123');
    assert.deepEqual(f.actions.map(action => action[0]), patient === '123' ? ['refresh', 'refresh'] : []);
    if (patient === '123') assert.match(f.actions[0][2], /\/clinic\/encounter\/displayForms/);
  }
});

test('the generic Save and Exit page supplies either legacy or BC-AR patient parameter to the shared completion helper', () => {
  const page = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/encounter/close.jsp'), 'utf8');
  assert.match(page, /completeEncounterForm\(window,/);
  assert.match(page, /not empty param\.demographic_no \? param\.demographic_no : param\.demographicNo/);
  assert.doesNotMatch(page, /window\.opener\.location\.reload\(/);
});
