/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Shared plumbing for the get-reject-* checks: "does a state-changing route refuse
 * the same request when it arrives as a GET?"
 *
 * WHY. CSRFGuard validates tokens on POST/PUT/DELETE/PATCH only, and the session
 * cookie is SameSite=Lax, so a top-level GET navigation from another site (a link
 * in an e-mail, a window.open) or ANY same-origin GET (an <img> or link inside a
 * message, eForm or note) arrives with the clinician's session and no token check.
 * A route that writes on a GET is therefore a one-click CSRF. HttpMethodGuardFilter
 * refuses GET only for action names with a mutator prefix (add/delete/save/...),
 * a short explicit list, or an EXACT `method=` value it knows; everything else
 * relies on a POST check inside the action.
 *
 * HOW A PROBE IS BUILT. The positive request is first produced by the real page
 * (a click), captured with page.waitForRequest, and its parameters (query string
 * plus urlencoded or multipart text body) are replayed as a GET against a row the
 * check owns. CSRF-TOKEN is never replayed (a CSRF attacker does not have it), and
 * empty values are dropped so a long legacy form still fits in a request line.
 *
 * WHAT IS ASSERTED. A probe is "refused" when the app answers 405 (the filter's and
 * the actions' contract) or a 403 that is not the WAF's page; and the caller's
 * snapshot of the rows the request could touch is identical before and after. HEAD
 * is sent after the GET (a container runs doGet for HEAD, so a `"GET".equals` check
 * would let it through). Results go into a ledger and are asserted together in
 * the check's LAST step, so one open route does not hide the others.
 */
const h = require('./playwright-harness');

/** Parse the text fields of a multipart/form-data body (file parts are skipped). */
function multipartFields(buffer, contentType) {
  const match = /boundary=(?:"([^"]+)"|([^;]+))/i.exec(contentType || '');
  if (!match || !buffer) return [];
  const boundary = `--${match[1] || match[2]}`;
  const text = buffer.toString('latin1');
  const fields = [];
  for (const part of text.split(boundary)) {
    const split = part.indexOf('\r\n\r\n');
    if (split < 0) continue;
    const head = part.slice(0, split);
    const name = /name="([^"]*)"/i.exec(head);
    if (!name || /filename="/i.test(head)) continue;
    const value = part.slice(split + 4).replace(/\r\n$/, '');
    fields.push([name[1], Buffer.from(value, 'latin1').toString('utf8')]);
  }
  return fields;
}

/**
 * Run `act` and capture the first POST (or, with method:null, any request) it sends whose
 * URL satisfies `match`. Returns {path, params, method, status}; params = query + body.
 */
async function captureRequest(page, match, act, { timeout = 20000, method = 'POST' } = {}) {
  // method: null captures whatever verb the page used (to prove a page that WRITES by GET).
  const requested = page.waitForRequest(r => (!method || r.method() === method) && match(new URL(r.url())), { timeout });
  requested.catch(() => {});
  await act();
  const request = await requested;
  const url = new URL(request.url());
  const params = new URLSearchParams(url.search);
  const type = (await request.allHeaders())['content-type'] || '';
  if (/application\/x-www-form-urlencoded/i.test(type)) {
    for (const [k, v] of new URLSearchParams(request.postData() || '')) params.append(k, v);
  } else if (/multipart\/form-data/i.test(type)) {
    for (const [k, v] of multipartFields(request.postDataBuffer(), type)) params.append(k, v);
  }
  const response = await request.response().catch(() => null);
  return { path: url.pathname, params, method: request.method(), status: response ? response.status() : 0 };
}

/**
 * Copy captured params for a GET replay: CSRF-TOKEN and empty values are dropped,
 * `overrides` (object; null deletes a key, an array sets repeated values) applied.
 */
function replayParams(params, overrides = {}) {
  const out = new URLSearchParams();
  for (const [k, v] of params) {
    if (k === 'CSRF-TOKEN' || v === '' || Object.prototype.hasOwnProperty.call(overrides, k)) continue;
    out.append(k, v);
  }
  for (const [k, v] of Object.entries(overrides)) {
    if (v === null || v === undefined) continue;
    for (const one of Array.isArray(v) ? v : [v]) out.append(k, String(one));
  }
  return out;
}

function isWafPage(status, body) {
  return status === 403 && /ModSecurity|<center>nginx<\/center>/i.test(body || '');
}

/**
 * Ledger of probes. Each probe snapshots, sends GET then HEAD, re-snapshots, and
 * records the outcome without throwing; assertAllRefused() throws with every open
 * route named. `snapshot` must read ONLY rows the check owns.
 */
function createLedger(name) {
  const entries = [];
  return {
    entries,
    async probe(s, { label, path, params, snapshot, methods = ['GET', 'HEAD'], requireStatus = true }) {
      const url = new URL(`${path}?${params.toString()}`, s.config.baseUrl.origin).toString();
      const entry = { label, path: path.replace(/^\/[^/]+/, ''), answers: [], changed: false, changedBy: [] };
      h.assert(url.length < 7000, `${label}: the replayed GET is too long for a request line (${url.length})`);
      for (const method of methods) {
        const before = snapshot();
        const response = await s.context.request.fetch(url, { method, maxRedirects: 0, failOnStatusCode: false });
        const status = response.status();
        const body = method === 'GET' ? await response.text().catch(() => '') : '';
        const after = snapshot();
        const waf = isWafPage(status, body);
        const refused = status === 405 || (status === 403 && !waf);
        entry.answers.push(`${method} ${status}${waf ? ' (WAF page)' : ''}`);
        if (before !== after) { entry.changed = true; entry.changedBy.push(method); }
        // requireStatus=false: an include()d gate cannot set a status (the container ignores
        // sendError inside an include), so only the absence of a write can be asserted.
        if (!refused && requireStatus) entry.open = true;
      }
      entries.push(entry);
      const verdict = entry.changed ? `WROTE (${entry.changedBy.join('/')})` : entry.open ? 'answered (no write seen)'
        : requireStatus ? 'refused' : 'no write (status not assertable through include)';
      console.log(`  probe ${name}: ${label} -> ${entry.answers.join(', ')}; ${verdict}`);
      return entry;
    },
    assertAllRefused() {
      const failed = entries.filter(e => e.changed || e.open);
      h.assert(entries.length > 0, 'No GET probe was recorded');
      h.assert(!failed.length, `${failed.length} of ${entries.length} state-changing route(s) did not refuse GET/HEAD: `
        + failed.map(e => `${e.label} [${e.path}] ${e.answers.join(', ')}${e.changed ? ` and the ${e.changedBy.join('/')} CHANGED the owned rows` : ''}`)
          .join('; '));
      return entries.length;
    },
  };
}

/**
 * Move recorder entries that match `needle` (a known, separately tracked defect such as the
 * missing /styles.css, finding L87) out of the strict recorder and return them, so the caller
 * can REPORT them in its final assertion instead of failing an unrelated step on them.
 */
function takeKnownNoise(recorder, needle) {
  const taken = [];
  for (const key of ['consoleIssues', 'requestFailures', 'badResponses']) {
    const list = recorder[key] || [];
    for (let i = list.length - 1; i >= 0; i--) {
      if (JSON.stringify(list[i]).includes(needle)) taken.push(...list.splice(i, 1));
    }
  }
  return taken;
}

module.exports = { captureRequest, replayParams, createLedger, multipartFields, isWafPage, takeKnownNoise };
