/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';
/*
 * Shared plumbing for the authz-read-* checks: sign a restricted-role login in through the real
 * login form, issue GET (and HEAD) requests through that login's browser context, and classify
 * what came back.
 *
 * Classification (the application's own refusal vocabulary):
 *   refused  403 / 405 / 401, a redirect, or the "Security Exception" page (securityError.jsp is
 *            what every SecurityException ends in, delivered as HTTP 403)
 *   error    5xx or the generic "Error Page" (CARLOS error page): nothing was served, but the
 *            refusal is not deliberate, so a check that pins a refusal accepts only `refused`
 *   served   2xx whose body is real content (not an error page). A caller that wants proof of WHICH
 *            data was served also passes `needles` (strings only that data contains).
 *
 * Probes only ever GET/HEAD routes the caller has verified cannot write on GET, and requests
 * run through context.request (a negative probe, never the positive path of a workflow).
 */
const crypto = require('node:crypto');
const { execFileSync } = require('node:child_process');
const h = require('./playwright-harness');

/** One request through a login's browser context; never follows redirects. */
async function probe(context, url, { method = 'GET', needles = [] } = {}) {
  try {
    const response = await context.request.fetch(url, { method, maxRedirects: 0, timeout: 40000, failOnStatusCode: false });
    const body = method === 'HEAD' ? Buffer.alloc(0) : await response.body();
    const headers = response.headers();
    const type = (headers['content-type'] || '').split(';')[0];
    let text = body.toString('utf8');
    if (/pdf/i.test(type) && body.length > 0) {
      try {
        text = execFileSync('pdftotext', ['-', '-'], { input: body, encoding: 'utf8', timeout: 20000, maxBuffer: 8 << 20 });
      } catch (error) { text = ''; }
    }
    const title = (/<title[^>]*>([\s\S]*?)<\/title>/i.exec(text) || [])[1] || '';
    const plain = text.replace(/<script[\s\S]*?<\/script>|<style[\s\S]*?<\/style>|<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
    return {
      status: response.status(), type, length: body.length, location: headers.location || '',
      pdf: body.subarray(0, 5).toString() === '%PDF-',
      lead: (title || plain).replace(/\s+/g, ' ').trim().slice(0, 60),
      found: needles.filter(needle => text.includes(needle)),
      hash: crypto.createHash('sha1').update(body).digest('hex').slice(0, 10),
    };
  } catch (error) {
    return { status: 0, error: String(error.message).slice(0, 80), found: [], lead: '' };
  }
}

/** 'refused' | 'error' | 'served' | 'empty' (a 2xx with no body, e.g. a HEAD or an unconfigured fragment). */
function classify(result) {
  const { status } = result;
  const lead = result.lead || '';
  if ([401, 403, 405].includes(status)) return 'refused';
  if (status >= 300 && status < 400) return 'refused';
  if (/^Security Exception/i.test(lead)) return 'refused';
  if (status === 0 || status >= 400 || /^Error Page/i.test(lead)) return 'error';
  if (status >= 200 && status < 300) return result.length > 0 || result.pdf ? 'served' : 'empty';
  return 'error';
}

/**
 * The application's deliberate refusal: HTTP 403 ("Security Exception"), or the redirect a
 * <security:oscarSec> block in a view sends to /securityError?type=_object or /noRights.html.
 * A bare 3xx (a login redirect, a session timeout) is NOT a refusal here.
 */
function refusedByApp(result) {
  if (result.status === 403) return true;
  return result.status >= 300 && result.status < 400 && /securityError|noRights/i.test(result.location || '');
}

/** Absolute URL for an application path (`route?query`), validated by the harness. */
function urlFor(config, route) {
  return h.appUrl(config.baseUrl, `/${route.replace(/^\//, '')}`);
}

/**
 * Sign a throwaway login (from authzReadFixture.addLogin) in through the login form and return
 * its context. Pages of this context are not wired to the workflow recorder: the restricted
 * logins deliberately land on pages their role cannot fully load.
 */
async function signIn(session, login) {
  const context = await h.newContext(session.context.browser(), session.config);
  context.setDefaultTimeout(20000);
  const page = await h.login(context, { ...session.config, testUser: login.username }, h.createRecorder());
  return { context, page, login };
}

/**
 * Accumulate findings and assert them together in the LAST step, so one open route does not
 * hide the others. entries: {pair, detail}.
 */
function ledger() {
  const open = [];
  return {
    add(pair, detail) { open.push(`${pair}${detail ? ` (${detail})` : ''}`); },
    get open() { return open.slice(); },
    assertEmpty(message) { h.assert(open.length === 0, `${message}: ${open.join('; ')}`); },
  };
}

module.exports = { probe, classify, refusedByApp, urlFor, signIn, ledger };
