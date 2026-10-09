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
const { FACTS, OUTCOMES, ROW_ORDER, controlStepLabel, corpusStepFor, corpusStepLabel, outcomeOf, selectedKeys } = require('./lib/waf-corpus-rows');
const { exemptArguments, logicalLines } = require('./lib/waf-exclusion-rules');

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
  assert.ok(covered.has('1141'), 'the tickler list search term is covered too (exclusion 1141 unhooks search[value], the term going out; the saved message coming back is the separate tickler-list-response row, which no exclusion reaches)');
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
    // A response row measures a rule that reads the answer; the package has no exclusion to rely on there.
    if (fact.rules.length || fact.response) continue;
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

/** The tag family each CRS rule id belongs to, for an exemption written ctl:ruleRemoveTargetById. */
const FAMILY_OF_RULE = {
  930100: 'attack-lfi', 930110: 'attack-lfi', 931100: 'attack-rfi', 932100: 'attack-rce', 932110: 'attack-rce', 932130: 'attack-rce', 933100: 'attack-injection-php',
};

/** [{tag, arg}] for each `SecRuleUpdateTargetByTag "tag" "!ARGS:arg"` of a file; comments are not rules and are never read. */
function afterCrsExemptions(file) {
  const out = [];
  for (const { text } of logicalLines(fs.readFileSync(file, 'utf8'))) {
    const match = /^\s*SecRuleUpdateTargetByTag\s+"([^"]+)"\s+"!ARGS:([^"]+)"/.exec(text);
    if (match) out.push({ tag: match[1], arg: match[2] });
  }
  return out;
}

/** Whether an exemption's argument (a literal name, or a /regex/ as ModSecurity writes one) names the argument `sample`. */
function argumentNames(arg, sample) {
  const regex = /^\/(.*)\/$/.exec(arg);
  if (!regex) return arg === sample;
  // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp -- the pattern is an argument written in this repository's own WAF exclusion rule files, never request data
  try { return new RegExp(regex[1]).test(sample); } catch { return false; }
}

/**
 * The attack families the exclusion files unhook from the argument `sample` on `route`: the BEFORE-CRS rules keyed on that
 * route (route patterns are regular expressions, matched case-sensitively as ModSecurity does) plus the AFTER-CRS patterns,
 * which apply on every route. A fix may take any shape, so this looks for "some rule unhooks this", not for one rule id.
 * It reads rules, not comments (both files go through logicalLines), and an exemption counts only when its argument
 * name MATCHES the sample, so "instructions_" in a comment, or a literal `instructions_` that no real box
 * (instructions_288452) is called, unhooks nothing.
 */
function familiesUnhooked(route, sample, { rules = before, exemptions = afterCrsExemptions(AFTER_CRS) } = {}) {
  const families = new Set();
  for (const rule of rules.values()) {
    // nosemgrep: javascript.lang.security.audit.detect-non-literal-regexp.detect-non-literal-regexp -- rule.route comes from this repository's own WAF exclusion rule files, never request data
    if (!new RegExp(`^${rule.route}$`).test(route)) continue;
    for (const [arg, tags] of rule.removals) {
      if (!argumentNames(arg, sample)) continue;
      for (const tag of tags) {
        const family = tag.startsWith('id:') ? FAMILY_OF_RULE[tag.slice(3)] : tag;
        if (family) families.add(family);
      }
    }
  }
  for (const { tag, arg } of exemptions) if (argumentNames(arg, sample)) families.add(tag);
  return families;
}

test('shouldCountOnlyRulesThatNameTheArgument_whenFamiliesAreLookedFor', () => {
  const conf = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'waf-families-')), 'before.conf');
  fs.writeFileSync(conf, [
    '# 9000: drugName_ instructions_ comment_ are only mentioned in this comment',
    'SecRule REQUEST_URI "@rx ^/carlos/rx/WriteScript(?:[;?]|$)" \\',
    '    "id:9001,phase:1,pass,nolog,chain"',
    '    SecRule REQUEST_METHOD "@streq POST" \\',
    '        "t:none,\\',
    '        ctl:ruleRemoveTargetByTag=attack-rce;ARGS:instructions_,\\',
    '        ctl:ruleRemoveTargetByTag=attack-lfi;ARGS:/^drugName_[0-9]+$/,\\',
    '        ctl:ruleRemoveTargetById=931100;ARGS:customName"',
    'SecRule REQUEST_URI "@rx ^/carlos/rx/writeScript(?:[;?]|$)" \\',
    '    "id:9002,phase:1,pass,nolog,chain"',
    '    SecRule REQUEST_METHOD "@streq POST" \\',
    '        "t:none,\\',
    '        ctl:ruleRemoveTargetByTag=attack-rce;ARGS:customName"',
    '',
  ].join('\n'));
  const rules = exemptArguments(conf);
  const none = { rules, exemptions: [] };
  assert.deepEqual([...familiesUnhooked('rx/WriteScript', 'instructions_288452', none)], [], 'a literal prefix is not the box');
  assert.deepEqual([...familiesUnhooked('rx/WriteScript', 'comment_288452', none)], [], 'a comment unhooks nothing');
  assert.deepEqual([...familiesUnhooked('rx/WriteScript', 'drugName_288452', none)], ['attack-lfi']);
  assert.deepEqual([...familiesUnhooked('rx/WriteScript', 'customName', none)], ['attack-rfi'], 'an exemption by rule id counts for its family');
  assert.deepEqual([...familiesUnhooked('rx/writeScript', 'customName', none)], ['attack-rce'], 'route names are case-sensitive');
  assert.deepEqual([...familiesUnhooked('rx/WriteScript', 'drugName_1', { rules, exemptions: [{ tag: 'attack-rfi', arg: '/^drugName_[0-9]+$/' }] })].sort(), ['attack-lfi', 'attack-rfi']);
});

test('shouldUnhookAttackRfiOnLetter_whenTheRichTextLetterIsSaved', { todo: 'finding 209' }, () => {
  // A letter that starts with a pasted link with an IP address trips 931100 (attack-rfi) on ARGS:Letter.
  assert.ok(familiesUnhooked('eform/addEForm', 'Letter').has('attack-rfi'));
});

/**
 * What finding 210 still lacks: the families the corpus trips on ARGS:customName (931100 attack-rfi and 932110 attack-rce)
 * that no rule unhooks on `rx/WriteScript`, the route the page posts the rename to. 1107 covers `rx/writeScript` (a different
 * route: the match is case-sensitive), and unhooking only one family still leaves the other to refuse the name.
 */
function missingForCustomName(options) {
  const families = familiesUnhooked('rx/WriteScript', 'customName', options);
  return ['attack-rfi', 'attack-rce'].filter((family) => !families.has(family));
}

/**
 * What finding 211 still lacks: for each of the three boxes Save Only posts (`drugName_<n>`, `instructions_<n>`,
 * `comment_<n>`, the id changes per card), the families the corpus trips (930100/930110 attack-lfi, 931100 attack-rfi,
 * 932100/932110/932130 attack-rce, 933100 attack-injection-php) that nothing unhooks on `rx/WriteScript`.
 */
function missingForSaveOnlyBoxes(options) {
  const missing = [];
  for (const box of ['drugName_', 'instructions_', 'comment_']) {
    const families = familiesUnhooked('rx/WriteScript', `${box}288452`, options);
    for (const family of ['attack-lfi', 'attack-rfi', 'attack-rce', 'attack-injection-php']) {
      if (!families.has(family)) missing.push(`ARGS:${box}<n> still inspected for ${family}`);
    }
  }
  return missing;
}

test('shouldUnhookCustomNameOnTheRoutePageWritesTo_whenACustomDrugIsRenamed', { todo: 'finding 210' }, () => {
  assert.deepEqual(missingForCustomName(), []);
});

test('shouldUnhookTheSaveOnlyBoxes_whenAPrescriptionHoldsProse', { todo: 'finding 211' }, () => {
  assert.deepEqual(missingForSaveOnlyBoxes(), []);
});

// The two todo tests above must not start passing on anything short of the fix: a comment, one family, the lower-case
// route, a literal box name or a single box is not enough, and the full set of rules is. They run against a synthetic
// conf, so they are real tests and not todos.
function confWith(ctls, route = 'rx/WriteScript') {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'waf-fix-'));
  const conf = path.join(dir, 'before.conf');
  fs.writeFileSync(conf, [
    `# a comment that mentions customName, drugName_, instructions_ and comment_ unhooks nothing`,
    `SecRule REQUEST_URI "@rx ^/carlos/${route}(?:[;?]|$)" \\`,
    '    "id:9001,phase:1,pass,nolog,chain"',
    '    SecRule REQUEST_METHOD "@streq POST" \\',
    `        "t:none,\\`,
    `        ${ctls.join(',\\\n        ')}"`,
    '',
  ].join('\n'));
  return { rules: exemptArguments(conf), exemptions: [] };
}

test('shouldNotCallFinding210Fixed_untilBothScoringFamiliesAreUnhookedOnTheUpperCaseRoute', () => {
  const rfi = 'ctl:ruleRemoveTargetByTag=attack-rfi;ARGS:customName';
  const rce = 'ctl:ruleRemoveTargetByTag=attack-rce;ARGS:customName';
  assert.deepEqual(missingForCustomName({ rules: new Map(), exemptions: [] }), ['attack-rfi', 'attack-rce'], 'nothing unhooked');
  assert.deepEqual(missingForCustomName(confWith([rfi])), ['attack-rce'], 'attack-rfi alone leaves 932110');
  assert.deepEqual(missingForCustomName(confWith([rce])), ['attack-rfi'], 'attack-rce alone leaves 931100');
  assert.deepEqual(missingForCustomName(confWith([rfi, rce], 'rx/writeScript')), ['attack-rfi', 'attack-rce'], 'the lower-case route is the other route');
  assert.deepEqual(missingForCustomName(confWith([rfi, rce.replace('customName', 'name')])), ['attack-rce'], 'another argument is not customName');
  assert.deepEqual(missingForCustomName(confWith([rfi, rce])), [], 'both families, on the route the page posts to');
  assert.deepEqual(missingForCustomName(confWith(['ctl:ruleRemoveTargetById=931100;ARGS:customName', 'ctl:ruleRemoveTargetById=932110;ARGS:customName'])), [],
    'an exemption by rule id counts for its family');
});

test('shouldNotCallFinding211Fixed_untilEveryBoxIsUnhookedForEveryScoringFamily', () => {
  const families = ['attack-lfi', 'attack-rfi', 'attack-rce', 'attack-injection-php'];
  const all = (box) => families.map((family) => `ctl:ruleRemoveTargetByTag=${family};ARGS:${box}`);
  const pattern = (box) => `/^${box}_[0-9]+$/`;
  assert.equal(missingForSaveOnlyBoxes({ rules: new Map(), exemptions: [] }).length, 12, 'nothing unhooked: 3 boxes x 4 families');
  assert.equal(missingForSaveOnlyBoxes(confWith(all('instructions_'))).length, 12, 'a literal prefix is not the box, whose name carries the card id');
  assert.equal(missingForSaveOnlyBoxes(confWith(all(pattern('instructions')))).length, 8, 'one box of three');
  assert.equal(missingForSaveOnlyBoxes(confWith(['drugName', 'instructions', 'comment'].flatMap((box) => all(pattern(box)).slice(0, 3)))).length, 3, 'three families of four');
  assert.equal(missingForSaveOnlyBoxes(confWith(['drugName', 'instructions', 'comment'].flatMap((box) => all(pattern(box))), 'rx/writeScript')).length, 12, 'the lower-case route is the other route');
  assert.deepEqual(missingForSaveOnlyBoxes(confWith(['drugName', 'instructions', 'comment'].flatMap((box) => all(pattern(box))))), []);
  assert.deepEqual(missingForSaveOnlyBoxes(confWith(all('/^(?:drugName|instructions|comment)_[0-9]+$/'))), [], 'one pattern for the three boxes');
  assert.deepEqual(missingForSaveOnlyBoxes({ rules: new Map(), exemptions: families.map((tag) => ({ tag, arg: '/^(?:drugName|instructions|comment)_[0-9]+$/' })) }), [],
    'an AFTER-CRS pattern, which applies on every route');
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
    assert.equal(FACTS[key].corpusStep, corpusStepFor(FACTS[key]), key);
  }
});

test('shouldGiveEveryRowTwoDistinctStepLabels_soAPinNamesOnlyTheCorpusStep', () => {
  const labels = ROW_ORDER.flatMap((key) => [controlStepLabel(key), corpusStepLabel(key)]);
  assert.equal(new Set(labels).size, labels.length);
  for (const key of ROW_ORDER) {
    const { verb, tail } = OUTCOMES[outcomeOf(key)];
    assert.ok(corpusStepLabel(key).endsWith(`the clinical corpus ${verb} /${FACTS[key].route}${tail}`), key);
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
    // The log row has to cite every entry that is pinned to it, so a reader of the finding finds each row that measures it.
    const [logRow] = FINDINGS.match(new RegExp(`^\\| ${entry.expectedFailure.finding} \\|.*$`, 'm'));
    assert.ok(logRow.includes(`\`${entry.name}\``), `${entry.name}: finding ${entry.expectedFailure.finding} does not cite the entry pinned to it`);
  }
});

test('shouldSayWhatEachRowProves_withoutClaimingASaveForARowThatWritesNothing', () => {
  // The tickler search term goes out and nothing is stored; the Rx rename goes to a session stash; the invoice display only
  // shows. Each is "accepted". The tickler list answer is judged on the message coming back ("echoed"); the rest store.
  for (const key of ['tickler-list', 'rx-custom-drug-name', 'billing-on-display']) assert.equal(outcomeOf(key), 'accepted', key);
  assert.equal(outcomeOf('tickler-list-response'), 'echoed');
  for (const key of ['tickler-add', 'tickler-edit', 'rx-custom-drug-save', 'rx-update-script', 'chart-note', 'eform-letter']) assert.equal(outcomeOf(key), 'stored', key);
  for (const key of ROW_ORDER) {
    const { verb, tail } = OUTCOMES[outcomeOf(key)];
    if (outcomeOf(key) !== 'stored') assert.ok(!/lands exactly|saves through/.test(`${verb}${tail}`), `${key}: a row that stores nothing must not say it saved`);
  }
});

test('shouldReadTheRealServerHeader_forEveryReplayedAndTypedRow', () => {
  // The per-request front-door assertion reads the response's Server header; a row that returned a hard-coded
  // 'nginx' made it vacuous for the Rx rows (they reached the page through the proxy or not, the assertion said yes).
  const source = fs.readFileSync(path.join(__dirname, 'waf-clinical-text-corpus-playwright-checks.js'), 'utf8');
  assert.ok(!/server\s*:\s*['"]nginx['"]/i.test(source), 'no response header may be written by the check itself');
  assert.match(source, /headers: \(\) => answer\.headers/, 'the Rx rows hand back the page\'s own response headers');
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
