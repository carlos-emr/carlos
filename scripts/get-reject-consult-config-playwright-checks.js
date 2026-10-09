#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * GET-rejection and least-privilege sweep, consultation configuration. User path: Schedule > Consultations >
 * "Consultation configuration" (ViewShowAllServices) > Add Service / Delete Services / Show All Services (tick a
 * consultant) / Show All Institutions (tick a department) / Enable Consultation Request/Response.
 *
 * WHY. The five forms post to encounter/AddService, encounter/DelService, encounter/EnableConRequestResponse,
 * encounter/UpdateServiceSpecialists and encounter/UpdateInstitutionDepartment. Findings 107 and 108 were source
 * readings: that the actions accept GET, and that two of them are gated on `_con` read while the pages that post to
 * them need `_admin` or `_admin.consult`. Neither had been run. For each action the check
 *   1. drives the write through its own page and captures the POST the browser sent;
 *   2. replays that request as GET and as HEAD against rows it owns, and asserts h.assertRefused: the application's
 *      405/403 (its own response header or error page, never the front door's) and an unchanged row count;
 *   3. replays the same POST from a login that holds `_con` r and nothing else (a role the check builds, a valid
 *      token of its own), after proving that the full login's replay of that request writes, and asserts the same.
 *
 * WHAT THE ACTIONS DO TODAY (read from source, confirmed live). None checks the request method. HttpMethodGuardFilter
 * refuses GET/HEAD by action name, and the names AddService and DelService begin with its "add" and "del" prefixes,
 * so those two are answered 405 before the action runs; EnableConRequestResponse, UpdateServiceSpecialists and
 * UpdateInstitutionDepartment do not (the filter leaves "Update*" alone on purpose) and write on a GET and on a
 * HEAD. The privilege check inside the action is `_con` w for AddService, `_con` u for DelService and
 * EnableConRequestResponse, and `_con` r for the two Update actions, whose result JSP then redirects a login without
 * `_admin` or `_admin.consult` to /securityError: the write has already happened by then.
 *
 * KNOWN FAILURES AND CLAIMS. The entry asserts, one labelled step each, the (action, concern) pairs it CLAIMS; it runs
 * the page flow of every action it claims a pair of (the replays need the POST the page sent) and the probes of the
 * pairs it claims, records each outcome, and only then asserts, so one broken pair never hides another. CONSULT_CONFIG_ONLY and
 * CONSULT_CONFIG_EXCEPT (lib/form-claims.js: `<action>` or `<action>.<concern>`, lower case) choose them, so each
 * broken pair has its own manifest entry pinned on its own finding, the default entry leaves it out, and
 * scripts/form-claims.test.js proves the entries together claim every pair exactly once. The concerns are page (the
 * write through its page lands), get (GET and HEAD replays are refused) and restricted (the `_con` r login is
 * refused). A pair that cannot be judged (the page flow failed, the front door answered, the control replay did not
 * write) is reported under a label of its own, never the pinned one (lib/form-claims.js claimFailure).
 *
 * FIXTURES. Marker-named services, a consultant (professionalSpecialists, fName FAKE), an institution and a
 * department, seeded by SQL or created through the Add Service page, and one throwaway login with a role of its
 * own (lib/authz-read-fixture.js). Enable Request/Response is clinic-wide: the two property rows and the Referring
 * Doctor service are snapshotted and restored (lib/consult-config-state.js), and the legacy specialistsJavascript
 * script that Add Service, Delete Services and Update Service Specialists regenerate is cleaned of the owned
 * services' lines. Cleanup removes every owned row by key and asserts it, so this check must run with EXCLUSIVE=1.
 */
const h = require('./lib/playwright-harness');
const { clickAndAwaitReload, clickOpensPopupOrNavigates } = require('./lib/playwright-ui');
const claims = require('./lib/form-claims');
const state = require('./lib/consult-config-state');
const R = require('./lib/mutation-replay');
const { authzReadFixture } = require('./lib/authz-read-fixture');
const { signIn } = require('./lib/authz-read-probe');
const { markProblems, takeProblems } = require('./lib/form-problems');
const { captureRequest, replayParams } = require('./lib/get-reject-probe');
const { runWorkflow, expectValue } = require('./lib/workflow-session');

const NAME = 'get-reject-consult-config';
const q = h.sqlString;
const PAGES = ['consultations', 'consultation-config'];

/*
 * The five actions, in the order their pages are driven. `title` is the action's name in labels, `page` the menu
 * entry (suffix of the nav link) that reaches the form.
 */
const ACTIONS = [
  { key: 'addservice', title: 'AddService', route: '/encounter/AddService' },
  { key: 'delservice', title: 'DelService', route: '/encounter/DelService' },
  { key: 'enablerequest', title: 'EnableConRequestResponse', route: '/encounter/EnableConRequestResponse' },
  { key: 'updateservice', title: 'UpdateServiceSpecialists', route: '/encounter/UpdateServiceSpecialists' },
  { key: 'updateinstitution', title: 'UpdateInstitutionDepartment', route: '/encounter/UpdateInstitutionDepartment' },
];
const CONCERNS = ['page', 'get', 'restricted'];
const CLAIM_FORMS = ACTIONS.map(({ key }) => ({ key, concerns: CONCERNS }));
const ONLY = 'CONSULT_CONFIG_ONLY';
const EXCEPT = 'CONSULT_CONFIG_EXCEPT';

/**
 * CONSULT_CONFIG_ONLY and CONSULT_CONFIG_EXCEPT must be unset or lists of `<action>` / `<action>.<concern>` that
 * name real pairs and leave something to assert. Judged when the check runs, never when the module is required.
 */
function validatePin(env = process.env) {
  return claims.effectiveClaims({ only: env[ONLY], except: env[EXCEPT], onlyVariable: ONLY, exceptVariable: EXCEPT }, CLAIM_FORMS);
}

const PAGE_LABELS = {
  addservice: 'the Add Service page creates the typed service as active (POST encounter/AddService)',
  delservice: 'the Delete Services page inactivates only the ticked service (POST encounter/DelService)',
  enablerequest: 'the Enable Request/Response page stores both flags and activates Referring Doctor (POST encounter/EnableConRequestResponse)',
  updateservice: 'the Show All Services page links the ticked consultant to the service (POST encounter/UpdateServiceSpecialists)',
  updateinstitution: 'the Show All Institutions page links the ticked department to the institution (POST encounter/UpdateInstitutionDepartment)',
};
const concernLabel = (key, concern) => ({
  page: PAGE_LABELS[key],
  get: 'the request the page sends, replayed as GET and HEAD, is refused and writes nothing',
  restricted: 'a login holding only _con r is refused and its replay of the request writes nothing',
})[concern];
const generatedLabel = (key, concern) => `${ACTIONS.find((action) => action.key === key).title}: ${concernLabel(key, concern)}`;

/*
 * The labels a manifest pins are literals here: expectedFailure.step is checked against this file's text
 * (run-playwright-suite.js validateExpectedFailure), and a label assembled at run time cannot be found there.
 * scripts/form-claims.test.js proves each literal equals the label the run generates.
 */
const PINNED = Object.freeze({
  'enablerequest.get': 'EnableConRequestResponse: the request the page sends, replayed as GET and HEAD, is refused and writes nothing',
  'updateservice.get': 'UpdateServiceSpecialists: the request the page sends, replayed as GET and HEAD, is refused and writes nothing',
  'updateinstitution.get': 'UpdateInstitutionDepartment: the request the page sends, replayed as GET and HEAD, is refused and writes nothing',
  'updateservice.restricted': 'UpdateServiceSpecialists: a login holding only _con r is refused and its replay of the request writes nothing',
  'updateinstitution.restricted': 'UpdateInstitutionDepartment: a login holding only _con r is refused and its replay of the request writes nothing',
});
const stepLabel = (key, concern) => PINNED[claims.claimKey(key, concern)] || generatedLabel(key, concern);

const hasApplicationHeader = (response) => Object.prototype.hasOwnProperty.call(response.headers(), h.APPLICATION_HEADER);

async function workflow(s, { select = validatePin() } = {}) {
  const { sql, marker, provider } = s;
  const wanted = new Set(select);
  const claimed = (action, concern) => wanted.has(claims.claimKey(action.key, concern));
  const chosen = ACTIONS.filter((action) => CONCERNS.some((concern) => claimed(action, concern)));
  const has = (key) => chosen.some((action) => action.key === key);
  const results = new Map(chosen.map((action) => [action.key, {}]));
  const captured = {};
  const ids = {};
  const everOwned = new Set();

  // ---- state, fixtures and cleanup (registered first runs last) ----
  const baseline = state.snapshotSwitch(sql);
  s.cleanup(() => state.restoreSwitch(sql, baseline));

  const likeMarker = q(`${marker}%`);
  const ownedServices = `serviceDesc LIKE ${likeMarker}`;
  const ownedSpecialists = `fName='FAKE' AND lName=${q(marker)}`;
  s.cleanup(() => {
    // An empty id list must select nothing: `IN (0)` could still match a row of the demo data.
    const among = (column, values) => (values.length ? `${column} IN (${values.join(',')})` : '1=0');
    const column = (query) => sql.rows(query).map((row) => row[0]);
    const serviceIds = [...new Set([...everOwned, ...column(`SELECT serviceId FROM consultationServices WHERE ${ownedServices}`)])];
    const specialistIds = column(`SELECT specId FROM professionalSpecialists WHERE ${ownedSpecialists}`);
    const institutionIds = column(`SELECT id FROM Institution WHERE name LIKE ${likeMarker}`);
    const departmentIds = column(`SELECT id FROM Department WHERE name LIKE ${likeMarker}`);
    const serviceLinks = `(${among('serviceId', serviceIds)} OR ${among('specId', specialistIds)})`;
    const departmentLinks = `(${among('institutionId', institutionIds)} OR ${among('departmentId', departmentIds)})`;
    sql.execute([
      `DELETE FROM serviceSpecialists WHERE ${serviceLinks}`,
      `DELETE FROM InstitutionDepartment WHERE ${departmentLinks}`,
      `DELETE FROM consultationServices WHERE ${ownedServices}`,
      `DELETE FROM professionalSpecialists WHERE ${ownedSpecialists}`,
      `DELETE FROM Institution WHERE name LIKE ${likeMarker}`,
      `DELETE FROM Department WHERE name LIKE ${likeMarker}`,
    ].join(';'));
    // The actions regenerate the legacy script from the services as they stand, so it names the owned ones.
    state.removeScriptBlocks(sql, serviceIds);
    const remaining = sql.value(`SELECT ${[
      `(SELECT COUNT(*) FROM consultationServices WHERE ${ownedServices})`,
      `(SELECT COUNT(*) FROM serviceSpecialists WHERE ${serviceLinks})`,
      `(SELECT COUNT(*) FROM professionalSpecialists WHERE ${ownedSpecialists})`,
      `(SELECT COUNT(*) FROM Institution WHERE name LIKE ${likeMarker})`,
      `(SELECT COUNT(*) FROM Department WHERE name LIKE ${likeMarker})`,
      `(SELECT COUNT(*) FROM InstitutionDepartment WHERE ${departmentLinks})`,
      `(SELECT COUNT(*) FROM specialistsJavascript WHERE javascriptString LIKE ${q(`%${marker}%`)})`,
    ].join('+')}`);
    h.assert(remaining === '0', 'Owned consultation services, consultants, institutions, departments or links were not all removed');
    h.assert(state.scriptLinesFor(sql, serviceIds) === 0, 'The legacy consultation script still names an owned service');
  });

  const seedService = (name, what) => {
    const id = h.insertId(sql, `INSERT INTO consultationServices(serviceDesc,active) VALUES(${q(name)},'1')`, what);
    everOwned.add(id);
    return id;
  };
  const names = {
    add: `${marker}-Add`,
    probe: (tag) => `${marker}-A${tag}`,
    institution: `${marker}-Inst`,
    department: `${marker}-Dept`,
    service: `${marker}-Svc`,
  };
  if (has('delservice')) {
    ids.delTick = seedService(`${marker}-DelA`, 'service to delete');
    ids.delKeep = seedService(`${marker}-DelB`, 'service to keep');
  }
  if (has('updateservice')) {
    ids.svc = seedService(names.service, 'service');
    ids.spec = h.insertId(sql, `INSERT INTO professionalSpecialists
      (fName,lName,phone,fax,address,lastUpdated,institutionId,departmentId,hideFromView,deleted)
      VALUES('FAKE',${q(marker)},'555-0150','555-0151','1 Synthetic Way',NOW(),0,0,0,0)`, 'consultant');
  }
  if (has('updateinstitution')) {
    ids.inst = h.insertId(sql, `INSERT INTO Institution(name) VALUES(${q(names.institution)})`, 'institution');
    ids.dept = h.insertId(sql, `INSERT INTO Department(name) VALUES(${q(names.department)})`, 'department');
  }

  // ---- what a write to each action changes, and how to put the owned rows back before a probe ----
  const flags = state.FLAGS.map(q).join(',');
  const dropServices = (where) => {
    const doomed = sql.rows(`SELECT serviceId FROM consultationServices WHERE ${where}`).map((row) => row[0]);
    doomed.forEach((id) => everOwned.add(id));
    sql.execute(`DELETE FROM consultationServices WHERE ${where}`);
    state.removeScriptBlocks(sql, doomed);
  };
  const aim = {
    addservice: {
      table: 'consultationServices', expected: '0',
      where: (tag) => `serviceDesc=${q(names.probe(tag))}`,
      reset: (tag) => dropServices(`serviceDesc=${q(names.probe(tag))}`),
      overrides: (tag) => ({ service: names.probe(tag) }),
    },
    delservice: {
      table: 'consultationServices', expected: '1',
      where: () => `serviceId=${ids.delKeep} AND active='1'`,
      reset: () => sql.execute(`UPDATE consultationServices SET active='1' WHERE serviceId=${ids.delKeep}`),
      overrides: () => ({ service: ids.delKeep }),
    },
    enablerequest: {
      table: 'property', expected: '0',
      where: () => `name IN (${flags}) AND value='Y'`,
      reset: () => state.switchOff(sql, baseline),
      overrides: () => ({}),
    },
    updateservice: {
      table: 'serviceSpecialists', expected: '0',
      where: () => `serviceId=${ids.svc} AND specId=${ids.spec}`,
      reset: () => sql.execute(`DELETE FROM serviceSpecialists WHERE serviceId=${ids.svc}`),
      overrides: () => ({}),
    },
    updateinstitution: {
      table: 'InstitutionDepartment', expected: '0',
      where: () => `institutionId=${ids.inst} AND departmentId=${ids.dept}`,
      reset: () => sql.execute(`DELETE FROM InstitutionDepartment WHERE institutionId=${ids.inst}`),
      overrides: () => ({}),
    },
  };
  const rowsWatched = (action, tag) => sql.value(`SELECT COUNT(*) FROM ${aim[action.key].table} WHERE ${aim[action.key].where(tag)}`);

  // ---- open the configuration pages the way a clinician does ----
  let config;
  await s.step('Schedule > Consultations opens the consultation configuration pages', async () => {
    const { page: list } = await clickOpensPopupOrNavigates(s.schedule,
      s.schedule.getByRole('link', { name: 'Consultations', exact: true }),
      { context: s.context, recorder: s.recorder, label: 'consultations' });
    config = await s.popup(list, list.locator('a[href*="ViewShowAllServices"]'), 'consultation-config');
  });
  const menu = (suffix) => clickAndAwaitReload(config, config.locator(`nav a[href$="/${suffix}"]`), { label: suffix });
  const post = (action, click) => captureRequest(config, (url) => url.pathname.endsWith(action.route), click);

  /**
   * Run one concern's body and record how it ended (lib/form-claims.js outcomeOf). `needs` are the concerns it cannot
   * run without; unmet, it is recorded as not reached. The browser problems the configuration pages raised while a
   * page concern ran belong to it, so they are taken out of the recorder here.
   */
  async function attempt(action, concern, body, needs = []) {
    const entry = results.get(action.key);
    const label = stepLabel(action.key, concern);
    const unmet = needs.filter((need) => !(entry[need] && entry[need].ok));
    if (unmet.length) {
      entry[concern] = claims.blockedOutcome(`${unmet.join(' and ')} did not pass`);
      console.log(`  SKIP ${NAME}: ${label} -- ${entry[concern].message}`);
      return;
    }
    const mark = markProblems(s.recorder);
    let error;
    try {
      await body();
    } catch (caught) {
      error = caught;
    }
    const problems = concern === 'page' ? [...new Set(takeProblems(s.recorder, PAGES, mark))] : [];
    entry[concern] = claims.outcomeOf(error, problems);
    console.log(`  ${entry[concern].ok ? 'PASS' : 'FAIL'} ${NAME}: ${label}${entry[concern].ok ? '' : ` -- ${entry[concern].message}`}`);
  }

  // ---- page: each write driven through its own page ----
  const pageFlows = {
    async addservice(action) {
      await menu('ViewAddService');
      await config.locator('#service').fill(names.add);
      captured[action.key] = await post(action, () => clickAndAwaitReload(config, config.locator('input[type="submit"]')));
      h.assert(captured[action.key].params.get('service') === names.add, 'Add Service did not post the typed name');
      await expectValue(sql, `SELECT COUNT(*) FROM consultationServices WHERE serviceDesc=${q(names.add)} AND active='1'`, '1',
        'Add Service did not create exactly one active service');
    },
    async delservice(action) {
      await menu('ViewDeleteServices');
      const box = config.locator(`input[name="service"][value="${ids.delTick}"]`);
      await box.waitFor({ state: 'attached' });
      await box.check();
      const dialogs = await h.withExpectedDialogs(config, async () => {
        captured[action.key] = await post(action, () => clickAndAwaitReload(config, config.locator('input[name="delete"]')));
      });
      h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm', 'Delete Services did not ask for confirmation exactly once');
      const posted = captured[action.key].params.getAll('service');
      h.assert(posted.includes(ids.delTick) && !posted.includes(ids.delKeep), 'Delete Services did not post exactly the ticked service');
      await expectValue(sql, `SELECT active FROM consultationServices WHERE serviceId=${ids.delTick}`, state.INACTIVE,
        'Delete Services did not inactivate the ticked service');
      h.assert(sql.value(`SELECT active FROM consultationServices WHERE serviceId=${ids.delKeep}`) === state.ACTIVE,
        'Delete Services changed an unticked service');
    },
    async enablerequest(action) {
      // A known starting point: the check must see the page's write change the flags, whatever the clinic had.
      state.switchOff(sql, baseline);
      await menu('ViewEnableRequestResponse');
      await config.locator('#reqEnabled').check();
      await config.locator('#respEnabled').check();
      captured[action.key] = await post(action, () => clickAndAwaitReload(config,
        config.locator('form[action$="/EnableConRequestResponse"] input[type="submit"]')));
      h.assert(captured[action.key].params.get('consultRequestEnabled') === 'true'
        && captured[action.key].params.get('consultResponseEnabled') === 'true', 'The page did not post both ticked flags');
      const flag = (name) => `SELECT IFNULL(GROUP_CONCAT(IFNULL(value,'<NULL>')),'') FROM property WHERE name=${q(name)}`;
      await expectValue(sql, flag('consultRequestEnabled'), 'Y', 'Enable Request/Response did not store the request flag');
      h.assert(sql.value(flag('consultResponseEnabled')) === 'Y', 'Enable Request/Response did not store the response flag');
      h.assert(sql.value(`SELECT GROUP_CONCAT(active) FROM consultationServices WHERE serviceDesc=${q(state.REFERRING)}`) === state.ACTIVE,
        'Enabling responses did not activate the Referring Doctor service');
    },
    async updateservice(action) {
      await menu('ViewShowAllServices');
      await clickAndAwaitReload(config, config.getByRole('link', { name: names.service, exact: true }));
      const box = config.locator(`input[name="specialists"][value="${ids.spec}"]`);
      await box.waitFor({ state: 'attached' });
      h.assert(!(await box.isChecked()), 'A new service already lists the owned consultant');
      await box.check();
      captured[action.key] = await post(action, () => clickAndAwaitReload(config,
        config.locator('form[action$="/UpdateServiceSpecialists"] input[type="submit"]')));
      h.assert(captured[action.key].params.get('serviceId') === ids.svc && captured[action.key].params.getAll('specialists').includes(ids.spec),
        'The page did not post the service and the ticked consultant');
      await expectValue(sql, `SELECT GROUP_CONCAT(specId) FROM serviceSpecialists WHERE serviceId=${ids.svc}`, ids.spec,
        'The page did not link exactly the ticked consultant to the service');
    },
    async updateinstitution(action) {
      await menu('ViewShowAllInstitutions');
      await clickAndAwaitReload(config, config.getByRole('link', { name: names.institution, exact: true }));
      const box = config.locator(`input[name="specialists"][value="${ids.dept}"]`);
      await box.waitFor({ state: 'attached' });
      h.assert(!(await box.isChecked()), 'A new institution already lists the owned department');
      await box.check();
      captured[action.key] = await post(action, () => clickAndAwaitReload(config,
        config.locator('form[action$="/UpdateInstitutionDepartment"] input[type="submit"]')));
      h.assert(captured[action.key].params.get('id') === ids.inst && captured[action.key].params.getAll('specialists').includes(ids.dept),
        'The page did not post the institution and the ticked department');
      await expectValue(sql, `SELECT GROUP_CONCAT(departmentId) FROM InstitutionDepartment WHERE institutionId=${ids.inst}`, ids.dept,
        'The page did not link exactly the ticked department to the institution');
    },
  };
  for (const action of chosen) {
    await attempt(action, 'page', async () => {
      await pageFlows[action.key](action);
      h.assert(captured[action.key].method === 'POST' && captured[action.key].status > 0 && captured[action.key].status < 400,
        `${action.title}: the page's own request answered HTTP ${captured[action.key].status}`);
    });
  }

  // ---- get: the request the page sent, replayed as GET and as HEAD ----
  /** The answer is the application's own (its response header); a front-door block says nothing about the route. */
  const answeredByApplication = (action, who, response) => claims.precondition(hasApplicationHeader(response),
    `${action.title}: HTTP ${response.status()} to ${who} carried no ${h.APPLICATION_HEADER} header, so the front door answered and the route was not reached`);

  for (const action of chosen.filter((one) => claimed(one, 'get'))) {
    await attempt(action, 'get', async () => {
      const a = aim[action.key];
      const failures = [];
      for (const [method, tag] of [['GET', 'g'], ['HEAD', 'h']]) {
        a.reset(tag);
        const before = rowsWatched(action, tag);
        claims.precondition(before === a.expected,
          `${action.title}: the owned rows are not in the state a write would change (${before} counted, ${a.expected} expected)`);
        const params = replayParams(captured[action.key].params, a.overrides(tag));
        const url = new URL(`${captured[action.key].path}?${params}`, s.config.baseUrl.origin).toString();
        const response = await s.context.request.fetch(url, { method, maxRedirects: 0, failOnStatusCode: false });
        console.log(`  probe ${NAME}: ${action.title} ${method} -> HTTP ${response.status()}`);
        answeredByApplication(action, `the ${method} replay`, response);
        try {
          await h.assertRefused(s, { response, table: a.table, where: a.where(tag), before, label: `${action.title} ${method} replay` });
        } catch (error) {
          failures.push(String(error.message).split('\n')[0]);
        }
      }
      h.assert(!failures.length, failures.join(' | '));
    }, ['page']);
  }

  // ---- restricted: the same POST from a login that holds _con r and nothing else ----
  // Created only now, after the page flows: a new role's rows can change the order in which the database returns
  // the rows of a multi-object privilege lookup (finding 205), and the full login's pages must not depend on it.
  let restrictedLogin;
  const getRestrictedLogin = () => {
    if (!restrictedLogin) {
      restrictedLogin = (async () => {
        const fixture = authzReadFixture({ sql, marker, provider, testUser: s.config.testUser });
        s.cleanup(() => fixture.cleanup());
        const role = fixture.addRole({ _con: 'r' });
        h.assert(fixture.rolePrivileges(role).join() === '_con:r', 'The throwaway role does not hold exactly _con r');
        const who = await signIn(s, fixture.addLogin(role));
        return { ...who, token: await R.sessionToken(who.page, s.config.baseUrl) };
      })();
    }
    return restrictedLogin;
  };
  let fullToken;
  for (const action of chosen.filter((one) => claimed(one, 'restricted'))) {
    await attempt(action, 'restricted', async () => {
      const a = aim[action.key];
      const who = await claims.asPrecondition(getRestrictedLogin, 'the throwaway _con r login');
      // The control: the same request from the full login writes, so a refusal below is about WHO sent it, not about a
      // replay the application would never have accepted (a stale token, a missing field).
      await claims.asPrecondition(async () => {
        fullToken = fullToken || await R.sessionToken(s.schedule, s.config.baseUrl);
        a.reset('c');
        const before = rowsWatched(action, 'c');
        const replay = R.buildReplay(captured[action.key], { origin: s.config.baseUrl.origin, token: fullToken, overrides: a.overrides('c') });
        const response = await R.sendReplay(s.context, replay);
        h.assert(rowsWatched(action, 'c') !== before,
          `the full login's replay of the page request (HTTP ${response.status()}) did not change the rows the probe watches`);
      }, `${action.title}: the control replay`);
      a.reset('r');
      const before = rowsWatched(action, 'r');
      claims.precondition(before === a.expected,
        `${action.title}: the owned rows are not in the state a write would change (${before} counted, ${a.expected} expected)`);
      const replay = R.buildReplay(captured[action.key], { origin: s.config.baseUrl.origin, token: who.token, overrides: a.overrides('r') });
      const response = await R.sendReplay(who.context, replay);
      console.log(`  probe ${NAME}: ${action.title} POST by the _con r login -> HTTP ${response.status()}`);
      answeredByApplication(action, 'the _con r login', response);
      await h.assertRefused(s, { response, table: a.table, where: a.where('r'), before, label: `${action.title} replay by the _con r login` });
    }, ['page']);
  }

  // ---- assert the claimed pairs, in table order, one labelled step each ----
  for (const action of chosen) {
    for (const concern of CONCERNS.filter((name) => claimed(action, name))) {
      const label = stepLabel(action.key, concern);
      // Only the pair's own failure carries the pinned label; a precondition or a pair that was not reached is
      // reported under a label of its own (lib/form-claims.js).
      const failure = claims.claimFailure(results.get(action.key)[concern], label);
      if (failure) throw h.markFailedStep(new Error(failure.message), failure.label);
      console.log(`  ASSERTED ${NAME}: ${label}`);
    }
  }
}

// What scripts/form-claims.test.js and scripts/playwright-pin-validation.test.js read.
const testing = { validatePin, claimForms: CLAIM_FORMS, stepLabel, generatedLabel, PINNED };
if (require.main === module) runWorkflow(NAME, workflow, { openPatient: false });
module.exports = { workflow, ACTIONS, ...testing };
