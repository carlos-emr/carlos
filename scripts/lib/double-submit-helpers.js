/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Shared plumbing for the double-submit (idempotency of create actions) checks.
 *
 * Every helper drives the BROWSER's own event path (real mouse/keyboard events on the
 * element the user uses) and reads rows back through the owned-row query the caller
 * supplies. Nothing here counts global rows: callers scope every query to their
 * marker / owned patient.
 */
const h = require('./playwright-harness');

/** Rapid-activation modes a hurried user produces. */
const ALL_MODES = [
  { key: 'dblclick', tag: 'DC', label: 'dblclick()' },
  { key: 'twoClicks', tag: 'TC', label: 'two back-to-back click({noWaitAfter})' },
  { key: 'doubleEnter', tag: 'EN', label: 'double Enter in a text field' },
  // Impatient re-click while the (held-back) response is still pending: the server has already
  // processed request one, the browser has not shown the result yet.
  { key: 'slowResubmit', tag: 'SR', label: 'second click 500 ms later while the response is slow' },
];

// DS_ONLY=slowResubmit,dblclick narrows a debugging run; unset runs every mode.
const MODES = process.env.DS_ONLY ? ALL_MODES.filter((mode) => process.env.DS_ONLY.split(',').includes(mode.key)) : ALL_MODES;

// POST replay: submit once, let the page settle, then reload it. A page that renders its POST result
// directly (no redirect-after-POST) re-sends the form on reload; a PRG page does not. Only meaningful where
// the submit navigates the page it ran in, so checks opt in with MODES_REPLAY.
const REPLAY = { key: 'replay', tag: 'RP', label: 'submit once then reload the result page (POST replay)' };
const MODES_REPLAY = process.env.DS_ONLY && !process.env.DS_ONLY.split(',').includes('replay') ? MODES : [...MODES, REPLAY];

const GONE = /closed|detached|destroyed|navigat|Timeout|timeout|not attached|Target page/i;

/** Pause for a human-scale instant. */
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

/**
 * Activate `target` twice, as fast as the browser allows. `mode` is one of MODES[].key.
 * `textField` (a locator) is required for doubleEnter. A page that closes or navigates
 * after the first activation makes the second one throw; that is the guarded outcome
 * and is swallowed, any other error is rethrown.
 */
async function rapid(mode, target, { textField } = {}) {
  const page = target.page();
  // Playwright's locator.click() waits for a pending navigation to settle before it acts, so a
  // second locator click can never land while the first submit is in flight. A raw
  // page.mouse/page.keyboard event is delivered like a user's physical second click and is what
  // reproduces the real double submit.
  const rawSecondClick = async (box) => {
    if (!box) return;
    await page.mouse.click(box.x + box.width / 2, box.y + box.height / 2).catch((error) => {
      if (!GONE.test(error.message)) throw error;
    });
  };
  try {
    if (mode === 'dblclick') {
      await target.dblclick({ noWaitAfter: true, timeout: 8000 });
    } else if (mode === 'twoClicks') {
      const box = await target.boundingBox();
      await target.click({ noWaitAfter: true, timeout: 8000 });
      await rawSecondClick(box);
    } else if (mode === 'slowResubmit') {
      const box = await target.boundingBox();
      await target.click({ noWaitAfter: true, timeout: 8000 });
      await sleep(500);
      await rawSecondClick(box);
    } else if (mode === 'replay') {
      await target.click({ noWaitAfter: true, timeout: 8000 });
      await sleep(2500);
      await page.reload({ waitUntil: 'domcontentloaded', timeout: 20000 });
      await sleep(800);
    } else if (mode === 'doubleEnter') {
      h.assert(textField, 'doubleEnter needs the text field to press Enter in');
      await textField.focus();
      await textField.press('Enter', { noWaitAfter: true, timeout: 8000 });
      await page.keyboard.press('Enter').catch((error) => {
        if (!GONE.test(error.message)) throw error;
      });
    } else {
      throw new Error(`unknown mode ${mode}`);
    }
  } catch (error) {
    if (!GONE.test(error.message)) throw error;
  }
}

/**
 * Wait until `query` (a COUNT(*) scoped to owned rows) is at least `min`, then let the
 * system go quiet for `quietMs` and read the FINAL count, so a late second insert is seen.
 * Returns the final count as a number (0 when `min` is never reached within timeoutMs).
 */
async function settledCount(sql, query, { min = 1, quietMs = 3000, timeoutMs = 20000 } = {}) {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline && Number(sql.value(query)) < min) await sleep(200);
  await sleep(quietMs);
  return Number(sql.value(query));
}

/**
 * Hold the RESPONSE of every request whose URL matches `pattern` for `ms` after the server has
 * already processed it (the request is sent from the harness side with route.fetch()). Models a
 * slow network / loaded server: the clinician sees no result yet and clicks again. Redirects are
 * passed through to the browser untouched. Returns an async disarm function.
 */
async function armSlowServer(context, pattern, ms = 1800) {
  const handler = async (route) => {
    if (route.request().method() !== 'POST') return route.fallback();
    try {
      if (process.env.DS_DEBUG) console.log('    [slow] intercept', route.request().url());
      const response = await route.fetch({ maxRedirects: 0 });
      if (process.env.DS_DEBUG) console.log('    [slow] fetched', Date.now()%100000);
      await sleep(ms);
      if (process.env.DS_DEBUG) console.log('    [slow] fulfilling', Date.now()%100000);
      await route.fulfill({ response });
    } catch (error) {
      // the browser abandoned this navigation: that is the second click winning
      if (process.env.DS_DEBUG) console.log('    [slow] handler error', String(error.message).split('\n')[0]);
    }
  };
  await context.route(pattern, handler);
  return async () => { await context.unroute(pattern, handler).catch(() => {}); };
}

/** Position in the recorder before a rapid activation (see forgiveAbortedSecondRequest). */
function recorderMark(recorder) {
  return { failures: recorder.requestFailures.length, issues: recorder.consoleIssues.length };
}

/**
 * A fetch()-based save whose popup closes on the FIRST response aborts the SECOND in-flight request
 * (net::ERR_ABORTED, "Failed to fetch"). That abort is the double submit's own symptom, already counted
 * by the owned-row assertion, so it is consumed here -- narrowly: only ERR_ABORTED failures matching
 * `pattern` and only console errors that say the fetch failed, both recorded since `since`.
 */
function forgiveAbortedSecondRequest(recorder, since, pattern) {
  for (let i = recorder.requestFailures.length - 1; i >= since.failures; i--) {
    const failure = recorder.requestFailures[i];
    if (/ERR_ABORTED/.test(failure.errorText || '') && pattern.test(failure.url || '')) recorder.requestFailures.splice(i, 1);
  }
  for (let i = recorder.consoleIssues.length - 1; i >= since.issues; i--) {
    if (/Failed to fetch|ERR_ABORTED/.test(recorder.consoleIssues[i].text || '')) recorder.consoleIssues.splice(i, 1);
  }
}

/** Count (and remember) POSTs whose pathname matches `pattern` on a page or context. */
function watchPosts(emitter, pattern) {
  const seen = [];
  const listener = (request) => {
    try {
      if (request.method() === 'POST' && pattern.test(new URL(request.url()).pathname)) seen.push(Date.now());
    } catch (error) { /* ignore unparsable urls */ }
  };
  emitter.on('request', listener);
  return { seen, stop: () => emitter.off('request', listener) };
}

/**
 * Collect duplicate verdicts across modes so a check proves EVERY mode before it fails.
 * verdict(label, count, expected) records; finish() throws one error listing duplicates.
 */
function verdicts(name) {
  const rows = [];
  return {
    record(label, count, { exactly = 1, atMost } = {}) {
      const ok = atMost === undefined ? count === exactly : count <= atMost;
      rows.push({ label, count, ok });
      console.log(`  ${ok ? 'OK ' : 'DUP'} ${name}: ${label} -> ${count} owned row(s)`);
      return ok;
    },
    finish() {
      const bad = rows.filter((row) => !row.ok);
      h.assert(bad.length === 0,
        `${name}: double submit created duplicates or lost the write: ${bad.map((r) => `${r.label}=${r.count}`).join('; ')}`);
    },
  };
}

module.exports = { MODES, MODES_REPLAY, rapid, settledCount, watchPosts, verdicts, sleep, armSlowServer, recorderMark, forgiveAbortedSecondRequest };
