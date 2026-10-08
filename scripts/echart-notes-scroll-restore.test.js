/**
 * Copyright (c) 2026 CARLOS Contributors
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * CARLOS EMR
 */

// Issue #3961: when the eChart pages in older notes at the top of the notes pane, the reader
// must stay on the note they were reading. The poll only pages at scrollTop 0, where browser
// scroll anchoring has nothing to hold on to, so notesLoader() does the restore itself. This
// runs the real notesLoader()/notesCaptureScrollAnchor()/notesRestoreScrollAnchor() from
// newCaseManagementView.js.jsp against a small fake pane with real scroll geometry.
// scripts/echart-playwright-checks.js asserts the same thing in a browser.
// Run with `npm run test:scripts`.
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const SOURCE = fs.readFileSync(
  path.join(__dirname, '..', 'src', 'main', 'webapp', 'js', 'newCaseManagementView.js.jsp'),
  'utf8',
);

/** Source from `function <from>(` up to (not including) `function <to>(`. */
function sliceFunctions(from, to) {
  const start = SOURCE.indexOf(`function ${from}(`);
  const end = SOURCE.indexOf(`function ${to}(`, start + 1);
  assert.ok(start >= 0 && end > start, `${from}() .. ${to}() are defined in newCaseManagementView.js.jsp`);
  const code = SOURCE.slice(start, end);
  assert.doesNotMatch(code, /<%|\$\{/, 'the extracted functions carry no JSP the test would have to fake');
  return code;
}

const CODE = sliceFunctions('notesCaptureScrollAnchor', 'notesIncrementAndLoadMore')
  + sliceFunctions('notesLoader', 'navBarLoader');

/**
 * A notes pane: a scrolling wrapper whose single content child is #encMainDiv, holding
 * notes stacked top to bottom. Each note's bounding-box top is its offset in the content
 * minus the wrapper's scrollTop, exactly what a browser reports. A height of 0 stands for
 * a display:none note: it takes no space and, like a browser, reports no client rects.
 */
function makePane(noteHeights, viewportHeight = 100) {
  const wrapper = {
    id: 'encMainDivWrapper',
    scrollTop: 0,
    clientHeight: viewportHeight,
    get scrollHeight() { return Math.max(container.height(), viewportHeight); },
    getBoundingClientRect() { return { top: 40 }; },
    contains(node) { return node === container || container.contains(node); },
  };
  const container = {
    id: 'encMainDiv',
    children: [],
    get firstElementChild() { return this.children[0] || null; },
    contains(node) { return this.children.includes(node); },
    height() { return this.children.reduce((sum, note) => sum + note.height, 0); },
    offsetOf(note) {
      let offset = 0;
      for (const child of this.children) {
        if (child === note) return offset;
        offset += child.height;
      }
      throw new Error('note is not in the container');
    },
  };
  let serial = 0;
  function note(height) {
    const element = {
      id: `nc${serial++}`,
      height,
      get nextElementSibling() {
        return container.children[container.children.indexOf(element) + 1] || null;
      },
      getClientRects() { return height > 0 ? [element.getBoundingClientRect()] : []; },
      getBoundingClientRect() {
        if (height === 0) {
          return { top: 0, bottom: 0 };
        }
        const top = wrapper.getBoundingClientRect().top + container.offsetOf(element) - wrapper.scrollTop;
        return { top, bottom: top + height };
      },
    };
    return element;
  }
  container.children = noteHeights.map(note);
  return { wrapper, container, note };
}

/**
 * Loads the notes pagination code into a sandbox whose CarlosAjax.updater hands each request
 * back to the test, so the test decides when (and whether) a response lands, and what the
 * browser does to scrollTop while inserting it.
 */
function setup(pane) {
  const requests = [];
  const elements = {
    encMainDiv: pane.container,
    encMainDivWrapper: pane.wrapper,
    notesLoading: { show() {}, hide() {} },
  };
  const context = vm.createContext({
    $: (id) => elements[id] || null,
    jQuery: () => ({ serialize: () => '' }),
    ctx: '/carlos',
    notesLoadsInFlight: 0,
    notesLoadSequence: 0,
    notesActiveLoadId: 0,
    notesLastBatchSize: -1,
    notesRetrieveOk: false,
    // Failure bookkeeping (#3609) is covered by echart-notes-pagination-retry.test.js; here it
    // only has to exist so a failed fetch can roll the offset back without reaching the DOM.
    notesOffset: 20,
    notesIncrement: 20,
    notesFailedLoads: 0,
    NOTES_MAX_FAILED_LOADS: 3,
    notesShowLoadFailure() {},
    stopNotesScrollCheck() { context.pollStopped = true; },
    pollStopped: false,
    CarlosAjax: {
      updater(target, url, options) {
        // notesLoader() names only a success container (#3609), so a failed fetch's error
        // body is never inserted into the pane; the batch itself still lands in #encMainDiv.
        requests.push({ container: target.success, url, options });
      },
    },
  });
  vm.runInContext(CODE, context);

  /**
   * Answers a request the way CarlosAjax.updater does: onSuccess, then the insert at the
   * top, then onComplete. `browserAnchoring` stands in for a browser that adjusts
   * scrollTop itself during the insert (it only can when scrollTop was not 0).
   */
  function respond(request, { heights = [], ok = true, browserAnchoring = false } = {}) {
    if (ok) {
      request.options.onSuccess();
      const inserted = heights.map(pane.note);
      const insertedHeight = heights.reduce((sum, height) => sum + height, 0);
      request.container.children = inserted.concat(request.container.children);
      if (browserAnchoring && pane.wrapper.scrollTop > 0) {
        pane.wrapper.scrollTop += insertedHeight;
      }
      context.notesLastBatchSize = heights.length;
    }
    request.options.onComplete();
  }

  return { context, requests, respond };
}

/** Where a note sits in the visible pane, in px from the pane's top edge. */
function visibleTop(pane, note) {
  return note.getBoundingClientRect().top - pane.wrapper.getBoundingClientRect().top;
}

test('an older batch paged in at the top keeps the note that was on top where the reader saw it', () => {
  const pane = makePane([60, 60, 60]);
  const { context, requests, respond } = setup(pane);
  const readerNote = pane.container.children[0];
  assert.equal(pane.wrapper.scrollTop, 0, 'the poll only pages from the very top');

  context.notesLoader(20, 20, 1);
  respond(requests[0], { heights: [50, 70, 80] });

  assert.equal(pane.container.children[3], readerNote, 'the batch went in above the reader\'s note');
  assert.equal(pane.wrapper.scrollTop, 200, 'scrolled by exactly the height that was inserted');
  assert.equal(visibleTop(pane, readerNote), 0, 'the reader\'s note did not move on screen');
  assert.equal(context.notesRetrieveOk, true);
});

test('the position is measured at insert time, so scrolling during the fetch is not undone', () => {
  const pane = makePane([60, 60, 60]);
  const { context, requests, respond } = setup(pane);
  const readerNote = pane.container.children[0];

  context.notesLoader(20, 20, 1);
  // The reader scrolls down a little while the request is in flight.
  pane.wrapper.scrollTop = 25;
  respond(requests[0], { heights: [100] });

  assert.equal(visibleTop(pane, readerNote), -25, 'the reader\'s note stays where they had scrolled it');
  assert.equal(pane.wrapper.scrollTop, 125);
});

test('when the browser already anchored the scroll, the restore adds nothing on top of it', () => {
  const pane = makePane([60, 60, 60]);
  const { context, requests, respond } = setup(pane);
  const readerNote = pane.container.children[0];

  context.notesLoader(20, 20, 1);
  pane.wrapper.scrollTop = 30;
  respond(requests[0], { heights: [120], browserAnchoring: true });

  assert.equal(pane.wrapper.scrollTop, 150, 'scrolled once, by the browser, not twice');
  assert.equal(visibleTop(pane, readerNote), -30);
});

test('a hidden note on top is skipped, so the first rendered note is the one held in place', () => {
  // ChartNotesAjax.jsp renders notes hidden by encounter.hide_* as display:none; such a note
  // has no box and never moves, so anchoring to it would leave the visible note jumping.
  const pane = makePane([0, 60, 60]);
  const { context, requests, respond } = setup(pane);
  const readerNote = pane.container.children[1];

  context.notesLoader(20, 20, 1);
  respond(requests[0], { heights: [0, 150] });

  assert.equal(pane.wrapper.scrollTop, 150);
  assert.equal(visibleTop(pane, readerNote), 0);
});

test('the anchor is the first note still in view, not one already scrolled past', () => {
  const pane = makePane([60, 60, 60]);
  const { context, requests, respond } = setup(pane);
  const readerNote = pane.container.children[1];

  context.notesLoader(20, 20, 1);
  // Scrolled during the fetch so the first note is wholly above the pane.
  pane.wrapper.scrollTop = 70;
  respond(requests[0], { heights: [40] });

  assert.equal(visibleTop(pane, readerNote), -10, 'the note the reader is looking at stays put');
  assert.equal(pane.wrapper.scrollTop, 110);
});

test('a chart whose notes are all hidden has nothing to anchor to and does not scroll', () => {
  const pane = makePane([0, 0]);
  const { context, requests, respond } = setup(pane);

  context.notesLoader(20, 20, 1);
  respond(requests[0], { heights: [60] });

  assert.equal(pane.wrapper.scrollTop, 0);
});

test('the initial load still scrolls to the newest notes at the bottom', () => {
  const pane = makePane([]);
  const { context, requests, respond } = setup(pane);

  context.notesLoader(0, 20, 1);
  respond(requests[0], { heights: [80, 80, 80] });

  assert.equal(pane.wrapper.scrollTop, pane.wrapper.scrollHeight);
});

test('an empty final batch leaves the pane where it was and stops the poll', () => {
  const pane = makePane([60, 60, 60]);
  const { context, requests, respond } = setup(pane);

  context.notesLoader(40, 20, 1);
  respond(requests[0], { heights: [] });

  assert.equal(pane.wrapper.scrollTop, 0);
  assert.equal(context.notesRetrieveOk, false);
  assert.equal(context.pollStopped, true);
});

test('a failed request inserts nothing to restore past and does not scroll', () => {
  const pane = makePane([60, 60, 60]);
  const { context, requests, respond } = setup(pane);

  context.notesLoader(20, 20, 1);
  respond(requests[0], { ok: false });

  assert.equal(pane.wrapper.scrollTop, 0);
});

test('an empty chart has no note to anchor to and pages in without error', () => {
  const pane = makePane([]);
  const { context, requests, respond } = setup(pane);

  context.notesLoader(20, 20, 1);
  respond(requests[0], { heights: [60] });

  assert.equal(pane.wrapper.scrollTop, 0);
});

test('a load superseded by a newer chart render does not scroll the newer render', () => {
  const pane = makePane([60, 60, 60]);
  const { context, requests, respond } = setup(pane);

  context.notesLoader(20, 20, 1);
  // A filter or save reload starts a fresh initial load before the page-in lands.
  context.notesLoader(0, 20, 1);
  pane.wrapper.scrollTop = 0;
  respond(requests[0], { heights: [100] });

  assert.equal(pane.wrapper.scrollTop, 0, 'the superseded page-in must not move the pane');
});

test('a batch that lands in a container the chart has since replaced is not followed', () => {
  const pane = makePane([60, 60, 60]);
  const { context, requests, respond } = setup(pane);

  context.notesLoader(20, 20, 1);
  // The container this request targets is detached; the pane now holds a new one.
  const replaced = makePane([60]).container;
  const stale = requests[0].container;
  pane.wrapper.contains = (node) => node === replaced || replaced.contains(node);
  respond({ ...requests[0], container: stale }, { heights: [100] });

  assert.equal(pane.wrapper.scrollTop, 0);
});
