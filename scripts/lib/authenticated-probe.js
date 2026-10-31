/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
'use strict';

/**
 * Playwright's APIRequestContext omits Secure cookies on HTTP loopback even when
 * Chromium sends them. Replay only this application's cookies explicitly, with
 * redirects disabled so a refusal probe cannot leak them to another origin.
 */
async function authenticatedProbe(session, target, options = {}) {
  const base = session.config.baseUrl;
  const url = new URL(target, base);
  if (url.origin !== base.origin || url.username || url.password) {
    throw new Error('Authenticated probe must stay on the application origin');
  }
  const host = url.hostname.replace(/^\[|\]$/g, '').toLowerCase();
  const loopback = ['localhost', '127.0.0.1', '::1'].includes(host);
  if (!['http:', 'https:'].includes(url.protocol) || (url.protocol === 'http:' && !loopback)) {
    throw new Error('Authenticated probes require HTTPS outside loopback');
  }
  if (!['GET', 'HEAD'].includes(options.method || 'GET')) {
    throw new Error('Authenticated refusal probes support only GET and HEAD');
  }
  const now = Date.now() / 1000;
  const cookies = (await session.context.cookies()).filter(cookie => {
    const domain = cookie.domain.toLowerCase();
    const host = url.hostname.toLowerCase();
    const domainMatches = domain.startsWith('.')
      ? host === domain.slice(1) || host.endsWith(domain)
      : host === domain;
    const path = cookie.path || '/';
    const pathMatches = url.pathname === path || (url.pathname.startsWith(path)
      && (path.endsWith('/') || url.pathname[path.length] === '/'));
    return domainMatches && pathMatches && (cookie.expires === -1 || cookie.expires > now);
  }).sort((a, b) => (b.path || '/').length - (a.path || '/').length);
  if (!cookies.some(cookie => cookie.name === 'JSESSIONID' && cookie.value)) {
    throw new Error('Authenticated probe requires an application session cookie');
  }
  const response = await session.context.request.fetch(url.href, {
    ...options,
    headers: { ...Object.fromEntries(Object.entries(options.headers || {}).filter(([name]) => name.toLowerCase() !== 'cookie')),
      Cookie: cookies.map(cookie => `${cookie.name}=${cookie.value}`).join('; ') },
    maxRedirects: 0,
    failOnStatusCode: false,
  });
  // Even no-write/include assertions must not accept a logged-out redirect.
  if (response.status() >= 300 && response.status() < 400) {
    throw new Error('Authenticated probe redirected; session/refusal evidence is inconclusive');
  }
  return response;
}

module.exports = { authenticatedProbe };
