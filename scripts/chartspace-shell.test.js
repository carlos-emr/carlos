/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');

const { FakeDocument, byClass, descendants, make } = require('./chartspace-fake-dom.js');

const JS_DIR = path.join(__dirname, '..', 'src', 'main', 'webapp', 'js', 'chartspace');
const SHELL_PATH = path.join(JS_DIR, 'chartspace.js');
const layout = require(path.join(JS_DIR, 'chartspace-layout.js'));
const ALLERGIES_PATH = path.join(JS_DIR, 'chartspace-allergies.js');

const I18N = {
  btnHidden: 'Hidden',
  msgHiddenHasData: 'contains data',
  msgLoading: 'Loading…',
  msgEmpty: 'No records',
  msgNoAccess: 'You do not have access to this section',
  msgError: 'This section could not be loaded',
  announceLoaded: '{0}: {1} records',
  announceEmpty: '{0}: no records, moved to hidden sections',
  announceNoAccess: '{0}: no access',
  announceError: '{0}: could not be loaded',
  titles: { allergies: 'Allergies' },
  severity: { severe: 'Severe', moderate: 'Moderate', mild: 'Mild', none: 'No reaction', unknown: 'Unknown severity' }
};

/** The static frame chartSpace.jsp renders, built on the fake DOM. */
function buildPage() {
  const doc = new FakeDocument();
  const b = doc.body;
  make(doc, b, 'button', { id: 'cs-hidden-toggle', 'aria-expanded': 'false', 'data-has-data': 'false', disabled: true });
  make(doc, b, 'section', { id: 'cs-top' });
  make(doc, b, 'section', { id: 'cs-right' });
  const panel = make(doc, b, 'section', { id: 'cs-hidden-panel', hidden: true });
  make(doc, panel, 'h2', { id: 'cs-hidden-title', tabindex: '-1' });
  make(doc, panel, 'button', { id: 'cs-hidden-close' });
  make(doc, panel, 'div', { id: 'cs-hidden-body' });
  make(doc, b, 'div', { id: 'cs-announcer', role: 'status', 'aria-live': 'polite' });
  return doc;
}

/**
 * Loads chartspace.js fresh on a fake window, runs init with a block that
 * resolves `json` (real allergies view model and renderer unless overridden)
 * and waits for the load to settle. The allergies module is also required
 * fresh so its renderer captures the fake window as its root.
 */
async function startShell(json, overrides) {
  const doc = buildPage();
  global.window = { document: doc };
  [SHELL_PATH, ALLERGIES_PATH].forEach((p) => delete require.cache[require.resolve(p)]);
  try {
    const real = require(ALLERGIES_PATH);
    const block = Object.assign({
      id: 'allergies',
      load: () => Promise.resolve(json),
      toViewModel: real.toViewModel,
      render: real.render
    }, overrides);
    global.window.ChartSpace = { layout, allergies: block };
    require(SHELL_PATH);
    global.window.ChartSpace.init({ contextPath: '/carlos', demographicNo: '2', i18n: I18N });
    for (let i = 0; i < 5; i++) {
      await new Promise((resolve) => setImmediate(resolve));
    }
  } finally {
    delete global.window;
    [SHELL_PATH, ALLERGIES_PATH].forEach((p) => delete require.cache[require.resolve(p)]);
  }
  const $ = (id) => doc.getElementById(id);
  return { doc, $, card: byClass(doc.body, 'cs-block')[0] };
}

const OK_JSON = {
  status: 'OK',
  items: [
    { description: 'Penicillin', severityCode: '3', reaction: 'Hives', startDate: '2020-03-15' },
    { description: 'Latex', severityCode: '1', reaction: '', startDate: '' }
  ]
};

test('a block that loads OK sits in the right region and its count is announced', async () => {
  const { $, card } = await startShell(OK_JSON);
  assert.equal(card.getAttribute('data-block'), 'allergies');
  assert.equal(card.getAttribute('data-state'), 'OK');
  assert.equal(card.parentNode, $('cs-right'));
  assert.equal(card.hasAttribute('aria-busy'), false);
  assert.equal($('cs-announcer').textContent, 'Allergies: 2 records');
  assert.equal($('cs-hidden-toggle').disabled, true);
});

test('an EMPTY block moves to the hidden panel, enables the toggle and is announced', async () => {
  const { $, card } = await startShell({ status: 'EMPTY', items: [] });
  assert.equal(card.getAttribute('data-state'), 'EMPTY');
  assert.equal(card.parentNode, $('cs-hidden-body'));
  const toggle = $('cs-hidden-toggle');
  assert.equal(toggle.disabled, false);
  assert.match(toggle.textContent, /\(1\)/);
  assert.equal(toggle.getAttribute('data-has-data'), 'false');
  assert.equal($('cs-announcer').textContent, 'Allergies: no records, moved to hidden sections');
});

test('a NO_ACCESS block stays visible and is announced', async () => {
  const { $, card } = await startShell({ status: 'NO_ACCESS', items: [] });
  assert.equal(card.getAttribute('data-state'), 'NO_ACCESS');
  assert.equal(card.parentNode, $('cs-right'));
  assert.equal($('cs-announcer').textContent, 'Allergies: no access');
});

test('a render that throws ends in ERROR with the error message and an announcement', async () => {
  const { $, card } = await startShell(OK_JSON, {
    render() { throw new Error('boom'); }
  });
  assert.equal(card.getAttribute('data-state'), 'ERROR');
  assert.equal(card.parentNode, $('cs-right'));
  assert.equal(byClass(card, 'cs-block-body')[0].textContent, I18N.msgError);
  assert.equal(card.hasAttribute('aria-busy'), false);
  assert.equal($('cs-announcer').textContent, 'Allergies: could not be loaded');
});

test('a load that rejects ends in ERROR too', async () => {
  const { $, card } = await startShell(null, { load: () => Promise.reject(new Error('x')) });
  assert.equal(card.getAttribute('data-state'), 'ERROR');
  assert.equal($('cs-announcer').textContent, 'Allergies: could not be loaded');
});

test('card bodies are not live regions; the page announcer is the only one', async () => {
  const { doc, card } = await startShell(OK_JSON);
  [card].concat(byClass(card, 'cs-block-body')).forEach((n) => {
    assert.equal(n.getAttribute('aria-live'), null);
    assert.equal(n.getAttribute('role'), null);
  });
  const live = descendants(doc.body).filter((n) => n.getAttribute('aria-live'));
  assert.deepEqual(live.map((n) => n.id), ['cs-announcer']);
});

test('opening the hidden panel focuses its title; Esc closes it and returns focus to the toggle', async () => {
  const { doc, $ } = await startShell({ status: 'EMPTY', items: [] });
  const toggle = $('cs-hidden-toggle');
  const panel = $('cs-hidden-panel');
  toggle.focus();
  toggle.click();
  assert.equal(panel.hidden, false);
  assert.equal(toggle.getAttribute('aria-expanded'), 'true');
  assert.equal(doc.activeElement, $('cs-hidden-title'));

  doc.dispatch('keydown', { key: 'Escape' });
  assert.equal(panel.hidden, true);
  assert.equal(toggle.getAttribute('aria-expanded'), 'false');
  assert.equal(doc.activeElement, toggle);
});

test('the Close button closes the panel and returns focus to the toggle', async () => {
  const { doc, $ } = await startShell({ status: 'EMPTY', items: [] });
  const toggle = $('cs-hidden-toggle');
  toggle.click();
  $('cs-hidden-close').focus();
  $('cs-hidden-close').click();
  assert.equal($('cs-hidden-panel').hidden, true);
  assert.equal(doc.activeElement, toggle);
});

test('closing the panel with the toggle itself keeps focus on the toggle', async () => {
  const { doc, $ } = await startShell({ status: 'EMPTY', items: [] });
  const toggle = $('cs-hidden-toggle');
  toggle.click();
  toggle.focus();
  toggle.click();
  assert.equal($('cs-hidden-panel').hidden, true);
  assert.equal(toggle.getAttribute('aria-expanded'), 'false');
  assert.equal(doc.activeElement, toggle);
});

test('shell source uses no innerHTML-style sinks (secondary static check)', () => {
  const source = fs.readFileSync(SHELL_PATH, 'utf8');
  ['innerHTML', 'outerHTML', 'insertAdjacentHTML', 'document.write'].forEach((sink) => {
    assert.ok(!source.includes(sink), `source must not use ${sink}`);
  });
});

test('chartspace.css opens with the CARLOS /** header and uses a 3px focus ring', () => {
  const css = fs.readFileSync(path.join(__dirname, '..', 'src', 'main', 'webapp', 'css', 'chartspace.css'), 'utf8');
  assert.ok(css.startsWith('/**\n * Copyright (c) 2026 CARLOS Contributors.'), 'license header opens with /**');
  assert.match(css, /outline: 3px solid #0b5ed7; outline-offset: 2px;/);
  assert.ok(!/outline: 2px solid/.test(css));
});
