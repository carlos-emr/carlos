/* SPDX-License-Identifier: GPL-2.0-or-later */
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

/*
 * Every route a ModSecurity exclusion names must still be a route.
 *
 * WHY THIS IS A TEST. debian/assets/modsecurity/*.conf unhooks OWASP CRS rules for one
 * route at a time (`SecRule REQUEST_URI "@rx ^/carlos/eform/addEForm(?:[;?]|$)" ...`). The
 * anchor is deliberately exact, so renaming a Struts action leaves its exclusion pointing at a
 * URL nothing serves any more, and the renamed route then meets the full CRS rule set: the
 * save a clinician has done for years comes back as a bare WAF 403 on the packaged install,
 * while every unit test, the devcontainer (no WAF) and the application's own suite stay green.
 * Nothing but this comparison connects a conf file to struts-*.xml, so this is where the
 * connection is pinned.
 *
 * WHAT COUNTS AS RESOLVED. The exclusion's route, as the front door would deliver it
 * (/carlos/<route>, the context path stripped), must be one of:
 *   - a Struts action: an <action name="..."> in a src/main/webapp/WEB-INF/classes/struts*.xml
 *     package, joined to the package namespace (struts.action.extension is empty, so the
 *     action name IS the path); or
 *   - a <servlet-mapping> url-pattern in WEB-INF/web.xml, exact or `/prefix/*`.
 * <filter-mapping> patterns are deliberately NOT accepted. A filter never produces a response,
 * and web.xml maps most filters on the catch-all `/*`, which would make every string resolve.
 *
 * REGEX ROUTES. A few exclusions name a family of URLs (`ws/rs/eform/(?:[0-9]+/)?json`). The
 * pattern is expanded into concrete routes: every alternation branch is its own route, and an
 * optional group (`?`, `*`) is omitted, so the REQUIRED form of the route is what must resolve.
 * A pattern shape the expander does not understand throws, rather than being skipped, so a
 * rewrite of a rule cannot silently drop it from the check.
 */

const ROOT = path.resolve(__dirname, '..');
const WAF_DIR = path.join(ROOT, 'debian', 'assets', 'modsecurity');
const CLASSES_DIR = path.join(ROOT, 'src', 'main', 'webapp', 'WEB-INF', 'classes');
const WEB_XML = path.join(ROOT, 'src', 'main', 'webapp', 'WEB-INF', 'web.xml');

const { CONTEXT, wafRouteRules } = require('./lib/waf-exclusion-rules');

// ---------------------------------------------------------------------------------------------
// Route pattern -> concrete routes
// ---------------------------------------------------------------------------------------------

/** A single character that a bracket expression such as [0-9], [A-Za-z] or [^/] can match. */
function sampleOfClass(body) {
  if (body.startsWith('^')) return 'a';
  return body[0] === '\\' ? ({ d: '0', w: 'a', s: ' ' }[body[1]] || body[1]) : body[0];
}

function cross(left, right) {
  const out = [];
  for (const a of left) for (const b of right) out.push({ text: a.text + b.text, optional: a.optional || b.optional });
  return out;
}

/**
 * Expands the regex subset the exclusions use into the routes it stands for:
 * literals, escapes, [classes], (?:groups), alternation, and the ? * + {n} quantifiers.
 * Returns [{ text, optional }]; `optional` marks a route that includes an optional part.
 */
function expandRoutePattern(pattern, limit = 256) {
  let i = 0;
  function alternation() {
    const done = [];
    let sequence = [{ text: '', optional: false }];
    while (i < pattern.length && pattern[i] !== ')') {
      const c = pattern[i];
      if (c === '|') {
        done.push(...sequence);
        sequence = [{ text: '', optional: false }];
        i += 1;
        continue;
      }
      let atom;
      if (c === '(') {
        i += 1;
        if (pattern.startsWith('?:', i)) i += 2;
        else assert.notEqual(pattern[i], '?', `unsupported group construct in route pattern "${pattern}"`);
        atom = alternation();
        assert.equal(pattern[i], ')', `unbalanced group in route pattern "${pattern}"`);
        i += 1;
      } else if (c === '[') {
        const end = pattern.indexOf(']', i + 2);
        assert.ok(end > 0, `unterminated character class in route pattern "${pattern}"`);
        atom = [{ text: sampleOfClass(pattern.slice(i + 1, end)), optional: false }];
        i = end + 1;
      } else if (c === '\\') {
        atom = [{ text: ({ d: '0', w: 'a', s: ' ' }[pattern[i + 1]] || pattern[i + 1]), optional: false }];
        i += 2;
      } else if (c === '.') {
        atom = [{ text: 'a', optional: false }];
        i += 1;
      } else {
        atom = [{ text: c, optional: false }];
        i += 1;
      }
      const quantifier = pattern[i];
      if (quantifier === '?' || quantifier === '*') {
        atom = [{ text: '', optional: false }, ...atom.map((part) => ({ ...part, optional: true }))];
        i += 1;
      } else if (quantifier === '+') {
        i += 1;
      } else if (quantifier === '{') {
        const end = pattern.indexOf('}', i);
        const count = /^\{(\d+)(?:,\d*)?\}$/.exec(pattern.slice(i, end + 1));
        assert.ok(count, `unsupported quantifier in route pattern "${pattern}"`);
        atom = atom.map((part) => ({ ...part, text: part.text.repeat(Number(count[1])) }));
        i = end + 1;
      }
      sequence = cross(sequence, atom);
      assert.ok(sequence.length <= limit, `route pattern "${pattern}" expands to more than ${limit} routes`);
    }
    done.push(...sequence);
    return done;
  }
  const routes = alternation();
  assert.equal(i, pattern.length, `unbalanced ")" in route pattern "${pattern}"`);
  return routes;
}

// ---------------------------------------------------------------------------------------------
// The application's own routes
// ---------------------------------------------------------------------------------------------

function withoutXmlComments(xml) {
  return xml.replace(/<!--[\s\S]*?-->/g, '');
}

/**
 * Every Struts action as { path, file }: package namespace joined to the action name. Names that
 * contain a `*` wildcard are kept as written and matched by wildcardMatches().
 */
function strutsActions(dir = CLASSES_DIR) {
  const actions = [];
  for (const name of fs.readdirSync(dir).filter((entry) => /^struts.*\.xml$/.test(entry)).sort()) {
    const xml = withoutXmlComments(fs.readFileSync(path.join(dir, name), 'utf8'));
    let namespace = '/';
    const tokens = /<package\b[^>]*>|<action\b[^>]*>/g;
    let token;
    while ((token = tokens.exec(xml))) {
      if (token[0].startsWith('<package')) {
        const ns = /\bnamespace\s*=\s*"([^"]*)"/.exec(token[0]);
        namespace = ns ? ns[1] : '/';
        continue;
      }
      const actionName = /\bname\s*=\s*"([^"]+)"/.exec(token[0]);
      if (!actionName) continue;
      const joined = namespace === '/' ? `/${actionName[1]}` : `${namespace.replace(/\/$/, '')}/${actionName[1]}`;
      actions.push({ path: joined, file: name });
    }
  }
  return actions;
}

/** Struts matches `*` against anything but a slash and `**` against anything, inside an action name. */
function wildcardMatches(actionPath, candidate) {
  if (!actionPath.includes('*')) return actionPath === candidate;
  const source = actionPath.split('**').map((part) => part.split('*')
    .map((piece) => piece.replace(/[.+?^${}()|[\]\\]/g, '\\$&')).join('[^/]*')).join('.*');
  // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp -- the source is built from this repository's own exclusion route list with the regex metacharacters escaped earlier in this function
  return new RegExp(`^${source}$`).test(candidate);
}

/** The url-pattern of every <servlet-mapping> in web.xml. Filters, the `/*` catch-all and `*.ext` are not routes. */
function servletPatterns(file = WEB_XML) {
  const xml = withoutXmlComments(fs.readFileSync(file, 'utf8'));
  const patterns = [];
  for (const mapping of xml.matchAll(/<servlet-mapping>([\s\S]*?)<\/servlet-mapping>/g)) {
    for (const pattern of mapping[1].matchAll(/<url-pattern>\s*([^<\s]+)\s*<\/url-pattern>/g)) {
      if (pattern[1] === '/*' || pattern[1] === '/' || pattern[1].startsWith('*.')) continue;
      patterns.push(pattern[1]);
    }
  }
  return patterns;
}

function servletMatches(pattern, candidate) {
  if (pattern.endsWith('/*')) {
    const prefix = pattern.slice(0, -2);
    return candidate === prefix || candidate.startsWith(`${prefix}/`);
  }
  return pattern === candidate;
}

/** Where a candidate route (e.g. /eform/addEForm) is served, or null. */
function resolveRoute(candidate, { actions, servlets }) {
  const action = actions.find((entry) => wildcardMatches(entry.path, candidate));
  if (action) return { kind: 'struts action', via: `${action.path} (${action.file})` };
  const servlet = servlets.find((pattern) => servletMatches(pattern, candidate));
  if (servlet) return { kind: 'servlet mapping', via: `${servlet} (web.xml)` };
  return null;
}

/** Prefix rules resolve when anything is served at or beneath them. */
function resolvePrefix(prefix, { actions, servlets }) {
  const base = `/${prefix.replace(/\/$/, '')}`;
  const under = actions.find((entry) => entry.path === base || entry.path.startsWith(`${base}/`));
  if (under) return { kind: 'struts action', via: `${under.path} (${under.file})` };
  const servlet = servlets.find((pattern) => servletMatches(pattern, base) || pattern.startsWith(`${base}/`));
  return servlet ? { kind: 'servlet mapping', via: `${servlet} (web.xml)` } : null;
}

/**
 * Checks every rule; returns one message per route that nothing serves, naming the rule id, the
 * file and line, the pattern and the route tried.
 */
function unresolvedRoutes(rules, routes) {
  const problems = [];
  for (const rule of rules) {
    const where = `WAF rule ${rule.id} (${rule.file}:${rule.line})`;
    if (rule.shape === 'prefix') {
      if (!resolvePrefix(rule.route, routes)) {
        problems.push(`${where}: prefix /carlos/${rule.route} has no Struts action or servlet mapping beneath it`);
      }
      continue;
    }
    for (const candidate of expandRoutePattern(rule.route).filter((route) => !route.optional)) {
      if (!resolveRoute(`/${candidate.text}`, routes)) {
        problems.push(`${where}: route /carlos/${candidate.text} (from "${rule.route}") is neither a Struts action `
          + 'nor a servlet mapping; the exclusion no longer protects anything and the route now meets the full CRS rule set');
      }
    }
  }
  return problems;
}

function allRules() {
  return fs.readdirSync(WAF_DIR).filter((name) => name.endsWith('.conf')).sort()
    .flatMap((name) => wafRouteRules(path.join(WAF_DIR, name)));
}

function loadedRoutes() {
  return { actions: strutsActions(), servlets: servletPatterns() };
}

// ---------------------------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------------------------

test('shouldFindEveryAnchoredRoute_inTheExclusionFiles', () => {
  const rules = allRules();
  // A parser that silently drops rules would make the main check below vacuous, so the parsed count is
  // held to a second count that shares no code with the parser: physical lines that open a SecRule on
  // the request path and name /carlos. A rule the parser skips, or one written in a shape it merges
  // with its neighbour, shows up as a difference here.
  const opensRouteRule = /^\s*SecRule\s+(?:REQUEST_URI|REQUEST_URI_RAW|REQUEST_FILENAME)\S*\s+"@\w+\s+\^?\/carlos\//;
  for (const name of fs.readdirSync(WAF_DIR).filter((entry) => entry.endsWith('.conf')).sort()) {
    const independent = fs.readFileSync(path.join(WAF_DIR, name), 'utf8').split('\n')
      .filter((line) => !/^\s*#/.test(line) && opensRouteRule.test(line)).length;
    const parsed = rules.filter((rule) => rule.file === name).length;
    assert.equal(parsed, independent, `${name}: the parser found ${parsed} /carlos route rules but ${independent} lines open one`);
  }
  // And a floor just under today's 105, so that both counts collapsing to nothing cannot pass.
  assert.ok(rules.length >= 100, `expected the exclusion files to hold 100+ route rules (105 today), parsed ${rules.length}`);
  const routes = new Set(rules.map((rule) => rule.route));
  for (const known of ['CaseManagementEntry', 'eform/addEForm', 'rx/writeScript', 'ws/rs/eform/(?:[0-9]+/)?json']) {
    assert.ok(routes.has(known), `the parser did not find the known route "${known}"`);
  }
  assert.ok(rules.every((rule) => /^\d+$/.test(rule.id)), 'every rule must carry the id of its head rule');
  assert.ok(rules.every((rule) => rule.shape === 'exact'),
    'every exclusion is anchored with the (?:[;?]|$) terminator; a prefix match would also exclude unrelated URLs');
  assert.ok(fs.existsSync(path.join(WAF_DIR, 'REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf')));
});

test('shouldResolveEveryWafExclusionRoute_toStrutsActionOrServlet', () => {
  const problems = unresolvedRoutes(allRules(), loadedRoutes());
  // assert.ok, not deepEqual: the diff of two arrays would print every message a second time.
  assert.ok(problems.length === 0, `${problems.length} WAF exclusion route(s) resolve to nothing:\n${problems.join('\n')}\n`);
});

test('shouldExpandAlternationsAndOptionalGroups_intoRequiredRoutes', () => {
  const required = (pattern) => expandRoutePattern(pattern).filter((route) => !route.optional).map((route) => route.text);
  assert.deepEqual(required('eform/addEForm'), ['eform/addEForm']);
  assert.deepEqual(required('ws/rs/eform/(?:[0-9]+/)?json'), ['ws/rs/eform/json']);
  assert.deepEqual(expandRoutePattern('ws/rs/eform/(?:[0-9]+/)?json').map((route) => route.text).sort(),
    ['ws/rs/eform/0/json', 'ws/rs/eform/json']);
  assert.deepEqual(required('rx/(?:writeScript|WriteScript)').sort(), ['rx/WriteScript', 'rx/writeScript']);
  assert.deepEqual(required('a/(?:b|c(?:d)?)/e').sort(), ['a/b/e', 'a/c/e']);
  assert.throws(() => expandRoutePattern('a/(?=b)'), /unsupported group construct/);
});

test('shouldReportRuleIdAndRoute_whenAnActionIsRenamed', () => {
  // The negative control: without it a resolver that accepted everything would pass the check above.
  const rules = [
    { file: 'x.conf', line: 10, id: '1030', shape: 'exact', route: 'eform/addEForm' },
    { file: 'x.conf', line: 20, id: '1031', shape: 'exact', route: 'eform/addEFormRenamed' },
    { file: 'x.conf', line: 30, id: '1032', shape: 'exact', route: 'ws/rs/eform/(?:[0-9]+/)?json' },
  ];
  const routes = {
    actions: [{ path: '/eform/addEForm', file: 'struts-eform.xml' }],
    // No servlet mapping covers /ws, so the REST exclusion has nothing to protect.
    servlets: [],
  };
  const problems = unresolvedRoutes(rules, routes);
  assert.equal(problems.length, 2);
  assert.match(problems[0], /WAF rule 1031 \(x\.conf:20\): route \/carlos\/eform\/addEFormRenamed/);
  assert.match(problems[1], /WAF rule 1032 \(x\.conf:30\): route \/carlos\/ws\/rs\/eform\/json/);
  assert.deepEqual(unresolvedRoutes(rules.slice(0, 1), routes), []);
});

test('shouldNotTreatCatchAllFilterMappings_asRouteResolution', () => {
  const real = servletPatterns();
  assert.ok(!real.includes('/*'), 'the /* catch-all must never count as a route');
  assert.ok(real.includes('/ws/*'), 'the CXF servlet mapping /ws/* is expected in web.xml');
  // The PMMFilter is mapped on /PMmodule/*; being a filter it must not make an arbitrary route resolve.
  assert.ok(!real.includes('/PMmodule/*'), 'filter mappings are not servlet mappings');
  assert.equal(resolveRoute('/PMmodule/definitelyNotARoute', loadedRoutes()), null);
});

test('shouldIgnoreCommentedActions_whenReadingStrutsConfiguration', () => {
  const real = strutsActions();
  assert.ok(real.length > 500, `expected 500+ Struts actions across the modules, found ${real.length}`);
  assert.ok(real.every((entry) => !entry.path.includes('<') && entry.path.startsWith('/')));
  assert.ok(real.some((entry) => entry.path === '/eform/addEForm'));
  assert.ok(wildcardMatches('/a/*', '/a/b'));
  assert.ok(!wildcardMatches('/a/*', '/a/b/c'));
  assert.ok(wildcardMatches('/a/**', '/a/b/c'));
});
