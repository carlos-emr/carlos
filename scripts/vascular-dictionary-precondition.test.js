/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { createRequire } = require('node:module');

// Load the proposed complete browser module with its unchanged repository dependencies.
// This runs only read-only preconditions; no browser, database or child process is launched.
const repo = process.env.VT_REPO_ROOT || path.resolve(__dirname, '..');
const file = process.env.VT_CANDIDATE_SOURCE || path.join(__dirname, 'clinical-forms-save-reopen-playwright-checks.js');
const requireRepo = createRequire(path.join(repo, 'scripts/clinical-forms-save-reopen-playwright-checks.js'));
const mod = { exports: {} };
vm.runInNewContext(fs.readFileSync(file, 'utf8'), {
  require: requireRepo, module: mod, __dirname: path.join(repo, 'scripts'), console, process,
}, { filename: file });
const { workflow, FORMS, requireVascularDictionaryReady } = mod.exports;
const sourceXml = fs.readFileSync(path.join(repo, 'src/main/webapp/form/VTForm.xml'), 'utf8');
const xml = (type = 'BP', instruction = 'Sit &amp; stand') => `<formProp><measurement><type>${type}</type><measuringInstrc>${instruction}</measuringInstrc></measurement></formProp>`;
function sqlRows(result) {
  const seen = [];
  return { seen, rows(query) { seen.push(query); return result; },
    execute() { assert.fail('Precondition must never write a dictionary'); },
    value() { assert.fail('Precondition must use one read-only missing-key query'); } };
}

test('all 55 shipped VT keys use one read-only existence query and do not mutate definitions', () => {
  const sql = sqlRows([]);
  requireVascularDictionaryReady(sql, sourceXml);
  assert.equal(sql.seen.length, 1);
  assert.equal((sql.seen[0].match(/AS vtType/g) || []).length, 55);
  assert.equal((sql.seen[0].match(/UNION ALL/g) || []).length, 54);
  assert.match(sql.seen[0], /NOT EXISTS[\s\S]*mt\.type=vt_expected\.vtType AND mt\.measuringInstruction=vt_expected\.vtInstruction/);
  assert.doesNotMatch(sql.seen[0], /\b(?:INSERT|UPDATE|DELETE|REPLACE|CREATE|ALTER|DROP)\b/i);
});

test('a missing initialized key fails explicitly and retains all clinic rows', () => {
  const sql = sqlRows([['BP', 'Sit & stand']]);
  assert.throws(() => requireVascularDictionaryReady(sql, xml()), /initialization is required before the suite residue baseline.*BP/);
  assert.equal(sql.seen.length, 1);
});

for (const [label, rows] of [['null', null], ['undefined', undefined], ['object', {}], ['wrong columns', [['BP']]],
  ['nonstring', [[1, 'Sit']]], ['string row', ['BP']]]) {
  test(`invalid SQL evidence (${label}) fails rather than accepting an empty dictionary check`, () => {
    assert.throws(() => requireVascularDictionaryReady(sqlRows(rows), xml()), /invalid rows/);
  });
}

test('database lookup failure propagates without a fallback or dictionary write', () => {
  const failure = new Error('lookup failed');
  assert.throws(() => requireVascularDictionaryReady({rows() { throw failure; }}, xml()), error => error === failure);
});

for (const [label, content] of [['empty', '<formProp/>'], ['unclosed', '<formProp><measurement>'],
  ['missing type', '<formProp><measurement><measuringInstrc>x</measuringInstrc></measurement></formProp>'],
  ['empty type', xml('')], ['duplicate type element', xml('BP</type><type>HT')],
  ['unsupported entity', xml('BP', '&#65;')], ['doctype', '<!DOCTYPE x>' + xml()],
  ['duplicate key', '<formProp>' + xml().match(/<measurement>[\s\S]*<\/measurement>/)[0].repeat(2) + '</formProp>']]) {
  test(`incomplete or ambiguous source keys (${label}) refuse before querying`, () => {
    const sql = sqlRows([]);
    assert.throws(() => requireVascularDictionaryReady(sql, content));
    assert.equal(sql.seen.length, 0);
  });
}

test('source entities are decoded once and quoted as SQL data', () => {
  const sql = sqlRows([]);
  requireVascularDictionaryReady(sql, xml('B&apos;P', ' &lt; 2 &amp; &quot;x&quot; &amp;lt; '));
  assert.match(sql.seen[0], /'B''P' AS vtType/);
  assert.match(sql.seen[0], /'< 2 & "x" &lt;' AS vtInstruction/);
});

test('empty measuring instruction is valid native trim-to-empty data', () => {
  const sql = sqlRows([]);
  requireVascularDictionaryReady(sql, xml('BP', '  '));
  assert.match(sql.seen[0], /'' AS vtInstruction/);
});

for (const key of ['VT', 'VT2']) {
  test(`${key} workflow refuses missing initialization before its first registration, cleanup or navigation`, async () => {
    const sql = sqlRows([['BP', 'missing']]);
    await assert.rejects(workflow({sql, patient: '3302', marker: 'FAKE-PWtest',
      cleanup() { assert.fail('Must fail before workflow fixture callbacks and first writes'); },
      chart() { assert.fail('Must fail before browser navigation'); },
    }, {forms: FORMS.filter(form => form.key === key), select: [`${key.toLowerCase()}.open`]}), /dictionary initialization/);
    assert.equal(sql.seen.length, 1);
  });
}

test('initialized VT workflow reaches normal fixture setup after the read-only gate', async () => {
  const sql = sqlRows([]), stop = new Error('fixture boundary');
  await assert.rejects(workflow({sql, patient: '3302', marker: 'FAKE-PWtest', cleanup() { throw stop; }},
    {forms: FORMS.filter(form => form.key === 'VT'), select: ['vt.open']}), error => error === stop);
  assert.equal(sql.seen.length, 1);
});

test('unrelated selected clinical forms do not require or query the VT dictionary', async () => {
  const sql = sqlRows([]), stop = new Error('fixture boundary');
  await assert.rejects(workflow({sql, patient: '3302', marker: 'FAKE-PWtest', cleanup() { throw stop; }},
    {forms: FORMS.filter(form => form.key === 'ANN'), select: ['ann.open']}), error => error === stop);
  assert.equal(sql.seen.length, 0);
});

function registeredPreflight(selected) {
  const source = fs.readFileSync(file, 'utf8');
  const registration = source.slice(source.lastIndexOf('if (require.main === module)'));
  let preflight;
  const main = {exports: {}};
  const fakeRequire = Object.assign(name => {
    assert.equal(name, './lib/export-content-helpers');
    return {requirePoppler(tool) { assert.equal(tool, 'pdftotext'); }};
  }, {main});
  vm.runInNewContext(registration, { require: fakeRequire, module: main, NAME: 'test', workflow, FORMS,
    testing: {}, claims: requireRepo('./lib/form-claims'), validatePin: () => selected,
    requireVascularDictionaryReady, runWorkflow(name, body, options) { preflight = options.preflight; },
  });
  return preflight;
}

for (const key of ['vt.open', 'vt2.save']) {
  test(`${key} registered preflight refuses missing dictionary before runWorkflow creates its patient`, () => {
    const sql = sqlRows([['BP', 'missing']]);
    assert.throws(() => registeredPreflight([key])({sql}), /dictionary initialization/);
    assert.equal(sql.seen.length, 1);
  });
}

test('registered preflight does not add a VT fixture dependency to unrelated forms', () => {
  const sql = sqlRows([]);
  registeredPreflight(['ann.open'])({sql});
  assert.equal(sql.seen.length, 0);
});
