/* SPDX-License-Identifier: GPL-2.0-or-later */
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const test = require('node:test');
const claims = require('./lib/form-claims');
const { markProblems, settleProblems, takeProblems } = require('./lib/form-problems');
const manifest = require('./playwright-suite.json');

/*
 * The three table-driven form checks (form-catalog-smoke, form-rourke2020-growth and clinical-forms-save-reopen) and the
 * consultation configuration sweep (get-reject-consult-config, a table of actions) run one script under several
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

test('shouldLeaveAProblemInTheRecorder_whenAnotherConcernOwnsIt', () => {
  const recorder = recorderWith([
    ['pageErrors', { label: 'reopen-X', text: 'ReferenceError: docuemtn is not defined' }],
    ['pageErrors', { label: 'reopen-X', text: 'TypeError: something else' }],
  ]);
  const taken = takeProblems(recorder, ['reopen-X'], undefined, (text) => /docuemtn/.test(text));
  assert.deepEqual(taken, ['uncaught TypeError: something else']);
  assert.deepEqual(recorder.pageErrors.map((entry) => entry.text), ['ReferenceError: docuemtn is not defined']);
});

test('shouldKeepTheProblemsOfAnAssertionOnlyConcernAwayFromTheConcernThatOwnsThem', () => {
  // Finding 241's `bare` run opens Position Hazard before the fixture is completed. Once 241 is fixed the form opens and
  // its missing stylesheet (finding 235) is raised there too; the `open` concern that runs later must still meet it.
  const css = { label: 'form-PH', status: 404, resourceType: 'stylesheet', url: 'https://h/carlos/form/positionHazardStyle.css' };
  const recorder = recorderWith([['badResponses', css]]);
  const seen = new Set();
  assert.deepEqual(settleProblems(recorder, ['form-PH'], seen, { judge: false }), [], 'the bare run reports no problem');
  assert.equal(seen.size, 0, 'and does not remember it, so the open concern is not told it was already reported');
  assert.equal(recorder.badResponses.length, 0, 'but it is out of the recorder, so the run-wide judgement cannot trip on it');
  recorder.badResponses.push({ ...css });
  assert.deepEqual(settleProblems(recorder, ['form-PH'], seen), ['HTTP 404 on stylesheet https://h/carlos/form/positionHazardStyle.css']);
  recorder.badResponses.push({ ...css });
  assert.deepEqual(settleProblems(recorder, ['form-PH'], seen), [], 'a later concern does not repeat what an earlier one reported');
});

test('shouldHaveTheFirstConcernReportAProblem_whenItIsJudgedOnItsProblemsToo', () => {
  // The behaviour the assertion-only option exists to avoid: reporting the stylesheet on the bare run hid it from `open`.
  const css = { label: 'form-PH', status: 404, resourceType: 'stylesheet', url: 'https://h/carlos/form/positionHazardStyle.css' };
  const recorder = recorderWith([['badResponses', css]]);
  const seen = new Set();
  assert.equal(settleProblems(recorder, ['form-PH'], seen).length, 1);
  recorder.badResponses.push({ ...css });
  assert.deepEqual(settleProblems(recorder, ['form-PH'], seen), []);
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

/* ---- what a failure means for a pin ---- */

test('shouldCountABodyThatThrewAsAnAssertion_aPreconditionAsAPrecondition_andBrowserProblemsAlone', () => {
  assert.deepEqual(claims.outcomeOf(null, []), { ok: true });
  const assertion = claims.outcomeOf(new Error('the page shows no key\nsecond line'), ['console error: x']);
  assert.equal(assertion.kind, 'assertion');
  assert.equal(assertion.message, 'the page shows no key | 1 browser problem(s): console error: x');
  assert.equal(claims.outcomeOf(new claims.Precondition('no Print button'), []).kind, 'precondition');
  const only = claims.outcomeOf(null, ['uncaught A', 'uncaught B']);
  assert.equal(only.kind, 'problems');
  assert.deepEqual(only.problems, ['uncaught A', 'uncaught B']);
  assert.equal(claims.blockedOutcome('open did not pass').kind, 'blocked');
});

test('shouldTurnATrappedFailureIntoAPrecondition_whenAStepNeedsAControlThatIsNotThere', async () => {
  await assert.rejects(claims.asPrecondition(async () => { throw new Error('locator.click: Timeout 20000ms exceeded.\nCall log: ...'); }, 'the Print button'),
    (error) => error instanceof claims.Precondition && error.message === 'the Print button: locator.click: Timeout 20000ms exceeded.');
  assert.throws(() => claims.precondition(false, 'the Forms menu lists it 0 times'), claims.Precondition);
  assert.doesNotThrow(() => claims.precondition(true, 'fine'));
  // reaching() converts only the failures to find or click a control; an assertion about the page passes through.
  await assert.rejects(claims.reaching(async () => { throw new Error('locator.waitFor: Timeout 8000ms exceeded.'); }), claims.Precondition);
  await assert.rejects(claims.reaching(async () => { throw new Error('form-ALP rendered an error page'); }),
    (error) => !(error instanceof claims.Precondition) && /rendered an error page/.test(error.message));
});

test('shouldKeepThePinnedLabel_onlyForThePairsOwnFailure', () => {
  const label = 'Position Hazard: opens from the Forms menu';
  const known = /positionHazardStyle\.css/;
  assert.equal(claims.claimFailure({ ok: true }, label), null);
  // The pair's own failure: an assertion, or only the known browser problem.
  assert.deepEqual(claims.claimFailure(claims.outcomeOf(new Error('rendered an error page'), []), label, known),
    { label, message: 'rendered an error page' });
  const css = claims.outcomeOf(null, ['console error: Refused to apply style from .../positionHazardStyle.css']);
  assert.equal(claims.claimFailure(css, label, known).label, label);
  // Anything else reads as a failure elsewhere: the label no manifest pins.
  assert.equal(claims.claimFailure(claims.blockedOutcome('Save stored no usable row'), label, known).label, `${label} (not reached)`);
  assert.equal(claims.claimFailure(claims.outcomeOf(new claims.Precondition('the Forms menu lists it 0 times'), []), label, known).label,
    `${label} (precondition)`);
  const mixed = claims.outcomeOf(null, ['console error: Refused to apply style from .../positionHazardStyle.css', 'uncaught TypeError: x']);
  const verdict = claims.claimFailure(mixed, label, known);
  assert.equal(verdict.label, `${label} (other browser problems)`);
  assert.match(verdict.message, /1 browser problem\(s\) besides the known one: uncaught TypeError: x/);
  // With no signature declared, browser problems are the pair's own failure.
  assert.equal(claims.claimFailure(mixed, label).label, label);
  assert.match(claims.claimFailure(undefined, label).label, /\(no outcome\)$/);
});

test('shouldKeepNoBrowserProblemAsThePairsOwn_whenItIsPinnedOnAnAssertion', () => {
  const label = 'ALPHA: opens from the Forms menu';
  const stray = claims.outcomeOf(null, ['console error: Failed to load resource: 404', 'uncaught TypeError: x']);
  // Without a declaration the pair takes the problems as its own failure: the pin would stay "known" after a fix.
  assert.equal(claims.claimFailure(stray, label).label, label);
  // ASSERTION_ONLY: every problem is reported apart, whatever it says.
  const verdict = claims.claimFailure(stray, label, claims.ASSERTION_ONLY);
  assert.equal(verdict.label, `${label} (other browser problems)`);
  assert.match(verdict.message, /^2 browser problem\(s\) with the pinned assertion intact: console error: Failed to load resource: 404 \| uncaught TypeError: x$/);
  // The assertion itself, a precondition, an unreached concern and a pass behave as they do with a signature.
  assert.equal(claims.claimFailure(claims.outcomeOf(new Error('answered HTTP 500'), []), label, claims.ASSERTION_ONLY).label, label);
  assert.equal(claims.claimFailure(claims.outcomeOf(new Error('answered HTTP 500'), ['console error: 500']), label, claims.ASSERTION_ONLY).label, label,
    'a failed assertion is the pair\'s own failure even when the browser also reported problems');
  assert.equal(claims.claimFailure(claims.blockedOutcome('x'), label, claims.ASSERTION_ONLY).label, `${label} (not reached)`);
  assert.equal(claims.claimFailure(claims.outcomeOf(new claims.Precondition('p'), []), label, claims.ASSERTION_ONLY).label, `${label} (precondition)`);
  assert.equal(claims.claimFailure({ ok: true }, label, claims.ASSERTION_ONLY), null);
  // And it is not a pattern a pair could be mistaken for: it has no test().
  assert.equal(typeof claims.ASSERTION_ONLY.test, 'undefined');
  assert.ok(Object.isFrozen(claims.ASSERTION_ONLY));
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
    // What the pair declares about browser problems (a pattern, or ASSERTION_ONLY); the suites that pass it must declare one for every pin.
    knownOf(suite, key, concern) { return suite.module.knownProblem(key, concern); },
  },
  {
    label: 'form-rourke2020-growth',
    script: 'scripts/form-rourke2020-growth-playwright-checks.js',
    module: require('./form-rourke2020-growth-playwright-checks.js'),
    variables: { only: 'ROURKE_GROWTH_ONLY', except: 'ROURKE_GROWTH_EXCEPT' },
    labelOf(suite, key, concern) { return suite.module.stepLabel(key, concern); },
    generatedOf(suite, key, concern) { return suite.module.generatedLabel(key, concern); },
    knownOf(suite, key, concern) { return suite.module.knownProblem(key, concern); },
  },
  {
    label: 'clinical-forms-save-reopen',
    script: 'scripts/clinical-forms-save-reopen-playwright-checks.js',
    module: require('./clinical-forms-save-reopen-playwright-checks.js'),
    variables: { only: 'CLINICAL_FORMS_ONLY', except: 'CLINICAL_FORMS_EXCEPT' },
    labelOf(suite, key, concern) { return suite.module.labelFor(suite.module.FORMS.find((form) => form.claim === key), concern); },
    generatedOf(suite, key, concern) { return suite.module.generatedLabel(suite.module.FORMS.find((form) => form.claim === key), concern); },
  },
  {
    // The (action, concern) table of the consultation configuration writes: AddService, DelService,
    // EnableConRequestResponse, UpdateServiceSpecialists and UpdateInstitutionDepartment.
    label: 'get-reject-consult-config',
    script: 'scripts/get-reject-consult-config-playwright-checks.js',
    module: require('./get-reject-consult-config-playwright-checks.js'),
    variables: { only: 'CONSULT_CONFIG_ONLY', except: 'CONSULT_CONFIG_EXCEPT' },
    labelOf(suite, key, concern) { return suite.module.stepLabel(key, concern); },
    generatedOf(suite, key, concern) { return suite.module.generatedLabel(key, concern); },
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

  if (suite.knownOf) {
    test(`shouldDeclareHowEveryPinnedPairTreatsBrowserProblems_in${suite.label}`, () => {
      // A pair that takes the page's problems and declares nothing reads ANY of them as its own failure, so after its defect is fixed
      // a stray problem would keep the pin "known". Each pin says which it is: the signature of its browser problem, or an assertion.
      for (const claim of Object.keys(suite.module.PINNED)) {
        const [key, concern] = claim.split('.');
        const known = suite.knownOf(suite, key, concern);
        assert.ok(known === claims.ASSERTION_ONLY || known instanceof RegExp,
          `${claim} is pinned but declares neither a browser-problem signature nor claims.ASSERTION_ONLY`);
      }
    });
  }

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

test('shouldNeverPinAStepThatIsADemotedLabel_inAnyManifestEntry', () => {
  // claimFailure reports a precondition, an unreached concern, stray browser problems and a missing outcome under
  // `<label> (reason)` precisely so that no pin can name it; a manifest step that ends that way would turn a real
  // failure elsewhere into a "known failure".
  const demotions = /\((?:precondition|not reached|other browser problems|no outcome)\)$/;
  const pinned = manifest.checks.filter((check) => check.expectedFailure);
  assert.ok(pinned.length > 20, 'the manifest pins more than a handful of steps');
  for (const check of pinned) {
    assert.doesNotMatch(check.expectedFailure.step, demotions, `${check.name} pins a demoted label`);
  }
  // And the reasons listed above are exactly the ones claimFailure can produce.
  const labels = [
    claims.claimFailure(undefined, 'L'),
    claims.claimFailure(claims.blockedOutcome('x'), 'L'),
    claims.claimFailure(claims.outcomeOf(new claims.Precondition('p'), []), 'L'),
    claims.claimFailure(claims.outcomeOf(null, ['uncaught B']), 'L', /A/),
  ].map((failure) => failure.label);
  assert.deepEqual(labels, ['L (no outcome)', 'L (not reached)', 'L (precondition)', 'L (other browser problems)']);
  for (const label of labels) assert.match(label, demotions);
});

test('shouldAssertEveryPalliativeCarePair_whateverTheCallersShellExports', () => {
  const { spawnSync } = require('node:child_process');
  const expected = 'pc.open,pc.save,pc.redisplay,pc.reopen,pc.dialogs,pc.resave,pc.problems';
  for (const leaked of [{}, { CLINICAL_FORMS_ONLY: 'vt', CLINICAL_FORMS_EXCEPT: 'pc.open' }, { CLINICAL_FORMS_ONLY: 'not-a-pin' }]) {
    const result = spawnSync(process.execPath, ['-e', "console.log(require('./scripts/palliative-care-save-reopen-playwright-checks.js').select.join())"],
      { cwd: path.join(__dirname, '..'), env: { ...process.env, ...leaked }, encoding: 'utf8', timeout: 30000 });
    assert.equal(result.status, 0, result.stderr);
    assert.equal(result.stdout.trim(), expected, `a leaked ${Object.keys(leaked).join(', ') || 'nothing'} changed what the check asserts`);
  }
});

test('shouldRunTheSameCommandFromPackageJson_asTheManifestEntryDoes', () => {
  const scripts = JSON.parse(fs.readFileSync(path.join(__dirname, '..', 'package.json'), 'utf8')).scripts;
  for (const suite of SUITES) {
    for (const check of manifest.checks.filter((entry) => entry.script === suite.script)) {
      const variables = Object.entries(check.envSet || {}).filter(([, value]) => value).map(([name, value]) => `${name}=${value}`);
      const expected = [...variables, 'node', check.script].join(' ');
      assert.equal(scripts[`test:${check.name}-playwright`], expected,
        `${check.name}: the npm alias must run what the manifest entry runs (its non-empty envSet, then the script)`);
    }
  }
});
