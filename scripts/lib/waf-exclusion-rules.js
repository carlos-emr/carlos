/* SPDX-License-Identifier: GPL-2.0-or-later */
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
/*
 * Reads the packaged ModSecurity exclusion files (debian/assets/modsecurity/*.conf).
 *
 * Shared by scripts/waf-exclusion-routes.test.js (every exclusion's route must still be a route) and
 * by waf-clinical-text-corpus (every route that has a prose exclusion is driven through the front
 * door, and the arguments it fills are the ones the rule actually unhooks). It was extracted from the
 * first so that both read the conf with the same parser.
 */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

/** The context path every packaged exclusion is anchored on. */
const CONTEXT = '/carlos/';
/** Request variables that carry the URL path. */
const PATH_VARIABLES = new Set(['REQUEST_URI', 'REQUEST_URI_RAW', 'REQUEST_FILENAME']);
/** The terminator every anchored exclusion ends with: the route ends at `;` (path parameter), `?` or the end. */
const TERMINATOR = '(?:[;?]|$)';

// ---------------------------------------------------------------------------------------------
// ModSecurity configuration -> route exclusions
// ---------------------------------------------------------------------------------------------

/**
 * Splits a ModSecurity configuration into logical lines: a trailing backslash continues the
 * line, and comment lines are dropped. Returns each logical line with the physical line it began on.
 */
function logicalLines(text) {
  const physical = text.split('\n');
  const out = [];
  for (let i = 0; i < physical.length; i += 1) {
    if (/^\s*#/.test(physical[i])) continue;
    const start = i + 1;
    let joined = physical[i];
    while (/\\\s*$/.test(joined) && i + 1 < physical.length) {
      i += 1;
      joined = `${joined.replace(/\\\s*$/, ' ')}${physical[i].replace(/^\s+/, '')}`;
    }
    if (joined.trim() !== '') out.push({ line: start, text: joined });
  }
  return out;
}

/** Parses `SecRule <variables> "<operator>" "<actions>"`; the actions string is optional (chained links). */
function parseSecRule(text) {
  const match = /^\s*SecRule\s+(\S+)\s+"((?:[^"\\]|\\.)*)"(?:\s+"((?:[^"\\]|\\.)*)")?/.exec(text);
  if (!match) return null;
  return { variables: match[1], operator: match[2], actions: match[3] || '' };
}

/** `REQUEST_URI|REQUEST_FILENAME|!ARGS:x` -> ['REQUEST_URI', 'REQUEST_FILENAME'] (exclusions and selectors dropped). */
function variableNames(variables) {
  return variables.split('|')
    .filter((token) => !token.startsWith('!'))
    .map((token) => token.replace(/^[&]/, '').replace(/:.*$/, ''));
}

/**
 * Every exclusion in a configuration file that is keyed on a /carlos/ URL path, as
 * { file, line, id, shape, route }. `shape` is 'exact' (anchored with the terminator) or 'prefix'.
 * An operator or pattern that names /carlos/ but is not understood throws.
 */
function wafRouteRules(file) {
  const rules = [];
  let currentId = '?';
  for (const { line, text } of logicalLines(fs.readFileSync(file, 'utf8'))) {
    const rule = parseSecRule(text);
    if (!rule) continue;
    const id = /(?:^|[,\s])id:(\d+)/.exec(rule.actions);
    // A chain's links carry no id of their own: they belong to the head rule above them.
    if (id) currentId = id[1];
    if (!variableNames(rule.variables).some((name) => PATH_VARIABLES.has(name))) continue;
    if (!rule.operator.includes('/carlos')) continue;
    const where = `${path.basename(file)}:${line} (rule id ${currentId})`;
    const rx = /^@rx\s+(.*)$/.exec(rule.operator);
    if (rx) {
      const pattern = rx[1];
      assert.ok(pattern.startsWith(`^${CONTEXT}`),
        `${where}: the @rx pattern "${pattern}" names /carlos but is not anchored as ^${CONTEXT}<route>; `
        + 'teach this test the new shape before relying on it');
      let route = pattern.slice(`^${CONTEXT}`.length);
      let shape = 'prefix';
      if (route.endsWith(TERMINATOR)) {
        route = route.slice(0, -TERMINATOR.length);
        shape = 'exact';
      } else if (route.endsWith('$')) {
        route = route.slice(0, -1);
        shape = 'exact';
      }
      rules.push({ file: path.basename(file), line, id: currentId, shape, route });
      continue;
    }
    const literal = /^@(beginsWith|streq)\s+(\S+)$/.exec(rule.operator);
    assert.ok(literal && literal[2].startsWith(CONTEXT),
      `${where}: operator "${rule.operator}" names /carlos but is not @rx, @beginsWith or @streq on ${CONTEXT}<route>`);
    rules.push({
      file: path.basename(file), line, id: currentId,
      shape: literal[1] === 'streq' ? 'exact' : 'prefix',
      route: literal[2].slice(CONTEXT.length),
    });
  }
  return rules;
}

/**
 * The prose arguments each BEFORE-CRS rule unhooks, for the rules that are keyed on a /carlos/ route:
 * rule id -> { id, line, route, shape, args, removals, methods }. `args` is the set of ARGS names in the rule's
 * `ctl:ruleRemoveTarget...;ARGS:<name>` actions, `removals` maps each name to what is unhooked from it (a CRS tag such
 * as attack-rfi, or `id:932110` for one rule), and `methods` is the REQUEST_METHOD tests of its chain.
 * Chained links carry no id of their own, so they are read into the head above them.
 */
function exemptArguments(file) {
  const byId = new Map();
  let current = null;
  // The `ctl:ruleRemoveTarget...` actions of one rule, read into `current`. A rule may carry them on the head itself
  // (one rule with an id) as well as on a chained link, so both paths use this.
  const readRemovals = (rule) => {
    for (const ctl of rule.actions.matchAll(/ctl:ruleRemoveTarget(ByTag|ById)=([^;,"]+);ARGS:([^,"\s]+)/g)) {
      current.args.add(ctl[3]);
      if (!current.removals.has(ctl[3])) current.removals.set(ctl[3], new Set());
      current.removals.get(ctl[3]).add(ctl[1] === 'ByTag' ? ctl[2] : `id:${ctl[2]}`);
    }
  };
  for (const { line, text } of logicalLines(fs.readFileSync(file, 'utf8'))) {
    const rule = parseSecRule(text);
    if (!rule) continue;
    const id = /(?:^|[,\s])id:(\d+)/.exec(rule.actions);
    if (id) {
      current = null;
      if (variableNames(rule.variables).some((name) => PATH_VARIABLES.has(name)) && rule.operator.includes('/carlos')) {
        const head = routeOf(file, line, id[1], rule);
        current = { id: id[1], line, route: head.route, shape: head.shape, args: new Set(), removals: new Map(), methods: new Set() };
        byId.set(id[1], current);
        readRemovals(rule);
      }
      continue;
    }
    if (!current) continue;
    const method = /^@streq\s+(\w+)$/.exec(rule.operator);
    if (method && variableNames(rule.variables).includes('REQUEST_METHOD')) current.methods.add(method[1]);
    readRemovals(rule);
  }
  return byId;
}

/** The route and shape of a head rule that names /carlos (the reading wafRouteRules does for @rx). */
function routeOf(file, line, id, rule) {
  const rx = /^@rx\s+(.*)$/.exec(rule.operator);
  assert.ok(rx && rx[1].startsWith(`^${CONTEXT}`), `${path.basename(file)}:${line} (rule id ${id}): not an anchored ^${CONTEXT}<route> @rx`);
  let route = rx[1].slice(`^${CONTEXT}`.length);
  let shape = 'prefix';
  if (route.endsWith(TERMINATOR)) {
    route = route.slice(0, -TERMINATOR.length);
    shape = 'exact';
  } else if (route.endsWith('$')) {
    route = route.slice(0, -1);
    shape = 'exact';
  }
  return { route, shape };
}

module.exports = { CONTEXT, PATH_VARIABLES, TERMINATOR, exemptArguments, logicalLines, parseSecRule, variableNames, wafRouteRules };
