/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { createRequire } = require('node:module');
const test = require('node:test');
const vm = require('node:vm');
const { sqlString } = require('./lib/playwright-harness');
const { vascularTrackerCatalogFixture } = require('./lib/vascular-tracker-catalog-fixture');
const definitions = require('./fixtures/vascular-tracker-catalog.json');
const definition = definitions[0];

const patient = '101';
const marker = 'FAKE-vt-caller';
const oldType = [1, 'WT', 'Clinic weight', 'Clinic description', 'in kg', '5', '2020-01-01 00:00:00'];
const oldRule = [5, 'Clinic numeric', null, 500, 0, 0, null, 1, null, null];
const newType = [2, ...definition.metadata, '7', '2026-10-25 00:00:00'];
const newRule = [7, ...definition.rule];
const normal = value => value.replace(/\s+/g, ' ').trim();
const setupFailure = 'controlled native VT setup failure';

/** Stateful SQL boundary for the real catalog fixture and the callers' registered cleanups. */
function database({ initialized = false, omitDefinition, foreignReference = false } = {}) {
  const state = { measurementType: [structuredClone(oldType)], validations: [structuredClone(oldRule)] };
  if (initialized) {
    for (const [index, entry] of definitions.entries()) {
      const validation = index + 7;
      state.validations.push([validation, ...structuredClone(entry.rule)]);
      if (index !== omitDefinition) state.measurementType.push([
        index + 2, ...structuredClone(entry.metadata), String(validation), '2026-10-25 00:00:00',
      ]);
    }
  }
  const before = structuredClone(state);
  const events = [];
  const cleanups = [];
  const weights = [];
  const registry = [['Vascular Tracker', '../form/SetupForm.do?formName=VTForm&demographic_no=', 'formVTForm', '1']];
  let registeredForms = 0;
  let secondPatientRemoved = false;
  let snapshots = 0;
  let popupCount = 0;
  const sql = {
    rows(statement) {
      const query = normal(statement);
      if (query.startsWith('SELECT vt_expected.vtType,vt_expected.vtInstruction FROM ')) {
        // Execute the readiness query against the same catalog rows that the real
        // ownership fixture snapshots. A partial dictionary must still be refused.
        assert.equal((statement.match(/AS vtType/g) || []).length, definitions.length);
        assert.match(query, /WHERE NOT EXISTS \(SELECT 1 FROM measurementType mt WHERE mt.type=vt_expected.vtType AND mt.measuringInstruction=vt_expected.vtInstruction\)/);
        events.push('check dictionary readiness');
        return definitions.flatMap(({ metadata: [type, , , instruction] }) => {
          assert.ok(statement.includes(`SELECT ${sqlString(type)} AS vtType,${sqlString(instruction)} AS vtInstruction`),
            'Readiness must query every shipped type and instruction');
          return state.measurementType.some(row => row[1] === type && row[4] === instruction)
            ? [] : [[type, instruction]];
        });
      }
      const catalog = query.match(/ FROM (measurementType|validations) ORDER BY id$/);
      if (catalog) {
        snapshots++;
        events.push(`snapshot catalog ${catalog[1]}`);
        return state[catalog[1]].map(row => [String(row[0]), JSON.stringify(row)]);
      }
      if (/^SELECT (?:\*|form_name,form_value,form_table,hidden) FROM encounterForm/.test(query)) {
        return structuredClone(registry);
      }
      if (/^SELECT (?:note_id FROM casemgmt_note|id FROM preventions|id FROM measurements)/.test(query)) return [];
      throw new Error(`Unexpected rows operation: ${query}`);
    },
    value(statement) {
      const query = normal(statement);
      if (/^SELECT COUNT\(\*\) FROM measurements(?:Deleted)? WHERE BINARY type=/.test(query)) {
        return foreignReference ? '1' : '0';
      }
      if (/^SELECT \(SELECT COUNT\(\*\) FROM measurementType WHERE validation=/.test(query)) return '0';
      const deletion = query.match(/^DELETE FROM (measurementType|validations) WHERE id=(\d+)/);
      if (deletion) {
        const [, table, id] = deletion;
        const index = state[table].findIndex(row => String(row[0]) === id);
        if (index < 0 || !query.includes(`BINARY ${sqlString(JSON.stringify(state[table][index]))}`)) return '0';
        assert.equal(weights.length, 0, 'Catalog deletion must follow owned patient child cleanup');
        events.push(`delete catalog ${table}`);
        state[table].splice(index, 1);
        return '1';
      }
      if (/^SELECT provider_no FROM provider WHERE provider_no<>/.test(query)) return '999997';
      if (/^INSERT INTO demographic /.test(query)) return '102';
      if (/^INSERT INTO measurements /.test(query)) {
        const id = String(1001 + weights.length);
        weights.push(id);
        return id;
      }
      if (/^SELECT COUNT\(\*\) FROM demographic WHERE demographic_no=102 AND last_name=/.test(query)) {
        return secondPatientRemoved ? '0' : '1';
      }
      if (/^SELECT COUNT\(\*\) FROM demographic WHERE demographic_no=102$/.test(query)) return secondPatientRemoved ? '0' : '1';
      if (/^SELECT COUNT\(\*\) FROM encounterForm WHERE form_value LIKE /.test(query)) return String(registeredForms);
      if (/^SELECT COUNT\(\*\) FROM measurements WHERE demographicNo=101$/.test(query)) return '0';
      if (/^SELECT \(SELECT COUNT\(\*\) FROM (?:casemgmt_note|formAnnual)/.test(query)) return '0';
      throw new Error(`Unexpected value operation: ${query}`);
    },
    execute(statement) {
      for (const part of statement.split(';').map(normal).filter(Boolean)) {
        if (/^INSERT INTO encounterForm /.test(part)) { registeredForms++; events.push('register form'); continue; }
        if (/^DELETE FROM encounterForm WHERE /.test(part)) { registeredForms--; events.push('delete owned registration'); continue; }
        if (/^UPDATE demographic SET /.test(part)) continue;
        if (/^DELETE FROM demographic WHERE demographic_no=102 AND last_name=/.test(part)) {
          secondPatientRemoved = true;
          continue;
        }
        if (/^DELETE FROM measurements WHERE demographicNo(?:=| IN )/.test(part)) {
          weights.length = 0;
          events.push('delete owned measurements');
          continue;
        }
        if (/^DELETE (?:FROM (?:hash_audit|casemgmt_issue_notes|casemgmt_note_ext|casemgmt_note_link|casemgmt_note|casemgmt_issue|casemgmt_cpp|casemgmt_note_lock|casemgmt_tmpsave|eChart|dxresearch|preventions|measurementsExt|measurementsDeleted|form[A-Za-z0-9]+|appointmentArchive|appointment|immunizations|demographicExt|demographicArchive) WHERE |x FROM preventionsExt x JOIN )/.test(part)) {
          events.push('delete owned child');
          continue;
        }
        throw new Error(`Unexpected execute operation: ${part}`);
      }
    },
  };
  return {
    sql, state, before, events, cleanups,
    get registeredForms() { return registeredForms; },
    get snapshots() { return snapshots; },
    get popupCount() { return popupCount; },
    setup({ partial = false } = {}) {
      popupCount++;
      events.push('native VT setup');
      if (!initialized) {
        state.validations.push(structuredClone(newRule));
        if (!partial) state.measurementType.push(structuredClone(newType));
      }
    },
    async cleanup() { for (const callback of [...cleanups].reverse()) await callback(); },
  };
}

/** Evaluate the complete real caller; replace only browser/session integration boundaries. */
function caller(file, fixture) {
  const filename = path.join(__dirname, file);
  const realRequire = createRequire(filename);
  const module = { exports: {} };
  const harness = {
    assert: (condition, message) => assert.ok(condition, message), sqlString,
    wireStrictPage() {}, markFailedStep: (error, label) => Object.assign(error, { label }),
  };
  const boundaryRequire = request => {
    if (request === './lib/playwright-harness') return harness;
    if (request === './lib/vascular-tracker-catalog-fixture') return { vascularTrackerCatalogFixture };
    if (request === './lib/playwright-ui') return { openFormsMenu: async () => {} };
    if (request === './lib/chart-lock-cleanup') return { releaseChartLocks: async () => {} };
    if (request === './echart-navbar-modules-playwright-checks') return { waitForNavbars: async () => {} };
    return realRequire(request);
  };
  vm.runInNewContext(fs.readFileSync(filename, 'utf8'), {
    require: boundaryRequire, module, exports: module.exports, Buffer, URL, URLSearchParams, process,
    __dirname: path.dirname(filename), console: { log() {}, error() {} },
  }, { filename });
  return module.exports;
}

function locator(extra = {}) {
  const result = {
    first() { return result; }, async waitFor() {}, async dispatchEvent() {}, async evaluate() {},
    async count() { return 1; }, getByRole() { return result; }, locator() { return result; },
    ...extra,
  };
  return result;
}

function baseSession(fixture) {
  return {
    sql: fixture.sql, patient, provider: '999998', marker, config: { baseUrl: 'https://fixture.test/carlos' },
    recorder: { pageErrors: [], consoleIssues: [], requestFailures: [], badResponses: [], unexpectedDialogs: [] },
    cleanup(callback) { fixture.cleanups.push(callback); },
  };
}

async function clinicalScenario({ key = 'VT', initialized = false, omitDefinition, fail = false, foreignReference = false } = {}) {
  const fixture = database({ initialized, omitDefinition, foreignReference });
  const clinical = caller('clinical-forms-save-reopen-playwright-checks.js', fixture);
  const form = clinical.FORMS.find(candidate => candidate.key === key);
  assert.ok(form, 'Use the real caller form definition');
  const chart = { locator: () => locator(), url: () => `https://fixture.test/carlos/CaseManagementView?demographicNo=${patient}` };
  const page = {
    url: () => `https://fixture.test/carlos/form/SetupForm?demographic_no=${patient}`,
    getByRole: () => locator(), async close() {}, isClosed: () => false,
    locator(selector) {
      assert.equal(selector, 'input[name="demographic_no"]', 'The VT2 open assertion reads its actual hidden patient field');
      return locator({ inputValue: async () => patient });
    },
  };
  const session = {
    ...baseSession(fixture), chart: async () => { fixture.events.push('open chart'); return chart; },
    context: { newPage: async () => ({ async goto() {}, async close() {} }) },
    async popup() {
      fixture.setup();
      if (fail) throw new Error(setupFailure);
      return page;
    },
  };
  const running = clinical.workflow(session, { forms: [form], select: [`${form.claim}.open`] });
  if (!initialized || omitDefinition !== undefined) {
    await assert.rejects(running, /VT dictionary initialization is required before the suite residue baseline/);
    assert.equal(fixture.popupCount, 0, 'An uninitialized clinical workflow must not open VT');
    assert.equal(fixture.registeredForms, 0, 'Refusal must precede Forms-menu registration');
    assert.equal(fixture.cleanups.length, 0, 'Refusal must precede cleanup registration');
    assert.equal(fixture.snapshots, 0, 'Refusal must precede catalog ownership setup');
    assert.deepEqual(fixture.events, ['check dictionary readiness'], 'Refusal must precede browser or mutation work');
    assert.deepEqual(fixture.state, fixture.before, 'Refusal must retain the complete preexisting dictionary');
    return fixture;
  }
  if (fail) await assert.rejects(running, new RegExp(setupFailure));
  else await running;
  assert.equal(fixture.popupCount, 1, 'The actual caller must reach its native VT form popup');
  return fixture;
}

async function isolationScenario({ initialized = false, foreignReference = false } = {}) {
  const fixture = database({ initialized, foreignReference });
  const isolation = caller('echart-two-patient-isolation-playwright-checks.js', fixture);
  const history = {
    locator: selector => locator({
      evaluateAll: async () => ['1001'], innerText: async () => 'PatientAlpha 61.2',
      async click() { assert.equal(selector, 'input[onclick*="GraphMeasurements"]'); },
    }),
    async close() {},
  };
  const chart = {
    locator: () => locator({
      innerText: async () => 'PatientAlpha 61.2',
      evaluateAll: async () => [`open('SetupForm?demographic_no=${patient}')`],
    }),
  };
  const session = {
    ...baseSession(fixture), chart: async () => chart,
    context: {
      waitForEvent: async () => ({
        url: () => `https://fixture.test/carlos/encounter/GraphMeasurements?demographic_no=${patient}`,
        status: () => 200, headers: () => ({ 'content-type': 'image/png' }),
        body: async () => Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]),
      }),
      pages: () => [],
    },
    step: async (label, body) => body(),
    async popup(parent, control, label) {
      if (label === 'a-history-alone') return history;
      assert.equal(label, 'a-form-alone', 'Stop at the actual first generic Add Form popup');
      fixture.setup();
      throw new Error(setupFailure);
    },
  };
  await assert.rejects(isolation.workflow(session), new RegExp(setupFailure));
  assert.equal(fixture.popupCount, 1, 'The actual first generic form popup must initialize VT');
  return fixture;
}

for (const key of ['VT', 'VT2']) {
  test(`clinical ${key} refuses first-use initialization before registrations, cleanup or browser work`, async () => {
    await clinicalScenario({ key });
  });
  test(`clinical ${key} refuses a dictionary missing only its last shipped definition`, async () => {
    await clinicalScenario({ key, initialized: true, omitDefinition: definitions.length - 1 });
  });
  for (const fail of [false, true]) {
    test(`clinical ${key} ${fail ? 'failed native open' : 'native open'} preserves initialized definitions after owned child cleanup`, async () => {
      const fixture = await clinicalScenario({ key, initialized: true, fail, foreignReference: true });
      const snapshotsBeforeCleanup = fixture.snapshots;
      assert.equal(fixture.events[0], 'check dictionary readiness');
      assert.ok(fixture.events.indexOf('register form') < fixture.events.indexOf('open chart'));
      await fixture.cleanup();
      assert.equal(fixture.registeredForms, 0, 'Owned registrations must be removed');
      assert.deepEqual(fixture.state, fixture.before, 'All preexisting definitions and validations must survive');
      assert.equal(fixture.events.some(event => event.startsWith('delete catalog')), false,
        'Even referenced preexisting definitions never belong to this run');
      assert.ok(fixture.snapshots > snapshotsBeforeCleanup, 'Catalog cleanup must recheck its original snapshot');
      const finalCatalogRead = fixture.events.lastIndexOf('snapshot catalog measurementType');
      for (const event of ['delete owned registration', 'delete owned child', ...(key === 'VT2' ? ['delete owned measurements'] : [])]) {
        assert.ok(fixture.events.indexOf(event) >= 0 && fixture.events.lastIndexOf(event) < finalCatalogRead,
          `${event} must finish before catalog cleanup`);
      }
    });
  }
}

test('the first generic chart form can be VT and failed native setup still restores its catalog', async () => {
  const fixture = await isolationScenario();
  await fixture.cleanup();
  assert.deepEqual(fixture.state, fixture.before);
  assert.ok(fixture.events.indexOf('delete owned measurements') < fixture.events.indexOf('delete catalog measurementType'));
});

test('two-patient chart preserves every preexisting clinic catalog row', async () => {
  const fixture = await isolationScenario({ initialized: true });
  await fixture.cleanup();
  assert.deepEqual(fixture.state, fixture.before);
  assert.equal(fixture.events.some(event => event.startsWith('delete catalog')), false);
});

test('two-patient chart retains new definitions with a foreign clinical reference and fails cleanup', async () => {
  const fixture = await isolationScenario({ foreignReference: true });
  const retained = structuredClone(fixture.state);
  await assert.rejects(fixture.cleanup(), /clinical references; refusing cleanup/);
  assert.deepEqual(fixture.state, retained);
  assert.equal(fixture.events.some(event => event.startsWith('delete catalog')), false);
});
