#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Special characters and field lengths on the patient demographics forms (wave 6, boundary values).
 * User path: Schedule > Search > no-match search > Create Demographic > fill the form > Add Record >
 * Go to record > Edit > Update Record.
 * Asserts: names with an apostrophe, accents, CJK, an emoji, a literal "&amp;", quotes, backslash,
 * "%41", "+" and ";" are stored byte for byte (utf8mb4, no "?" substitution, no double encoding); the
 * Master Record and the Edit form redisplay them identically; an unchanged Update leaves every column
 * as it was; every text box carries its column limit (Unicode code points for names); the patient alert and the
 * waiting-list note carry their VARCHAR(255) limit in code points (#5869), the alert counting each line break as the CR LF
 * the browser submits, so an alert at the limit is saved whole and redisplayed by the Edit form while one more character
 * is refused in both forms without being cut or sent; a surname of exactly the column
 * length is stored whole and one more character is visibly limited; and, last, a Name Used longer than
 * pref_name is stored whole in the alias (pref_name keeps its first 30 characters by design, #5128) and a
 * pronoun / gender past their columns are refused or stored whole, never silently cut. (Patient search
 * by these characters is boundary-demographic-search.)
 * Fixtures: patients whose surname starts with the run tag; cleanup removes every row of those patients
 * and asserts none remain (the append-only audit log is not touched).
 * Implements the wave-6 "boundary values" pattern (special characters and field lengths), Part 1.
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const b = require('./lib/boundary-values');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { removeMarkedPatients } = require('./lib/gap-records-fixtures');
const { failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');

const TIMEOUT = 30000;

// Name Used is stored whole in alias; pref_name holds its leading characters (#5128).
const FIELD_COLUMNS = [['last_name', 'last_name'], ['first_name', 'first_name'], ['middleNames', 'middleNames'],
  ['address', 'address'], ['city', 'city'], ['residentialAddress', 'residentialAddress'],
  ['residentialCity', 'residentialCity'], ['postal', 'postal'], ['residentialPostal', 'residentialPostal'],
  ['phone', 'phone'], ['phone2', 'phone2'], ['email', 'email'], ['hin', 'hin'], ['ver', 'ver'],
  ['sin', 'sin'], ['chart_no', 'chart_no'], ['nameUsed', 'alias'], ['pronouns', 'pronoun'], ['gender', 'gender']];

async function assertFormLimits(form, sql) {
  for (const [input, column] of FIELD_COLUMNS) {
    const box = form.locator(`[name="${input}"]`).first();
    const declared = b.columnLength(sql, 'demographic', column);
    const codePoints = ['last_name', 'first_name', 'pronouns', 'gender'].includes(input);
    // HTML maxlength counts UTF-16 units; name validation counts the VARCHAR's Unicode code points.
    h.assert(Number(await box.getAttribute('maxlength')) === declared * (codePoints ? 2 : 1),
      `${input} maxlength disagrees with column ${column} (${declared} characters)`);
    if (codePoints) h.assert(Number(await box.getAttribute('data-max-codepoints')) === declared,
      `${input} Unicode character limit disagrees with column ${column}`);
  }
}

// The patient alert (cust3 on the Add form, alert on the Edit form) and the waiting-list note, which
// demographic-field-length-limits.jspf counts in Unicode code points like the names (#5869): maxlength is twice the
// column, data-max-codepoints the column itself.
const ALERT_AND_NOTE_COLUMNS = {
  add: [['cust3', 'demographiccust', 'cust3'], ['waiting_list_note', 'waitingList', 'note']],
  edit: [['alert', 'demographiccust', 'cust3'], ['waiting_list_note', 'waitingList', 'note']],
};
const ADD_RECORD = /\/demographic\/DemographicAddRecord$/;
const UPDATE_RECORD = /\/demographic\/DemographicUpdate$/;

async function assertAlertAndNoteLimits(page, formId, sql, fields) {
  for (const [input, table, column] of fields) {
    // The HTML parser can place a control outside the form element while preserving its form owner.
    const box = page.locator(`input[name="${input}"], textarea[name="${input}"]`);
    // edit-form-clinical.jsp draws its waiting-list section only while DEMOGRAPHIC_WAITING_LIST=true (the bundled
    // default); the Add form always draws the note, read-only until a waiting list exists.
    h.assert(await box.count() === 1, `Expected one ${input} control for ${formId}${input === 'waiting_list_note'
      ? ' (the Edit form draws it only while DEMOGRAPHIC_WAITING_LIST=true)' : ''}`);
    h.assert(await box.evaluate((field, id) => field.form?.id === id, formId), `${input} is detached from ${formId}`);
    const declared = b.columnLength(sql, table, column);
    h.assert(Number(await box.getAttribute('maxlength')) === declared * 2,
      `${input} maxlength disagrees with column ${table}.${column} (${declared} characters, two UTF-16 units each)`);
    h.assert(Number(await box.getAttribute('data-max-codepoints')) === declared,
      `${input} Unicode character limit disagrees with column ${table}.${column}`);
  }
}

/** `letters` letters over `breaks + 1` lines, as a textarea holds them: each line break is one LF. */
function multiline(letters, breaks) {
  const lines = Array.from({ length: breaks + 1 }, () => '');
  for (let i = 0; i < letters; i++) lines[i % lines.length] += 'x';
  return lines.join('\n');
}

/** A textarea value as the browser submits it, and the database stores it: each line break as CR LF. */
const submitted = (value) => value.replace(/\n/g, '\r\n');

async function assertAlertAccepted(box, value, label) {
  await box.fill(value);
  h.assert(await box.inputValue() === value, `${label}: the alert box cut what was typed`);
  h.assert(await box.evaluate(field => field.checkValidity()), `${label}: the alert box refuses an alert within the limit`);
}

async function assertAlertRefused(box, value, limit, label) {
  await box.fill(value);
  h.assert(await box.inputValue() === value, `${label}: the alert box cut what was typed`);
  h.assert(await box.evaluate((field, max) => !field.checkValidity() && field.validationMessage.includes(String(max)), limit),
    `${label}: the over-long alert has no visible validation error naming the ${limit}-character limit`);
}

async function assertNoRecordPost(page, path, submit, label) {
  let sent = false;
  const record = request => {
    if (request.method() === 'POST' && path.test(new URL(request.url()).pathname)) {
      sent = true;
    }
  };
  page.on('request', record);
  try {
    await submit(page);
  } finally {
    page.off('request', record);
  }
  h.assert(!sent, `${label} sent the form with an over-long alert`);
}

async function openAddForm(s, term) {
  const search = await ui.clickOpensPopup(s.schedule, s.schedule.locator('#search a, a:has-text("Search")').first(),
    { context: s.context, recorder: s.recorder, label: 'add-search', timeout: TIMEOUT });
  await search.locator('#keyword, input[name="keyword"]').first().fill(term);
  await search.locator("input[type='submit']").first().click();
  await search.waitForLoadState('networkidle').catch(() => {});
  await search.locator("form[action$='/demographic/ViewDemographicAddARecordHtm'] button[type='submit']").first().click();
  await search.locator('form[name="adddemographic"]').waitFor({ timeout: TIMEOUT });
  // The shared field-limit script applies its attributes during DOMContentLoaded.
  await search.waitForLoadState('domcontentloaded');
  return search;
}

async function fillBasics(add, last, first) {
  const form = add.locator('form[name="adddemographic"]');
  await form.locator('input[name="last_name"]').fill(last);
  await form.locator('input[name="first_name"]').fill(first);
  await form.locator('select[name="sex"]').selectOption('F');
  await form.locator('input[name="inputDOB"]').fill('1985-07-09');
  await form.locator('input[name="postal"]').fill('K1A0B1');
  return form;
}

async function submitAdd(add) {
  await h.withExpectedDialogs(add, () => add.locator('input[type="submit"][value="Add Record"]').first().click(), { accept: true });
  await add.waitForLoadState('networkidle', { timeout: TIMEOUT }).catch(() => {});
}

async function submitEdit(page) {
  await h.withExpectedDialogs(page, () => page.locator('#updateButton input[type="submit"]').first().click(), { accept: true });
  await page.waitForLoadState('networkidle', { timeout: TIMEOUT });
}

async function workflow(s) {
  const { sql, marker } = s;
  const tag = 'FAKE-PW' + marker.slice(-6);
  s.cleanup(() => b.removeTaggedPatients(sql, tag, marker, removeMarkedPatients));
  const where = (suffix) => `last_name LIKE ${h.sqlString(tag + suffix + '%')}`;
  const T = b.TOKENS;
  // The form upper-cases the name boxes on blur (upCaseCtrl), by design: expected values are upper-cased.
  const typed = {
    last: tag + T.apostrophe,
    first: T.latin,
    middle: `${T.cjk} ${T.emoji} ${T.entity} ${T.quotes} ${T.backslash} ${T.percent} ${T.plus} ${T.semicolon}`,
    address: `12 O'Neil St, Apt #5 & "B" ${T.backslash} ${T.percent} ${T.plus} ${T.semicolon} ${T.entity}`,
    city: `Zoë-Ålesund ${T.cjk}`,
    comment: `${T.cjk} ${T.emoji} ${T.entity} ${T.quotes} ${T.backslash} ${T.percent} ${T.plus} ${T.semicolon} ${T.latin}`,
  };
  const stored = {
    last_name: typed.last.toUpperCase(), first_name: typed.first.toUpperCase(), middleNames: typed.middle.toUpperCase(),
    address: typed.address, city: typed.city,
  };
  let add;
  let id;

  await s.step('Add Record stores names, address and comment with special characters byte for byte', async () => {
    add = await openAddForm(s, tag);
    const form = await fillBasics(add, typed.last, typed.first);
    await form.locator('input[name="middleNames"]').fill(typed.middle);
    await form.locator('input[name="address"]').fill(typed.address);
    await form.locator('input[name="city"]').fill(typed.city);
    await form.locator('textarea[name="phoneComment"]').fill(typed.comment);
    await form.locator('input[name="phone"]').fill('613-555-0188');
    await submitAdd(add);
    await expectValue(sql, `SELECT COUNT(*) FROM demographic WHERE ${where("O'")}`, '1', 'Add Record did not store the patient');
    id = sql.value(`SELECT demographic_no FROM demographic WHERE ${where('')}`);
    h.assert(/^[1-9]\d*$/.test(id), 'The stored patient was not found by its run tag');
    for (const [column, value] of Object.entries(stored)) {
      b.assertStored(sql, 'demographic', column, `demographic_no=${id}`, value, `Add form ${column}`);
    }
    b.assertStored(sql, 'demographicExt', 'value', `demographic_no=${id} AND key_val='phoneComment'`, typed.comment, 'Add form phone comment');
  });

  await s.step('the new Master Record shows the stored text unchanged (no entity or encoding damage)', async () => {
    await add.getByRole('link', { name: /Go to record/i }).first().click();
    await add.locator('#editBtn').waitFor({ timeout: TIMEOUT });
    const text = await add.locator('body').innerText();
    for (const [label, value] of [['address', typed.address], ['city', typed.city]]) {
      h.assert(text.includes(value), `The Master Record does not display the ${label} exactly as typed (a literal "&amp;" or an accent was altered)`);
    }
    h.assert(text.includes(stored.middleNames), 'The Master Record does not display the middle names exactly as stored');
  });

  await s.step('the Edit form redisplays every value identically and an unchanged Update changes no column', async () => {
    await add.locator('#editBtn').click();
    await add.locator('#editDemographic').waitFor({ state: 'visible', timeout: TIMEOUT });
    const shown = async (name) => add.locator(`#editDemographic [name="${name}"]`).first().inputValue();
    const wrong = [];
    for (const [input, value] of [['last_name', stored.last_name], ['first_name', stored.first_name], ['middleNames', stored.middleNames],
      ['address', typed.address], ['city', typed.city], ['phoneComment', typed.comment]]) {
      if (await shown(input) !== value) wrong.push(input);
    }
    h.assert(wrong.length === 0, `The Edit form does not redisplay what was stored: ${wrong.join(', ')}`);
    const before = sql.rows(`SELECT HEX(last_name), HEX(first_name), HEX(middleNames), HEX(address), HEX(city) FROM demographic WHERE demographic_no=${id}`)[0].join('|');
    const beforeComment = sql.value(`SELECT HEX(value) FROM demographicExt WHERE demographic_no=${id} AND key_val='phoneComment'`);
    await ui.clickAndAwaitReload(add, add.locator('#updateButton input[type="submit"]').first(), { timeout: TIMEOUT, label: 'Update Record' });
    const after = sql.rows(`SELECT HEX(last_name), HEX(first_name), HEX(middleNames), HEX(address), HEX(city) FROM demographic WHERE demographic_no=${id}`)[0].join('|');
    h.assert(after === before, 'An Update Record with no edits altered the stored names or address (re-encoding on save)');
    h.assert(sql.value(`SELECT HEX(value) FROM demographicExt WHERE demographic_no=${id} AND key_val='phoneComment'`) === beforeComment,
      'An Update Record with no edits altered the stored phone comment (re-encoding on save)');
    await add.close();
  });

  await s.step('the add form limits each text box to its database column', async () => {
    add = await openAddForm(s, tag + 'M');
    await assertFormLimits(add.locator('form[name="adddemographic"]'), sql);
    await add.close();
  });

  // #5869: the alert is VARCHAR(255) counted in code points, and a textarea line break, one LF in the box, is
  // submitted and stored as CR LF. At the limit: max - 2 letters and one break (max as submitted). Over it: max - 1
  // letters and one break (max as LF, max + 1 as submitted, so only CR LF counting refuses it) and max - 1 letters
  // and two breaks.
  const alertMax = () => b.columnLength(sql, 'demographiccust', 'cust3');
  let alertPatient;

  await s.step('the add form counts an alert line break as CR LF: an alert at the limit is saved whole, one more is refused (#5869)', async () => {
    const max = alertMax();
    add = await openAddForm(s, tag + 'A');
    await fillBasics(add, tag + 'A', 'Alert');
    await assertAlertAndNoteLimits(add, 'adddemographic', sql, ALERT_AND_NOTE_COLUMNS.add);
    const alertBox = add.locator('textarea[name="cust3"]');
    await assertAlertRefused(alertBox, multiline(max - 1, 1), max, 'Add form alert one past the limit by a CR LF');
    const over = multiline(max - 1, 2);
    await assertAlertRefused(alertBox, over, max, 'Add form alert with two line breaks');
    await assertNoRecordPost(add, ADD_RECORD, submitAdd, 'Add Record');
    h.assert(sql.value('SELECT COUNT(*) FROM demographic WHERE last_name LIKE ?', [tag + 'A%']) === '0',
      'The patient was saved with an over-long alert');
    h.assert(await alertBox.inputValue() === over, 'The refused alert was cut');
    const atLimit = multiline(max - 2, 1);
    await assertAlertAccepted(alertBox, atLimit, 'Add form alert at the limit');
    await submitAdd(add);
    alertPatient = sql.value(`SELECT demographic_no FROM demographic WHERE ${where('A')}`);
    h.assert(/^[1-9]\d*$/.test(alertPatient), 'Add Record did not save the patient whose alert is at the limit');
    b.assertStored(sql, 'demographiccust', 'cust3', `demographic_no=${alertPatient}`, submitted(atLimit), 'Add form alert at the limit');
  });

  await s.step('the Edit form redisplays the alert at the limit whole and refuses one more without cutting or sending it (#5869)', async () => {
    const max = alertMax();
    const atLimit = multiline(max - 2, 1);
    await add.getByRole('link', { name: /Go to record/i }).first().click();
    await add.locator('#editBtn').click();
    const edit = add.locator('#editDemographic');
    await edit.waitFor({ state: 'visible', timeout: TIMEOUT });
    await add.waitForLoadState('domcontentloaded');
    await assertAlertAndNoteLimits(add, 'updatedelete', sql, ALERT_AND_NOTE_COLUMNS.edit);
    const alertBox = add.locator('textarea[name="alert"]');
    // The stored CR LF reads back as one LF in the box: the same value the Add form held.
    h.assert(await alertBox.inputValue() === atLimit, 'The Edit form does not redisplay the stored alert whole');
    h.assert(await alertBox.evaluate(field => field.checkValidity()), 'The Edit form refuses the alert it stored');
    await assertAlertRefused(alertBox, multiline(max - 1, 1), max, 'Edit form alert one past the limit by a CR LF');
    const over = multiline(max - 1, 2);
    await assertAlertRefused(alertBox, over, max, 'Edit form alert with two line breaks');
    await assertNoRecordPost(add, UPDATE_RECORD, submitEdit, 'Update Record');
    b.assertStored(sql, 'demographiccust', 'cust3', `demographic_no=${alertPatient}`, submitted(atLimit), 'Refused Edit form alert');
    h.assert(await alertBox.inputValue() === over, 'The refused edit was cut');
    await add.close();
  });

  await s.step('a surname of exactly the column length is stored whole and one more character is visibly limited', async () => {
    const max = b.columnLength(sql, 'demographic', 'last_name');
    const exact = b.exactly(max, tag + 'E');
    add = await openAddForm(s, tag + 'E');
    const form = await fillBasics(add, exact, 'Edge');
    await submitAdd(add);
    b.assertStored(sql, 'demographic', 'last_name', where('E'), exact.toUpperCase(), 'Surname at the column length');
    await add.close();
    const over = b.exactly(max + 1, tag + 'V');
    add = await openAddForm(s, tag + 'V');
    const overForm = await fillBasics(add, over, 'Over');
    const surname = overForm.locator('input[name="last_name"]');
    h.assert(await surname.inputValue() === over.toUpperCase(), 'The over-length surname was silently changed');
    h.assert(await surname.evaluate(box => !box.checkValidity() && box.validationMessage.length > 0),
      'The over-length surname has no visible validation error');
    await submitAdd(add);
    h.assert(sql.value('SELECT COUNT(*) FROM demographic WHERE last_name LIKE ?', [tag + 'V%']) === '0',
      'The invalid over-length surname was saved');
    await add.close();
  });

  await s.step('Add and Edit preserve fifty Unicode characters and refuse fifty-one without truncation', async () => {
    const max = b.columnLength(sql, 'demographic', 'last_name');
    const unicode = tag + 'W' + '😀'.repeat(max - b.cpLength(tag + 'W'));
    add = await openAddForm(s, tag + 'W');
    await fillBasics(add, unicode, '😀'.repeat(max));
    await submitAdd(add);
    b.assertStored(sql, 'demographic', 'last_name', where('W'), unicode.toUpperCase(), 'Unicode surname at the column length');
    b.assertStored(sql, 'demographic', 'first_name', where('W'), '😀'.repeat(max), 'Unicode first name at the column length');
    await add.getByRole('link', { name: /Go to record/i }).first().click();
    await add.locator('#editBtn').click();
    const edit = add.locator('#editDemographic');
    await edit.waitFor({ state: 'visible', timeout: TIMEOUT });
    await assertFormLimits(edit, sql);
    for (const input of ['last_name', 'first_name']) {
      const box = edit.locator(`[name="${input}"]`).first();
      const original = await box.inputValue();
      const invalid = input === 'first_name' ? 'AA' + '😀'.repeat(max - 1) : original + '😀';
      await box.fill(invalid);
      h.assert(await box.evaluate(field => !field.checkValidity() && field.validationMessage.length > 0),
        `The Edit form accepts fifty-one characters in ${input}`);
      await submitEdit(add);
      b.assertStored(sql, 'demographic', input, where('W'), original, `Rejected Edit form ${input}`);
      h.assert(await box.inputValue() === invalid, 'The invalid edit was silently truncated');
      await box.fill(original);
    }
    const changed = tag + 'W' + '𠮷'.repeat(max - b.cpLength(tag + 'W'));
    await edit.locator('[name="last_name"]').fill(changed);
    await ui.clickAndAwaitReload(add, add.locator('#updateButton input[type="submit"]').first(), { timeout: TIMEOUT, label: 'Update Unicode name' });
    b.assertStored(sql, 'demographic', 'last_name', where('W'), changed.toUpperCase(), 'Updated Unicode surname');
    await add.close();
  });

  await s.step('preferred name, pronoun and gender past their columns are refused or stored whole', async () => {
    const pref = b.columnLength(sql, 'demographic', 'pref_name');
    const pron = b.columnLength(sql, 'demographic', 'pronoun');
    const gender = b.columnLength(sql, 'demographic', 'gender');
    // Past pref_name but within the alias: the alias, which the record shows, keeps it whole (#5128).
    const nameUsed = b.exactly(pref + 10, 'P');
    const pronOver = b.exactly(pron + 1, 'q');
    const genderOver = b.exactly(gender + 1, 'g');
    const problems = [];
    // Its own patient, so a pronoun or gender failure (a refused or failed save) cannot hide its result.
    add = await openAddForm(s, tag + 'U');
    const nameForm = await fillBasics(add, tag + 'U', 'Wide');
    await nameForm.locator('input[name="nameUsed"]').fill(nameUsed);
    await submitAdd(add);
    const typedNameUsed = nameUsed.toUpperCase();
    try {
      b.assertStored(sql, 'demographic', 'alias', where('U'), typedNameUsed, 'Add form Name Used (alias)');
      // By design pref_name holds the first characters that fit its column.
      b.assertStored(sql, 'demographic', 'pref_name', where('U'), Array.from(typedNameUsed).slice(0, pref).join(''),
        'Add form Name Used (pref_name, its first characters)');
    } catch (error) { problems.push(error.message); }
    await add.close();
    add = await openAddForm(s, tag + 'Q');
    const form = await fillBasics(add, tag + 'Q', 'Wide');
    await form.locator('input[name="pronouns"]').fill(pronOver);
    await form.locator('input[name="gender"]').fill(genderOver);
    for (const input of ['pronouns', 'gender']) {
      const box = form.locator(`[name="${input}"]`);
      h.assert(await box.evaluate(field => !field.checkValidity() && field.validationMessage.length > 0),
        `${input} overflow has no visible validation error`);
    }
    await submitAdd(add);
    h.assert(sql.value('SELECT COUNT(*) FROM demographic WHERE last_name LIKE ?', [tag + 'Q%']) === '0',
      'An over-length pronoun or gender was saved');
    // Each VARCHAR must hold its full Unicode capacity, not half as many supplementary characters.
    const acceptedPronoun = '😀'.repeat(pron);
    const acceptedGender = '😀'.repeat(gender);
    await form.locator('input[name="pronouns"]').fill(acceptedPronoun);
    await form.locator('input[name="gender"]').fill(acceptedGender);
    await submitAdd(add);
    b.assertStored(sql, 'demographic', 'pronoun', where('Q'), acceptedPronoun, 'Unicode pronoun at the column length');
    b.assertStored(sql, 'demographic', 'gender', where('Q'), acceptedGender, 'Unicode gender at the column length');
    await add.close();
    h.assert(problems.length === 0, problems.join(' || '));
  });

  await s.step('direct Add POSTs reject pronoun and gender overflow before saving', async () => {
    for (const [field, label, suffix] of [['pronouns', 'Pronouns', 'R'], ['gender', 'Gender', 'S']]) {
      add = await openAddForm(s, tag + suffix);
      const form = await fillBasics(add, tag + suffix, 'Direct');
      const column = field === 'pronouns' ? 'pronoun' : 'gender';
      const maximum = b.columnLength(sql, 'demographic', column);
      const invalid = '😀'.repeat(maximum) + 'X';
      const box = form.locator(`[name="${field}"]`);
      // Chromium's normal fill honors maxlength; direct POST probes must bypass it too.
      await box.evaluate((element, value) => { element.value = value; }, invalid);
      h.assert(await box.inputValue() === invalid, `The direct ${field} POST fixture was truncated`);
      const mark = failureMark(s.recorder);
      // Native submit bypasses both HTML validity and the form's submit listeners.
      const [response] = await Promise.all([
        add.waitForNavigation({ waitUntil: 'networkidle', timeout: TIMEOUT }),
        form.evaluate(element => HTMLFormElement.prototype.submit.call(element)),
      ]);
      h.assert(response && response.status() === 400, `The server did not reject the direct ${field} POST`);
      h.assert((await add.locator('body').innerText()).includes(`${label} exceeds maximum length of ${maximum} characters.`),
        `The server did not report the ${field} length error`);
      h.assert(sql.value('SELECT COUNT(*) FROM demographic WHERE last_name LIKE ?', [tag + suffix + '%']) === '0',
        `The direct over-length ${field} POST saved a patient`);
      consumeExpectedFailure(s.recorder, mark, { status: 400, path: /\/demographic\/DemographicAddRecord$/ });
      await add.close();
    }
  });

}

if (require.main === module) runWorkflow('boundary-demographic-text', workflow, { openPatient: false });
module.exports = { workflow };
