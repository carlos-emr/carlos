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
const acknowledgePath = slice('    function refreshInboxhubAfterHrmRevoke()',
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
 * @param {boolean} hasMoreData whether pages remain unloaded. The default is false -- a
 *        fully loaded list -- because that is the only state in which an item may be dropped
 *        in place; see the page-boundary tests at the end for why.
 */
function setup(mode, items, shortPreview = false, hasMoreData = false) {
  const state = { fetches: 0, viewFetches: 0, submits: 0, draws: [], opened: null, scrolledTo: null };
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
      attr(name) {
        if (!matched.length) { return undefined; }
        return name === 'data-lab-type' ? matched[0].labType : matched[0].segmentId;
      },
      each(body) { matched.forEach((item) => body.call(item)); return set(matched); },
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
      rows: elements => ({
        remove() { drop(elements); return { draw(paging) { state.draws.push(paging); } }; },
      }),
    }),
  };
  const counter = id => ({
    val(value) { if (value === undefined) { return String(totals[id]); } totals[id] = Number(value); },
  });

  const SCAN = '#inboxViewItems .document-card, #inboxhubListModeTableBody tr[data-segment-id]';
  function jQuery(selector) {
    if (selector === undefined) { return set([]); }
    // dedupeInboxhubItems() hands back the duplicate elements it collected, and wraps each
    // scanned element with jQuery(this) to read its identity.
    if (Array.isArray(selector)) { return set(selector); }
    if (typeof selector === 'object') { return set([selector]); }
    // Its scan covers both modes in one selector; the fixture renders one at a time.
    if (selector === SCAN) { return set(rendered); }
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
  const form = {
    saved: null, raw: '',
    requestSubmit() { state.submits++; },
    querySelector() { return this.saved; },
    appendChild(input) { this.saved = input; },
    getAttribute() { return this.raw; },
  };
  const document = {
    createElement() { return {}; },
    getElementById(id) {
      if (id === 'inboxSearchForm') return form;
      if (/^patient/.test(id)) return {classList: {add() { state.selectedCategory = id; }}};
      return id === 'inboxViewItems' && mode === 'preview' ? container : null;
    },
    querySelector(selector) {
      assert.equal(selector, '#inbox_table tbody tr a');
      if (mode !== 'list' || rendered.length === 0) { return null; }
      const first = rendered[0];
      return { click() { state.opened = first.segmentId; } };
    },
  };

  const context = vm.createContext({
    jQuery, BroadcastChannel, document, URLSearchParams,
    filter: '', activeTypeFilter: null, ackToggleState: false,
    hasMoreData, isFetchingData: false, rapidReviewState: false,
    showInboxhubStats() {},
    fetchInboxhubData() { state.fetches++; },
    fetchInboxhubViewData() { state.viewFetches++; },
  });
  vm.runInContext(acknowledgePath + rapidReview, context);

  return {
    state, totals, context, form,
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

test('dropping a card in place asks the server for nothing at all', () => {
  // A removal can shorten the list past the point where #inboxViewItems scrolls, which is how
  // preview mode asks for its next page. Topping up from the server here would be wasted work
  // in every case: an item is only removed in place when the whole result set is already
  // loaded, and while pages remain the acknowledgement re-syncs instead and that re-fetch
  // repopulates the list. So neither request may be made.
  const inbox = setup('preview', twoLabs, true, false);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.state.viewFetches, 0, 'there is no next page to ask for');
  assert.equal(inbox.state.fetches, 0, 'and nothing needs re-running');
  assert.deepEqual(inbox.shown(), ['HL7:171']);
});

/*
 * THE PAGE BOUNDARY.
 *
 * The server pages by OFFSET, not by cursor: LabDataController turns the page number into
 * `page - 1` and the DAOs multiply it out (HRMDocumentToProviderDao:
 * `setFirstResult(page * pageSize)`). An acknowledged result leaves the New set, so every
 * later result shifts up by one and the next page number starts one item too far in -- the
 * result on the page boundary is then never fetched at all.
 *
 * The client cannot compensate: one page number drives three windows at two page sizes
 * (labs at 100 per page, documents and HRM at pageSize), so no arithmetic on it expresses
 * "everything moved up by one item". So while pages remain unloaded the acknowledgement
 * must re-sync, however well the item could otherwise have been dropped in place.
 */

test('an acknowledgement re-syncs while pages remain unloaded', () => {
  const inbox = setup('list', twoLabs, false, true);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.state.fetches, 1,
    'paging on from a shifted result set silently skips the result on the page boundary');
});

test('the same acknowledgement is dropped in place once everything is loaded', () => {
  const inbox = setup('list', twoLabs, false, false);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.state.fetches, 0, 'no later page will be asked for, so no offset can be skipped');
  assert.deepEqual(inbox.shown(), ['HL7:171']);
});

test('the re-sync still moves the counters exactly once', () => {
  // The re-fetch reloads the LIST; the badges are re-read from hidden inputs that only the
  // in-place bookkeeping moves, so they must not be skipped along with the removal.
  const inbox = setup('preview', twoLabs, false, true);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.totals.totalLabsCount, 4);
});

test('the drop helper itself reports that a re-sync is required, not just the listener', () => {
  // labDisplay.jsp's no-BroadcastChannel fallback calls this function directly and re-fetches
  // on a falsy answer. If the paging condition lived only in the BroadcastChannel listener,
  // that browser would drop the item in place with pages still unloaded and reintroduce the
  // page-boundary bug. The contract is the shared guarantee, so it is asserted directly.
  const pending = setup('preview', twoLabs, false, true);
  assert.equal(pending.context.dropAcknowledgedInboxhubItem('170', 'HL7', 1), false,
    'pages remain unloaded, so every caller must re-sync');

  const loaded = setup('preview', twoLabs, false, false);
  assert.equal(loaded.context.dropAcknowledgedInboxhubItem('171', 'HL7', 1), true,
    'everything is loaded, so no caller needs to re-sync');
});

/*
 * THE RESULT-SET LIFETIME.
 *
 * handledInboxhubItems answers "is this item already off screen", so it means nothing once
 * the screen is replaced. resetDataPageCount() clears it for that reason. The counted record
 * deliberately does not follow: the totals are hidden-input page state that a fetch does not
 * re-render, so counting an item a second time would take rows off the badge that nobody
 * cleared.
 */

test('a new result set forgets what the old one had already taken off screen', () => {
  // Acknowledge in the New view, then switch to the Acknowledged view, where that same item
  // now belongs ON the list. A stale "already handled" would answer the notification with
  // silence instead of the re-fetch that renders its row.
  const inbox = setup('preview', twoLabs);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.state.fetches, 0);

  inbox.context.forgetHandledInboxhubItems();   // what resetDataPageCount() does
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.state.fetches, 1, 'the new result set has never been told about this item');
});

test('forgetting what is off screen does not let the badge be moved twice', () => {
  const inbox = setup('preview', twoLabs);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  inbox.context.forgetHandledInboxhubItems();
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.totals.totalLabsCount, 4, 'the counted record has the longer lifetime');
  assert.equal(inbox.totals.totalResultsCount, 14);
});

for (const mode of ['list', 'preview']) {
  test(mode + ' revocation reloads authoritative totals without acknowledging the row', () => {
    const inbox = setup(mode, [['7', 'HRM']]);
    inbox.acknowledge({ action: 'hrm-revoked', segmentID: '7', labType: 'HRM' });
    assert.equal(inbox.state.submits, 1);
    assert.equal(inbox.state.fetches, 0);
    assert.equal(inbox.totals.totalHRMCount, 5);
    assert.deepEqual(inbox.shown(), ['HRM:7']);
  });
}
test('malformed revocation cannot acknowledge an item', () => {
  const inbox = setup('list', [['7', 'HL7']]);
  inbox.acknowledge({ action: 'hrm-revoked', segmentID: '7', labType: 'HL7' });
  assert.equal(inbox.state.submits, 0);
  assert.equal(inbox.state.fetches, 0);
  assert.deepEqual(inbox.shown(), ['HL7:7']);
  assert.equal(inbox.totals.totalLabsCount, 5);
});

for (const mode of ['list', 'preview']) {
  test(mode + ' duplicate HRM routing rows count as one distinct report', () => {
    const inbox = setup(mode, [['7', 'HRM'], ['8', 'HRM']]);
    inbox.acknowledge({ action: 'refresh', segmentID: '7', labType: 'HRM', clearedCount: 2 });
    assert.equal(inbox.totals.totalHRMCount, 4);
    assert.equal(inbox.totals.totalResultsCount, 14);
    assert.deepEqual(inbox.shown(), ['HRM:8']);
  });
}

test('revocation carries category and toolbar state through a full reload', () => {
  const before = setup('list', [['7', 'HRM']]);
  Object.assign(before.context, {filter: '&demographicFilter=1&typeFilter=hrm',
    activeTypeFilter: 'HRM', ackToggleState: true, rapidReviewState: true});
  before.acknowledge({action: 'hrm-revoked', segmentID: '7', labType: 'HRM'});
  assert.equal(before.form.saved.name, 'inboxhubRevokeState');
  const after = setup('list', [['7', 'HRM']]);
  after.form.raw = before.form.saved.value;
  after.context.restoreInboxhubAfterHrmRevoke();
  assert.equal(after.context.filter, '&demographicFilter=1&typeFilter=hrm');
  assert.equal(after.context.activeTypeFilter, 'HRM');
  assert.equal(after.context.ackToggleState, true);
  assert.equal(after.context.rapidReviewState, true);
  assert.equal(after.state.selectedCategory, 'patient1hrms');
});

test('restored category state cannot append unrelated query parameters', () => {
  const inbox = setup('list', []);
  inbox.form.raw = JSON.stringify({filter: '&demographicFilter=0&typeFilter=hrm&query.searchAll=true'});
  inbox.context.restoreInboxhubAfterHrmRevoke();
  assert.equal(inbox.context.filter, '&demographicFilter=0&typeFilter=hrm');
});

for (const raw of ['not JSON', 'null', JSON.stringify({filter: '&demographicFilter=99999999999&typeFilter=hrm'}),
  JSON.stringify({filter: '&demographicFilter=1&typeFilter=__proto__', activeTypeFilter: 'invalid'})]) {
  test('invalid reload state is ignored: ' + raw, () => {
    const inbox = setup('list', []);
    inbox.form.raw = raw;
    inbox.context.restoreInboxhubAfterHrmRevoke();
    assert.equal(inbox.context.filter, '');
    assert.equal(inbox.context.activeTypeFilter, null);
  });
}
