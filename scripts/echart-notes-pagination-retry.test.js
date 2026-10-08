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

// Issue #3609: one pagination fetch that rendered nothing (a 500, a CSRF rejection, a login
// redirect, a dropped connection) used to be treated exactly like the server saying "no more
// notes": the scroll poll was cleared for the rest of the chart session and no older note could
// be paged in again, silently. The notes pagination code must tell the two apart: nothing
// rendered (notesLastBatchSize left at -1) rolls the offset back, keeps the poll armed so the
// next scroll-to-top retries the same batch, shows an indicator with a Retry link, and gives up
// only after a run of consecutive failures so a persistent error cannot bring back the 1 req/s
// loop #3589 fixed. A zero-note batch is still the end of the chart.
//
// This runs the real pagination state and functions from newCaseManagementView.js.jsp in a
// sandbox whose CarlosAjax.updater hands every request back to the test.
// scripts/echart-notes-pagination-retry-playwright-checks.js asserts the same in a browser.
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

/** The whole pagination block: its state declarations and every function, up to navBarLoader(). */
function paginationBlock() {
  const start = SOURCE.indexOf('// --- Notes pagination state ---');
  const end = SOURCE.indexOf('function navBarLoader(', start + 1);
  assert.ok(start >= 0 && end > start, 'the notes pagination block is where the test expects it');
  const code = SOURCE.slice(start, end);
  assert.doesNotMatch(code, /<%|\$\{/, 'the pagination block carries no JSP the test would have to fake');
  return code;
}

const CODE = paginationBlock();
const MAX_FAILED_LOADS = 3;

/**
 * Loads the pagination block into a sandbox. The notes pane is tall enough to scroll and sits
 * at scrollTop 0, the position from which the poll pages; it holds no rendered note, so the
 * scroll-anchor code (covered by echart-notes-scroll-restore.test.js) has nothing to do.
 */
function setup() {
  const requests = [];
  const timers = { started: 0, cleared: 0, handle: null };
  const indicator = { visible: false, show() { this.visible = true; }, hide() { this.visible = false; } };
  const throbber = { visible: false, show() { this.visible = true; }, hide() { this.visible = false; } };
  const container = {
    id: 'encMainDiv',
    children: [],
    get firstChild() { return this.children[0] || null; },
    get firstElementChild() { return this.children[0] || null; },
    contains(node) { return this.children.includes(node); },
    removeChild(node) { this.children.splice(this.children.indexOf(node), 1); },
  };
  const wrapper = {
    id: 'encMainDivWrapper',
    scrollTop: 0,
    scrollHeight: 500,
    getHeight() { return 100; },
    getBoundingClientRect() { return { top: 0 }; },
    contains(node) { return node === container; },
  };
  const elements = {
    encMainDiv: container,
    encMainDivWrapper: wrapper,
    notesLoading: throbber,
    notesLoadFailed: indicator,
  };
  const context = vm.createContext({
    $: (id) => elements[id] || null,
    jQuery: () => ({ serialize: () => '' }),
    ctx: '/carlos',
    demographicNo: 1,
    setInterval(fn, ms) {
      assert.equal(typeof fn, 'function', 'the poll is armed with a function, not an eval string');
      assert.equal(ms, 1000);
      timers.started += 1;
      timers.handle = { fn, id: timers.started };
      return timers.handle;
    },
    clearInterval(handle) {
      if (handle) timers.cleared += 1;
      timers.handle = null;
    },
    CarlosAjax: {
      updater(target, url, options) {
        const params = new URLSearchParams(options.postBody);
        requests.push({
          target, url, options,
          offset: Number(params.get('offset')),
          numToReturn: Number(params.get('numToReturn')),
        });
      },
    },
  });
  vm.runInContext(CODE, context);

  let serial = 0;
  /**
   * A node in the pane. It reports no client rects, so the scroll-anchor code (covered by
   * echart-notes-scroll-restore.test.js) skips it as it would a display:none note.
   */
  function node(id, note) {
    const element = {
      id, note,
      getClientRects() { return []; },
      get nextElementSibling() { return container.children[container.children.indexOf(element) + 1] || null; },
    };
    return element;
  }
  /** What CarlosAjax.updater does with a 2xx body: insert it at the top of the container. */
  function insertAtTop(nodes) {
    container.children = nodes.concat(container.children);
  }

  /** The server rendered `notes` notes: onSuccess, the insert, the fragment scripts, then onComplete. */
  function succeed(request, notes) {
    request.options.onSuccess();
    const batch = [];
    for (let i = 0; i < notes; i += 1) batch.push(node(`note${serial++}`, true));
    insertAtTop(batch);
    context.notesLastBatchSize = notes;
    request.options.onComplete();
  }

  /** Nothing rendered: a non-2xx (error page, CSRF rejection, 401), or a dropped connection. */
  function fail(request) {
    request.options.onComplete();
  }

  /** A 2xx that was not the fragment (expired.jsp, domain-error.jsp): inserted, but no scripts ran. */
  function failWithPage(request) {
    request.options.onSuccess();
    const page = node(`page${serial++}`, false);
    insertAtTop([page]);
    request.options.onComplete();
    return page;
  }

  /** One tick of the scroll poll with the reader at the top of a scrollable pane. */
  function pollTick() {
    wrapper.scrollTop = 0;
    context.notesIncrementAndLoadMore();
  }

  const pollArmed = () => timers.handle !== null;

  return { context, requests, timers, indicator, throbber, wrapper, container, succeed, fail, failWithPage, pollTick, pollArmed };
}

/** A chart that rendered its first page and armed the poll, as ChartNotes.jsp does on load. */
function chartWithFirstPage() {
  const s = setup();
  s.context.notesLoadFirstPage();
  assert.equal(s.requests[0].offset, 0);
  s.succeed(s.requests[0], 20);
  assert.equal(s.context.notesOffset, 0);
  assert.equal(s.context.notesRetrieveOk, true);
  assert.ok(s.pollArmed());
  return s;
}

test('a fetch that rendered nothing rolls the offset back and keeps the poll armed', () => {
  const s = chartWithFirstPage();

  s.pollTick();
  assert.equal(s.requests.length, 2);
  assert.equal(s.requests[1].offset, 20);
  s.fail(s.requests[1]);

  assert.equal(s.context.notesOffset, 0, 'the batch that was not rendered is asked for again next time');
  assert.equal(s.context.notesRetrieveOk, true, 'one failure does not read as the end of the chart');
  assert.ok(s.pollArmed(), 'the scroll poll is not cleared');
  assert.equal(s.context.notesFailedLoads, 1);
  assert.equal(s.indicator.visible, true, 'the clinician is told older notes could not be loaded');
  assert.equal(s.throbber.visible, false);
});

test('the next scroll-to-top retries the same batch, and its success clears the indicator', () => {
  const s = chartWithFirstPage();
  s.pollTick();
  s.fail(s.requests[1]);

  s.pollTick();
  assert.equal(s.requests.length, 3);
  assert.equal(s.requests[2].offset, 20, 'the retry asks for the batch that failed, not the one after it');
  assert.equal(s.requests[2].numToReturn, 20);
  s.succeed(s.requests[2], 20);

  assert.equal(s.context.notesOffset, 20);
  assert.equal(s.context.notesRetrieveOk, true);
  assert.equal(s.context.notesFailedLoads, 0, 'a rendered batch ends the failure streak');
  assert.equal(s.indicator.visible, false);

  s.pollTick();
  assert.equal(s.requests[3].offset, 40, 'paging carries on from where the retry landed');
});

test('a batch the server rendered empty is still the end of the chart', () => {
  const s = chartWithFirstPage();
  s.pollTick();
  s.succeed(s.requests[1], 0);

  assert.equal(s.context.notesRetrieveOk, false);
  assert.ok(!s.pollArmed(), 'the poll stops instead of requesting ever-higher offsets');
  assert.equal(s.context.notesOffset, 20, 'an empty batch is not rolled back');
  assert.equal(s.indicator.visible, false, 'an exhausted chart is not an error');
  assert.equal(s.context.notesFailedLoads, 0);
});

test('a run of consecutive failures stops the poll so a persistent error cannot loop', () => {
  const s = chartWithFirstPage();
  for (let i = 1; i <= MAX_FAILED_LOADS; i += 1) {
    s.pollTick();
    assert.equal(s.requests.length, 1 + i, `failure ${i} was requested`);
    assert.equal(s.requests[i].offset, 20, 'every retry asks for the same batch');
    s.fail(s.requests[i]);
  }

  assert.equal(s.context.notesFailedLoads, MAX_FAILED_LOADS);
  assert.equal(s.context.notesRetrieveOk, false);
  assert.ok(!s.pollArmed(), 'the poll is cleared after the cap');
  assert.equal(s.indicator.visible, true, 'the indicator stays up so the clinician can retry by hand');
  assert.equal(s.context.notesOffset, 0, 'the offset still points at the batch that never rendered');

  s.pollTick();
  assert.equal(s.requests.length, 1 + MAX_FAILED_LOADS, 'a further tick issues no request');
});

test('a rendered batch between failures resets the streak, so scattered failures never hit the cap', () => {
  const s = chartWithFirstPage();
  let next = 1;
  for (let round = 0; round < MAX_FAILED_LOADS; round += 1) {
    s.pollTick();
    s.fail(s.requests[next]);
    next += 1;
    s.pollTick();
    s.succeed(s.requests[next], 20);
    next += 1;
  }
  s.pollTick();
  s.fail(s.requests[next]);

  assert.equal(s.context.notesFailedLoads, 1);
  assert.equal(s.context.notesRetrieveOk, true);
  assert.ok(s.pollArmed());
});

test('Retry re-arms the poll, requests the batch that failed and clears the streak', () => {
  const s = chartWithFirstPage();
  for (let i = 1; i <= MAX_FAILED_LOADS; i += 1) {
    s.pollTick();
    s.fail(s.requests[i]);
  }
  assert.ok(!s.pollArmed());
  const before = s.requests.length;

  s.context.notesRetryLoad();

  assert.equal(s.requests.length, before + 1, 'Retry issues one request without waiting for a scroll');
  assert.equal(s.requests[before].offset, 20);
  assert.equal(s.requests[before].numToReturn, 20);
  assert.equal(s.context.notesFailedLoads, 0);
  assert.ok(s.pollArmed(), 'the poll runs again so paging continues after the retry');
  assert.equal(s.indicator.visible, false, 'the indicator is taken down while the retry is in flight');
  assert.equal(s.throbber.visible, true);

  s.succeed(s.requests[before], 20);
  assert.equal(s.context.notesOffset, 20);
  assert.equal(s.context.notesRetrieveOk, true);
  s.pollTick();
  assert.equal(s.requests[before + 1].offset, 40);
});

test('Retry while a fetch is still in flight does nothing', () => {
  const s = chartWithFirstPage();
  s.pollTick();
  const inFlight = s.requests.length;

  s.context.notesRetryLoad();

  assert.equal(s.requests.length, inFlight, 'no second request is stacked behind the pending one');
  assert.equal(s.context.notesOffset, 20, 'the pending request\'s offset is left alone');
});

test('Retry after a failed initial load asks for the first page again', () => {
  const s = setup();
  s.context.notesLoadFirstPage();
  s.fail(s.requests[0]);
  assert.equal(s.indicator.visible, true);

  s.context.notesRetryLoad();

  assert.equal(s.requests.length, 2);
  assert.equal(s.requests[1].offset, 0, 'the first page, not the one after it');
  s.succeed(s.requests[1], 20);
  assert.equal(s.context.notesOffset, 0);
  assert.equal(s.context.notesRetrieveOk, true);
  assert.equal(s.indicator.visible, false);
});

test('a failed Load All can be tried again and the poll can still page', () => {
  const s = chartWithFirstPage();

  s.context.notesLoadAll();
  assert.equal(s.requests[1].offset, 20);
  assert.ok(s.requests[1].numToReturn >= 1000000, 'Load All asks for the whole chart');
  s.fail(s.requests[1]);

  assert.equal(s.context.notesOffset, 0, 'the offset is not left parked past MAXNOTES');
  assert.equal(s.context.notesRetrieveOk, true);
  assert.equal(s.indicator.visible, true);

  s.context.notesLoadAll();
  assert.equal(s.requests[2].offset, 20, 'the second click asks for the same batch');
  s.succeed(s.requests[2], 25);
  assert.equal(s.indicator.visible, false);
  s.pollTick();
  assert.equal(s.requests.length, 3, 'after a full load the poll does not re-request the inserted notes');
});

test('a 200 page that is not the fragment is taken back out of the pane and counts as a failure', () => {
  // expired.jsp and domain-error.jsp answer 200; CarlosAjax.updater has already inserted them
  // above the notes by the time the loader learns no fragment script ran.
  const s = chartWithFirstPage();
  const notesBefore = s.container.children.slice();
  s.pollTick();
  const page = s.failWithPage(s.requests[1]);

  assert.ok(!s.container.contains(page), 'the session-expired page is not left above the notes');
  assert.deepEqual(s.container.children, notesBefore, 'the notes already shown are untouched');
  assert.equal(s.context.notesFailedLoads, 1);
  assert.equal(s.indicator.visible, true);
  assert.equal(s.context.notesOffset, 0, 'rolled back so the batch is asked for again');
  assert.ok(s.pollArmed());
});

test('a 200 page that is not the fragment on an empty pane leaves the pane empty', () => {
  const s = setup();
  s.context.notesLoadFirstPage();
  s.failWithPage(s.requests[0]);

  assert.deepEqual(s.container.children, []);
  assert.equal(s.indicator.visible, true);
});

test('a rendered batch stays in the pane', () => {
  const s = chartWithFirstPage();
  s.pollTick();
  s.succeed(s.requests[1], 3);
  assert.equal(s.container.children.length, 23);
});

test('a superseded load that rendered first cannot make the newer load\'s failure look like a success', () => {
  const s = chartWithFirstPage();
  s.pollTick();
  const stale = s.requests[1];
  // A save reload starts a fresh initial load while the page-in is still pending.
  s.context.notesLoadFirstPage();
  const fresh = s.requests[2];

  // The older response lands first: its fragment script writes its count, then its
  // completion is skipped as superseded.
  s.succeed(stale, 20);
  // The newer load then fails.
  s.fail(fresh);

  assert.equal(s.context.notesFailedLoads, 1, 'the failure is counted, not read as the stale batch');
  assert.equal(s.indicator.visible, true);
  assert.equal(s.context.notesOffset, -20, 'rolled back so the next request asks for the first page again');
});

test('Retry after a failed Load All asks for the whole chart again and parks the offset', () => {
  const s = chartWithFirstPage();
  s.context.notesLoadAll();
  s.fail(s.requests[1]);
  assert.equal(s.context.notesOffset, 0);

  s.context.notesRetryLoad();
  assert.equal(s.requests[2].offset, 20);
  assert.ok(s.requests[2].numToReturn >= 1000000, 'retried as a Load All, not as one page');
  s.succeed(s.requests[2], 25);
  assert.equal(s.indicator.visible, false);
  s.pollTick();
  assert.equal(s.requests.length, 3, 'the poll does not re-request the notes the full load inserted');

  // A later ordinary failure is retried as one page again.
  s.context.notesLoadFirstPage();
  s.succeed(s.requests[3], 20);
  s.pollTick();
  s.fail(s.requests[4]);
  s.context.notesRetryLoad();
  assert.equal(s.requests[5].numToReturn, 20);
});

test('a superseded load that failed leaves the newer render\'s state alone', () => {
  const s = chartWithFirstPage();
  s.pollTick();
  const stale = s.requests[1];
  // A filter or save reload starts a fresh initial load before the page-in lands.
  s.context.notesLoader(0, 20, 1);
  const fresh = s.requests[2];

  s.fail(stale);
  assert.equal(s.context.notesOffset, 20, 'the stale failure did not roll anything back');
  assert.equal(s.context.notesFailedLoads, 0);
  assert.equal(s.indicator.visible, false);

  s.succeed(fresh, 20);
  assert.equal(s.context.notesRetrieveOk, true);
});

test('the body of a failed response is never inserted into the notes pane', () => {
  const s = chartWithFirstPage();
  s.pollTick();
  const { target } = s.requests[1];

  assert.equal(target.success, s.context.$('encMainDiv'), 'a rendered batch goes into #encMainDiv');
  assert.equal(target.failure, undefined,
    'an error page, a CSRF rejection text or a login page must not land at the top of the chart');
});

test('a fragment re-render after paging starts over from the first page, not from where paging had reached', () => {
  // A filter apply/reset, a note save or the Full/Quick chart toggle re-renders ChartNotes.jsp
  // into #notCPP and runs its ready handler again; the pagination state is in the page script.
  const s = chartWithFirstPage();
  s.pollTick();
  s.succeed(s.requests[1], 20);
  assert.equal(s.context.notesOffset, 20);

  s.context.notesLoadFirstPage();
  assert.equal(s.requests[2].offset, 0, 'the re-render asks for the newest page');
  s.succeed(s.requests[2], 20);
  s.pollTick();
  assert.equal(s.requests[3].offset, 20, 'paging resumes with the second page, not the third');
  assert.ok(s.pollArmed());
});

test('a fragment re-render clears a failure streak and the indicator', () => {
  const s = chartWithFirstPage();
  for (let i = 1; i <= MAX_FAILED_LOADS; i += 1) {
    s.pollTick();
    s.fail(s.requests[i]);
  }
  assert.ok(!s.pollArmed());

  s.context.notesLoadFirstPage();
  assert.equal(s.context.notesFailedLoads, 0);
  assert.equal(s.indicator.visible, false);
  assert.ok(s.pollArmed(), 'the fresh fragment arms its own poll');
  assert.equal(s.requests[s.requests.length - 1].offset, 0);
});

test('stopping and starting the poll is idempotent and never leaks a timer', () => {
  const s = setup();
  s.context.startNotesScrollCheck();
  s.context.startNotesScrollCheck();
  assert.equal(s.timers.started, 2);
  assert.equal(s.timers.cleared, 1, 'the second start cleared the first timer before arming its own');
  s.context.stopNotesScrollCheck();
  s.context.stopNotesScrollCheck();
  assert.equal(s.timers.cleared, 2);
  assert.ok(!s.pollArmed());
});
