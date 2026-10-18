/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');

const {
  oauthHeader, oauthSignature, pct, signatureBaseUri,
} = require('./oauth-rest-surfaces-playwright-checks');

/*
 * oauth-rest-surfaces-playwright-checks.js signs every OAuth 1.0a request it sends.
 * If its signer drifted from the protocol, the deployed check would report the
 * server's signature verifier as broken. These tests pin the signer offline, so a
 * failure there points at the server.
 */

test('the signature matches the OAuth Core 1.0 Appendix A test vector', () => {
  // OAuth Core 1.0, Appendix A.5.2 (photos.example.net). Query parameters take part
  // in the signature; the base string URI does not carry them.
  const signature = oauthSignature({
    method: 'GET',
    url: 'http://photos.example.net/photos?file=vacation.jpg&size=original',
    oauthParams: {
      oauth_consumer_key: 'dpf43f3p2l4k3l03', // ggignore - published OAuth Core 1.0 Appendix A example value, not a credential
      oauth_token: 'nnch734d00sl2jdk', // ggignore - published OAuth Core 1.0 Appendix A example value, not a credential
      oauth_signature_method: 'HMAC-SHA1',
      oauth_timestamp: '1191242096',
      oauth_nonce: 'kllo9940pd9333jh', // ggignore - published OAuth Core 1.0 Appendix A example value, not a credential
      oauth_version: '1.0',
    },
    consumerSecret: 'kd94hf93k423kf44', // ggignore - published OAuth Core 1.0 Appendix A example value, not a credential
    tokenSecret: 'pfkkdhi9sl3r4s00', // ggignore - published OAuth Core 1.0 Appendix A example value, not a credential
  });
  assert.equal(signature, 'tR3+Ty81lMeYAr/Fid0kMTYa/WM='); // ggignore - the vector's published signature
});

test('the base string URI is rebuilt the way the server verifier rebuilds it', () => {
  // OAuth1SignatureVerifierImplementation: lower-case scheme and host, default port
  // dropped, path only.
  assert.equal(signatureBaseUri('HTTPS://LocalHost:443/carlos/ws/oauth/initiate?scope=a%20b'),
    'https://localhost/carlos/ws/oauth/initiate');
  assert.equal(signatureBaseUri('http://127.0.0.1:80/carlos/ws/oauth/token'),
    'http://127.0.0.1/carlos/ws/oauth/token');
  assert.equal(signatureBaseUri('http://127.0.0.1:18080/carlos/ws/services/oauth/info'),
    'http://127.0.0.1:18080/carlos/ws/services/oauth/info');
});

test('percent-encoding follows RFC 3986, as OAuth 1.0a requires', () => {
  // encodeURIComponent alone leaves !'()* unencoded; the server's pct() does not.
  assert.equal(pct("a b!'()*~-._"), 'a%20b%21%27%28%29%2A~-._');
  assert.equal(pct('demographic.read provider.read'), 'demographic.read%20provider.read');
});

test('a built header carries a signature that verifies over its own parameters', () => {
  const url = 'https://127.0.0.1/carlos/ws/oauth/initiate?scope=demographic.read%20provider.read';
  const header = oauthHeader({
    method: 'POST', url, consumerKey: 'key', consumerSecret: 'secret', extra: { oauth_callback: 'oob' },
  });
  assert.match(header, /^OAuth /);
  const params = Object.fromEntries(header.slice('OAuth '.length).split(', ').map((pair) => {
    const [name, quoted] = pair.split('=');
    return [decodeURIComponent(name), decodeURIComponent(quoted.slice(1, -1))];
  }));
  assert.match(params.oauth_nonce, /^[0-9a-f]{32}$/);
  assert.ok(!('oauth_token' in params), 'a request without a token must not send oauth_token');
  const { oauth_signature: sent, ...signed } = params;
  assert.equal(sent, oauthSignature({ method: 'POST', url, oauthParams: signed, consumerSecret: 'secret' }));
});

test('a header built with a token signs it, and two headers never share a nonce', () => {
  const url = 'https://127.0.0.1/carlos/ws/services/oauth/info';
  const make = () => oauthHeader({
    method: 'GET', url, consumerKey: 'key', consumerSecret: 'secret', token: 'tok', tokenSecret: 'tsecret',
  });
  const first = make();
  assert.match(first, /oauth_token="tok"/);
  const nonce = (header) => /oauth_nonce="([^"]+)"/.exec(header)[1];
  assert.notEqual(nonce(first), nonce(make()));
});
