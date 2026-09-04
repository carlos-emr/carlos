/**
 * Unit tests for src/main/webapp/share/javascript/csrfTokenFetch.js.
 *
 * The helper runs in a browser, so it is loaded into a vm context with just
 * enough of window/document/fetch/console to exercise it. Run with:
 *   node --test scripts/csrf-token-fetch.test.js
 *
 * What these pin: a navigation cancels an in-flight fetch and the rejection
 * arrives as a bare "TypeError: Failed to fetch" — the same message a real
 * network failure produces. The helper must stay quiet for the first and warn
 * for the second, and must reject either way, because callers (efmformmanager's
 * csrfToken(), the csrf-token.jspf bootstrap) branch on the rejection.
 */

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const SOURCE = fs.readFileSync(
  path.join(__dirname, '..', 'src', 'main', 'webapp', 'share', 'javascript', 'csrfTokenFetch.js'),
  'utf8',
);

const TOKEN_SCRIPT = 'var masterTokenValue = "TOKEN-VALUE-1234";';

/** Builds a fresh browser-ish context around the helper for one test. */
function loadHelper({ fetchImpl, inputCount = 1 }) {
  const warnings = [];
  const unloadListeners = { pagehide: [], beforeunload: [], pageshow: [] };
  const inputs = Array.from({ length: inputCount }, () => ({ value: '' }));

  const context = {
    fetch: fetchImpl,
    setTimeout,
    clearTimeout,
    console: { warn: (...args) => warnings.push(args.map(String).join(' ')) },
    document: {
      querySelectorAll: () => inputs,
    },
  };
  context.window = {
    addEventListener: (type, handler) => {
      if (unloadListeners[type]) {
        unloadListeners[type].push(handler);
      }
    },
  };
  vm.createContext(context);
  vm.runInContext(SOURCE, context);

  return {
    context,
    warnings,
    inputs,
    fetchCsrfToken: (ctxPath) => vm.runInContext('fetchCsrfToken', context)(ctxPath),
    /** Simulates the document starting to go away. */
    fireUnload: () => unloadListeners.pagehide.forEach((handler) => handler()),
    /** Simulates the document being restored from the back/forward cache. */
    fireRestore: () => unloadListeners.pageshow.forEach((handler) => handler()),
  };
}

/** Lets any setTimeout(..., 0) the helper queued actually run. */
function drainTimers() {
  return new Promise((resolve) => setTimeout(resolve, 5));
}

test('populates every CSRF-TOKEN input and stays silent on success', async () => {
  const helper = loadHelper({
    fetchImpl: async () => ({ ok: true, text: async () => TOKEN_SCRIPT }),
    inputCount: 2,
  });

  await helper.fetchCsrfToken('/carlos');
  await drainTimers();

  assert.deepEqual(helper.inputs.map((input) => input.value), ['TOKEN-VALUE-1234', 'TOKEN-VALUE-1234']);
  assert.deepEqual(helper.warnings, []);
});

test('warns and rejects when the server rejects the token request', async () => {
  const helper = loadHelper({ fetchImpl: async () => ({ ok: false, status: 500 }) });

  await assert.rejects(helper.fetchCsrfToken('/carlos'), /status 500/);
  await drainTimers();

  assert.equal(helper.warnings.length, 1);
  assert.match(helper.warnings[0], /CSRF token fetch failed/);
});

test('warns and rejects on a genuine network failure', async () => {
  // Same message a cancelled request produces, so the helper cannot tell these
  // apart by the error alone — only by whether the page is going away.
  const helper = loadHelper({
    fetchImpl: async () => { throw new TypeError('Failed to fetch'); },
  });

  await assert.rejects(helper.fetchCsrfToken('/carlos'), /Failed to fetch/);
  await drainTimers();

  assert.equal(helper.warnings.length, 1);
});

test('rejects without warning when the page unloads before the request fails', async () => {
  let helper;
  helper = loadHelper({
    fetchImpl: async () => {
      helper.fireUnload();
      throw new TypeError('Failed to fetch');
    },
  });

  await assert.rejects(helper.fetchCsrfToken('/carlos'), /Failed to fetch/);
  await drainTimers();

  assert.deepEqual(helper.warnings, []);
});

test('rejects without warning when the unload event arrives after the rejection', async () => {
  // The rejection can be delivered before pagehide runs, which is why the
  // warning is deferred and the flag re-checked rather than read once.
  const helper = loadHelper({
    fetchImpl: async () => { throw new TypeError('Failed to fetch'); },
  });

  await assert.rejects(helper.fetchCsrfToken('/carlos'), /Failed to fetch/);
  helper.fireUnload();
  await drainTimers();

  assert.deepEqual(helper.warnings, []);
});

test('warns again on a real failure after a back/forward-cache restore', async () => {
  // Entering the bfcache fires pagehide without destroying the document, and
  // coming back does not re-run this script. Without the pageshow reset the
  // flag would stay set and silence every genuine failure on the restored page.
  const helper = loadHelper({
    fetchImpl: async () => { throw new TypeError('Failed to fetch'); },
  });

  helper.fireUnload();
  await assert.rejects(helper.fetchCsrfToken('/carlos'), /Failed to fetch/);
  await drainTimers();
  assert.deepEqual(helper.warnings, [], 'suppressed while the page is away');

  helper.fireRestore();
  await assert.rejects(helper.fetchCsrfToken('/carlos'), /Failed to fetch/);
  await drainTimers();
  assert.equal(helper.warnings.length, 1, 'reported again once the page is back');
});
