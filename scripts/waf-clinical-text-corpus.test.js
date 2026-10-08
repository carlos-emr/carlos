/* SPDX-License-Identifier: GPL-2.0-or-later */
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const { PROSE_CORPUS } = require('./lib/clinical-prose-corpus');
const waf = require('./lib/waf-corpus');
const { FACTS, ROW_ORDER, controlStepLabel, corpusStepLabel, selectedKeys } = require('./lib/waf-corpus-rows');
const { exemptArguments } = require('./lib/waf-exclusion-rules');

/*
 * waf-clinical-text-corpus saves the clinical phrase corpus through every route that has a prose WAF exclusion.
 * Its browser half needs a packaged install; this is the half that does not: how the corpus is fitted to a column,
 * how the ModSecurity audit log is read back to name a rule, and whether the table of rows still agrees with the
 * exclusion files it claims to test.
 */

const ROOT = path.resolve(__dirname, '..');
const WAF_DIR = path.join(ROOT, 'debian', 'assets', 'modsecurity');
const BEFORE_CRS = path.join(WAF_DIR, 'REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf');
const AFTER_CRS = path.join(WAF_DIR, 'RESPONSE-999-EXCLUSION-RULES-AFTER-CRS.conf');
const characters = (text) => Array.from(text).length;

// ---------------------------------------------------------------------------------------------
// The corpus

test('shouldHoldEveryPhraseInOneValue_whenTheFieldHasNoLimit', () => {
  const { texts, clipped } = waf.corpusTexts();
  assert.equal(texts.length, 1);
  assert.deepEqual(clipped, []);
  for (const phrase of PROSE_CORPUS) assert.ok(texts[0].includes(phrase.text), `missing: ${phrase.label}`);
  assert.equal(texts[0].split('\n').length, PROSE_CORPUS.length, 'phrases are joined with newlines by default');
});

test('shouldStartWithThePastedLink_whenPhrasesAreJoined', () => {
  // CRS 931100 is anchored on the START of the argument: a link buried mid-text would not exercise it.
  const leading = PROSE_CORPUS.filter((phrase) => phrase.leading);
  assert.equal(leading.length, 1);
  assert.match(leading[0].text, /^https?:\/\/\d{1,3}(?:\.\d{1,3}){3}\//);
  assert.ok(waf.corpusTexts().texts[0].startsWith(leading[0].text));
  assert.ok(waf.corpusTexts({ limit: 300 }).texts[0].startsWith(leading[0].text));
});

test('shouldJoinWithTheJoiner_whenTheFieldIsASingleLineBox', () => {
  const { texts } = waf.corpusTexts({ joiner: ' ' });
  assert.equal(texts.length, 1);
  assert.ok(!texts[0].includes('\n'));
});

test('shouldPackWithinTheLimit_whenTheColumnIsShort', () => {
  for (const limit of [60, 80, 128, 255]) {
    const { texts } = waf.corpusTexts({ limit, joiner: ' ' });
    assert.ok(texts.length > 1 || limit >= 400, `${limit}: expected several values`);
    for (const text of texts) assert.ok(characters(text) <= limit, `${limit}: a value is ${characters(text)} characters`);
    // Every phrase is still sent: whole when it fits, clipped when it cannot.
    const joined = texts.join(' ');
    for (const phrase of PROSE_CORPUS) {
      assert.ok(joined.includes(phrase.text.slice(0, Math.min(phrase.text.length, limit - 1))), `${limit}: lost ${phrase.label}`);
    }
  }
});

test('shouldClipAndReportAPhrase_whenItIsLongerThanTheColumn', () => {
  const { texts, clipped } = waf.corpusTexts({ limit: 80, joiner: ' ' });
  assert.deepEqual(clipped.map((clip) => [clip.label, clip.from, clip.to]), [['pasted PACS link first', 81, 80]]);
  assert.equal(characters(texts[0]), 80, 'the clipped link is as long as the column allows');
  assert.match(texts[0], /^http:\/\/10\.0\.0\.5\/pacs\/study\?id=1&cmd=view/, 'the clip keeps the part CRS reads');
  const sixty = waf.corpusTexts({ limit: 60, joiner: ' ' }).clipped.map((clip) => clip.label);
  assert.deepEqual(sixty, ['pasted PACS link first', 'sentence semicolon']);
});

test('shouldRejectALimitBelowOne_whenCorpusTextsIsAsked', () => {
  assert.throws(() => waf.corpusTexts({ limit: 0 }), /positive number/);
  assert.throws(() => waf.corpusTexts({ limit: Number.NaN }), /positive number/);
});

test('shouldGiveEveryFieldAllItsValues_whenRequestsAreBuilt', () => {
  const { requests, clipped } = waf.corpusRequests([
    { name: 'reason', limit: 80, joiner: ' ' }, { name: 'notes', limit: 255 },
  ]);
  const reasons = new Set(requests.map((request) => request.reason));
  const notes = new Set(requests.map((request) => request.notes));
  assert.equal(requests.length, waf.corpusTexts({ limit: 80, joiner: ' ' }).texts.length, 'the busiest field sets the count');
  assert.equal(reasons.size, requests.length, 'each request carries a different reason value');
  assert.equal(notes.size, waf.corpusTexts({ limit: 255 }).texts.length, 'the shorter list wraps, using every notes value');
  assert.deepEqual(clipped.map((clip) => clip.field), ['reason']);
  assert.equal(waf.corpusRequests([{ name: 'ticklerMessage' }]).requests.length, 1);
});

test('shouldShareTheCorpusWithClinicalFreetext_insteadOfCopyingIt', () => {
  const freetext = fs.readFileSync(path.join(__dirname, 'clinical-freetext-playwright-checks.js'), 'utf8');
  assert.match(freetext, /require\('\.\/lib\/clinical-prose-corpus'\)/);
  assert.ok(!/label: 'sentence semicolon'/.test(freetext), 'clinical-freetext must not carry its own copy of the phrases');
  const check = fs.readFileSync(path.join(__dirname, 'waf-clinical-text-corpus-playwright-checks.js'), 'utf8');
  for (const phrase of PROSE_CORPUS) assert.ok(!check.includes(phrase.text), `the check copies "${phrase.label}"`);
});

// ---------------------------------------------------------------------------------------------
// The audit log

const line = (uri, method, status, messages) => JSON.stringify({
  transaction: { request: { method, uri }, response: { http_code: status }, messages },
});
const message = (ruleId, text, data) => ({ message: text, details: { ruleId, data } });

test('shouldNameTheRulesOfTheRefusedRoute_whenTheAuditLogIsRead', () => {
  const text = [
    line('/carlos/other/Route?x=1', 'POST', 403, [message('942100', 'SQL Injection Attack Detected via libinjection', 'Matched Data: s&1c found within ARGS:x: 1')]),
    line('/carlos/eform/addEForm?efmfid=1', 'POST', 403, [
      message('931100', 'Possible Remote File Inclusion (RFI) Attack: URL Parameter using IP Address', 'Matched Data: http://10.0.0.5 found within ARGS:Letter: http://10.0.0.5/pacs'),
      message('949110', 'Inbound Anomaly Score Exceeded (Total Score: 5)', ''),
    ]),
  ].join('\n');
  const rules = waf.describeRules(waf.auditTransactions(text, '/carlos/eform/addEForm'));
  assert.deepEqual(rules, ['931100 "Possible Remote File Inclusion (RFI) Attack: URL Parameter using IP Address" on ARGS:Letter']);
  assert.ok(!rules.join(' ').includes('10.0.0.5'), 'only the variable NAME is reported, never the matched data');
});

test('shouldIgnoreOtherMethodsAndMalformedLines_whenTheAuditLogIsRead', () => {
  const text = ['not json', '', '{"transaction":', line('/carlos/a', 'GET', 403, [message('932100', 'rce', 'found within ARGS:q: x')])].join('\n');
  assert.deepEqual(waf.auditTransactions(text, '/carlos/a', 'POST'), []);
  assert.equal(waf.auditTransactions(text, '/carlos/a', 'GET').length, 1);
});

test('shouldNotCountAnAllowedRequest_whenRulesAreDescribed', () => {
  const text = line('/carlos/a', 'POST', 200, [message('932100', 'rce', 'found within ARGS:q: x')]);
  assert.deepEqual(waf.describeRules(waf.auditTransactions(text, '/carlos/a')), []);
});

test('shouldTellARequestRuleFromAResponseRule_whenTheMatchedVariableIsRead', () => {
  assert.equal(waf.matchedVariable('Matched Data: <? found within RESPONSE_BODY: {"encounterText":"BP'), 'RESPONSE_BODY');
  assert.equal(waf.matchedVariable('Matched Data: ../ found within ARGS:instructions_522730: a'), 'ARGS:instructions_522730');
  assert.equal(waf.matchedVariable('Matched Data: ../ found within REQUEST_URI_RAW: /carlos/x?q=../'), 'REQUEST_URI_RAW');
  assert.equal(waf.matchedVariable(''), '');
});

test('shouldReadOnlyWhatWasAppended_whenAMarkIsTaken', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'waf-audit-'));
  try {
    const file = path.join(dir, 'audit.log');
    fs.writeFileSync(file, `${line('/carlos/a', 'POST', 403, [message('930100', 'old', 'found within ARGS:x: y')])}\n`);
    const audit = waf.auditLog(file);
    assert.equal(audit.available, true);
    const mark = audit.mark();
    fs.appendFileSync(file, `${line('/carlos/a', 'POST', 403, [message('931100', 'new', 'found within ARGS:x: y')])}\n`);
    const { rules } = await audit.rulesSince(mark, '/carlos/a', { waitMs: 200 });
    assert.deepEqual(rules, ['931100 "new" on ARGS:x']);
    const none = await audit.rulesSince(audit.mark(), '/carlos/a', { waitMs: 200 });
    assert.deepEqual(none.rules, []);
    assert.match(none.note, /holds no signature rule/);
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
});

test('shouldSayItCannotRead_whenTheAuditLogIsMissing', async () => {
  const audit = waf.auditLog(path.join(os.tmpdir(), 'no-such-dir', 'modsec_audit.log'));
  assert.equal(audit.available, false);
  assert.equal(audit.mark(), 0);
  const answer = await audit.rulesSince(0, '/carlos/a');
  assert.deepEqual(answer.rules, []);
  assert.match(answer.note, /cannot be read here/);
});

// ---------------------------------------------------------------------------------------------
// The rows against the exclusion files

const before = exemptArguments(BEFORE_CRS);

/** The route the exclusion covers, with the context path of the packaged install stripped. */
test('shouldListEveryRowOnce_inRowOrder', () => {
  assert.deepEqual([...ROW_ORDER].sort(), Object.keys(FACTS).sort());
  assert.equal(new Set(ROW_ORDER).size, ROW_ORDER.length);
});

test('shouldTestEveryExclusionTheBriefNames_exceptTheBritishColumbiaOne', () => {
  const covered = new Set(Object.values(FACTS).flatMap((fact) => fact.rules));
  // Tickler add and edit, Rx write and update, allergy add, measurement, prevention, manage document, lab status,
  // messenger, appointment add and update, case-management entry, eForm add, and the billing comment routes.
  const named = ['1104', '1105', '1108', '1109', '1113', '1116', '1117', '1118', '1120', '1123', '1125', '1126', '1010', '1030', '1040', '1045', '1134', '1135', '1136'];
  for (const id of named) assert.ok(covered.has(id), `no row tests exclusion ${id}`);
  assert.ok(!covered.has('1137'), '1137 is the British Columbia reprocess route: no BC work is in scope (issue #4439)');
  assert.ok(covered.has('1141'), 'the tickler list search is covered too (it is what shows the saved message back)');
});

test('shouldNameAnExistingRuleOnTheRowsRoute_forEveryRow', () => {
  for (const [key, fact] of Object.entries(FACTS)) {
    for (const id of fact.rules) {
      const rule = before.get(id);
      assert.ok(rule, `${key}: rule ${id} is not in the BEFORE-CRS exclusion file`);
      assert.equal(rule.route, fact.route, `${key}: rule ${id} covers /${rule.route}, not /${fact.route} (route names are case-sensitive)`);
      assert.ok(rule.methods.has(fact.method), `${key}: rule ${id} is keyed on ${[...rule.methods].join('/')}, the row sends ${fact.method}`);
    }
  }
});

test('shouldFillOnlyArgumentsTheRulesUnhook_forEveryRowWithRules', () => {
  for (const [key, fact] of Object.entries(FACTS)) {
    if (!fact.rules.length) continue;
    const unhooked = new Set(fact.rules.flatMap((id) => [...before.get(id).args]));
    for (const arg of fact.args) assert.ok(unhooked.has(arg), `${key}: no rule of ${fact.rules.join('/')} unhooks ARGS:${arg}`);
  }
});

test('shouldRelyOnTheAfterCrsPattern_whenARowHasNoNumberedRule', () => {
  const text = fs.readFileSync(AFTER_CRS, 'utf8');
  for (const [key, fact] of Object.entries(FACTS)) {
    if (fact.rules.length) continue;
    assert.ok(fact.afterCrs && fact.afterCrs.length, `${key}: a row without a rule names the after-CRS pattern it relies on`);
    for (const pattern of fact.afterCrs) {
      for (const tag of ['attack-sqli', 'attack-rce', 'attack-injection-php', 'attack-protocol', 'attack-lfi', 'attack-rfi']) {
        assert.ok(text.includes(`SecRuleUpdateTargetByTag "${tag}"`) && text.includes(pattern), `${key}: ${tag} ${pattern} is not in the after-CRS file`);
      }
    }
  }
});

// The open findings, held against the conf. Each asserts the state the finding asks for, so it fails (and is
// reported as a todo) while the defect stands and the run says so the day the conf is fixed. A fix may take
// any shape, so each looks for "some rule on this route unhooks this argument", not for one rule id.

/** Whether some BEFORE-CRS rule covers `route` (route patterns are regular expressions) and unhooks `tag` from ARGS:`arg`. */
function unhooks(route, arg, tag) {
  return [...before.values()].some((rule) => new RegExp(`^${rule.route}$`).test(route)
    && rule.removals.has(arg) && (!tag || rule.removals.get(arg).has(tag)));
}

test('shouldUnhookAttackRfiOnLetter_whenTheRichTextLetterIsSaved', { todo: 'finding 209' }, () => {
  // A letter that starts with a pasted link with an IP address trips 931100 (attack-rfi) on ARGS:Letter.
  assert.ok(unhooks('eform/addEForm', 'Letter', 'attack-rfi'));
});

test('shouldUnhookCustomNameOnTheRoutePageWritesTo_whenACustomDrugIsRenamed', { todo: 'finding 210' }, () => {
  // The page posts saveCustomName's customName to rx/WriteScript; 1107 unhooks it on rx/writeScript only.
  assert.ok(unhooks('rx/WriteScript', 'customName', 'attack-rfi'));
});

test('shouldUnhookTheSaveOnlyBoxes_whenAPrescriptionHoldsProse', { todo: 'finding 211' }, () => {
  // Save Only posts every card's boxes as instructions_<n>, drugName_<n>, comment_<n>; no rule or pattern names them.
  const everything = fs.readFileSync(BEFORE_CRS, 'utf8') + fs.readFileSync(AFTER_CRS, 'utf8');
  assert.match(everything, /instructions_/);
});

// ---------------------------------------------------------------------------------------------
// The run knobs and the step labels

test('shouldRunEveryRow_whenNoSelectionIsMade', () => {
  assert.deepEqual(selectedKeys({}), ROW_ORDER);
});

test('shouldNarrowTheRows_whenOnlyOrSkipIsSet', () => {
  assert.deepEqual(selectedKeys({ WAF_CORPUS_ONLY: 'chart-note, tickler-add' }), ['tickler-add', 'chart-note']);
  assert.ok(!selectedKeys({ WAF_CORPUS_SKIP: 'chart-note' }).includes('chart-note'));
  assert.deepEqual(selectedKeys({ WAF_CORPUS_ONLY: 'chart-note,tickler-add', WAF_CORPUS_SKIP: 'chart-note' }), ['tickler-add']);
});

test('shouldRefuseAnUnknownRow_whenOnlyOrSkipNamesOne', () => {
  assert.throws(() => selectedKeys({ WAF_CORPUS_ONLY: 'tickler-ad' }), /unknown row "tickler-ad"/);
  assert.throws(() => selectedKeys({ WAF_CORPUS_SKIP: 'nope' }), /unknown row "nope"/);
  assert.throws(() => selectedKeys({ WAF_CORPUS_ONLY: 'chart-note', WAF_CORPUS_SKIP: 'chart-note' }), /leave no row/);
});

test('shouldSpellTheCorpusStepLabelFromTheRowsTitleAndRoute_forEveryRow', () => {
  for (const key of ROW_ORDER) {
    assert.equal(FACTS[key].corpusStep, `${FACTS[key].title}: the clinical corpus saves through /${FACTS[key].route} and lands exactly`, key);
  }
});

test('shouldGiveEveryRowTwoDistinctStepLabels_soAPinNamesOnlyTheCorpusStep', () => {
  const labels = ROW_ORDER.flatMap((key) => [controlStepLabel(key), corpusStepLabel(key)]);
  assert.equal(new Set(labels).size, labels.length);
  for (const key of ROW_ORDER) {
    assert.match(corpusStepLabel(key), new RegExp(`through /${FACTS[key].route.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')} and lands exactly$`));
    assert.match(controlStepLabel(key), /\(control\)$/);
  }
});

// ---------------------------------------------------------------------------------------------
// The manifest entries and their pins

const MANIFEST = JSON.parse(fs.readFileSync(path.join(__dirname, 'playwright-suite.json'), 'utf8')).checks;
const PACKAGE = JSON.parse(fs.readFileSync(path.join(ROOT, 'package.json'), 'utf8')).scripts;
const FINDINGS = fs.readFileSync(path.join(ROOT, 'docs', 'ui-tests', 'app-findings-log.md'), 'utf8');
const entries = MANIFEST.filter((check) => check.script === 'scripts/waf-clinical-text-corpus-playwright-checks.js');
const main = entries.find((check) => check.name === 'waf-clinical-text-corpus');
const pinned = entries.filter((check) => check.expectedFailure);

test('shouldRunThroughTheFrontDoorTierOnly_forEveryEntryOfTheCheck', () => {
  assert.ok(main, 'the check has its main entry');
  for (const entry of entries) {
    assert.deepEqual(entry.tiers, ['front-door'], `${entry.name}: a WAF check means nothing through bare Tomcat`);
    assert.equal(entry.assertsDatabase, true);
    assert.deepEqual(entry.provinces, ['ON'], `${entry.name}: the billing comment rows are Ontario`);
    assert.ok(entry.env.includes('EXPECT_FRONT_DOOR'));
  }
});

test('shouldPinEachOpenFindingToItsOwnRowsCorpusStep_andLeaveTheMainEntryUnpinned', () => {
  assert.equal(main.expectedFailure, undefined, 'the main entry runs the rows that pass');
  assert.ok(pinned.length >= 1);
  for (const entry of pinned) {
    const [row] = selectedKeys({ WAF_CORPUS_ONLY: entry.envSet.WAF_CORPUS_ONLY });
    assert.equal(entry.envSet.WAF_CORPUS_ONLY, row, `${entry.name} runs exactly one row`);
    assert.equal(entry.expectedFailure.step, corpusStepLabel(row), `${entry.name}: the pin must name the row's corpus step`);
    assert.notEqual(entry.expectedFailure.step, controlStepLabel(row), `${entry.name}: the control step is never pinned`);
    const finding = new RegExp(`^\\| ${entry.expectedFailure.finding} \\|.*\\| (open|issue-filed|needs-live-check) \\|$`, 'm');
    assert.match(FINDINGS, finding, `${entry.name}: finding ${entry.expectedFailure.finding} must be in the log and not fixed`);
  }
});

test('shouldSkipInTheMainEntryExactlyTheRowsThatArePinned', () => {
  const skipped = main.envSet.WAF_CORPUS_SKIP.split(',').sort();
  assert.deepEqual(skipped, pinned.map((entry) => entry.envSet.WAF_CORPUS_ONLY).sort());
  assert.deepEqual(selectedKeys(main.envSet).sort(), ROW_ORDER.filter((key) => !skipped.includes(key)).sort());
});

test('shouldGiveEveryEntryTheNpmAliasThatRunsIt_withTheSameRowSelector', () => {
  for (const entry of entries) {
    const alias = `test:${entry.name}-playwright`;
    assert.ok(PACKAGE[alias], `${entry.name}: no npm alias ${alias}`);
    const [selector, value] = Object.entries(entry.envSet)[0];
    assert.equal(PACKAGE[alias], `${selector}=${value} node ${entry.script}`);
  }
});
