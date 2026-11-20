#!/usr/bin/env node
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
 * The routes of every action the GET/HEAD rejection contract covers, read from
 * the contract itself.
 *
 * WHY DERIVED AND NOT LISTED. CLAUDE.md makes registering a new mutator in
 * MutatorActionGetRejectionContractUnitTest a build requirement: the discovery
 * scan fails until it is classified. A hand-written list here would fall behind
 * that the first time somebody added an action, and the live check would quietly
 * stop covering it -- the failure mode the unit contract was built to prevent,
 * reintroduced one layer up. Reading the Java manifest means the live check
 * cannot cover less than the UNCONDITIONAL half of the unit contract.
 *
 * THAT IS THE WHOLE CLAIM, deliberately. Only `unconditionalMutators()` is read.
 * NON_MUTATOR_GATES are supposed to permit GET, so there is nothing to drive.
 * CONDITIONAL_MUTATORS reject GET only when a mutation-intent parameter is
 * present -- and supplying one against a LIVE deployment is not a probe worth
 * running: if the guard under test is broken, the request it takes to prove that
 * is the request that performs the mutation. Those actions are covered by the
 * focused *2ActionTest CLAUDE.md requires alongside the manifest entry, where
 * the dependency is mocked and a failure costs nothing. Do not widen this to
 * them without solving that first.
 */

const fs = require('node:fs');
const path = require('node:path');

const REPO_ROOT = path.join(__dirname, '..', '..');
const CONTRACT_SOURCE = path.join(
  REPO_ROOT, 'src', 'test', 'java', 'io', 'github', 'carlos_emr', 'carlos', 'app', 'contract',
  'MutatorActionGetRejectionContractUnitTest.java',
);
const STRUTS_DIR = path.join(REPO_ROOT, 'src', 'main', 'webapp', 'WEB-INF', 'classes');

/** The fully-qualified class names registered as unconditional mutators. */
function unconditionalMutatorClasses(source = fs.readFileSync(CONTRACT_SOURCE, 'utf8')) {
  const start = source.indexOf('static Stream<Arguments> unconditionalMutators()');
  const end = source.indexOf('private static final Set<String> CONDITIONAL_MUTATORS');
  if (start < 0 || end < 0 || end < start) {
    throw new Error(
      'MutatorActionGetRejectionContractUnitTest no longer has the unconditionalMutators() block this reads. '
      + 'The live check derives its routes from it, so it must be updated with the contract rather than left '
      + 'to silently cover nothing.',
    );
  }
  return [...source.slice(start, end).matchAll(/Arguments\.of\(\s*"([a-zA-Z0-9_.]+2Action)"/g)]
    .map((found) => found[1]);
}

/**
 * The classes registered as CONDITIONAL mutators, so the live check can say what
 * it is not probing instead of leaving the omission to be inferred.
 *
 * Deliberately NOT turned into probes: see the header. Naming them in the report
 * is the difference between "12 mutator routes refused GET" and a reader taking
 * that for the whole contract.
 */
function conditionalMutatorClasses(source = fs.readFileSync(CONTRACT_SOURCE, 'utf8')) {
  const start = source.indexOf('private static final Set<String> CONDITIONAL_MUTATORS');
  if (start < 0) {
    throw new Error(
      'MutatorActionGetRejectionContractUnitTest no longer declares CONDITIONAL_MUTATORS. The live check '
      + 'reports the classes it deliberately does not probe, and a silent empty list would read as full '
      + 'coverage of the contract.',
    );
  }
  const end = source.indexOf(');', start);
  return [...source.slice(start, end).matchAll(/"([a-zA-Z0-9_.]+2Action)"/g)].map((found) => found[1]);
}

/** Every `<action name=... class=...>` pair across the modular Struts config. */
function strutsActions(directory = STRUTS_DIR) {
  const xml = fs.readdirSync(directory)
    .filter((name) => name.startsWith('struts') && name.endsWith('.xml'))
    .map((name) => fs.readFileSync(path.join(directory, name), 'utf8'))
    .join('\n');
  // THE START TAG FIRST, THEN ITS ATTRIBUTES. The old pattern required class to
  // follow name with only whitespace between them, so `<action name="x"
  // method="y" class="Z">` matched nothing and that mutator dropped silently
  // out of the live check -- a security check quietly covering less, which is
  // the failure this suite exists to report. Measured on release/2026.08: both
  // spellings find the same 1,077 actions today, so this is protection against
  // a reformat rather than a fix for a present gap, and the test pins that
  // equality so the change stays behaviour-preserving.
  //
  // ONE pass, not two. An earlier attempt added a second "multiline" pattern
  // beside the first; \s+ already matches a newline, so every multiline action
  // matched twice and the live check probed those mutators twice and reported
  // an inflated route count -- the number a reader uses to judge whether
  // coverage shrank.
  const actions = [];
  for (const tag of xml.matchAll(/<action\b[^>]*>/g)) {
    const name = tag[0].match(/\bname="([^"]+)"/);
    const declared = tag[0].match(/\bclass="([^"]+)"/);
    if (name && declared) {
      actions.push({ route: name[1], declared: declared[1] });
    }
  }
  return actions;
}

/**
 * Resolve a class to the routes that reach it.
 *
 * The config names an action either by its fully-qualified class or by its
 * Spring bean id, which is the simple name with a lowercase initial
 * (`class="securityDelete2Action"`). Both forms are live in struts-admin.xml, so
 * matching only the FQCN silently drops those routes from the check.
 */
function routesForClass(className, actions) {
  const simple = className.split('.').pop();
  const bean = simple.charAt(0).toLowerCase() + simple.slice(1);
  return actions
    .filter((action) => action.declared === className || action.declared === bean)
    .map((action) => action.route);
}

/**
 * Every route the live GET-rejection check should drive.
 *
 * @returns {{route: string, className: string, simpleName: string}[]}
 */
function mutatorRoutes(options = {}) {
  const classes = options.classes || unconditionalMutatorClasses();
  const actions = options.actions || strutsActions();
  const routes = [];
  const unmapped = [];
  for (const className of classes) {
    const found = routesForClass(className, actions);
    if (!found.length) {
      unmapped.push(className);
      continue;
    }
    for (const route of found) {
      routes.push({ route, className, simpleName: className.split('.').pop() });
    }
  }
  return { routes, unmapped };
}

module.exports = {
  CONTRACT_SOURCE, STRUTS_DIR, conditionalMutatorClasses, mutatorRoutes, routesForClass, strutsActions,
  unconditionalMutatorClasses,
};
