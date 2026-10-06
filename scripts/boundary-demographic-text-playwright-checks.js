#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Special characters and field lengths on the patient demographics forms (wave 6, boundary values).
 * User path: Schedule > Search > no-match search > Create Demographic > fill the form > Add Record >
 * Go to record > Edit > Update Record.
 * Asserts: names with an apostrophe, accents, CJK, an emoji, a literal "&amp;", quotes, backslash,
 * "%41", "+" and ";" are stored byte for byte (utf8mb4, no "?" substitution, no double encoding); the
 * Master Record and the Edit form redisplay them identically; an unchanged Update leaves every column
 * as it was; every text box declares a limit matching its column; a surname of exactly the column
 * length is stored whole and one more character is visibly limited; and, last, a preferred name /
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

async function openAddForm(s, term) {
  const search = await ui.clickOpensPopup(s.schedule, s.schedule.locator('#search a, a:has-text("Search")').first(),
    { context: s.context, recorder: s.recorder, label: 'add-search', timeout: TIMEOUT });
  await search.locator('#keyword, input[name="keyword"]').first().fill(term);
  await search.locator("input[type='submit']").first().click();
  await search.waitForLoadState('networkidle').catch(() => {});
  await search.locator("form[action$='/demographic/ViewDemographicAddARecordHtm'] button[type='submit']").first().click();
  await search.locator('form[name="adddemographic"]').waitFor({ timeout: TIMEOUT });
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
    for (const [input, column] of [['nameUsed', 'pref_name'], ['pronouns', 'pronoun'], ['gender', 'gender']]) {
      const limit = await add.locator(`#editDemographic [name="${input}"]`).getAttribute('data-code-point-maxlength');
      h.assert(Number(limit) === b.columnLength(sql, 'demographic', column), `Edit form ${input} has an incorrect limit`);
    }
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

  const limitMismatches = [];
  await s.step('the add form declares each text limit from its database column', async () => {
    add = await openAddForm(s, tag + 'M');
    const mapping = [['last_name', 'last_name'], ['first_name', 'first_name'], ['middleNames', 'middleNames'], ['address', 'address'],
      ['city', 'city'], ['residentialAddress', 'residentialAddress'], ['residentialCity', 'residentialCity'], ['postal', 'postal'],
      ['phone', 'phone'], ['phone2', 'phone2'], ['email', 'email'], ['hin', 'hin'], ['sin', 'sin'], ['chart_no', 'chart_no'],
      ['nameUsed', 'pref_name'], ['pronouns', 'pronoun'], ['gender', 'gender']];
    const mismatched = [];
    for (const [input, column] of mapping) {
      const box = add.locator(`form[name="adddemographic"] [name="${input}"]`).first();
      const attribute = ['nameUsed', 'pronouns', 'gender'].includes(input) ? 'data-code-point-maxlength' : 'maxlength';
      const limit = await box.getAttribute(attribute);
      const declared = b.columnLength(sql, 'demographic', column);
      if (limit === null) mismatched.push(`${input} has no ${attribute} (column ${column} holds ${declared})`);
      else if (Number(limit) !== declared) mismatched.push(`${input} ${attribute}=${limit} but column ${column} holds ${declared}`);
    }
    await add.close();
    // Reported in the last step, so the storage checks below still run when the form limits are wrong.
    limitMismatches.push(...mismatched);
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
    const accepted = await overForm.locator('input[name="last_name"]').inputValue();
    h.assert(b.cpLength(accepted) <= max, 'The surname box accepted more characters than the column holds');
    await submitAdd(add);
    b.assertStored(sql, 'demographic', 'last_name', where('V'), accepted.toUpperCase(), 'Surname typed past the column');
    await add.close();
  });

  await s.step('preferred name, pronoun and gender accept supplementary characters up to their code-point limits', async () => {
    add = await openAddForm(s, tag + 'Q');
    const form = await fillBasics(add, tag + 'Q', 'Wide');
    const accepted = [];
    for (const [input, column] of [['nameUsed', 'pref_name'], ['pronouns', 'pronoun'], ['gender', 'gender']]) {
      const limit = b.columnLength(sql, 'demographic', column);
      const box = form.locator(`input[name="${input}"]`);
      await box.fill('😀'.repeat(limit + 1));
      // Name fields deliberately uppercase on blur; compare storage with that displayed value.
      await box.press('Tab');
      const value = await box.inputValue();
      h.assert(value === '😀'.repeat(limit), `${input} did not enforce its column limit in Unicode code points`);
      accepted.push([column, value]);
    }
    await submitAdd(add);
    for (const [column, value] of accepted) {
      b.assertStored(sql, 'demographic', column, where('Q'), value, `Displayed add form ${column}`);
    }
  });

  await s.step('an overlong update is refused without changing any submitted patient fields', async () => {
    const before = sql.value(`SELECT CONCAT(HEX(pref_name),'|',HEX(city)) FROM demographic WHERE ${where('Q')}`);
    await add.getByRole('link', { name: /Go to record/i }).first().click();
    await add.locator('#editBtn').click();
    await add.locator('#editDemographic').waitFor({ state: 'visible', timeout: TIMEOUT });
    for (const [input, column] of [['nameUsed', 'pref_name'], ['pronouns', 'pronoun'], ['gender', 'gender']]) {
      const box = add.locator(`#editDemographic [name="${input}"]`);
      const limit = b.columnLength(sql, 'demographic', column);
      h.assert(await box.inputValue() === '😀'.repeat(limit), `Edit redisplay damaged supplementary ${input}`);
      await box.fill('😀'.repeat(limit + 1));
      await box.press('Tab');
      h.assert(await box.inputValue() === '😀'.repeat(limit), `Edit ${input} used UTF-16 units instead of code points`);
    }
    const preferred = add.locator('#editDemographic [name="nameUsed"]');
    await preferred.evaluate(element => { element.removeAttribute('maxlength'); element.removeAttribute('data-code-point-maxlength'); });
    await preferred.fill('P'.repeat(31));
    await add.locator('#editDemographic [name="city"]').fill('MUST NOT BE SAVED');
    const mark = failureMark(s.recorder);
    const responsePromise = add.waitForResponse(response => response.request().method() === 'POST'
      && h.pathOnly(response.url()).endsWith('/demographic/DemographicUpdate'));
    await ui.clickAndAwaitReload(add, add.locator('#updateButton input[type="submit"]').first(), { timeout: TIMEOUT, label: 'Overlong Update Record' });
    const response = await responsePromise;
    h.assert(response.status() === 400, `Overlong update answered HTTP ${response.status()} instead of 400`);
    consumeExpectedFailure(s.recorder, mark, { status: 400, path: /\/demographic\/DemographicUpdate$/ });
    h.assert(sql.value(`SELECT CONCAT(HEX(pref_name),'|',HEX(city)) FROM demographic WHERE ${where('Q')}`) === before,
      'A refused update still changed the patient record');
    h.assert((await add.locator('body').innerText()).includes('maximum length'), 'The update refusal did not explain the field limit');
    await add.close();
  });

  for (const [input, column, suffix] of [['nameUsed', 'pref_name', 'P'], ['pronouns', 'pronoun', 'R'], ['gender', 'gender', 'G']]) {
    await s.step(`the server refuses overlong ${input} when its client limit is bypassed`, async () => {
      add = await openAddForm(s, tag + suffix);
      const form = await fillBasics(add, tag + suffix, 'Bypass');
      const box = form.locator(`input[name="${input}"]`);
      await box.evaluate(element => { element.removeAttribute('maxlength'); element.removeAttribute('data-code-point-maxlength'); });
      await box.fill(b.exactly(b.columnLength(sql, 'demographic', column) + 1, 'X'));
      const mark = failureMark(s.recorder);
      const responsePromise = add.waitForResponse(response => response.request().method() === 'POST'
        && h.pathOnly(response.url()).endsWith('/demographic/DemographicAddRecord'));
      await submitAdd(add);
      const response = await responsePromise;
      h.assert(response.status() === 400, `Overlong ${input} answered HTTP ${response.status()} instead of 400`);
      consumeExpectedFailure(s.recorder, mark, { status: 400, path: /\/demographic\/DemographicAddRecord$/ });
      h.assert(sql.value(`SELECT COUNT(*) FROM demographic WHERE ${where(suffix)}`) === '0',
        `A refused ${input} request still created a patient`);
      h.assert((await add.locator('body').innerText()).includes('maximum length'), 'The refusal did not explain the field limit');
      await add.close();
    });
  }

  await s.step('the add form limits agree with the database columns', async () => {
    h.assert(limitMismatches.length === 0, `Add form limits disagree with the database columns: ${limitMismatches.join('; ')}`);
  });
}

if (require.main === module) runWorkflow('boundary-demographic-text', workflow, { openPatient: false });
module.exports = { workflow };
