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
 * cannot cover less than the unit contract does.
 *
 * Only `unconditionalMutators()` is read. CONDITIONAL_MUTATORS reject GET only
 * when a mutation-intent parameter is present, so driving them without one
 * proves nothing, and NON_MUTATOR_GATES are supposed to permit GET.
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

/** Every `<action name=... class=...>` pair across the modular Struts config. */
function strutsActions(directory = STRUTS_DIR) {
  const xml = fs.readdirSync(directory)
    .filter((name) => name.startsWith('struts') && name.endsWith('.xml'))
    .map((name) => fs.readFileSync(path.join(directory, name), 'utf8'))
    .join('\n');
  // ONE pattern. The config is hand-formatted and puts the class attribute on
  // the same line as the name or on the next one -- but \s+ already matches a
  // newline, so the first pattern covered both and a second "multiline" pattern
  // matched every multiline action a SECOND time. The live check then probed
  // those mutators twice and reported an inflated route count, which is the
  // number a reader uses to judge whether coverage shrank.
  return [...xml.matchAll(/<action\s+name="([^"]+)"\s+class="([^"]+)"/g)]
    .map((found) => ({ route: found[1], declared: found[2] }));
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
  CONTRACT_SOURCE, STRUTS_DIR, mutatorRoutes, routesForClass, strutsActions, unconditionalMutatorClasses,
};
