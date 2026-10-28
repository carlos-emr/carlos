/* SPDX-License-Identifier: GPL-2.0-or-later */
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

/*
 * Which packaged nginx location serves a URL, and does it carry a limit_req?
 *
 * debian/assets/nginx/carlos-emr.conf rate-limits only the credential surfaces: the human login
 * routes and /ws/LoginService plus the two unauthenticated OAuth legs. Everything else falls
 * through to `location /`, which has no limit. Finding 172 records that /ws/services (the OAuth
 * data API, live since #4415) is in that unthrottled remainder although every rejected call
 * writes a synchronous audit row, so an anonymous client can turn a flood of bad calls into
 * database writes. The file's own comment still says the rest of /ws "must not be throttled";
 * the finding is that this no longer holds once the data API is reachable.
 *
 * A text search for "limit_req" cannot answer the question, because the directive is attached to
 * a location and nginx picks the location by its own precedence rules. So this reads the
 * configuration into server / location blocks and runs nginx's selection (exact match, longest
 * prefix, then regular expressions in file order) for the URL, then looks for a limit_req on the
 * chosen location or, failing that, on the enclosing server (nginx inherits the directive when a
 * location sets none of its own). The control tests prove the selection by routing /ws/LoginService
 * to its limited location, so a parser fault cannot masquerade as a missing limit.
 */

const ROOT = path.resolve(__dirname, '..');
const NGINX_DIR = path.join(ROOT, 'debian', 'assets', 'nginx');
const SERVER_CONF = path.join(NGINX_DIR, 'carlos-emr.conf');
const LIMITS_CONF = path.join(NGINX_DIR, 'conf.d', 'carlos-emr-limits.conf');

// ---------------------------------------------------------------------------------------------
// A small nginx configuration reader
// ---------------------------------------------------------------------------------------------

/** Splits configuration text into words, quoted strings (kept whole, quotes removed) and `{ } ;`. */
function tokenize(text) {
  const tokens = [];
  let i = 0;
  while (i < text.length) {
    const c = text[i];
    if (/\s/.test(c)) { i += 1; continue; }
    if (c === '#') { while (i < text.length && text[i] !== '\n') i += 1; continue; }
    if (c === '{' || c === '}' || c === ';') { tokens.push({ type: c }); i += 1; continue; }
    if (c === '"' || c === "'") {
      let value = '';
      i += 1;
      while (i < text.length && text[i] !== c) {
        if (text[i] === '\\' && i + 1 < text.length) { value += text[i] + text[i + 1]; i += 2; } else { value += text[i]; i += 1; }
      }
      assert.ok(i < text.length, 'unterminated quoted string in the nginx configuration');
      i += 1;
      tokens.push({ type: 'word', value, quoted: true });
      continue;
    }
    let value = '';
    while (i < text.length && !/[\s{};#]/.test(text[i])) { value += text[i]; i += 1; }
    tokens.push({ type: 'word', value });
  }
  return tokens;
}

/** Parses tokens into nested { name, args, children } directive nodes. */
function parseBlock(tokens, start = 0, nested = false) {
  const nodes = [];
  let i = start;
  while (i < tokens.length) {
    if (tokens[i].type === '}') {
      assert.ok(nested, 'unbalanced "}" in the nginx configuration');
      return { nodes, next: i + 1 };
    }
    const words = [];
    while (i < tokens.length && tokens[i].type === 'word') { words.push(tokens[i].value); i += 1; }
    assert.ok(words.length > 0, 'a directive with no name in the nginx configuration');
    const [name, ...args] = words;
    if (tokens[i] && tokens[i].type === '{') {
      const inner = parseBlock(tokens, i + 1, true);
      nodes.push({ name, args, children: inner.nodes });
      i = inner.next;
    } else {
      assert.ok(tokens[i] && tokens[i].type === ';', `directive "${name}" is not terminated by ";"`);
      nodes.push({ name, args, children: null });
      i += 1;
    }
  }
  assert.ok(!nested, 'unbalanced "{" in the nginx configuration');
  return { nodes, next: i };
}

function parseFile(file) {
  return parseBlock(tokenize(fs.readFileSync(file, 'utf8'))).nodes;
}

/** `location [= | ^~ | ~ | ~*] pattern { ... }` -> { modifier, pattern, directives }. */
function locationOf(node) {
  const [first, second] = node.args;
  const modifiers = ['=', '^~', '~', '~*'];
  const hasModifier = modifiers.includes(first) && second !== undefined;
  return {
    modifier: hasModifier ? first : '',
    pattern: hasModifier ? second : first,
    directives: node.children,
  };
}

/** Server blocks that proxy application traffic (the plain-HTTP block only redirects). */
function proxyingServers(nodes = parseFile(SERVER_CONF)) {
  return nodes.filter((node) => node.name === 'server').map((server) => ({
    directives: server.children.filter((child) => child.children === null),
    locations: server.children.filter((child) => child.name === 'location').map(locationOf),
  })).filter((server) => server.locations.some((location) => location.directives.some((d) => d.name === 'proxy_pass')));
}

/**
 * The location nginx would select for `uri`: an exact match wins; otherwise the longest matching
 * prefix is remembered, and a `^~` prefix ends the search; otherwise the first matching regular
 * expression in file order wins; otherwise the remembered prefix serves the request.
 */
function selectLocation(server, uri) {
  const exact = server.locations.find((location) => location.modifier === '=' && location.pattern === uri);
  if (exact) return exact;
  const prefixes = server.locations.filter((location) => ['', '^~'].includes(location.modifier) && uri.startsWith(location.pattern));
  const longest = prefixes.reduce((best, location) => (!best || location.pattern.length > best.pattern.length ? location : best), null);
  if (longest && longest.modifier === '^~') return longest;
  for (const location of server.locations) {
    if (location.modifier !== '~' && location.modifier !== '~*') continue;
    // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp -- the pattern is a location from this repository's own checked-in nginx configuration, never request data
    const regex = new RegExp(location.pattern, location.modifier === '~*' ? 'i' : '');
    if (regex.test(uri)) return location;
  }
  return longest;
}

/** The limit_req directives in force for a location: its own, else the enclosing server's. */
function limitRequests(server, location) {
  const own = location.directives.filter((directive) => directive.name === 'limit_req');
  if (own.length > 0) return own;
  return server.directives.filter((directive) => directive.name === 'limit_req');
}

/** Names of the limit_req_zone zones declared in the http-level include. */
function declaredZones() {
  return parseFile(LIMITS_CONF).filter((node) => node.name === 'limit_req_zone')
    .map((node) => /^zone=([^:]+):/.exec(node.args.find((arg) => arg.startsWith('zone=')))[1]);
}

/** { location, limits } for `uri` on every server block that proxies application traffic. */
function limitsFor(uri) {
  const servers = proxyingServers();
  assert.ok(servers.length > 0, 'no server block in carlos-emr.conf proxies application traffic; the parser found nothing');
  return servers.map((server) => {
    const location = selectLocation(server, uri);
    assert.ok(location, `no nginx location serves ${uri}`);
    return { location, limits: limitRequests(server, location) };
  });
}

// ---------------------------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------------------------

test('shouldFindCredentialLocations_inPackagedNginxConfiguration', () => {
  const [{ locations }] = proxyingServers();
  assert.ok(locations.length >= 6, `expected the HTTPS server to define 6+ locations, parsed ${locations.length}`);
  assert.ok(locations.some((location) => location.modifier === '' && location.pattern === '/'), 'the catch-all location / is missing');
  assert.ok(locations.some((location) => location.modifier === '~*' && location.pattern.includes('forcepasswordreset')),
    'the login location was not parsed (its regex is quoted and contains ";")');
});

test('shouldRouteLoginService_toItsRateLimitedLocation', () => {
  // The control. If this fails the reader is broken and the finding test below proves nothing.
  for (const uri of ['/carlos/ws/LoginService', '/carlos/ws/oauth/initiate', '/carlos/ws/oauth/token', '/carlos/login']) {
    for (const { location, limits } of limitsFor(uri)) {
      assert.ok(limits.length > 0, `${uri} was routed to location ${location.pattern} which carries no limit_req`);
    }
  }
});

test('shouldDeclareEveryLimitReqZone_thatALocationUses', () => {
  const zones = declaredZones();
  assert.ok(zones.includes('carlos_login') && zones.includes('carlos_wsauth'), `limit_req_zone zones parsed: ${zones.join(', ')}`);
  for (const uri of ['/carlos/ws/LoginService', '/carlos/login']) {
    for (const { limits } of limitsFor(uri)) {
      for (const directive of limits) {
        const zone = /^zone=(.+)$/.exec(directive.args.find((arg) => arg.startsWith('zone=')) || '');
        assert.ok(zone && zones.includes(zone[1]), `limit_req on ${uri} names zone ${zone && zone[1]}, which conf.d does not declare`);
      }
    }
  }
});

test('shouldLeaveOrdinaryApplicationTraffic_unthrottled', () => {
  // The guard behind the comment in carlos-emr.conf: a whole clinic arrives from one NAT address,
  // so the chatty AJAX application must NOT acquire a per-address budget when /ws/services does.
  for (const uri of ['/carlos/provider/providercontrol', '/carlos/loginResource/app.js', '/carlos/demographic/DemographicUpdate']) {
    for (const { limits } of limitsFor(uri)) {
      assert.equal(limits.length, 0, `${uri} must stay unthrottled`);
    }
  }
});

test('shouldRateLimitWsServices_atTheFrontDoor', { todo: 'finding 172' }, () => {
  for (const uri of ['/carlos/ws/services', '/carlos/ws/services/oauth/info', '/carlos/ws/services/demographics']) {
    for (const { location, limits } of limitsFor(uri)) {
      assert.ok(limits.length > 0,
        `${uri} is served by nginx location "${location.modifier} ${location.pattern}" which has no limit_req; `
        + 'every rejected /ws/services call writes a synchronous audit row (OAuthInterceptor.auditAuthFailure), '
        + 'so the data API needs a per-address request ceiling like /ws/LoginService has');
    }
  }
});
