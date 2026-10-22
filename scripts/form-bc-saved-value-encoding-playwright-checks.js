#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Issue #5090 (BC installs only): the older BC form pages give back a saved value exactly as it was typed. A value
 * holding a double quote, <, > or & used to come back cut off at the quote (5'6" became 5'6), or changed, when the
 * form was reopened, and the next Save stored the damaged text. Every page now prints each saved value encoded
 * for where it is printed (scripts/encode-legacy-form-values.js; scripts/legacy-form-value-encoding.test.js proves
 * the source has no unencoded saved value left); this check proves the round trip in the browser.
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ Forms menu ▸ <form> ▸ on each page, type into two text
 * boxes ▸ Save ▸ the saved record redisplayed ▸ the page link to the next page ▸ ... ▸ a fresh E-Chart's
 * saved-form entry ▸ each page again.
 *
 * Forms (every page the issue names): BC-AR, BC-AR 2007 and BC-AR 2012 (three pages each), the Labour and Birth
 * Summary and the Birth Summary 2008, BC-NewBorn and BC-NewBorn 2008 (three pages each), BC-INR, Chart Checklist
 * and the BC Health Passport (two pages). The boxes are chosen on the page: the first two plain text boxes (no
 * script of their own, not a date or time) whose maxlength and column both hold the whole typed value.
 *
 * Asserted, per form:
 *   1. it opens from the Forms menu;
 *   2. on each page, Save stores the typed values byte for byte in the form's newest row (and keeps the values typed
 *      on the pages before it), and the redisplayed page shows them exactly;
 *   3. a fresh chart's saved-form entry reopens the record, and each page shows every typed value exactly.
 * The typed value is P<page>F<box>"<b>&amp;' : a double quote, both angle brackets, an ampersand that would decode
 * to a different text if it were decoded twice, and an apostrophe.
 *
 * Fixtures: the owned FAKE- patient, and one marker-named Forms-menu registration per form (clinic-wide, so the
 * check runs EXCLUSIVE=1). Cleanup deletes every form row of the owned patient and every registration, and asserts
 * both.
 *
 *   npm run test:form-bc-saved-value-encoding-playwright
 */
const h = require('./lib/playwright-harness');
const ui = require('./lib/playwright-ui');
const { runWorkflow } = require('./lib/workflow-session');
const { waitForNavbars } = require('./echart-navbar-modules-playwright-checks');

const NAME = 'form-bc-saved-value-encoding';
const FORMS = [
  { code: 'AR', title: 'BC-AR', view: 'formbcar', table: 'formBCAR', pages: ['formbcarpg1', 'formbcarpg2', 'formbcarpg3'] },
  { code: 'AR07', title: 'BC-AR 2007', view: 'formbcar2007', table: 'formBCAR2007',
    pages: ['formbcar2007pg1', 'formbcar2007pg2', 'formbcar2007pg3'] },
  { code: 'AR12', title: 'BC-AR 2012', view: 'formbcar2012', table: 'formBCAR2012',
    pages: ['formbcar2012pg1', 'formbcar2012pg2', 'formbcar2012pg3'] },
  { code: 'LB', title: 'Labour and Birth Summary', view: 'formbcbirthsummo', table: 'formBCBirthSumMo', pages: ['formbcbirthsummo'] },
  { code: 'BS08', title: 'Birth Summary 2008', view: 'formbcbirthsummo2008', table: 'formBCBirthSumMo2008',
    pages: ['formbcbirthsummo2008'] },
  { code: 'NB', title: 'BC-NewBorn', view: 'formbcnewborn', table: 'formBCNewBorn',
    pages: ['formbcnewbornpg1', 'formbcnewbornpg2', 'formbcnewbornpg3'] },
  { code: 'NB08', title: 'BC-NewBorn 2008', view: 'formBCNewBorn2008', table: 'formBCNewBorn2008',
    pages: ['formBCNewBorn2008pg1', 'formBCNewBorn2008pg2', 'formBCNewBorn2008pg3'] },
  { code: 'INR', title: 'BC-INR', view: 'formbcinr', table: 'formBCINR', pages: ['formbcinr'] },
  { code: 'CL', title: 'Chart Checklist', view: 'formbcclientchartchecklist', table: 'formBCClientChartChecklist',
    pages: ['formbcclientchartchecklist'] },
  { code: 'HP', title: 'BC Health Passport', view: 'formbchp', table: 'formBCHP', pages: ['formbchppg1', 'formbchppg2'] },
];
const BOXES_PER_PAGE = 2;
const typedValue = (page, box) => `P${page}F${box}"<b>&amp;'`;
// Boxes whose name says they hold a date or a time are filled by calendars and checked by the page's own
// date validation, so they are not chosen.
const NOT_FREE_TEXT = /date|time|dob|edd|lmp|phn|hin|postal|phone/i;

/** The page's plain text boxes in document order: visible, enabled, writable and without a script of their own. */
async function plainTextBoxes(page) {
  return page.evaluate(() => [...(document.forms[0] ? document.forms[0].elements : [])]
    .filter(el => el.tagName === 'INPUT' && (el.type || 'text').toLowerCase() === 'text' && el.name
      && !el.disabled && !el.readOnly && el.offsetParent !== null
      && ![...el.attributes].some(attr => /^on/i.test(attr.name)))
    .map(el => ({ name: el.name, maxLength: el.maxLength })));
}

async function saveAndWait(page) {
  await h.withExpectedDialogs(page, () => Promise.all([
    page.waitForNavigation({ waitUntil: 'load', timeout: 30000 }),
    page.getByRole('button', { name: 'Save', exact: true }).first().click(),
  ]));
  await h.assertNotErrorPage(page, 'the saved form');
}

async function workflow(s) {
  const { sql, patient, marker } = s;
  const q = h.sqlString;
  // FORM_ENCODING_ONLY=<code>,<code> narrows the run to some forms (a debugging aid; the manifest runs them all).
  const only = (process.env.FORM_ENCODING_ONLY || '').split(',').map(code => code.trim()).filter(Boolean);
  const entries = FORMS.filter(form => !only.length || only.includes(form.code)).map(form => ({ form, name: `${marker.slice(-12)} ${form.code}`.slice(0, 30), typed: {} }));
  const tables = [...new Set(entries.map(entry => entry.form.table))];
  const regValue = form => `../form/${form.view}.jsp?fixture=${marker}&demographic_no=`;

  // A table this install does not have means it is not a BC install.
  const present = new Set(sql.rows(`SELECT TABLE_NAME FROM information_schema.TABLES WHERE TABLE_SCHEMA=DATABASE()
    AND TABLE_NAME IN (${tables.map(q).join(',')})`).map(row => row[0]));
  if (present.size !== tables.length) throw new h.SkipCheck(`This BC check needs the BC form tables (missing: ${tables.filter(t => !present.has(t)).join(', ')})`);

  s.cleanup(() => {
    sql.execute(tables.map(table => `DELETE FROM ${table} WHERE demographic_no=${patient}`).join(';'));
    h.assert(sql.value(`SELECT ${tables.map(t => `(SELECT COUNT(*) FROM ${t} WHERE demographic_no=${patient})`).join('+')}`) === '0',
      'Form rows of the owned patient were not removed');
  });
  s.cleanup(() => {
    sql.execute(`DELETE FROM encounterForm WHERE form_value LIKE ${q(`%fixture=${marker}&%`)}`);
    h.assert(sql.value(`SELECT COUNT(*) FROM encounterForm WHERE form_value LIKE ${q(`%fixture=${marker}&%`)}`) === '0',
      'The owned Forms-menu registrations were not removed');
  });

  // Each table's text columns and their lengths, so a box is chosen only when its column holds the value.
  const width = {};
  for (const table of tables) {
    width[table] = Object.fromEntries(sql.rows(`SELECT COLUMN_NAME, COALESCE(CHARACTER_MAXIMUM_LENGTH, 0), DATA_TYPE
      FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME=${q(table)}`)
      .filter(([, , type]) => /char|text/i.test(type)).map(([column, length]) => [column, Number(length)]));
  }
  for (const entry of entries) {
    sql.execute(`INSERT INTO encounterForm (form_value,form_name,form_table,hidden)
      SELECT ${q(regValue(entry.form))},${q(entry.name)},${q(entry.form.table)},COALESCE(MAX(hidden),0)+1 FROM encounterForm`);
  }
  let chart = await s.chart();

  /** The newest row of the owned patient's record, the named columns only. */
  const newest = (table, columns) => {
    const row = sql.rows(`SELECT ID, ${columns.map(c => `\`${c}\``).join(',')} FROM ${table}
      WHERE demographic_no=${patient} ORDER BY ID DESC LIMIT 1`)[0];
    return row ? { id: row[0], values: Object.fromEntries(columns.map((c, i) => [c, row[i + 1]])) } : null;
  };
  const shownDifferences = async (page, typed) => {
    const wrong = [];
    for (const [name, value] of Object.entries(typed)) {
      const shown = await page.locator(`[name="${name}"]`).first().inputValue();
      if (shown !== value) wrong.push(`${name} shows ${JSON.stringify(shown)}, typed ${JSON.stringify(value)}`);
    }
    return wrong;
  };
  /** Follow the page's own link to another page of the same record. */
  const goToPage = async (page, view, formId) => {
    const link = page.locator(`a[href*="${view}?"]:not([href^="javascript"])`).first();
    h.assert(await link.count(), `there is no link to ${view} on ${new URL(page.url()).pathname}`);
    const from = page.url();
    try {
      await Promise.all([page.waitForURL(url => url.pathname.endsWith(`/form/${view}`), { waitUntil: 'load', timeout: 30000 }),
        link.click()]);
    } catch (error) {
      throw new Error(`following the link to ${view} (${await link.getAttribute('href')}) from ${from} did not reach it: `
        + `still at ${page.url()} (${error.message.split('\n')[0]})`);
    }
    if (formId) h.assert(await page.locator('[name="formId"]').first().inputValue() === formId, `${view} opened another record`);
  };

  for (const entry of entries) {
    const { form } = entry;
    let page;
    await s.step(`${form.title}: opens from the Forms menu`, async () => {
      await ui.openFormsMenu(chart);
      const link = chart.getByRole('link', { name: entry.name, exact: true });
      await link.waitFor({ state: 'visible', timeout: 20000 });
      page = await s.popup(chart, link, `encode-${form.code}`);
      h.assert(new URL(page.url()).searchParams.get('demographic_no') === patient, 'the form opened for another patient');
      await page.getByRole('button', { name: 'Save', exact: true }).first().waitFor({ state: 'visible', timeout: 15000 });
    });

    for (const [index, view] of form.pages.entries()) {
      await s.step(`${form.title}, ${view}: Save stores and redisplays values with " < > & ' exactly`, async () => {
        if (index > 0) await goToPage(page, view, entry.formId);
        const columns = width[form.table];
        const length = typedValue(9, 9).length;
        // The box and its column must both take the whole value: the browser stops typing at a box's maxlength.
        const chosen = (await plainTextBoxes(page))
          .filter(({ name, maxLength }) => !NOT_FREE_TEXT.test(name) && !(name in entry.typed)
            && (maxLength < 0 || maxLength >= length)
            && columns[name] !== undefined && (columns[name] === 0 || columns[name] >= length))
          .map(({ name }) => name)
          .slice(0, BOXES_PER_PAGE);
        h.assert(chosen.length === BOXES_PER_PAGE, `${view} has ${chosen.length} plain text boxes to type into, not ${BOXES_PER_PAGE}`);
        for (const [box, name] of chosen.entries()) {
          entry.typed[name] = typedValue(index + 1, box + 1);
          await page.locator(`[name="${name}"]`).first().fill(entry.typed[name]);
        }
        await saveAndWait(page);
        const row = newest(form.table, Object.keys(entry.typed));
        h.assert(row, `Save stored no ${form.table} row`);
        const stored = Object.entries(entry.typed).filter(([name, value]) => row.values[name] !== value)
          .map(([name, value]) => `${name} stored ${JSON.stringify(row.values[name])}, typed ${JSON.stringify(value)}`);
        h.assert(!stored.length, `the newest ${form.table} row does not hold the typed values: ${stored.join('; ')}`);
        entry.formId = row.id;
        // Save comes back through form/forwardname?form_link=<page>.jsp; the page names itself in form_link.
        const shownPage = await page.locator('[name="form_link"]').first().inputValue().catch(() => '');
        h.assert(shownPage === `${view}.jsp` || new URL(page.url()).pathname.endsWith(`/form/${view}`),
          `Save redisplayed ${shownPage || page.url()}, not ${view}`);
        const wrong = await shownDifferences(page, Object.fromEntries(chosen.map(name => [name, entry.typed[name]])));
        h.assert(!wrong.length, `the redisplayed ${view} does not show the saved values: ${wrong.join('; ')}`);
      });
    }

    await s.step(`${form.title}: a fresh chart reopens the record and every page shows the typed values exactly`, async () => {
      await page.close();
      const refreshed = await s.context.newPage();
      await refreshed.goto(chart.url(), { waitUntil: 'domcontentloaded' });
      await waitForNavbars(refreshed, 20000);
      await chart.close();
      chart = refreshed;
      const opener = `a[onclick*="/form/forwardshortcutname?formname=${entry.name}&"]`;
      const saved = chart.locator('#leftNavBar li, #rightNavBar li').filter({ has: chart.locator(opener) });
      await saved.first().waitFor({ state: 'attached', timeout: 20000 }).catch(() => {});
      h.assert(await saved.count() === 1, `the chart should list the saved ${form.title} once (listed ${await saved.count()} times)`);
      page = await ui.clickOpensPopup(chart, saved.first().locator(opener).first(),
        { context: s.context, recorder: s.recorder, label: `encode-${form.code}-reopen`, timeout: 20000, position: { x: 8, y: 9 } });
      h.assert(await page.locator('[name="formId"]').first().inputValue() === entry.formId, 'the chart opened another record');
      const wrong = [];
      // A multi-page record reopens on the page it was last saved from (c_lastVisited); the page names itself in
      // form_link. Check that page first, then follow the links to the others.
      const shown = (await page.locator('[name="form_link"]').first().inputValue().catch(() => '')).replace(/\.jsp$/, '');
      const first = form.pages.includes(shown) ? shown : form.pages[0];
      for (const [index, view] of [first, ...form.pages.filter(other => other !== first)].entries()) {
        if (index > 0) await goToPage(page, view, entry.formId);
        const here = await page.evaluate(names => names.filter(name => document.querySelector(`[name="${name}"]`)),
          Object.keys(entry.typed));
        wrong.push(...await shownDifferences(page, Object.fromEntries(here.map(name => [name, entry.typed[name]]))));
      }
      h.assert(!wrong.length, `the reopened ${form.title} does not show the saved values: ${wrong.join('; ')}`);
      await page.close();
    });
  }
}

if (require.main === module) runWorkflow(NAME, workflow);
module.exports = { workflow, FORMS };
