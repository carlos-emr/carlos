/* SPDX-License-Identifier: GPL-2.0-or-later */
/**
 * Unit tests for src/main/webapp/share/javascript/dobSearchKeyword.js, the
 * patient search DOB formatter/validator (issue #3956). Run with:
 *   node --test scripts/dob-search-keyword.test.js
 *
 * The browser validator is user feedback only; DobSearchPattern.java is the
 * authoritative parser. Both read src/test/resources/demographic/dob-search-keywords.tsv
 * so an accept/reject decision cannot change on one side without failing the other.
 */

const assert = require('node:assert/strict');
const test = require('node:test');
const fs = require('node:fs');
const path = require('node:path');

const ROOT = path.join(__dirname, '..');
const dob = require(path.join(ROOT, 'src', 'main', 'webapp', 'share', 'javascript', 'dobSearchKeyword.js'));

const FIXTURE = fs.readFileSync(
  path.join(ROOT, 'src', 'test', 'resources', 'demographic', 'dob-search-keywords.tsv'),
  'utf8',
);
const FORM_JSP = fs.readFileSync(
  path.join(ROOT, 'src', 'main', 'webapp', 'WEB-INF', 'jsp', 'demographic', 'zdemographicfulltitlesearch.jsp'),
  'utf8',
);
const DAO = fs.readFileSync(
  path.join(ROOT, 'src', 'main', 'java', 'io', 'github', 'carlos_emr', 'carlos', 'commn', 'dao', 'DemographicDaoImpl.java'),
  'utf8',
);

function fixtureCases() {
  return FIXTURE.split('\n')
    .filter((line) => line.trim() !== '' && !line.startsWith('#'))
    .map((line) => {
      const cols = line.split('\t');
      return { keyword: cols[0].replace(/<SP>/g, ' '), valid: cols[1] !== 'INVALID' };
    });
}

test('the fixture is non-trivial in both directions', () => {
  const cases = fixtureCases();
  assert.ok(cases.filter((c) => c.valid).length >= 8, 'expected several accepted shapes');
  assert.ok(cases.filter((c) => !c.valid).length >= 8, 'expected several rejected shapes');
});

test('isValid agrees with the server parser on every shared fixture keyword', () => {
  for (const { keyword, valid } of fixtureCases()) {
    assert.equal(dob.isValid(keyword), valid, `isValid(${JSON.stringify(keyword)})`);
  }
});

test('isValid refuses empty and missing input', () => {
  for (const value of ['', '   ', null, undefined]) {
    assert.equal(dob.isValid(value), false);
  }
});

test('format keeps the issue #3956 shapes and wildcards intact', () => {
  const cases = {
    '1975': '1975',
    '1975-03': '1975-03',
    '1975-%-05': '1975-%-05',
    '%-03-05': '%-03-05',
    '1975-%': '1975-%',
    '1975-3-5': '1975-3-5',
  };
  for (const [raw, expected] of Object.entries(cases)) {
    assert.equal(dob.format(raw), expected, `format(${JSON.stringify(raw)})`);
  }
});

test('format keeps the pre-existing digit auto-formatting (issue #3237 regression)', () => {
  const cases = {
    '19800101': '1980-01-01',
    '1980-': '1980-',
    '1980-01-': '1980-01-',
    '1980/01/01': '1980-01-01',
    '1980.01.01': '1980-01-01',
    '1980 01 01': '1980-01-01',
    '': '',
  };
  for (const [raw, expected] of Object.entries(cases)) {
    assert.equal(dob.format(raw), expected, `format(${JSON.stringify(raw)})`);
  }
});

test('format preserves malformed input so validation cannot silently search a different date', () => {
  for (const raw of ['19%', '%%', '1975_03_05', '1975_%_05', '1980010199', '1980a01b01', '1980--01--01', '-1980']) {
    assert.equal(dob.format(raw), raw);
    assert.equal(dob.isValid(dob.format(raw)), false);
  }
});

test('format is idempotent on its own output', () => {
  const inputs = ['19800101', '1975-%-05', '%-03-05', '1975-3-5', '1980-', '1980 01 01', '%5'];
  for (const raw of inputs) {
    const once = dob.format(raw);
    assert.equal(dob.format(once), once, `format is not stable for ${JSON.stringify(raw)}`);
  }
});

test('every keystroke prefix of an accepted keyword formats to a prefix of it', () => {
  // Typing one character at a time must never rewrite what the user already
  // entered, which is how the #3237 "stuck at 4 characters" bug presented.
  for (const target of ['1975-03-05', '1975-%-05', '%-03-05', '1975-03', '1975']) {
    let value = '';
    for (const ch of target) {
      value = dob.format(value + ch);
      assert.ok(target.startsWith(value), `${JSON.stringify(value)} is not a prefix of ${target}`);
    }
    assert.equal(value, target);
  }
});

test('formatInput keeps the caret beside the same character on a mid-field edit', () => {
  // "1980-0101" with the caret after the first month digit: the reformat
  // inserts a separator later in the string, so the caret must not jump.
  const input = {
    value: '1980-0101',
    selectionStart: 6,
    setSelectionRange(start, end) { this.selection = [start, end]; },
  };
  dob.formatInput(input);
  assert.equal(input.value, '1980-01-01');
  assert.deepEqual(input.selection, [6, 6]);
});

test('formatInput leaves an already-formatted value untouched', () => {
  const input = {
    value: '1975-%-05',
    selectionStart: 2,
    setSelectionRange() { throw new Error('must not move the caret when nothing changed'); },
  };
  dob.formatInput(input);
  assert.equal(input.value, '1975-%-05');
});

test('the search form loads the helper and validates through it', () => {
  assert.match(FORM_JSP, /share\/javascript\/dobSearchKeyword\.js/);
  assert.match(FORM_JSP, /CarlosDobSearch\.formatInput\(input\)/);
  assert.match(FORM_JSP, /!CarlosDobSearch\.isValid\(dobValue\)/);
  // The old 8-digit rule is what issue #3956 removed.
  assert.ok(!/dobValue\.length < 8/.test(FORM_JSP), 'the 8-digit length check must not come back');
  // The alert text is localized and JavaScript-encoded, never a raw literal.
  assert.match(FORM_JSP, /carlos:forJavaScript\(dobFormatMessage\)/);
});

test('the DAO binds the parsed segments, not a raw split of the keyword', () => {
  assert.match(DAO, /DobSearchPattern\.parse\(dobStr\)/);
  assert.match(DAO, /setParameter\("yearOfBirth", dob\.get\(\)\.year\(\)\)/);
  assert.ok(!/dobStr\.split\("-"\)/.test(DAO), 'the 3-segment split that rejected partial dates must not come back');
});

// Execute the appointment form's real submit handler with a dropdown contract.
// A legacy radio-button index check silently accepts malformed dates here.
function appointmentSubmit(keyword, mode = 'search_dob', jsp = 'demographic/demographicsearch2apptresults.jsp') {
  const source = fs.readFileSync(path.join(ROOT,
    'src/main/webapp/WEB-INF/jsp', jsp), 'utf8');
  const start = source.indexOf('function checkTypeIn() {');
  const end = source.indexOf(jsp.startsWith('admin/') ? 'function confirmMerge()' : 'function searchInactive()', start);
  assert.ok(start >= 0 && end > start);
  const alerts = [];
  const form = { keyword: { value: keyword }, search_mode: { value: mode } };
  // Dropdown options expose selected, never the checked property of a radio.
  for (let i = 0; i < 7; i++) form.search_mode[i] = { selected: false, checked: jsp.startsWith('admin/') && i === 2 && mode === 'search_dob' };
  const context = {
    document: { titlesearch: form }, CarlosDobSearch: dob,
    DOB_FORMAT_MESSAGE: 'localized DOB guidance', alert: message => alerts.push(message),
  };
  require('node:vm').runInNewContext(source.slice(start, end).replace(/<fmt:message[^>]+\/>/g, 'localized DOB guidance'), context);
  return { accepted: context.checkTypeIn(), alerts, form };
}

test('appointment dropdown rejects malformed DOB rather than silently submitting', () => {
  for (const keyword of ['198', '1980-13', '1980-01-32', '%', '%-%-%']) {
    const result = appointmentSubmit(keyword);
    assert.equal(result.accepted, false, keyword);
    assert.deepEqual(result.alerts, ['localized DOB guidance']);
  }
});

test('appointment dropdown preserves full, partial and wildcard DOB searches', () => {
  for (const keyword of ['1980', '1980-01', '1980-01-01', '1980-%-01', '%-01-01', '1980-']) {
    const result = appointmentSubmit(keyword);
    assert.equal(result.accepted, true, keyword);
    assert.deepEqual(result.alerts, []);
    assert.equal(result.form.keyword.value, keyword);
  }
});

test('appointment search still supports other modes and exact-length card swipes', () => {
  assert.equal(appointmentSubmit('Example', 'search_name').accepted, true);
  assert.equal(appointmentSubmit('', 'search_dob').accepted, true);
  for (const keyword of ['%b6100541234567890', '%b6100541234567890EXTRA']) {
    const result = appointmentSubmit(keyword, 'search_name');
    assert.equal(result.accepted, true);
    assert.equal(result.form.search_mode.value, 'search_hin');
    assert.equal(result.form.keyword.value, '1234567890');
  }
});

test('appointment submit normalizes a prefilled eight-digit DOB without an input event', () => {
  const result = appointmentSubmit('19800101');
  assert.equal(result.accepted, true);
  assert.equal(result.form.keyword.value, '1980-01-01');
  assert.deepEqual(result.alerts, []);
});

test('appointment submit does not repair arbitrary malformed prefilled input', () => {
  for (const keyword of ['1980abc0101', '19800132', '19801301']) {
    assert.equal(appointmentSubmit(keyword).accepted, false);
  }
});

test('DOB formatting preserves a card swipe while it is being typed', () => {
  const input = { value: '', selectionStart: 0, setSelectionRange() {} };
  const barcode = '%b6100541234567890';
  for (const character of barcode) {
    input.value += character;
    input.selectionStart = input.value.length;
    dob.formatInput(input);
  }
  assert.equal(input.value, barcode);
});

test('merge-record search uses the shared DOB grammar and rejects malformed input', () => {
  const jsp = 'admin/demographicmergerecord.jsp';
  for (const keyword of ['1980', '1980-01', '1980-%-01', '19800101']) {
    assert.equal(appointmentSubmit(keyword, 'search_dob', jsp).accepted, true);
  }
  for (const keyword of ['198', '1980-13', '%']) {
    assert.equal(appointmentSubmit(keyword, 'search_dob', jsp).accepted, false);
  }
});

for (const [jsp, formName] of [['addappointment.jsp', 'ADDAPPT'], ['editappointment.jsp', 'EDITAPPT']]) {
  test(`${jsp} hands DOB searches to the picker without logging terms`, () => {
    const source = fs.readFileSync(path.join(ROOT, 'src/main/webapp/WEB-INF/jsp/appointment', jsp), 'utf8');
    const start = source.indexOf('function parseSearch() {');
    const end = source.indexOf('\n            }', start) + '\n            }'.length;
    assert.ok(start >= 0 && end > start);
    for (const [typed, mode, expected] of [
      ['1980', 'search_dob', '1980'], [' 1980-01 ', 'search_dob', '1980-01'], ['1980/01', 'search_dob', '1980-01'],
      ['1980-%-01', 'search_dob', '1980-%-01'], ['19800101', 'search_dob', '1980-01-01'],
      ['%b6100541234567890', 'search_hin', '1234567890'],
      ['Example Patient', 'search_name', 'Example Patient'],
      ['  Example Patient  ', 'search_name', 'Example Patient'],
      ['1980 Main Street', 'search_address', '1980 Main Street'],
      ['555-555-1212', 'search_phone', '555-555-1212'], ['1234567890', 'search_hin', '1234567890'],
    ]) {
      const form = { keyword: { value: typed }, displaymode: { value: '' } };
      const searchMode = { value: '' };
      const logs = [];
      const context = { CarlosDobSearch: dob, console: { log: (...args) => logs.push(args) },
        document: { forms: { [formName]: form }, getElementById: () => searchMode } };
      require('node:vm').runInNewContext(source.slice(start, end), context);
      assert.equal(context.parseSearch(), true);
      assert.equal(searchMode.value, mode);
      assert.equal(form.keyword.value, expected);
      assert.equal(logs.length, 0, 'search terms must not reach the console');
    }
    for (const typed of ['1980-13', '1980-01-32', '19801301', '%', '1980--01']) {
      const form = { keyword: { value: typed }, displaymode: { value: '' } };
      const alerts = [];
      const context = { CarlosDobSearch: dob, alert: text => alerts.push(text),
        document: { forms: { [formName]: form }, getElementById: () => ({ value: '' }) } };
      require('node:vm').runInNewContext(source.slice(start, end), context);
      assert.equal(context.parseSearch(), false, typed);
      assert.equal(alerts.length, 1);
      assert.equal(form.keyword.value, typed);
    }
    assert.match(source, /onclick="if \(!parseSearch\(\)\) return false;/);
  });
}
