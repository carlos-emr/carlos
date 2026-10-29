/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Shared steps for the Preventions coverage checks of issue #5279.
 *
 * Each check owns its runWorkflow FAKE- patient. These helpers reach the patient's Preventions page the way a
 * clinician does (E-Chart ▸ Preventions), open the add or edit popup from the page, save it with the form's own Save
 * button, and read back what reached MariaDB. SQL here only arranges owned fixtures, reads the owned patient's rows
 * and removes them: every positive clinical action goes through the page.
 *
 * The amendment contract these checks verify is AddPrevention2Action's: saving an existing record archives it
 * (deleted=1, its own extension rows untouched) and stores one replacement whose previousId extension names it.
 */
const fs = require('node:fs');
const h = require('./playwright-harness');
const { clickDownloadsOrOpens } = require('./playwright-ui');
const { expectValue } = require('./workflow-session');
const { pdfTextBuffer } = require('./export-content-helpers');

const ID = /^[1-9]\d*$/;
const SAVE = 'input[type="submit"][name="action"]';

/** index.jsp r(): the list colour of a completed screening by its result (#BDFCC9 Other, #ee5f5b abnormal). */
const LIST_COLOURS = Object.freeze({
  other: 'rgb(189, 252, 201)',
  abnormal: 'rgb(238, 95, 91)',
});

function requireId(value, what) {
  h.assert(ID.test(String(value)), `${what} is not a stored record id`);
  return String(value);
}

/**
 * Removes every prevention, extension row and partial date (PartialDate.PREVENTION is table 4) of the owned patient
 * once the workflow ends.
 */
function ownPatientPreventions(s, patient = s.patient) {
  const owned = requireId(patient, 'The owned patient');
  s.cleanup(() => {
    s.sql.execute(`DELETE x FROM preventionsExt x JOIN preventions p ON p.id=x.prevention_id WHERE p.demographic_no=${owned};
      DELETE d FROM partial_date d JOIN preventions p ON p.id=d.table_id AND d.table_name=4 WHERE p.demographic_no=${owned};
      DELETE FROM preventions WHERE demographic_no=${owned}`);
    h.assert(s.sql.value(`SELECT COUNT(*) FROM preventions WHERE demographic_no=${owned}`) === '0',
      'The owned patient\'s prevention rows were not removed');
  });
}

/** E-Chart ▸ Preventions: the patient's Preventions page in its own popup. */
async function openPreventionIndex(s) {
  const chart = await s.chart();
  const index = await s.popup(chart, chart.locator('a[onclick*="ViewPreventionIndex"]').first(), 'prevention-index');
  // The page always draws this link; waiting on it means the list below it has rendered.
  await index.getByText('Immunization Schedules', { exact: false }).first().waitFor({ state: 'visible' });
  return index;
}

/**
 * The add and edit popups of one prevention type on one open Preventions page.
 *
 * Every method that saves waits for the popup to close and the saved record to be listed again, so a step that
 * follows reads the page and the database after the save, never during it.
 */
function preventionForms(s, index, type) {
  const { sql, patient } = s;
  const owned = requireId(patient, 'The owned patient');
  const q = h.sqlString;

  const api = {
    index,
    type,
    /** The one live record of the type, which every save in these checks leaves behind. */
    active() {
      const rows = sql.rows(`SELECT id FROM preventions WHERE demographic_no=${owned}
        AND prevention_type=${q(type)} AND deleted=0`);
      h.assert(rows.length === 1 && ID.test(rows[0][0]), `Expected one active ${type} record, found ${rows.length}`);
      return rows[0][0];
    },
    /** One extension value of a record: '(none)' when no row exists, values joined by | when several do. */
    ext(id, key) {
      return sql.value(`SELECT IFNULL((SELECT GROUP_CONCAT(val ORDER BY id SEPARATOR '|') FROM preventionsExt
        WHERE prevention_id=${requireId(id, 'The record')} AND keyval=${q(key)}),'(none)')`);
    },
    /** Every extension row of a record, keyed by name; a name stored twice is refused rather than hidden. */
    exts(id) {
      const rows = sql.rows(`SELECT keyval,val FROM preventionsExt WHERE prevention_id=${requireId(id, 'The record')} ORDER BY id`);
      const values = {};
      for (const [key, value] of rows) {
        h.assert(!(key in values), `The record stores ${key} twice`);
        values[key] = value === undefined ? '' : value;
      }
      return values;
    },
    /** The record's row on the open Preventions page, matched on its whole id. */
    record(id) {
      return index.locator(`.preventionProcedure[onclick*="ViewAddPreventionData?id=${requireId(id, 'The record')}&"]`).first();
    },
    /** The type's heading link, showing the page's "other Preventions" list first when the type is kept there. */
    async addLink() {
      const link = index.locator(`a[onclick*="ViewAddPreventionData"][onclick*="prevention=${encodeURIComponent(type)}&"]`).first();
      await link.waitFor({ state: 'attached' });
      if (!(await link.isVisible())) {
        await index.getByRole('link', { name: 'show/hide all other Preventions' }).click();
        await link.waitFor({ state: 'visible' });
      }
      return link;
    },
    /** Opens a new record of the type from its heading on the Preventions page. */
    async openNew() {
      const editor = await s.popup(index, await api.addLink(), 'prevention-editor');
      h.assert(await editor.locator('[name="prevention"]').inputValue() === type, `The list opened a form for another type than ${type}`);
      h.assert(await editor.locator('input[name="id"]').count() === 0, 'A new record opened with a record id');
      return editor;
    },
    /** Opens a listed record from its row on the Preventions page. */
    async open(id) {
      const row = api.record(id);
      await row.waitFor({ state: 'visible' });
      const editor = await s.popup(index, row, 'prevention-editor');
      h.assert(await editor.locator('input[name="id"]').inputValue() === String(id), 'The row opened another record');
      return editor;
    },
    /** Saves the open form with its Save button and waits for the popup to close itself. */
    async save(editor) {
      await editor.locator(SAVE).first().click();
      if (!editor.isClosed()) await editor.waitForEvent('close', { timeout: 30000 });
    },
    /** Saves a new record and returns its id once the page lists it. */
    async saveNew(editor) {
      await api.save(editor);
      const id = api.active();
      await api.record(id).waitFor({ state: 'visible' });
      return id;
    },
    /**
     * Saves an edit of `previous` and returns the replacement's id once the page lists it, after checking the
     * amendment contract: the original is archived, the replacement is the only live record and names the original.
     */
    async saveAmendment(editor, previous) {
      await api.save(editor);
      await expectValue(sql, `SELECT deleted FROM preventions WHERE id=${requireId(previous, 'The edited record')}`, '1',
        'The edited record was not archived');
      const id = api.active();
      h.assert(id !== String(previous), 'Saving the edit did not store a replacement record');
      h.assert(api.ext(id, 'previousId') === String(previous), 'The replacement does not name the record it replaced');
      await api.record(id).waitFor({ state: 'visible' });
      h.assert(await api.record(previous).count() === 0, 'The page still lists the archived record');
      return id;
    },
  };
  return api;
}

/**
 * Types a lot number on the injection layout. When the clinic keeps a lot list for the type, the form offers it as a
 * drop-down with "Other" last; a lot that is not on the list is typed after choosing Other, as a clinician would.
 */
async function enterLot(editor, lot) {
  const list = editor.locator('#lotDrop');
  if (await list.count() && await list.isVisible()) await list.selectOption('-1');
  const box = editor.locator('#lot');
  await box.waitFor({ state: 'visible' });
  await box.fill(lot);
}

/** The background the Preventions page gives a listed record, and its text with whitespace folded. */
async function listedRecord(row) {
  const line = row.locator('p').first();
  return {
    text: (await line.innerText()).replace(/\s+/g, ' '),
    colour: await line.evaluate(p => getComputedStyle(p).backgroundColor),
  };
}

/** The <option> values and labels a select offers, in page order. */
async function offeredOptions(select) {
  return select.locator('option').evaluateAll(options => options.map(option => ({
    value: option.value, label: option.textContent.replace(/\s+/g, ' ').trim(),
  })));
}

/**
 * Prints the open Preventions page (Enable Print ▸ Print) and returns the PDF's text.
 * The page's Print form posts only the ticked type names; the server reads the records itself.
 * Compare with printedIncludes: the PDF wraps long lines, at spaces and after hyphens.
 */
async function printPreventions(s, index) {
  const button = index.locator('input[name="printButton"]');
  if (await button.inputValue() !== 'Print') await button.click();
  h.assert(await button.inputValue() === 'Print', 'Enable Print did not switch the button to Print');
  const outcome = await clickDownloadsOrOpens(index, button, { context: s.context, recorder: s.recorder, label: 'prevention-print', timeout: 30000 });
  h.assert(outcome.kind === 'download', 'Print did not deliver a file');
  h.assert(outcome.download.suggestedFilename() === 'Prevention.pdf', `Print saved ${outcome.download.suggestedFilename()}`);
  return pdfTextBuffer(fs.readFileSync(await outcome.download.path()), { raw: true });
}

/** Whether printed text contains a phrase, wherever the PDF wrapped either of them. */
function printedIncludes(printed, phrase) {
  const squash = text => String(text).replace(/\s+/g, '');
  return squash(printed).includes(squash(phrase));
}

/**
 * Today as the application reads it: the date a new record's form offers. Ages and intervals in the reminder rules
 * are counted on the application server's clock, whose time zone (CARLOS_TZ, America/Toronto by default) need not be
 * the database's, so dates are derived from this rather than from the database's CURDATE().
 */
async function applicationToday(s, index) {
  const editor = await preventionForms(s, index, 'Inf').openNew();
  const today = await editor.locator('#prevDate').inputValue();
  await editor.close();
  h.assert(/^\d{4}-\d{2}-\d{2}$/.test(today), `A new record offered the date "${today}"`);
  return today;
}

/** Dates before the application's today, computed by the database's calendar arithmetic. */
function datesBefore(sql, today) {
  h.assert(/^\d{4}-\d{2}-\d{2}$/.test(today), 'today must be a yyyy-mm-dd date');
  return {
    today,
    monthsAgo: months => sql.value(`SELECT DATE_SUB('${today}', INTERVAL ${Number(months)} MONTH)`),
    yearsAgo: years => sql.value(`SELECT DATE_SUB('${today}', INTERVAL ${Number(years)} YEAR)`),
  };
}

/**
 * The Prevention Recommendations box of the open Preventions page: its warning lines (red), its reminder lines
 * (blue), and whether decision support reported an error. No box means no lines.
 */
async function readRecommendations(index) {
  await index.getByText('Immunization Schedules', { exact: false }).first().waitFor({ state: 'visible' });
  const lines = async selector => (await index.locator(`.recommendations ${selector}`).allInnerTexts())
    .flatMap(text => text.split('\n')).map(line => line.trim()).filter(Boolean);
  const failure = 'Decision Support Had Errors Running.';
  const danger = await lines('.alert-danger');
  return {
    warnings: danger.filter(line => line !== failure),
    reminders: await lines('.alert-info'),
    failed: danger.includes(failure),
  };
}

module.exports = {
  LIST_COLOURS,
  applicationToday,
  datesBefore,
  enterLot,
  listedRecord,
  offeredOptions,
  openPreventionIndex,
  ownPatientPreventions,
  preventionForms,
  printPreventions,
  printedIncludes,
  readRecommendations,
  requireId,
};
