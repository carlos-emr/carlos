/* SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Acknowledging one inbox item must not re-run the whole inbox search.
 *
 * The Inboxhub used to answer every acknowledgement with fetchInboxhubData(), which empties
 * #inboxhubMode and refetches from page 1: the clinician lost their place in the list, every
 * page after the first was discarded, and in preview mode each remaining card's iframe was
 * re-rendered from scratch. These checks pin the in-place path that replaced it, and the
 * cases that must still fall back to asking the server.
 */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const formJsp = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/web/inboxhub/InboxhubForm.jsp'), 'utf8');

function slice(from, to) {
  const start = formJsp.indexOf(from);
  const end = formJsp.indexOf(to, start);
  assert.ok(start >= 0 && end > start, 'slice anchors not found in InboxhubForm.jsp: ' + from);
  return formJsp.slice(start, end);
}

// The acknowledge path exactly as the page ships it. resetInboxFilters sits between the two
// slices and is left out because it carries a JSP encoder tag, which is not JavaScript.
const acknowledgePath = slice('    try {\n        const inboxhubRefreshChannel',
                              '    /**\n     * Resets all inbox filters');
const rapidReview = slice('    // Flag set by BroadcastChannel listener',
                          '    // State variables preserved');

/**
 * Builds an inbox in one of its two modes over the given items, and returns the handles the
 * assertions need: the broadcast the popups send, the items still rendered, and the counters.
 *
 * @param {string} mode 'list' (DataTable) or 'preview' (cards in #inboxViewItems)
 * @param {Array} items [segmentId, labType] pairs, in rendered order
 * @param {boolean} shortPreview whether the preview list is too short to scroll
 */
function setup(mode, items, shortPreview = false) {
  const state = { fetches: 0, viewFetches: 0, draws: [], opened: null, scrolledTo: null };
  const totals = { totalDocsCount: 5, totalLabsCount: 5, totalHRMCount: 5, totalResultsCount: 15 };
  let rendered = items.map(([segmentId, labType]) => ({
    segmentId, labType,
    scrollIntoView(options) { state.scrolledTo = segmentId; assert.equal(options.block, 'start'); },
  }));
  const drop = doomed => { rendered = rendered.filter(item => !doomed.includes(item)); };

  function set(matched) {
    return {
      length: matched.length,
      matched,
      0: matched[0],
      data(name) { assert.equal(name, 'labType'); return matched.length ? matched[0].labType : undefined; },
      filter(selector) {
        const wanted = selector.match(/="([^"]+)"/)[1];
        return set(matched.filter(item => item.labType === wanted));
      },
      first() { return set(matched.slice(0, 1)); },
      next(selector) {
        assert.equal(selector, '.document-card');
        const at = rendered.indexOf(matched[0]);
        return set(at >= 0 && rendered[at + 1] ? [rendered[at + 1]] : []);
      },
      remove() { drop(matched); return set(matched); },
    };
  }

  const table = {
    length: 1,
    DataTable: () => ({
      row: element => ({
        remove() { drop(element.matched); return { draw(paging) { state.draws.push(paging); } }; },
      }),
    }),
  };
  const counter = id => ({
    val(value) { if (value === undefined) { return String(totals[id]); } totals[id] = Number(value); },
  });

  function jQuery(selector) {
    if (selector === undefined) { return set([]); }
    if (selector === '#inbox_table') { return mode === 'list' ? table : set([]); }
    if (selector === '#inboxViewItems') { return mode === 'preview' ? set([{}]) : set([]); }
    if (/^#total\w+Count$/.test(selector)) { return counter(selector.slice(1)); }
    const lookups = [...selector.matchAll(/\[(id|data-segment-id)="([^"]+)"\]/g)];
    assert.ok(lookups.length > 0, 'selector must use the expected bounded attribute lookup');
    return set(rendered.filter(item => lookups.some(([, name, value]) =>
      (name === 'data-segment-id' ? item.segmentId : 'labdoc_' + item.segmentId) === value)));
  }

  let listener = null;
  class BroadcastChannel {
    constructor(name) { assert.equal(name, 'inboxhub-refresh'); }
    set onmessage(handler) { listener = handler; }
    close() {}
  }
  const container = { scrollHeight: shortPreview ? 100 : 900, clientHeight: 400 };
  const document = {
    getElementById: id => (id === 'inboxViewItems' && mode === 'preview' ? container : null),
    querySelector(selector) {
      assert.equal(selector, '#inbox_table tbody tr a');
      if (mode !== 'list' || rendered.length === 0) { return null; }
      const first = rendered[0];
      return { click() { state.opened = first.segmentId; } };
    },
  };

  const context = vm.createContext({
    jQuery, BroadcastChannel, document,
    hasMoreData: true, isFetchingData: false, rapidReviewState: false,
    showInboxhubStats() {},
    fetchInboxhubData() { state.fetches++; },
    fetchInboxhubViewData() { state.viewFetches++; },
  });
  vm.runInContext(acknowledgePath + rapidReview, context);

  return {
    state, totals, context,
    acknowledge: data => listener({ data }),
    shown: () => rendered.map(item => item.labType + ':' + item.segmentId),
  };
}

const twoLabs = [['170', 'HL7'], ['171', 'HL7']];

test('preview mode drops the acknowledged card instead of re-running the search', () => {
  const inbox = setup('preview', twoLabs);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.state.fetches, 0, 're-fetch reloads every remaining card iframe');
  assert.deepEqual(inbox.shown(), ['HL7:171']);
  assert.equal(inbox.totals.totalLabsCount, 4);
  assert.equal(inbox.totals.totalResultsCount, 14);
});

test('list mode drops the acknowledged row through the DataTable without resetting paging', () => {
  const inbox = setup('list', twoLabs);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.state.fetches, 0);
  assert.deepEqual(inbox.state.draws, [false], 'draw(true) would reset the scroll position');
  assert.deepEqual(inbox.shown(), ['HL7:171']);
});

test('an item that is not on screen still asks the server', () => {
  // The inbox may be filtered, or listing Acknowledged items, where this ADDS a row.
  const inbox = setup('preview', twoLabs);
  inbox.acknowledge({ action: 'refresh', segmentID: '900', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.state.fetches, 1);
  assert.deepEqual(inbox.shown(), ['HL7:170', 'HL7:171']);
});

test('a patient match, which names no item, still asks the server', () => {
  // PatientSearch.jsp posts the bare string: the matched row has to be re-rendered.
  const inbox = setup('list', twoLabs);
  inbox.acknowledge('refresh');
  assert.equal(inbox.state.fetches, 1);
  assert.deepEqual(inbox.shown(), ['HL7:170', 'HL7:171']);
});

test('a popup that removed the row through window.opener does not also force a re-fetch', () => {
  const inbox = setup('list', twoLabs);
  inbox.context.removeInboxhubRow('170', 'HL7');
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.state.fetches, 0, 'the row is gone because this window removed it');
  assert.equal(inbox.totals.totalLabsCount, 4, 'and the badge still moves, exactly once');
});

test('the counters move once however many times one acknowledgement arrives', () => {
  const inbox = setup('preview', twoLabs);
  for (let i = 0; i < 3; i++) {
    inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  }
  assert.equal(inbox.totals.totalLabsCount, 4);
  assert.equal(inbox.totals.totalResultsCount, 14);
});

test('a multi-version lab takes the whole chain off the badge but only its one card', () => {
  const inbox = setup('preview', twoLabs);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 3 });
  assert.deepEqual(inbox.shown(), ['HL7:171']);
  assert.equal(inbox.totals.totalLabsCount, 2);
});

test('an acknowledgement that cleared nothing leaves the badge alone', () => {
  const inbox = setup('preview', twoLabs);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 0 });
  assert.deepEqual(inbox.shown(), ['HL7:171']);
  assert.equal(inbox.totals.totalLabsCount, 5);
  assert.equal(inbox.state.fetches, 0);
});

test('a segment id shared across report types drops only the acknowledged type', () => {
  const inbox = setup('preview', [['170', 'DOC'], ['170', 'HL7']]);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  assert.deepEqual(inbox.shown(), ['DOC:170']);
  assert.equal(inbox.totals.totalDocsCount, 5);
  assert.equal(inbox.totals.totalLabsCount, 4);
});

test('Rapid Review advances to the card that took the acknowledged one\'s place', () => {
  const inbox = setup('preview', [['170', 'HL7'], ['171', 'HL7'], ['172', 'HL7']]);
  inbox.context.rapidReviewState = true;
  inbox.acknowledge({ action: 'refresh', segmentID: '171', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.state.scrolledTo, '172', 'scrolling to the first card is the bug, not the fix');
  assert.equal(inbox.state.fetches, 0);
});

test('Rapid Review opens the next row in list mode', () => {
  const inbox = setup('list', twoLabs);
  inbox.context.rapidReviewState = true;
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.state.opened, '171');
});

test('a preview list too short to scroll pulls in its next page', () => {
  // Preview mode reaches later pages from #inboxViewItems' scroll event; with nothing left
  // to scroll that event never fires again.
  const inbox = setup('preview', twoLabs, true);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.state.viewFetches, 1);
  assert.equal(inbox.state.fetches, 0, 'the next page, not the whole search over again');
});
