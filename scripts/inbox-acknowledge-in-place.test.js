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
const rapidReview = slice('    /**\n     * Whether Rapid Review still owes the clinician the next result',
                          '    // State variables preserved');
// The legacy window.opener entry point ships in InboxhubListMode.jsp and runs in the same
// window as the functions above, so it is evaluated into the same context.
const listModeJsp = fs.readFileSync(path.join(__dirname,
  '../src/main/webapp/WEB-INF/jsp/web/inboxhub/InboxhubListMode.jsp'), 'utf8');
const removeReportStart = listModeJsp.indexOf('    function removeReport(reportId, labType)');
const removeReportEnd = listModeJsp.indexOf('\n    }\n', removeReportStart) + '\n    }\n'.length;
assert.ok(removeReportStart >= 0 && removeReportEnd > removeReportStart, 'removeReport not found in InboxhubListMode.jsp');
const legacyRemoveReport = listModeJsp.slice(removeReportStart, removeReportEnd);

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
function setup(mode, items, shortPreview = false, hasMoreData = false, page = 1) {
  const state = { fetches: 0, viewFetches: 0, submits: 0, draws: [], opened: null, opens: [], scrolledTo: null,
    scrolls: [], boundaryRequests: [], inserted: [], aborted: 0 };
  const totals = { totalDocsCount: 5, totalLabsCount: 5, totalHRMCount: 5, totalResultsCount: 15 };
  let rendered = [];
  // One rendered row or card. after()/before() are what mergeInboxhubPreviewCards uses to
  // slot a fetched card in beside its neighbour, so they splice into the rendered order.
  function element(segmentId, labType) {
    const el = {
      segmentId, labType,
      scrollIntoView(options) { state.scrolledTo = segmentId; state.scrolls.push(segmentId); assert.equal(options.block, 'start'); },
      getAttribute(name) { return name === 'data-lab-type' ? labType : segmentId; },
      link: { click() { state.opened = segmentId; state.opens.push(segmentId); } },
      after(node) { rendered.splice(rendered.indexOf(el) + 1, 0, node); state.inserted.push(node.segmentId); },
      before(node) { rendered.splice(rendered.indexOf(el), 0, node); state.inserted.push(node.segmentId); },
    };
    return el;
  }
  rendered = items.map(([segmentId, labType]) => element(segmentId, labType));
  const drop = doomed => { rendered = rendered.filter(item => !doomed.includes(item)); };

  function set(matched) {
    return {
      length: matched.length,
      matched,
      0: matched[0],
      data(name) { assert.equal(name, 'labType'); return matched.length ? matched[0].labType : undefined; },
      filter(selector) {
        // The production code narrows an item lookup to one mode's element kind before it
        // opens or scrolls to it; the fixture renders one mode at a time.
        if (selector === 'tr') { return set(mode === 'list' ? matched : []); }
        if (selector === '.document-card') { return set(mode === 'preview' ? matched : []); }
        const wanted = selector.match(/="([^"]+)"/)[1];
        return set(matched.filter(item => item.labType === wanted));
      },
      find(selector) {
        assert.equal(selector, 'a');
        return set(matched.length ? [matched[0].link] : []);
      },
      first() { return set(matched.slice(0, 1)); },
      attr(name) {
        if (!matched.length) { return undefined; }
        return name === 'data-lab-type' ? matched[0].labType : matched[0].segmentId;
      },
      each(body) { matched.forEach((item) => body.call(item)); return set(matched); },
      next(selector) {
        assert.equal(selector, mode === 'list' ? 'tr' : '.document-card',
          'the following element is asked for by the kind the mode renders');
        const at = rendered.indexOf(matched[0]);
        return set(at >= 0 && rendered[at + 1] ? [rendered[at + 1]] : []);
      },
      prev(selector) {
        assert.equal(selector, mode === 'list' ? 'tr' : '.document-card');
        const at = rendered.indexOf(matched[0]);
        return set(at > 0 ? [rendered[at - 1]] : []);
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
  const container = { scrollHeight: shortPreview ? 100 : 900, clientHeight: 400, scrollTop: 0,
    append(node) { rendered.push(node); state.inserted.push(node.segmentId); } };
  const form = {
    saved: null, raw: '',
    requestSubmit() { throw new Error("Search validation must not gate a committed revoke"); },
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
      return rendered[0].link;
    },
  };

  // The boundary re-sync posts through jQuery.ajax and parses the answer with DOMParser. The
  // fixture records the request and lets a test hand back a page of cards.
  jQuery.ajax = options => {
    assert.equal(options.method, 'POST');
    assert.match(options.url, /Inboxhub\?method=displayInboxView$/);
    const request = { aborted: false, abort() { this.aborted = true; state.boundaryAborted++; options.error({}, 'abort'); } };
    state.boundaryRequests.push(Object.assign(options, { request }));
    return request;
  };
  state.boundaryAborted = 0;
  class DOMParser {
    parseFromString(html, type) {
      assert.equal(type, 'text/html');
      // A fetched page is described to the fixture as "segment:type" tokens, one per card, with
      // any <script>...</script> kept apart the way a parsed document keeps its script elements.
      // (Plain index arithmetic, not a regular expression: this is a fixture format, not HTML
      // filtering, and a filtering-shaped regexp reads as one to static analysis.)
      const OPEN = '<script>';
      const CLOSE = '</script>';
      const scripts = [];
      let content = html;
      for (let at = content.indexOf(OPEN); at >= 0; at = content.indexOf(OPEN)) {
        const end = content.indexOf(CLOSE, at);
        assert.ok(end > at, 'fixture script element is not closed');
        scripts.push({ textContent: content.slice(at + OPEN.length, end) });
        content = content.slice(0, at) + ' ' + content.slice(end + CLOSE.length);
      }
      const cards = content.split(/\s+/).filter(token => /^[A-Za-z0-9_-]+:[A-Za-z0-9_-]+$/.test(token))
        .map(token => { const [labType, segmentId] = token.split(':'); return element(segmentId, labType); });
      return { querySelectorAll(selector) {
        if (selector === 'script') { return scripts; }
        assert.equal(selector, '.document-card'); return cards;
      } };
    }
  }

  const context = vm.createContext({
    jQuery, BroadcastChannel, document, URLSearchParams, DOMParser,
    HTMLFormElement: {prototype: {submit() { assert.equal(this, form); state.submits++; }}},
    filter: '', activeTypeFilter: null, ackToggleState: false,
    hasMoreData, isFetchingData: false, currentFetchRequest: null, rapidReviewState: false,
    page, pageSize: 20, inboxSearchFormData: 'query.status=N', inboxContextPath: '/carlos',
    showInboxhubStats() {},
    // The real one empties the screen through resetDataPageCount(); the bookkeeping half of
    // that reset is run here so a fetch has the same effect on the pending-advance state.
    fetchInboxhubData() {
      state.fetches++;
      if (typeof context.resetPendingRapidReviewForNewResultSet === 'function') {
        context.resetPendingRapidReviewForNewResultSet();
      }
    },
    fetchInboxhubViewData() { state.viewFetches++; },
  });
  vm.runInContext(acknowledgePath + rapidReview + legacyRemoveReport, context);
  // resetDataPageCount() sits outside the evaluated slices (it drives the spinner and aborts
  // requests); the bookkeeping statements it runs are lifted verbatim so the tests exercise
  // the same lines the page does.
  const resetSource = formJsp.slice(formJsp.indexOf('    function resetDataPageCount()'));
  const resetBody = resetSource.slice(resetSource.indexOf('        forgetHandledInboxhubItems();'),
    resetSource.indexOf('        page = 1;'));
  assert.ok(resetBody.includes('inboxhubResultSetGeneration++;'), 'resetDataPageCount bookkeeping not found');
  vm.runInContext('function resetPendingRapidReviewForNewResultSet() {\n' + resetBody + '\n}', context);

  return {
    state, totals, context, form,
    acknowledge: data => listener({ data }),
    shown: () => rendered.map(item => item.labType + ':' + item.segmentId),
    // Replaces what is on screen, the way a re-fetched page 1 (then each later page) does.
    render: items => { rendered = items.map(([segmentId, labType]) => element(segmentId, labType)); },
    // What resetDataPageCount() does to the acknowledgement bookkeeping.
    reset: () => { context.forgetHandledInboxhubItems(); context.resetPendingRapidReviewForNewResultSet(); },
    // Puts a next-page preview fetch in flight, the way a scroll to the bottom does.
    startPageFetch: () => { context.isFetchingData = true; context.currentFetchRequest = { abort() { state.aborted++; } }; },
    // Answers the most recent boundary re-sync request with a page of cards.
    answerBoundary: html => { state.boundaryRequests[state.boundaryRequests.length - 1].success(html); },
    container,
    failBoundary: () => { state.boundaryRequests[state.boundaryRequests.length - 1].error({}, 'error'); },
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

/*
 * THE ROW THAT IS OPENED NEXT.
 *
 * An alpha15 tester started Rapid Review part-way down the list and, on acknowledging, was
 * given the lab at the TOP of the table rather than the one below the lab they had just
 * dealt with. The row to open is the one that took the acknowledged row's place, in the
 * order the DataTable shows them; the first row is only the fallback for when nothing
 * followed.
 */

test('Rapid Review opens the row BELOW the acknowledged one when the clinician started part-way down', () => {
  const inbox = setup('list', [['170', 'HL7'], ['171', 'HL7'], ['172', 'HL7'], ['173', 'HL7']]);
  inbox.context.rapidReviewState = true;
  inbox.acknowledge({ action: 'refresh', segmentID: '172', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.state.opened, '173', 'opening the first row of the table is the bug, not the fix');
  assert.deepEqual(inbox.shown(), ['HL7:170', 'HL7:171', 'HL7:173']);
});

test('Rapid Review falls back to the first row only when nothing followed the acknowledged one', () => {
  const inbox = setup('list', [['170', 'HL7'], ['171', 'HL7'], ['172', 'HL7']]);
  inbox.context.rapidReviewState = true;
  inbox.acknowledge({ action: 'refresh', segmentID: '172', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.state.opened, '170', 'the last row has no successor, so the review wraps to the top');
});

test('Rapid Review remembers the following row by identity, so a popup going through window.opener still advances correctly', () => {
  // labDisplay.jsp calls removeInboxhubRow directly when window.opener survives, and the
  // broadcast lands moments later. The row is already gone by then; the identity of the row
  // that followed it is what has to survive to the advance.
  const inbox = setup('list', [['170', 'HL7'], ['171', 'HL7'], ['172', 'HL7']]);
  inbox.context.rapidReviewState = true;
  inbox.context.removeInboxhubRow('171', 'HL7');
  inbox.acknowledge({ action: 'refresh', segmentID: '171', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.state.opened, '172');
});

test('Rapid Review opens exactly one result when the opener call and the broadcast both arrive', () => {
  // The popup's direct window.opener call removes the row, and its broadcast lands moments
  // later. Both reach the shared contract; only one of them may open the next result, or the
  // clinician gets the successor and then, the remembered item being spent, the first row.
  const inbox = setup('list', [['170', 'HL7'], ['171', 'HL7'], ['172', 'HL7']]);
  inbox.context.rapidReviewState = true;
  inbox.context.dropAcknowledgedInboxhubItem('171', 'HL7', 1);   // the direct route
  inbox.acknowledge({ action: 'refresh', segmentID: '171', labType: 'HL7', clearedCount: 1 });
  assert.deepEqual(inbox.state.opens, ['172']);
});

test('Rapid Review advances on the no-BroadcastChannel route too', () => {
  // labDisplay.jsp's dropFromInboxhubDirectly() calls the contract and nothing else; a browser
  // without BroadcastChannel must still get the next result opened.
  const inbox = setup('list', [['170', 'HL7'], ['171', 'HL7'], ['172', 'HL7']]);
  inbox.context.rapidReviewState = true;
  assert.equal(inbox.context.dropAcknowledgedInboxhubItem('171', 'HL7', 1), true);
  assert.deepEqual(inbox.state.opens, ['172']);
});

test('the direct route arms the post-redraw advance when list mode is still loading', () => {
  const inbox = setup('list', [['170', 'HL7'], ['171', 'HL7'], ['172', 'HL7']], false, true);
  inbox.context.rapidReviewState = true;
  assert.equal(inbox.context.dropAcknowledgedInboxhubItem('171', 'HL7', 1), false, 'the caller re-fetches');
  assert.deepEqual(inbox.state.opens, [], 'nothing is opened before the redraw');
  assert.equal(inbox.context.pendingRapidReviewOpen, true);
  inbox.render([['170', 'HL7'], ['172', 'HL7']]);
  inbox.context.advancePendingRapidReview();
  assert.deepEqual(inbox.state.opens, ['172']);
});

test('acknowledging the LAST loaded row waits for its successor instead of falling back to the top', () => {
  // Nothing followed the row on screen, but pages remain: the successor is the first row of
  // the next page. The row above is remembered, and the successor is "the row after it" once
  // the re-fetched list has drawn that far.
  const inbox = setup('list', [['170', 'HL7'], ['171', 'HL7'], ['172', 'HL7']], false, true);
  inbox.context.rapidReviewState = true;
  inbox.acknowledge({ action: 'refresh', segmentID: '172', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.state.fetches, 1);
  inbox.render([['170', 'HL7'], ['171', 'HL7']]);
  inbox.context.advancePendingRapidReview();
  assert.deepEqual(inbox.state.opens, [], 'page one ends at the row above; the successor has not arrived');
  inbox.render([['170', 'HL7'], ['171', 'HL7'], ['173', 'HL7']]);
  inbox.context.advancePendingRapidReview();
  assert.deepEqual(inbox.state.opens, ['173']);
});

test('preview mode acknowledging the LAST loaded card advances once the boundary merge brings its successor', () => {
  const inbox = setup('preview', [['170', 'HL7'], ['171', 'HL7'], ['172', 'HL7']], false, true, 2);
  inbox.context.rapidReviewState = true;
  inbox.acknowledge({ action: 'refresh', segmentID: '172', labType: 'HL7', clearedCount: 1 });
  assert.deepEqual(inbox.state.scrolls, [], 'the successor is not on screen yet');
  assert.equal(inbox.context.pendingRapidReviewOpen, true);
  inbox.answerBoundary('HL7:170 HL7:171 HL7:173');
  assert.deepEqual(inbox.shown(), ['HL7:170', 'HL7:171', 'HL7:173']);
  assert.deepEqual(inbox.state.scrolls, ['173'], 'the card that took the acknowledged one\'s place is brought into view');
  assert.equal(inbox.context.pendingRapidReviewOpen, false);
});

test('a waiting preview advance gives up quietly when the list turns out to be finished', () => {
  const inbox = setup('preview', [['170', 'HL7'], ['171', 'HL7']], false, true, 2);
  inbox.context.rapidReviewState = true;
  inbox.acknowledge({ action: 'refresh', segmentID: '171', labType: 'HL7', clearedCount: 1 });
  inbox.answerBoundary('HL7:170 <script>hasMoreData = false;</script>');
  assert.deepEqual(inbox.state.scrolls, []);
  assert.equal(inbox.context.pendingRapidReviewOpen, false, 'nothing more will arrive, so nothing is owed');
});

test('a search the clinician makes while an advance is pending drops it and the remembered neighbours', () => {
  const inbox = setup('list', [['170', 'HL7'], ['171', 'HL7'], ['172', 'HL7']], false, true);
  inbox.context.rapidReviewState = true;
  inbox.acknowledge({ action: 'refresh', segmentID: '171', labType: 'HL7', clearedCount: 1 });
  // The acknowledgement's own re-fetch (a reset) kept the advance armed...
  assert.equal(inbox.state.fetches, 1);
  assert.equal(inbox.context.pendingRapidReviewOpen, true, 'the re-fetch the acknowledgement asked for carries the advance');
  // ...a further reset is the clinician changing the search, and must not.
  inbox.reset();
  assert.equal(inbox.context.pendingRapidReviewOpen, false);
  inbox.render([['500', 'DOC'], ['172', 'HL7']]);
  inbox.context.advancePendingRapidReview();
  assert.deepEqual(inbox.state.opens, [], 'a row of the new list is not opened on the strength of the old one');
});

test('the direct route arms the advance for the re-fetch that follows it, not for a later search', () => {
  const inbox = setup('list', [['170', 'HL7'], ['171', 'HL7'], ['172', 'HL7']], false, true);
  inbox.context.rapidReviewState = true;
  inbox.context.dropAcknowledgedInboxhubItem('171', 'HL7', 1);
  inbox.reset();   // the popup's fetchInboxhubData()
  inbox.render([['170', 'HL7'], ['172', 'HL7']]);
  inbox.context.advancePendingRapidReview();
  assert.deepEqual(inbox.state.opens, ['172']);
});

test('a next-page fetch in flight is withdrawn before the boundary re-sync and asked for again after it', () => {
  // The in-flight page may have been computed before the acknowledgement committed. Appended
  // as it is, it carries the pre-shift window while every later page is post-shift, and the
  // result on ITS boundary is never fetched by anyone.
  const inbox = setup('preview', [['170', 'HL7'], ['171', 'HL7']], false, true, 2);
  inbox.startPageFetch();
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.state.aborted, 1, 'the in-flight page is withdrawn');
  assert.equal(inbox.context.isFetchingData, true, 'the hold passes from the withdrawn page to the boundary re-sync');
  assert.equal(inbox.state.boundaryRequests.length, 1);
  assert.equal(inbox.state.viewFetches, 0, 'the page is not asked for again before the boundary has been merged');
  inbox.answerBoundary('HL7:171 HL7:172');
  assert.deepEqual(inbox.shown(), ['HL7:171', 'HL7:172']);
  assert.equal(inbox.context.isFetchingData, false, 'released before the page is asked for again');
  assert.equal(inbox.state.viewFetches, 1, 'and then it is, post-shift');
});

test('no fetch in flight means nothing to withdraw and nothing to resume', () => {
  const inbox = setup('preview', [['170', 'HL7'], ['171', 'HL7']], false, true, 2);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  inbox.answerBoundary('HL7:171 HL7:172');
  assert.equal(inbox.state.aborted, 0);
  assert.equal(inbox.state.viewFetches, 0);
});

test('the legacy removeReport opener entry point re-syncs a list still paging, once for the row on screen', () => {
  const inbox = setup('list', [['170', 'HL7'], ['171', 'HL7'], ['172', 'HL7']], false, true);
  inbox.context.rapidReviewState = true;
  inbox.context.removeReport('171', 'HL7');   // the row on screen: a full re-sync is required
  inbox.context.removeReport('169', 'HL7');   // an older version in its chain: no row, no re-fetch
  assert.equal(inbox.state.fetches, 1, 'one re-fetch for the chain, not one per version');
  assert.equal(inbox.totals.totalLabsCount, 3);
  assert.equal(inbox.context.pendingRapidReviewOpen, true);
  inbox.render([['170', 'HL7'], ['172', 'HL7']]);
  inbox.context.advancePendingRapidReview();
  assert.deepEqual(inbox.state.opens, ['172'], 'the older version must not make the advance forget the successor');
});

test('the legacy removeReport opener entry point goes through the shared contract', () => {
  // A popup running an older script calls this once per id in the chain and never asks for a
  // re-fetch itself. Each call is one routing row; the row on screen goes, Rapid Review
  // advances once, and an older version with no row of its own changes nothing further.
  const inbox = setup('preview', [['170', 'HL7'], ['171', 'HL7'], ['172', 'HL7']], false, true, 2);
  inbox.context.rapidReviewState = true;
  inbox.context.removeReport('171', 'HL7');
  inbox.context.removeReport('169', 'HL7');   // an older version in 171's chain: no card, still one row
  assert.deepEqual(inbox.shown(), ['HL7:170', 'HL7:172']);
  assert.equal(inbox.totals.totalLabsCount, 3, 'two calls, two routing rows');
  assert.equal(inbox.state.boundaryRequests.length, 1, 'preview re-synced its boundary page');
  assert.equal(inbox.state.fetches, 0, 'and no full re-fetch was started for the version with no card');
  assert.equal(inbox.state.scrolledTo, '172');
  assert.equal(inbox.context.pendingRapidReviewOpen, false, 'the version with no card must not arm a stray advance');
});

test('Rapid Review on the re-fetch route opens the remembered row once a drawn page holds it', () => {
  // List mode still loading: the acknowledgement re-fetches, and the row to open arrives
  // again with the fresh list -- on whichever page it now lands.
  const inbox = setup('list', [['170', 'HL7'], ['171', 'HL7'], ['172', 'HL7']], false, true);
  inbox.context.rapidReviewState = true;
  inbox.acknowledge({ action: 'refresh', segmentID: '171', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.state.fetches, 1, 'list mode still loading re-syncs with the server');
  assert.equal(inbox.context.pendingRapidReviewOpen, true);
  // Page 1 of the fresh list has not reached the remembered row yet: keep waiting.
  inbox.render([['170', 'HL7']]);
  inbox.context.advancePendingRapidReview();
  assert.equal(inbox.state.opened, null, 'the row is on a later page, so nothing is opened yet');
  // The next page draws it.
  inbox.render([['170', 'HL7'], ['172', 'HL7']]);
  inbox.context.advancePendingRapidReview();
  assert.equal(inbox.state.opened, '172');
  assert.equal(inbox.context.pendingRapidReviewOpen, false);
});

test('Rapid Review on the re-fetch route falls back to the first row only once the whole list is loaded', () => {
  const inbox = setup('list', [['170', 'HL7'], ['171', 'HL7']], false, true);
  inbox.context.rapidReviewState = true;
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  // Another window acknowledged 171 meanwhile: the fresh list never renders it.
  inbox.render([]);
  inbox.context.advancePendingRapidReview();
  assert.equal(inbox.state.opened, null, 'pages remain, so the row may still arrive');
  inbox.context.hasMoreData = false;
  inbox.context.advancePendingRapidReview();
  assert.equal(inbox.state.opened, null, 'no row is left to fall back to');
  assert.equal(inbox.context.pendingRapidReviewOpen, false, 'and the advance is spent, not left armed');
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

test('a list still loading re-syncs while pages remain unloaded', () => {
  const inbox = setup('list', twoLabs, false, true);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.state.fetches, 1,
    'paging on from a shifted result set silently skips the result on the page boundary');
});

/*
 * PREVIEW MODE, PAGES REMAINING. This is the case an alpha15 tester reported as "preview is
 * still slow on reloads": preview pages only as the clinician scrolls, so a morning's inbox
 * is almost never fully loaded, and every acknowledgement re-fetched from page 1 and
 * re-rendered every card's iframe. Only ONE loaded page can have changed -- the last one,
 * whose window now ends one item further on -- so that page alone is re-fetched, and only
 * the card the window has never shown is inserted; every surviving card, and its iframe,
 * stays exactly where it is.
 */

test('preview mode with pages remaining re-fetches only the last loaded page, and keeps every card', () => {
  const inbox = setup('preview', [['170', 'HL7'], ['171', 'HL7'], ['172', 'HL7']], false, true, 3);
  inbox.acknowledge({ action: 'refresh', segmentID: '171', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.state.fetches, 0, 'a full re-fetch reloads every remaining card iframe');
  assert.deepEqual(inbox.shown(), ['HL7:170', 'HL7:172'], 'the acknowledged card is gone at once');
  assert.equal(inbox.state.boundaryRequests.length, 1);
  assert.match(inbox.state.boundaryRequests[0].data, /&page=2&pageSize=20$/,
    'preview increments page after each append, so the last loaded page is page - 1');
  assert.equal(inbox.totals.totalLabsCount, 4, 'the badge still moves');
});

test('the boundary re-sync inserts only the card that shifted in, beside its neighbour', () => {
  const inbox = setup('preview', [['170', 'HL7'], ['171', 'HL7'], ['172', 'HL7']], false, true, 2);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  // The server's page 1 now ends with 173, which used to open page 2.
  inbox.answerBoundary('HL7:171 HL7:172 HL7:173');
  assert.deepEqual(inbox.shown(), ['HL7:171', 'HL7:172', 'HL7:173']);
  assert.deepEqual(inbox.state.inserted, ['173'], 'the cards already on screen were not touched');
});

test('the boundary re-sync never puts back the card this window took off screen', () => {
  const inbox = setup('preview', [['170', 'HL7'], ['171', 'HL7']], false, true, 2);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  // A stale answer that still lists the acknowledged lab.
  inbox.answerBoundary('HL7:170 HL7:171 HL7:172');
  assert.deepEqual(inbox.shown(), ['HL7:171', 'HL7:172']);
});

test('the boundary re-sync adds nothing twice when a scroll fetch raced it', () => {
  const inbox = setup('preview', [['170', 'HL7'], ['171', 'HL7'], ['172', 'HL7']], false, true, 2);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  // 172 was already appended by the next page's fetch before the boundary answer landed.
  inbox.answerBoundary('HL7:171 HL7:172');
  assert.deepEqual(inbox.shown(), ['HL7:171', 'HL7:172']);
  assert.deepEqual(inbox.state.inserted, []);
});

test('a card with no rendered predecessor goes in ahead of its rendered successor', () => {
  const inbox = setup('preview', [['170', 'DOC'], ['171', 'HL7']], false, true, 2);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'DOC', clearedCount: 1 });
  // Documents lead a page; the document that shifted in has nothing rendered before it.
  inbox.answerBoundary('DOC:180 HL7:171');
  assert.deepEqual(inbox.shown(), ['DOC:180', 'HL7:171']);
});

test('the boundary re-sync adopts the end-of-results flag the page carries', () => {
  const inbox = setup('preview', [['170', 'HL7'], ['171', 'HL7']], false, true, 2);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  inbox.answerBoundary('HL7:171 <script>hasMoreData = false;</script>');
  assert.equal(inbox.context.hasMoreData, false);
});

test('a boundary re-sync that fails falls back to the full re-fetch rather than leaving a result unfetched', () => {
  const inbox = setup('preview', twoLabs, false, true, 2);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  inbox.failBoundary();
  assert.equal(inbox.state.fetches, 1);
});

test('the boundary re-sync holds the next-page fetch off until it has merged', () => {
  // The preview scroll handler starts the next page only while isFetchingData is false. A page
  // appended in the middle of the merge could leave the shifted card with no rendered
  // neighbour to sit beside, so the re-sync owns the flag for as long as it is in flight.
  const inbox = setup('preview', [['170', 'HL7'], ['171', 'HL7']], false, true, 2);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.context.isFetchingData, true, 'held while the boundary answer is outstanding');
  inbox.answerBoundary('HL7:171 HL7:172');
  assert.equal(inbox.context.isFetchingData, false, 'released once merged');
  assert.equal(inbox.state.viewFetches, 0, 'the clinician has not scrolled to the end, so nothing is asked for');
});

test('paging resumes after the merge when the clinician scrolled to the end meanwhile', () => {
  const inbox = setup('preview', [['170', 'HL7'], ['171', 'HL7']], false, true, 2);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  inbox.container.scrollTop = inbox.container.scrollHeight - inbox.container.clientHeight;   // at the bottom
  inbox.answerBoundary('HL7:171 HL7:172');
  assert.equal(inbox.state.viewFetches, 1, 'the scroll the hold swallowed is honoured now');
});

test('a second acknowledgement during a boundary re-sync supersedes it for the same page', () => {
  const inbox = setup('preview', [['170', 'HL7'], ['171', 'HL7'], ['172', 'HL7']], false, true, 2);
  inbox.startPageFetch();
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  inbox.acknowledge({ action: 'refresh', segmentID: '171', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.state.boundaryRequests.length, 2);
  assert.equal(inbox.state.boundaryRequests[0].request.aborted, true, 'the first answer would be stale');
  assert.equal(inbox.state.fetches, 0, 'an abort is not a failure and starts no full re-fetch');
  assert.equal(inbox.context.isFetchingData, true, 'the hold passes to the newer re-sync');
  inbox.answerBoundary('HL7:172 HL7:173 HL7:174');
  assert.deepEqual(inbox.shown(), ['HL7:172', 'HL7:173', 'HL7:174']);
  assert.equal(inbox.state.viewFetches, 1, 'the withdrawn page fetch is resumed once, by the re-sync that finished');
});

test('the end-of-results flag is read off the page\'s script elements, never off rendered content', () => {
  const inbox = setup('preview', [['170', 'HL7'], ['171', 'HL7']], false, true, 2);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  // A card whose rendered text happens to spell the statement must not end paging.
  inbox.answerBoundary('HL7:171 hasMoreData = false HL7:172');
  assert.equal(inbox.context.hasMoreData, true);
  assert.deepEqual(inbox.shown(), ['HL7:171', 'HL7:172']);
});

test('a failed boundary re-sync carries a waiting Rapid Review advance into the full re-fetch', () => {
  // The last loaded card was acknowledged, so the advance is waiting on the boundary page. The
  // fallback re-fetch is a reset; the advance must be re-armed for the result set it creates
  // or the reset drops it and the clinician is left without the next result.
  const inbox = setup('preview', [['170', 'HL7'], ['171', 'HL7']], false, true, 2);
  inbox.context.rapidReviewState = true;
  inbox.acknowledge({ action: 'refresh', segmentID: '171', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.context.pendingRapidReviewOpen, true);
  inbox.failBoundary();
  assert.equal(inbox.state.fetches, 1);
  assert.equal(inbox.context.pendingRapidReviewOpen, true, 'the reset the fallback made kept the advance');
  inbox.render([['170', 'HL7'], ['172', 'HL7']]);
  inbox.context.settlePendingPreviewAdvance();
  assert.deepEqual(inbox.state.scrolls, ['172']);
});

test('a boundary answer that arrives after the clinician changed the search is ignored', () => {
  const inbox = setup('preview', twoLabs, false, true, 2);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  inbox.context.inboxhubResultSetGeneration++;   // what resetDataPageCount() does
  inbox.answerBoundary('HL7:171 HL7:172');
  assert.deepEqual(inbox.shown(), ['HL7:171'], 'cards from the old query must not land in the new list');
});

test('Rapid Review in preview mode advances at once even while pages remain', () => {
  const inbox = setup('preview', [['170', 'HL7'], ['171', 'HL7'], ['172', 'HL7']], false, true, 2);
  inbox.context.rapidReviewState = true;
  inbox.acknowledge({ action: 'refresh', segmentID: '171', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.state.scrolledTo, '172');
  assert.equal(inbox.state.fetches, 0);
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
  const inbox = setup('list', twoLabs, false, true);
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  inbox.acknowledge({ action: 'refresh', segmentID: '170', labType: 'HL7', clearedCount: 1 });
  assert.equal(inbox.totals.totalLabsCount, 4);
});

test('the drop helper itself reports whether a full re-sync is required, not just the listener', () => {
  // labDisplay.jsp's no-BroadcastChannel fallback calls this function directly and re-fetches
  // on a falsy answer. If the paging condition lived only in the BroadcastChannel listener,
  // that browser would drop the item in place with pages still unloaded and reintroduce the
  // page-boundary bug. The contract is the shared guarantee, so it is asserted directly.
  const loading = setup('list', twoLabs, false, true);
  assert.equal(loading.context.dropAcknowledgedInboxhubItem('170', 'HL7', 1), false,
    'a list still loading pages, so every caller must re-sync');

  const loaded = setup('preview', twoLabs, false, false);
  assert.equal(loaded.context.dropAcknowledgedInboxhubItem('171', 'HL7', 1), true,
    'everything is loaded, so no caller needs to re-sync');

  const paged = setup('preview', twoLabs, false, true, 2);
  assert.equal(paged.context.dropAcknowledgedInboxhubItem('170', 'HL7', 1), true,
    'preview with pages remaining re-syncs its own boundary page, so no caller may re-fetch on top');
  assert.equal(paged.state.boundaryRequests.length, 1);
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
