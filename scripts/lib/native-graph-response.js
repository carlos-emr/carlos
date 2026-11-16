/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';

/**
 * Capture the native Plot navigation without Chromium's text-charset decoding.
 * Some installed PNG responses declare charset=UTF-8. CDP response.body() then
 * replaces binary bytes even though the browser image is intact. Fetch the one
 * intercepted UI request losslessly and fulfill it with those same bytes and
 * headers. No guessed URL, second graph request or re-encoded image is used.
 */
async function nativeGraphResponse(session, trigger, { type, timeout = 20000 } = {}) {
  const base = new URL(session.config.baseUrl);
  const pathname = base.pathname.replace(/\/$/, '') + '/encounter/GraphMeasurements';
  const matches = url => url.origin === base.origin && url.pathname === pathname;
  let resolve, reject, failure, installed = false, claimed = false;
  const inflight = [];
  const captured = new Promise((yes, no) => { resolve = yes; reject = no; });
  // Observe rejection even if clicking fails before the caller awaits capture.
  captured.catch(() => {});
  const timer = setTimeout(() => reject(new Error('Native Plot navigation was not captured within its budget')), timeout);
  const handler = route => {
    const task = (async () => {
      let response;
      try {
        if (claimed) throw new Error('Native Plot produced more than one graph navigation');
        claimed = true;
        const request = route.request(), url = new URL(request.url());
        if (request.method() !== 'GET' || !request.isNavigationRequest()
            || url.searchParams.getAll('demographic_no').length !== 1
            || url.searchParams.has('demographicNo')
            || url.searchParams.get('demographic_no') !== String(session.patient)
            || url.searchParams.getAll('type').length !== 1 || url.searchParams.get('type') !== type) {
          throw new Error('Native Plot requested an unexpected patient, type or method');
        }
        response = await route.fetch({ maxRedirects: 0, timeout: Math.min(timeout, 15000) });
        const body = await response.body();
        const result = { body, url: request.url(), status: response.status(), headers: response.headers(),
          page: request.frame().page() };
        await route.fulfill({ response, body });
        resolve(result);
      } catch (error) {
        failure = failure || error;
        reject(error);
        // Already-handled routes need no abort; preserve the primary failure.
        try { await route.abort(); } catch { /* the primary failure is reported */ }
      } finally {
        if (response) await response.dispose();
      }
    })();
    inflight.push(task);
    return task;
  };
  try {
    await session.context.route(matches, handler);
    installed = true;
    await trigger();
    const result = await captured;
    await Promise.all(inflight);
    if (failure) throw failure;
    return result;
  } finally {
    clearTimeout(timer);
    if (installed) await session.context.unroute(matches, handler);
    await Promise.all(inflight);
  }
}

module.exports = { nativeGraphResponse };
