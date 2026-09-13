/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const {
  CONTRACT_SOURCE, mutatorRoutes, routesForClass, strutsActions, unconditionalMutatorClasses,
} = require('./lib/mutator-routes');
const { EXPECTED_STATUS, diagnose, orderedRoutes } = require('./mutator-get-rejection-live-playwright-checks');

const SOURCE = fs.readFileSync(
  path.join(__dirname, 'mutator-get-rejection-live-playwright-checks.js'), 'utf8',
);
const CONTRACT = fs.readFileSync(CONTRACT_SOURCE, 'utf8');

/*
 * The live check's value rests entirely on covering the same actions the unit
 * contract does. If the derivation silently returns fewer routes -- a renamed
 * block, a config format it does not parse -- the check still reports green
 * while testing less, which is precisely the drift CLAUDE.md made the unit
 * contract a build requirement to prevent.
 */

test('the route list is derived from the contract, not written out by hand', () => {
  assert.ok(!/appointment\/AddRecord|admin\/SecurityUpdate/.test(SOURCE),
    'a hard-coded route in the check would fall behind the contract the first time an action was added');
  assert.match(SOURCE, /require\('\.\/lib\/mutator-routes'\)/);
});

test('every class the contract registers resolves to at least one route', () => {
  const { routes, unmapped } = mutatorRoutes();
  assert.deepEqual(unmapped, [],
    `these contract-registered actions have no Struts route: ${unmapped.join(', ')}`);
  const classes = unconditionalMutatorClasses();
  assert.ok(classes.length >= 30, `expected the contract to register many mutators, found ${classes.length}`);
  assert.ok(routes.length >= classes.length,
    'an action mapped under two routes should contribute both, so routes cannot be fewer than classes');
});

test('a Spring bean id in the config resolves as well as a fully-qualified class', () => {
  // struts-admin.xml declares class="securityDelete2Action" and
  // class="clinicNbrManage2Action". Matching only the FQCN would silently drop
  // those two mutators from the live check while everything still looked green.
  const actions = strutsActions();
  assert.deepEqual(
    routesForClass('io.github.carlos_emr.carlos.admin.web.SecurityDelete2Action', actions),
    ['admin/SecurityDelete'],
  );
  assert.deepEqual(
    routesForClass('io.github.carlos_emr.carlos.admin.web.ClinicNbrManage2Action', actions),
    ['admin/clinicNbrManage'],
  );
});

test('the parser fails loudly if the contract block is renamed', () => {
  // Returning an empty list would leave the check passing while driving nothing.
  assert.throws(
    () => unconditionalMutatorClasses('class Something { /* no such block */ }'),
    /no longer has the unconditionalMutators\(\) block/,
  );
});

test('only unconditional mutators are driven', () => {
  // A CONDITIONAL_MUTATOR rejects GET only when a mutation-intent parameter is
  // present, and this check deliberately sends none -- so including them would
  // produce failures that are correct behaviour.
  const classes = unconditionalMutatorClasses();
  // The DECLARATIONS, not the first mention: both names appear in the class
  // javadoc above them, and slicing from there gives an empty block that would
  // make this test pass while checking nothing.
  const conditionalBlock = CONTRACT.slice(
    CONTRACT.indexOf('private static final Set<String> CONDITIONAL_MUTATORS'),
    CONTRACT.indexOf('private static final Set<String> NON_MUTATOR_GATES'),
  );
  const conditional = [...conditionalBlock.matchAll(/"([a-zA-Z0-9_.]+2Action)"/g)].map((found) => found[1]);
  assert.ok(conditional.length > 0, 'the contract must still have a conditional list for this to mean anything');
  for (const name of conditional) {
    assert.ok(!classes.includes(name), `${name} is conditional and must not be driven without mutation intent`);
  }
});

test('a route that ends the session is driven last', () => {
  // logout is on the list and is supposed to answer 405. If it does not, the GET
  // ends the session and every route after it reports 401/403 -- one real
  // finding turning into thirty misleading ones.
  const ordered = orderedRoutes(mutatorRoutes().routes);
  const last = ordered[ordered.length - 1];
  assert.match(last.route, /logout/i);
  assert.equal(ordered.filter((entry) => /logout/i.test(entry.route)).length, 1);
  // And nothing was lost by the reordering.
  assert.equal(ordered.length, mutatorRoutes().routes.length);
});

test('405 is what the contract actually does, not a guess', () => {
  assert.equal(EXPECTED_STATUS, 405);
  assert.match(CONTRACT, /SC_METHOD_NOT_ALLOWED|405/);
  // And the actions really answer that way rather than throwing.
  const action = fs.readFileSync(path.join(
    __dirname, '..', 'src', 'main', 'java', 'io', 'github', 'carlos_emr', 'carlos', 'appointment',
    'pageUtil', 'AppointmentAddRecord2Action.java',
  ), 'utf8');
  assert.match(action, /sendError\(HttpServletResponse\.SC_METHOD_NOT_ALLOWED, "POST required"\)/);
});

test('each wrong status is explained as what it means, not just as "not 405"', () => {
  // A 200 and a 404 are different findings and want different follow-up; a run
  // that says only "expected 405" makes the reader re-derive that every time.
  assert.match(diagnose(200), /one-click CSRF/);
  assert.match(diagnose(302), /redirected instead of refusing/);
  assert.match(diagnose(404), /route did not resolve/);
  assert.match(diagnose(403), /authentication rather than method/);
  assert.match(diagnose(500), /past the point the contract requires/);
});

test('the GETs carry no parameters, so a hole is found without being fed', () => {
  // If the contract does NOT hold for some route, the request reaches the action
  // body. Sending no demographic_no, appointment_no or form data means it finds
  // nothing to act on.
  assert.match(SOURCE, /context\.request\.get\(url, \{ timeout, maxRedirects: 0 \} \)|context\.request\.get\(url, \{ timeout, maxRedirects: 0 \}\)/);
  assert.ok(!/demographic_no=|appointment_no=|params:/.test(SOURCE),
    'the check must not supply the data a real mutation would need');
});

test('redirects are observed, not followed', () => {
  // Following a 302 would report the status of wherever it landed, turning "it
  // redirected instead of refusing" into a 200 or a 404 about another page.
  assert.match(SOURCE, /maxRedirects: 0/);
});

test('the session is a real logged-in provider', () => {
  // An anonymous request is refused before it reaches the action, so it would
  // prove nothing about the method check.
  assert.match(SOURCE, /await login\(context, config, recorder\)/);
});
