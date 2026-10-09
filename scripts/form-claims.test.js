/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const test = require('node:test');
const claims = require('./lib/form-claims');
const { markProblems, takeProblems } = require('./lib/form-problems');
const manifest = require('./playwright-suite.json');

/*
 * The three table-driven form checks (form-catalog-smoke, form-rourke2020-growth and clinical-forms-save-reopen) run one script under several
 * manifest entries, and an entry names in an environment variable the (form, concern) pairs it asserts
 * (scripts/lib/form-claims.js). That arrangement is only honest if the entries together claim every pair exactly once:
 * a pair claimed by nobody is asserted by nothing, and a pair claimed twice hides behind two pins. These tests read the
 * real manifest, so a new form, a new concern or a new entry that breaks the partition fails here, not silently.
 */
const FORMS = [
  { key: 'alpha', concerns: ['open', 'save'] },
  { key: 'beta', concerns: ['open', 'save', 'print'] },
];

test('shouldClaimEveryConcernOfEveryForm_whenNothingIsSelected', () => {
  assert.deepEqual(claims.effectiveClaims({}, FORMS),
    ['alpha.open', 'alpha.save', 'beta.open', 'beta.save', 'beta.print']);
});

test('shouldClaimAWholeForm_whenOnlyNamesTheForm', () => {
  assert.deepEqual(claims.effectiveClaims({ only: 'beta' }, FORMS), ['beta.open', 'beta.save', 'beta.print']);
  assert.deepEqual(claims.effectiveClaims({ only: ' alpha.save , beta.print ' }, FORMS), ['alpha.save', 'beta.print']);
});

test('shouldLeaveOutWhatExceptNames_whenBothAreGiven', () => {
  assert.deepEqual(claims.effectiveClaims({ except: 'alpha,beta.print' }, FORMS), ['beta.open', 'beta.save']);
  assert.deepEqual(claims.effectiveClaims({ only: 'beta', except: 'beta.open' }, FORMS), ['beta.save', 'beta.print']);
});

test('shouldRefuseToRun_whenATokenNamesNothingReal', () => {
  const run = (selection) => () => claims.effectiveClaims({ onlyVariable: 'THE_ONLY', exceptVariable: 'THE_EXCEPT', ...selection }, FORMS);
  assert.throws(run({ only: 'not-a-pin' }), /THE_ONLY must be unset or a comma-separated list/);
  assert.throws(run({ except: 'Alpha' }), /THE_EXCEPT must be unset or a comma-separated list/);
  assert.throws(run({ only: 'gamma' }), /THE_ONLY names an unknown form "gamma"/);
  assert.throws(run({ only: 'alpha.print' }), /THE_ONLY names alpha\.print, but alpha has only these concerns: open, save/);
  assert.throws(run({ only: 'alpha', except: 'alpha' }), /leave nothing to assert/);
});

test('shouldReportAPairThatNoEntryClaims_orThatTwoEntriesClaim', () => {
  const universe = ['a.open', 'a.save', 'b.open'];
  assert.deepEqual(claims.partitionProblems([
    { name: 'one', claims: ['a.open', 'a.save'] }, { name: 'two', claims: ['b.open'] },
  ], universe), []);
  const problems = claims.partitionProblems([
    { name: 'one', claims: ['a.open', 'a.save'] }, { name: 'two', claims: ['a.save', 'c.open'] },
  ], universe).join(' | ');
  assert.match(problems, /a\.save is claimed by one and two/);
  assert.match(problems, /b\.open is claimed by no entry/);
  assert.match(problems, /c\.open is claimed by two but is not a pair of the table/);
});

/** The recorder the harness keeps, with one entry in each list for two pages. */
function recorderWith(entries) {
  const recorder = { pageErrors: [], consoleIssues: [], requestFailures: [], badResponses: [], unexpectedDialogs: [] };
  for (const [list, entry] of entries) recorder[list].push(entry);
  return recorder;
}

test('shouldTakeOnlyTheLabelledEntries_andLeaveTheRestForTheRunToJudge', () => {
  const recorder = recorderWith([
    ['pageErrors', { label: 'form-X', text: 'ReferenceError: x is not defined\n    at y' }],
    ['pageErrors', { label: 'chart', text: 'TypeError: chart' }],
    ['badResponses', { label: 'form-X', status: 404, resourceType: 'stylesheet', url: 'https://h/carlos/form/a.css?demographic_no=77' }],
    ['unexpectedDialogs', { label: 'reopen-X', type: 'alert' }],
  ]);
  const taken = takeProblems(recorder, ['form-X', 'reopen-X']);
  assert.deepEqual(taken, [
    'uncaught ReferenceError: x is not defined',
    'HTTP 404 on stylesheet https://h/carlos/form/a.css',
    'an unexpected alert dialog was raised and dismissed',
  ]);
  assert.deepEqual(recorder.pageErrors.map((entry) => entry.label), ['chart']);
  assert.equal(taken.join('').includes('77'), false, 'a query string can carry the patient number');
});

test('shouldTakeOnlyWhatArrivedAfterTheMark_whenAMarkIsGiven', () => {
  const recorder = recorderWith([['pageErrors', { label: 'form-X', text: 'Error: old' }]]);
  const mark = markProblems(recorder);
  recorder.pageErrors.push({ label: 'form-X', text: 'Error: new' });
  assert.deepEqual(takeProblems(recorder, ['form-X'], mark), ['uncaught Error: new']);
  assert.deepEqual(recorder.pageErrors.map((entry) => entry.text), ['Error: old']);
});

test('shouldExcuseARequestTheBrowserAbandoned_butNotOneThatFailedOnItsOwn', () => {
  const abandoned = { label: 'form-X', url: 'https://h/a.png', resourceType: 'image', errorText: 'net::ERR_ABORTED' };
  Object.defineProperty(abandoned, 'navigatedAway', { value: () => true });
  const failed = { label: 'form-X', url: 'https://h/b.css', resourceType: 'stylesheet', errorText: 'net::ERR_ABORTED' };
  Object.defineProperty(failed, 'navigatedAway', { value: () => false });
  const recorder = recorderWith([['requestFailures', abandoned], ['requestFailures', failed]]);
  assert.deepEqual(takeProblems(recorder, ['form-X']), ['stylesheet https://h/b.css failed (net::ERR_ABORTED)']);
  assert.equal(recorder.requestFailures.length, 0, 'the abandoned request is taken out too, so it cannot fail the run later');
});

/* ---- the real tables and the real manifest ---- */

const SUITES = [
  {
    label: 'form-catalog-smoke',
    script: 'scripts/form-catalog-smoke-playwright-checks.js',
    module: require('./form-catalog-smoke-playwright-checks.js'),
    variables: { only: 'FORM_CATALOG_ONLY', except: 'FORM_CATALOG_EXCEPT' },
    // The label the run gives a pair, and the one it would give with no PINNED entry.
    labelOf(suite, key, concern) { return suite.module.stepLabel(key, concern); },
    generatedOf(suite, key, concern) { return suite.module.generatedLabel(key, concern); },
  },
  {
    label: 'form-rourke2020-growth',
    script: 'scripts/form-rourke2020-growth-playwright-checks.js',
    module: require('./form-rourke2020-growth-playwright-checks.js'),
    variables: { only: 'ROURKE_GROWTH_ONLY', except: 'ROURKE_GROWTH_EXCEPT' },
    labelOf(suite, key, concern) { return suite.module.stepLabel(key, concern); },
    generatedOf(suite, key, concern) { return suite.module.generatedLabel(key, concern); },
  },
  {
    label: 'clinical-forms-save-reopen',
    script: 'scripts/clinical-forms-save-reopen-playwright-checks.js',
    module: require('./clinical-forms-save-reopen-playwright-checks.js'),
    variables: { only: 'CLINICAL_FORMS_ONLY', except: 'CLINICAL_FORMS_EXCEPT' },
    labelOf(suite, key, concern) { return suite.module.labelFor(suite.module.FORMS.find((form) => form.claim === key), concern); },
    generatedOf(suite, key, concern) { return suite.module.generatedLabel(suite.module.FORMS.find((form) => form.claim === key), concern); },
  },
];

function entriesOf(suite) {
  return manifest.checks.filter((check) => check.script === suite.script).map((check) => {
    const env = check.envSet || {};
    assert.deepEqual(Object.keys(env).sort(), [suite.variables.except, suite.variables.only].sort(),
      `${check.name}: envSet must set both claim variables, so an exported value in the caller's shell cannot leak in`);
    for (const variable of Object.keys(env)) assert.ok(check.env.includes(variable), `${check.name}: env must list ${variable}`);
    return {
      name: check.name, check,
      claims: claims.effectiveClaims({
        only: env[suite.variables.only], except: env[suite.variables.except],
        onlyVariable: suite.variables.only, exceptVariable: suite.variables.except,
      }, suite.module.claimForms),
    };
  });
}

for (const suite of SUITES) {
  const universe = suite.module.claimForms.flatMap((form) => form.concerns.map((concern) => claims.claimKey(form.key, concern)));

  test(`shouldClaimEveryPairOnce_by${suite.label}ManifestEntries`, () => {
    const entries = entriesOf(suite);
    assert.ok(entries.length > 1, 'the default entry and at least one variant');
    assert.deepEqual(claims.partitionProblems(entries, universe), []);
  });

  test(`shouldGiveEveryPinnedStepItsOwnPair_in${suite.label}`, () => {
    const used = new Set();
    for (const { name, check, claims: owned } of entriesOf(suite)) {
      if (!check.expectedFailure) continue;
      const labels = new Map(owned.map((claim) => {
        const [key, concern] = claim.split('.');
        return [suite.labelOf(suite, key, concern), claim];
      }));
      assert.ok(labels.has(check.expectedFailure.step),
        `${name}: expectedFailure.step is not the label of any pair the entry claims (${[...labels.keys()].slice(0, 3).join(' | ')} ...)`);
      used.add(labels.get(check.expectedFailure.step));
    }
    // A PINNED literal that no entry pins has gone stale, and one that differs from the generated label would
    // pin a step the run never prints.
    for (const [claim, label] of Object.entries(suite.module.PINNED)) {
      const [key, concern] = claim.split('.');
      assert.ok(universe.includes(claim), `PINNED names ${claim}, which is not a pair of the table`);
      assert.equal(label, suite.generatedOf(suite, key, concern), `PINNED ${claim} differs from the label the run generates`);
      assert.ok(used.has(claim), `PINNED ${claim} is pinned by no manifest entry`);
    }
  });
}
