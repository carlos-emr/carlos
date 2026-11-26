/**
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 *
 * This software is published under the GPL GNU General Public License.
 * You may redistribute it and/or modify it under version 2 of the License,
 * or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * OAuth 1.0a (RFC 5849) HMAC-SHA1 request signing for the live OAuth checks.
 *
 * These pure helpers used to be imported from oauth-rest-surfaces-playwright-checks.js, whose
 * module body asserts EXPECT_OAUTH_MODE when it is loaded. Keeping them here lets another check
 * sign requests without running that check's load-time configuration assertions.
 * oauth-rest-surfaces.test.js pins the signer to a published test vector.
 */
const crypto = require('node:crypto');

/** RFC 3986 percent-encoding, as OAuth 1.0a section 3.6 requires. */
function pct(value) {
  return encodeURIComponent(String(value))
    .replace(/[!'()*]/g, (c) => `%${c.charCodeAt(0).toString(16).toUpperCase()}`);
}

/**
 * The base string URI the server rebuilds (OAuth1SignatureVerifierImplementation):
 * lower-case scheme and host, default port dropped, path only.
 */
function signatureBaseUri(url) {
  const u = new URL(url);
  const scheme = u.protocol.replace(':', '').toLowerCase();
  const defaultPort = (scheme === 'https' && (u.port === '' || u.port === '443'))
    || (scheme === 'http' && (u.port === '' || u.port === '80'));
  return `${scheme}://${u.hostname.toLowerCase()}${defaultPort ? '' : `:${u.port}`}${u.pathname}`;
}

/**
 * The HMAC-SHA1 signature of a request (RFC 5849 section 3.4): the oauth_* parameters
 * plus the URL's query parameters, encoded, sorted by name then value, under
 * consumer secret & token secret.
 */
function oauthSignature({ method, url, oauthParams, consumerSecret, tokenSecret }) {
  const params = [...Object.entries(oauthParams), ...new URL(url).searchParams.entries()]
    .map(([k, v]) => [pct(k), pct(v)])
    .sort(([ak, av], [bk, bv]) => (ak === bk ? (av < bv ? -1 : av > bv ? 1 : 0) : (ak < bk ? -1 : 1)));
  const base = [
    method.toUpperCase(),
    pct(signatureBaseUri(url)),
    pct(params.map(([k, v]) => `${k}=${v}`).join('&')),
  ].join('&');
  const key = `${pct(consumerSecret)}&${pct(tokenSecret || '')}`;
  // HMAC-SHA1 is the only HMAC method OAuth 1.0a defines (RFC 5849 section 3.4.2) and the
  // only one OAuth1SignatureVerifierImplementation verifies; SHA-1 collisions do not apply to HMAC.
  // nosemgrep: javascript.node-stdlib.cryptography.crypto-weak-algorithm.crypto-weak-algorithm
  return crypto.createHmac('sha1', key).update(base).digest('base64');
}

/**
 * Builds an HMAC-SHA1 signed OAuth 1.0a Authorization header (RFC 5849) with a fresh
 * nonce and the current timestamp.
 */
function oauthHeader({ method, url, consumerKey, consumerSecret, token, tokenSecret, extra = {} }) {
  const oauth = {
    oauth_consumer_key: consumerKey,
    oauth_nonce: crypto.randomBytes(16).toString('hex'),
    oauth_signature_method: 'HMAC-SHA1',
    oauth_timestamp: String(Math.floor(Date.now() / 1000)),
    oauth_version: '1.0',
    ...(token ? { oauth_token: token } : {}),
    ...extra,
  };
  const signature = oauthSignature({ method, url, oauthParams: oauth, consumerSecret, tokenSecret });
  return `OAuth ${Object.entries({ ...oauth, oauth_signature: signature })
    .map(([k, v]) => `${pct(k)}="${pct(v)}"`).join(', ')}`;
}

module.exports = { oauthHeader, oauthSignature, pct, signatureBaseUri };
