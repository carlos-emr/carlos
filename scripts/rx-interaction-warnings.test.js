/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const {render, create, watch, UNAVAILABLE, NOT_CHECKED} = require('../src/main/webapp/share/javascript/rx-interaction-warnings');

/** Two staged cards (7 and 8), each with the five markers prescribe.jsp renders, plus the stage message. */
function fixture() {
  const elements = {};
  function element(id, severity) {
    const node = {id, style: {display: 'none'}, title: '', textContent: '', severity, attributes: {},
      setAttribute(name, value) { this.attributes[name] = String(value); }};
    elements[id] = node;
    return node;
  }
  const markers = [];
  for (const card of ['7', '8']) {
    for (const severity of ['major', 'moderate', 'minor', 'unknown', 'unchecked']) markers.push(element(`${severity}_${card}`, severity));
  }
  const message = element('interactingDrugErrorMsg');
  const document = {
    getElementById: id => elements[id] || null,
    querySelectorAll: selector => {
      assert.equal(selector, '[data-rx-interaction]');
      return markers;
    },
  };
  const shown = () => markers.filter(marker => marker.style.display === 'inline')
    .map(marker => `${marker.id}: ${marker.textContent}`);
  return {document, elements, message, shown};
}

const major = (randomId, partner, extra = {}) => ({randomId, severity: 'major', partner,
  summary: `CIPROFLOXACIN augments ${partner}`, evidence: 'good', comment: 'Monitor levels.', ...extra});

test('shows a warning on the card it names, as text, with the detail in the tooltip', () => {
  const f = fixture();
  render(f.document, {status: 'checked', level: 0, warnings: [major('7', 'THEOPHYLLINE <img src=x onerror=attack()>')]});
  assert.deepEqual(f.shown(), ['major_7: Major interaction: THEOPHYLLINE <img src=x onerror=attack()>']);
  assert.equal(f.elements.major_7.title,
    'CIPROFLOXACIN augments THEOPHYLLINE <img src=x onerror=attack()> (evidence: good)\nMonitor levels.');
  assert.equal(f.message.style.display, 'none');
  assert.equal(f.message.textContent, '');
  assert.equal(f.message.attributes['data-check-status'], 'checked');
});

test('lists every partner once and keeps each severity on its own marker', () => {
  const f = fixture();
  render(f.document, {status: 'checked', level: 0, warnings: [
    major('7', 'THEOPHYLLINE'),
    major('7', 'THEOPHYLLINE', {summary: 'THEOPHYLLINE augments CIPROFLOXACIN', comment: ''}),
    major('7', 'WARFARIN'),
    {randomId: '7', severity: 'minor', partner: 'ANTACID', summary: 'ANTACID inhibits CIPROFLOXACIN', evidence: 'fair', comment: ''},
    {randomId: '8', severity: 'unknown', partner: 'CIPROFLOXACIN', summary: 'X has an unknown effect on Y', evidence: 'unknown', comment: ''},
  ]});
  assert.deepEqual(f.shown(), [
    'major_7: Major interaction: THEOPHYLLINE, WARFARIN',
    'minor_7: Minor interaction: ANTACID',
    'unknown_8: Interaction of unknown significance: CIPROFLOXACIN',
  ]);
  assert.match(f.elements.major_7.title, /THEOPHYLLINE augments CIPROFLOXACIN \(evidence: good\)$/m);
});

test('marks a card DrugRef could not classify as not checked, never as clean', () => {
  const f = fixture();
  render(f.document, {status: 'checked', level: 0, warnings: [major('7', 'THEOPHYLLINE')], unchecked: ['8']});
  assert.deepEqual(f.shown(), ['major_7: Major interaction: THEOPHYLLINE', `unchecked_8: ${NOT_CHECKED}`]);
  assert.match(f.elements.unchecked_8.title, /no ATC code/);
  render(f.document, {status: 'checked', level: 0, warnings: [], unchecked: []});
  assert.deepEqual(f.shown(), [], 'a later answer without the card clears its note');
});

test('shows no not-checked note when the warning level is none', () => {
  const f = fixture();
  render(f.document, {status: 'off', level: 4, warnings: [], unchecked: ['8']});
  assert.deepEqual(f.shown(), []);
});

test('clears earlier markers when a later check finds nothing', () => {
  const f = fixture();
  render(f.document, {status: 'checked', level: 0, warnings: [major('7', 'THEOPHYLLINE')]});
  render(f.document, {status: 'checked', level: 0, warnings: []});
  assert.deepEqual(f.shown(), []);
  assert.equal(f.elements.major_7.textContent, '');
  assert.equal(f.elements.major_7.title, '');
});

test('shows nothing and no message when the warning level is none', () => {
  const f = fixture();
  render(f.document, {status: 'checked', level: 0, warnings: [major('7', 'THEOPHYLLINE')]});
  render(f.document, {status: 'off', level: 4, warnings: []});
  assert.deepEqual(f.shown(), []);
  assert.equal(f.message.style.display, 'none');
  assert.equal(f.message.attributes['data-check-status'], 'off');
});

for (const result of [null, {}, {status: 'unavailable', level: 0, warnings: []}, {status: 'checked'},
  {status: 'checked', warnings: [{randomId: 7, severity: 'major', partner: 'X', summary: '', evidence: '', comment: ''}]},
  {status: 'checked', warnings: [{randomId: '7', severity: 'severe', partner: 'X', summary: '', evidence: '', comment: ''}]},
  {status: 'checked', warnings: [], unchecked: '8'}, {status: 'checked', warnings: [], unchecked: [8]}]) {
  test(`reports an unavailable or malformed answer instead of a clean check: ${JSON.stringify(result)}`, () => {
    const f = fixture();
    render(f.document, {status: 'checked', level: 0, warnings: [major('7', 'THEOPHYLLINE')]});
    render(f.document, result);
    assert.deepEqual(f.shown(), []);
    assert.equal(f.message.textContent, UNAVAILABLE);
    assert.equal(f.message.style.display, 'block');
    assert.equal(f.message.attributes['data-check-status'], 'unavailable');
  });
}

test('ignores a warning for a card removed while the check ran', () => {
  const f = fixture();
  assert.doesNotThrow(() => render(f.document, {status: 'checked', level: 0, warnings: [major('99', 'THEOPHYLLINE')]}));
  assert.deepEqual(f.shown(), []);
});

/** A fetch whose answers the test releases in any order. */
function deferredFetch() {
  const pending = [];
  const fetch = (url, init) => new Promise((resolve, reject) => pending.push({url, init, resolve, reject}));
  const answer = (index, body, ok = true) => pending[index].resolve({ok, json: async () => body});
  return {fetch, pending, answer};
}

const settle = () => new Promise(resolve => setImmediate(resolve));

test('asks the named patient\'s URL without caching and shows only the newest answer', async () => {
  const f = fixture();
  const http = deferredFetch();
  const checker = create({document: f.document, url: '/carlos/rx/interactionWarnings?demographicNo=5', fetch: http.fetch});
  checker.refresh();
  checker.refresh();
  assert.equal(http.pending.length, 2);
  assert.equal(http.pending[0].url, '/carlos/rx/interactionWarnings?demographicNo=5');
  assert.equal(http.pending[0].init.cache, 'no-store');
  assert.equal(http.pending[0].init.credentials, 'same-origin');
  http.answer(1, {status: 'checked', level: 0, warnings: [major('8', 'CIPROFLOXACIN')]});
  await settle();
  http.answer(0, {status: 'checked', level: 0, warnings: [major('7', 'THEOPHYLLINE')]});
  await settle();
  assert.deepEqual(f.shown(), ['major_8: Major interaction: CIPROFLOXACIN']);
});

test('reports a refused, failed or non-JSON request as unavailable', async () => {
  for (const outcome of ['http-error', 'network', 'not-json', 'throws']) {
    const f = fixture();
    const fetch = outcome === 'http-error' ? async () => ({ok: false, json: async () => ({})})
      : outcome === 'network' ? async () => { throw new TypeError('Failed to fetch'); }
        : outcome === 'not-json' ? async () => ({ok: true, json: async () => { throw new SyntaxError('<!DOCTYPE'); }})
          : () => { throw new Error('blocked'); };
    create({document: f.document, url: '/x', fetch}).refresh();
    await settle();
    assert.equal(f.message.textContent, UNAVAILABLE, outcome);
  }
});

test('discards an answer that was in flight when the cards changed', async () => {
  const f = fixture();
  const http = deferredFetch();
  const timers = [];
  const checker = create({document: f.document, url: '/x', fetch: http.fetch,
    setTimeout: fn => { timers.push(fn); return timers.length - 1; }, clearTimeout: () => {}});
  checker.refresh();
  assert.equal(f.message.attributes['data-check-status'], 'pending');
  checker.schedule(); // a card was removed while the first check ran
  http.answer(0, {status: 'checked', level: 0, warnings: [major('7', 'THEOPHYLLINE')]});
  await settle();
  assert.deepEqual(f.shown(), [], 'an answer about the old cards was drawn');
  assert.equal(f.message.attributes['data-check-status'], 'pending');
  timers[0]();
  http.answer(1, {status: 'checked', level: 0, warnings: []});
  await settle();
  assert.equal(f.message.attributes['data-check-status'], 'checked');
});

test('batches a burst of card changes into one request', () => {
  const timers = [];
  let requests = 0;
  const checker = create({document: fixture().document, url: '/x', delay: 50,
    fetch: () => { requests++; return new Promise(() => {}); },
    setTimeout: (fn, delay) => { timers.push({fn, delay, cleared: false}); return timers.length - 1; },
    clearTimeout: index => { timers[index].cleared = true; }});
  checker.schedule();
  checker.schedule();
  checker.schedule();
  assert.deepEqual(timers.map(timer => [timer.delay, timer.cleared]), [[50, true], [50, true], [50, false]]);
  timers[2].fn();
  assert.equal(requests, 1);
});

/** Records the MutationObserver the module creates. */
class FakeObserver {
  constructor(callback) { this.callback = callback; FakeObserver.last = this; }
  observe(target, options) { this.target = target; this.options = options; }
}

test('re-checks when a card is added or removed, not when a card\'s own content changes', () => {
  const stage = {id: 'rxText'};
  let changes = 0;
  const observer = watch(stage, () => { changes++; }, FakeObserver);
  assert.equal(observer, FakeObserver.last);
  assert.equal(observer.target, stage);
  assert.deepEqual(observer.options, {childList: true, subtree: true});

  const card = {nodeType: 1, id: 'set_7', querySelector: () => null};
  const wrapper = {nodeType: 1, id: '', querySelector: selector => (selector === '[id^="set_"]' ? card : null)};
  const markerText = {nodeType: 3};
  const allergyRow = {nodeType: 1, id: 'alleg_7', querySelector: () => null};
  const record = (added, removed = []) => ({addedNodes: added, removedNodes: removed});

  observer.callback([record([markerText]), record([allergyRow])]);
  assert.equal(changes, 0);
  observer.callback([record([card])]);
  observer.callback([record([], [card])]);
  observer.callback([record([wrapper])]);
  observer.callback([record([card]), record([], [card])]);
  assert.equal(changes, 4);
});

test('does not watch without a staging area or MutationObserver', () => {
  assert.equal(watch(null, () => {}, FakeObserver), null);
  assert.equal(watch({}, () => {}, undefined), null);
});
