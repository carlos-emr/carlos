#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Gap check (clinical): every Cumulative Patient Profile box, not just Social History.
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ the "+" of Medical History, Ongoing
 * Concerns, Reminders (centre grid) and Family History, Other Meds, Risk Factors (right navbar) ▸
 * item editor ▸ Sign & Save; reopen the item ▸ edit ▸ Sign & Save; Archive; the box title link ▸
 * issue history popup; print dialog ▸ CPP icon ▸ Print.
 * cpp-note-extension-archive drives the Social History box only; the other six boxes use other
 * issue codes, other visible extension fields and (right navbar) a different repaint path, so a
 * regression in one would not show there.
 *
 * Asserts, per box: the casemgmt_note row (exact text, signed, not archived), its link to the box's
 * own issue code (casemgmt_issue_notes -> issue.code), exactly one casemgmt_note_ext row per field
 * filled in (exact keys and values, dates stored as dates) and none for a field left blank or hidden
 * (#5365: the editor posts every input, and empty ones used to be stored as empty rows), and that the
 * box lists the item. Then, on Medical History: reopening prefills every field; an edit saves a NEW
 * revision (#4641: same uuid, higher note id) that carries the new text and the changed field once,
 * while the earlier revision keeps its text and rows; Archive saves an archived revision with the same
 * rows and the box stops listing it; the issue history popup lists the saved text; the chart print with
 * the CPP icon on (pdftotext) lists the active items of every box, leaves out the Family History item
 * flagged Hide in CPP and, after the Archive, the archived Medical History item.
 *
 * Fixtures: the owned FAKE-PW patient (runWorkflow). Every note, link, extension, signature
 * (hash_audit), casemgmt_issue, casemgmt_cpp and eChart row of that patient is deleted in cleanup and
 * asserted gone.
 * Coverage plan §2.5 cpp-boxes (gap-clinical).
 */
const h = require('./lib/playwright-harness');
const {runWorkflow, expectValue} = require('./lib/workflow-session');
const {requirePoppler} = require('./lib/stored-pdf-documents');
const print = require('./lib/gap-clinical-print');

const DIALOG = '#showEditNote';

/*
 * One entry per CPP box. `plus` finds the "+" the way a clinician finds it; `fields` are the
 * visible editor fields on that box (read from the page: see the explore dump in the report) and the
 * extension key each one is stored under (CaseManagementNoteExt).
 */
const BOXES = [
  {
    name: 'Medical History', code: 'MedHistory', plus: '#divR1I2 a[title="Add Item"]', container: '#divR1I2',
    fields: {
      startdate: ['Start Date', '2018-03-04'], resolutiondate: ['Resolution Date', '2018-05-06'],
      procedure: ['Procedure', 'PWAppendix'], proceduredate: ['Procedure Date', '2018-04-01'],
      treatment: ['Treatment', 'PWRest'], problemstatus: ['Problem Status', 'PWResolved'],
    },
    select: {lifestage: ['Life Stage', 'A']},
  },
  {
    name: 'Ongoing Concerns', code: 'Concerns', plus: '#divR2I1 a[title="Add Item"]', container: '#divR2I1',
    fields: {
      problemdescription: ['Problem Description', 'PWChronic'], startdate: ['Start Date', '2019-01-02'],
      resolutiondate: ['Resolution Date', '2019-02-03'], problemstatus: ['Problem Status', 'PWActive'],
    },
    select: {lifestage: ['Life Stage', 'A']},
  },
  {
    name: 'Reminders', code: 'Reminders', plus: '#divR2I2 a[title="Add Item"]', container: '#divR2I2',
    fields: {startdate: ['Start Date', '2020-06-07'], resolutiondate: ['Resolution Date', '2020-08-09']},
    select: {},
  },
  {
    name: 'Family History', code: 'FamHistory', plus: '#rightNavBar a[title="Add Item"][onclick*="Family History"]',
    container: '#rightNavBar',
    fields: {
      startdate: ['Start Date', '2001-02-03'], resolutiondate: ['Resolution Date', '2002-03-04'],
      ageatonset: ['Age at Onset', '44'], treatment: ['Treatment', 'PWDiet'], relationship: ['Relationship', 'PWMother'],
    },
    select: {lifestage: ['Life Stage', 'A']},
  },
  {
    name: 'Other Meds', code: 'OMeds', plus: '#rightNavBar a[title="Add Item"][onclick*="Other Meds"]',
    container: '#rightNavBar',
    fields: {startdate: ['Start Date', '2022-04-05'], resolutiondate: ['Resolution Date', '2022-05-06']},
    select: {},
  },
  {
    name: 'Risk Factors', code: 'RiskFactors', plus: '#rightNavBar a[title="Add Item"][onclick*="Risk Factors"]',
    container: '#rightNavBar',
    fields: {
      startdate: ['Start Date', '2015-05-06'], resolutiondate: ['Resolution Date', '2016-06-07'],
      ageatonset: ['Age at Onset', '30'], exposuredetail: ['Exposure Details', 'PWSolvents'],
    },
    select: {lifestage: ['Life Stage', 'A']},
  },
];

const isNoteSave = response => /method=issueNoteSave/.test(response.url()) && response.request().method() === 'POST';

async function workflow(s) {
  const {sql, patient, provider, marker} = s;
  const owned = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
  const noteCount = () => sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}`);
  s.cleanup(() => {
    // A signed save hashes the note into hash_audit (type 'enc', the note id in `id`), one row per
    // revision. The ids are read before the notes go, so the closing count still names them.
    const noteIds = sql.rows(owned).map(([id]) => id);
    h.assert(noteIds.every(id => /^[1-9]\d*$/.test(id)), 'An owned note id was not numeric');
    const signatures = `type='enc' AND id IN (${noteIds.join(',') || 'NULL'})`;
    sql.execute(`DELETE FROM hash_audit WHERE ${signatures};
      DELETE FROM casemgmt_issue_notes WHERE note_id IN (${owned});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${owned});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${owned});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM casemgmt_issue WHERE demographic_no=${patient};
      DELETE FROM casemgmt_cpp WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM hash_audit WHERE ${signatures})
      + (SELECT COUNT(*) FROM casemgmt_issue WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_cpp WHERE demographic_no=${patient})`) === '0', 'Owned CPP rows were not removed');
  });
  h.assert(noteCount() === '0', 'The owned patient already has notes');

  const chart = await s.chart();
  const saves = [];
  chart.on('response', response => {
    if (/method=issueNoteSave/.test(response.url())) saves.push(response.status());
  });
  const dialog = chart.locator(DIALOG);
  const button = title => dialog.locator(`input[type="image"][title="${title}"]`);

  /** Opens the editor for a box, fills every field the box shows, presses Sign & Save. */
  async function addItem(box, text) {
    await chart.locator(box.plus).first().click();
    await dialog.waitFor({state: 'visible'});
    await chart.locator('#noteEditTxt').fill(text);
    for (const [id, [, value]] of Object.entries(box.fields)) {
      const field = chart.locator(`${DIALOG} #${id}`);
      h.assert(await field.isVisible(), `${box.name}: the editor does not show ${id}`);
      await field.fill(value);
    }
    for (const [id, [, value]] of Object.entries(box.select)) {
      await chart.locator(`${DIALOG} #${id}`).selectOption(value);
    }
    const arrived = chart.waitForResponse(isNoteSave, {timeout: 20000});
    await button('Sign & Save').click();
    const response = await arrived;
    h.assert(response.status() < 400, `${box.name}: Sign & Save answered HTTP ${response.status()}`);
    await dialog.waitFor({state: 'hidden', timeout: 10000});
  }

  const noteIdFor = text => sql.value(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}
    AND LOCATE(${h.sqlString(text)}, note) = 1 ORDER BY note_id DESC LIMIT 1`);
  const extRows = id => sql.rows(`SELECT key_val, COALESCE(NULLIF(value,''), date_value) FROM casemgmt_note_ext
    WHERE note_id=${id} ORDER BY key_val`).map(r => r.join('='));

  // A CPP edit, Hide in CPP and Archive each save a NEW revision row (#4641) under the item's uuid
  // rather than rewriting the row they opened; the box lists the newest one.
  const latestRevision = id => sql.value(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}
    AND uuid=(SELECT uuid FROM casemgmt_note WHERE note_id=${id}) ORDER BY note_id DESC LIMIT 1`);
  const revisionCount = id => sql.value(`SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}
    AND uuid=(SELECT uuid FROM casemgmt_note WHERE note_id=${id})`);
  /** Waits for the save that opened at `id` to commit its revision, and returns the new note id. */
  async function nextRevision(id, what) {
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}
      AND uuid=(SELECT uuid FROM casemgmt_note WHERE note_id=${id}) AND note_id>${id}`, '1',
    `${what} did not save exactly one new revision of the item`);
    return latestRevision(id);
  }

  const expectedExt = box => [
    ...Object.values(box.fields), ...Object.values(box.select),
  ].map(([key, value]) => `${key}=${value}`).sort();

  const texts = {};
  for (const box of BOXES) {
    texts[box.code] = `${marker} ${box.code} item`;
    await s.step(`${box.name}: "+" saves an item with every field the box shows, linked to ${box.code}`, async () => {
      await addItem(box, texts[box.code]);
      await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient}
        AND note=${h.sqlString(texts[box.code])} AND signed=1 AND archived=0
        AND provider_no=${h.sqlString(provider)}`, '1', `${box.name}: the signed CPP note was not stored`);
      const id = noteIdFor(texts[box.code]);
      h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_issue_notes n JOIN casemgmt_issue ci ON ci.id=n.id
        JOIN issue i ON i.issue_id=ci.issue_id WHERE n.note_id=${id} AND i.code=${h.sqlString(box.code)}`) === '1',
      `${box.name}: the note is not linked to the ${box.code} issue`);
      h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_issue_notes WHERE note_id=${id}`) === '1',
        `${box.name}: the note is linked to more than its own issue`);
      const stored = extRows(id).map(row => row.replace(/ 00:00:00$/, '')).sort();
      const wanted = expectedExt(box).map(row => row);
      h.assert(JSON.stringify(stored) === JSON.stringify(wanted),
        `${box.name}: extension rows ${JSON.stringify(stored)} differ from the fields entered ${JSON.stringify(wanted)}`);
      h.assert(!saves.some(status => status >= 400), `${box.name}: a CPP save answered HTTP ${saves.find(x => x >= 400)}`);
      // The box (centre grid) or the module (right navbar) lists the saved text.
      await chart.waitForFunction(({selector, text}) => {
        const root = document.querySelector(selector);
        return Boolean(root && root.innerText.includes(text));
      }, {selector: box.container, text: texts[box.code]}, {timeout: 15000})
        .catch(() => { throw new Error(`${box.name}: the saved item is not listed in the chart after the save`); });
    });
  }

  const medical = BOXES[0];
  const medId = () => noteIdFor(texts[medical.code]);

  await s.step('reopening an item prefills every field, and an edit saves one new revision carrying every extension row once', async () => {
    const id = medId();
    const before = noteCount();
    const originalRows = extRows(id);
    await chart.locator(`${medical.container} a[onclick*="noteId=${id}"]`).first().click();
    await dialog.waitFor({state: 'visible'});
    const shown = await chart.evaluate(({fields, selects}) => ({
      text: document.getElementById('noteEditTxt').value,
      fields: Object.fromEntries(Object.keys(fields).map(k => [k, document.getElementById(k).value])),
      selects: Object.fromEntries(Object.keys(selects).map(k => [k, document.getElementById(k).value])),
    }), {fields: medical.fields, selects: medical.select});
    h.assert(shown.text.trim() === texts[medical.code], 'Reopening did not load the saved text');
    for (const [id2, [, value]] of Object.entries(medical.fields)) {
      h.assert(shown.fields[id2] === value, `Reopening did not prefill ${id2}`);
    }
    h.assert(shown.selects.lifestage === 'A', 'Reopening did not prefill the life stage');
    texts.edited = `${texts[medical.code]} edited`;
    await chart.locator('#noteEditTxt').fill(texts.edited);
    await chart.locator(`${DIALOG} #problemstatus`).fill('PWRelapsed');
    const arrived = chart.waitForResponse(isNoteSave, {timeout: 20000});
    await button('Sign & Save').click();
    h.assert((await arrived).status() < 400, 'Saving the edit failed');
    await dialog.waitFor({state: 'hidden', timeout: 10000});
    const revised = await nextRevision(id, 'The edit');
    await expectValue(sql, `SELECT note FROM casemgmt_note WHERE note_id=${revised}`, texts.edited,
      'The edit did not store the new text on the new revision');
    h.assert(noteCount() === String(Number(before) + 1), 'The edit saved other notes besides one revision of the item');
    h.assert(sql.value(`SELECT note FROM casemgmt_note WHERE note_id=${id}`) === texts[medical.code],
      'The edit rewrote the earlier revision instead of keeping it as history');
    const wanted = expectedExt(medical).map(row => row === 'Problem Status=PWResolved' ? 'Problem Status=PWRelapsed' : row).sort();
    const rows = extRows(revised).map(row => row.replace(/ 00:00:00$/, '')).sort();
    h.assert(JSON.stringify(rows) === JSON.stringify(wanted),
      `After the edit the extension rows are ${JSON.stringify(rows)}; wanted each filled field exactly once, `
      + `with the new status and no blank field: ${JSON.stringify(wanted)}`);
    h.assert(JSON.stringify(extRows(id)) === JSON.stringify(originalRows),
      'The edit changed the extension rows of the earlier revision');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_issue_notes WHERE note_id=${revised}`) === '1',
      'The new revision is not linked to exactly its own issue');
  });

  await s.step('the box title opens the issue history listing the saved item', async () => {
    const link = chart.locator(`${medical.container} .nav-menu-title a`).first();
    h.assert(await link.count() === 1, 'Medical History offers no title link');
    const history = await s.popup(chart, link, 'cpp-issue-history');
    await history.waitForLoadState('domcontentloaded');
    const body = await history.locator('body').innerText();
    h.assert(body.includes(texts.edited), 'The issue history does not list the saved Medical History item');
    await history.close();
  });

  await s.step('Family History "Hide in CPP = Yes" is stored on the item without archiving it', async () => {
    const family = BOXES[3];
    const id = noteIdFor(texts[family.code]);
    await chart.locator(`${family.container} a[onclick*="noteId=${id}"]`).first().click();
    await dialog.waitFor({state: 'visible'});
    await chart.locator(`${DIALOG} #hidecpp`).selectOption('1');
    const arrived = chart.waitForResponse(isNoteSave, {timeout: 20000});
    await button('Sign & Save').click();
    h.assert((await arrived).status() < 400, 'Saving Hide in CPP answered an error');
    await dialog.waitFor({state: 'hidden', timeout: 10000});
    const hidden = await nextRevision(id, 'Hide in CPP');
    h.assert(sql.value(`SELECT COUNT(*) FROM casemgmt_note_ext WHERE note_id=${hidden} AND key_val='Hide Cpp' AND value='1'`) === '1',
      'Hide in CPP was not stored on the item');
    const wanted = [...expectedExt(family), 'Hide Cpp=1'].sort();
    const rows = extRows(hidden).map(row => row.replace(/ 00:00:00$/, '')).sort();
    h.assert(JSON.stringify(rows) === JSON.stringify(wanted),
      `Family History: after Hide in CPP the extension rows are ${JSON.stringify(rows)}, wanted ${JSON.stringify(wanted)}`);
    h.assert(sql.value(`SELECT archived FROM casemgmt_note WHERE note_id=${hidden}`) === '0', 'Hiding archived the item');
  });

  const scratch = print.scratchDir();
  s.cleanup(() => require('node:fs').rmSync(scratch, {recursive: true, force: true}));
  async function printCpp() {
    await print.openPrintDialog(chart);
    // "Print selected notes" with an empty queue: only the CPP section is wanted ("Print all" would also
    // list every CPP item again as an ordinary note).
    await chart.locator('#printopSelected').check();
    await print.setFlags(chart, ['printCPP']);
    return (await print.pressPrint(chart, scratch)).text;
  }

  await s.step('the chart print with the CPP icon lists the active items and leaves out the one flagged Hide in CPP', async () => {
    const text = await printCpp();
    for (const code of ['MedHistory', 'OMeds', 'Concerns', 'Reminders', 'RiskFactors']) {
      const wanted = code === 'MedHistory' ? texts.edited : texts[code];
      h.assert(text.includes(wanted), `The CPP print is missing the ${code} item`);
    }
    h.assert(!text.includes(texts.FamHistory), 'The CPP print carries the Family History item that is flagged Hide in CPP');
  });

  await s.step('Archive removes the item from the box and saves an archived revision with the same rows', async () => {
    const id = medId();
    const rowsBefore = extRows(id);
    const revisionsBefore = Number(revisionCount(id));
    await chart.locator(`${medical.container} a[onclick*="noteId=${id}"]`).first().click();
    await dialog.waitFor({state: 'visible'});
    const arrived = chart.waitForResponse(isNoteSave, {timeout: 20000});
    await button('Archive').click();
    h.assert((await arrived).status() < 400, 'Archive answered an error');
    const archived = await nextRevision(id, 'Archive');
    await expectValue(sql, `SELECT archived FROM casemgmt_note WHERE note_id=${archived}`, '1', 'Archive did not flag the item archived');
    h.assert(sql.value(`SELECT archived FROM casemgmt_note WHERE note_id=${id}`) === '0',
      'Archive rewrote the earlier revision instead of saving a new one');
    h.assert(Number(revisionCount(id)) === revisionsBefore + 1, 'Archive saved more than one revision');
    h.assert(JSON.stringify(extRows(archived)) === JSON.stringify(rowsBefore),
      `Archive changed the extension rows: ${JSON.stringify(extRows(archived))} after ${JSON.stringify(rowsBefore)}`);
    await chart.waitForFunction(({selector, text}) => !document.querySelector(selector).innerText.includes(text),
      {selector: medical.container, text: texts.edited}, {timeout: 15000})
      .catch(() => { throw new Error('The archived item is still listed in the Medical History box'); });
  });

  await s.step('the CPP print no longer carries the archived Medical History item', async () => {
    const text = await printCpp();
    h.assert(!text.includes(texts.edited), 'The CPP print still carries the archived Medical History item');
    h.assert(text.includes(texts.Concerns), 'The CPP print lost the Ongoing Concerns item');
  });
}

if (require.main === module) runWorkflow('gap-clinical-cpp-boxes', workflow, {openPatient: true, preflight: () => requirePoppler('pdftotext')});
module.exports = {workflow};
