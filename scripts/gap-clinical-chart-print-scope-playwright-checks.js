#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Gap check (clinical): what the E-Chart "Print" dialog actually puts in the PDF.
 *
 * User path: Schedule ▸ Search ▸ Master Record ▸ E-Chart ▸ a note's printer icon (queue the note) ▸
 * the control-panel printer icon ▸ "Print selected notes" / "Print all" with the section icons
 * (Allergies, Preventions) / "Print dates" ▸ Today ▸ Print / Clear.
 * echart-print proves the print is not blocked by the WAF and returns a PDF for every note body and
 * flag combination, but never reads the PDF: it would pass if "selected" printed everything or
 * "today" printed nothing. A chart print is what leaves the building (referrals, medico-legal
 * requests), so the scope is the contract.
 *
 * Asserts (pdftotext of each downloaded PDF): queued notes print and an unqueued one does not; Print
 * all prints every owned note and the Allergies/Preventions sections only when their icons are on
 * (and not when off); the Today link prints only notes observed today; Print with nothing queued and
 * no section raises the "nothing to print" alert and posts nothing; Clear empties the queue.
 *
 * Fixtures: the owned FAKE-PW patient (runWorkflow) with three SQL-seeded signed notes (observed
 * 2021-06-15, 2022-09-15 and now), one allergy and one prevention carrying the marker. Cleanup
 * deletes every note/allergy/prevention row of that patient and asserts them gone.
 * Coverage plan §2.5 chart-print (gap-clinical). Needs pdftotext.
 */
const h = require('./lib/playwright-harness');
const {runWorkflow} = require('./lib/workflow-session');
const {requirePoppler} = require('./lib/stored-pdf-documents');
const print = require('./lib/gap-clinical-print');

async function workflow(s) {
  const {sql, patient, provider, marker} = s;
  const q = h.sqlString;
  const scratch = print.scratchDir();
  s.cleanup(() => require('node:fs').rmSync(scratch, {recursive: true, force: true}));
  s.cleanup(() => {
    const owned = `SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`;
    sql.execute(`DELETE FROM casemgmt_issue_notes WHERE note_id IN (${owned});
      DELETE FROM casemgmt_note_ext WHERE note_id IN (${owned});
      DELETE FROM casemgmt_note_link WHERE note_id IN (${owned});
      DELETE FROM casemgmt_note WHERE demographic_no=${patient};
      DELETE FROM casemgmt_issue WHERE demographic_no=${patient};
      DELETE FROM allergies WHERE demographic_no=${patient};
      DELETE FROM preventions WHERE demographic_no=${patient};
      DELETE FROM casemgmt_cpp WHERE demographic_no=${patient};
      DELETE FROM eChart WHERE demographicNo=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM allergies WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM preventions WHERE demographic_no=${patient})`) === '0', 'Owned print fixtures were not removed');
  });

  const program = sql.value(`SELECT id FROM program WHERE name='OSCAR' ORDER BY id LIMIT 1`);
  const role = sql.value(`SELECT role_id FROM program_provider WHERE provider_no=${q(provider)} AND program_id=${program || 0} LIMIT 1`);
  h.assert(program && role, 'The default OSCAR program or the test provider\'s role in it is missing');
  const token = {old: `${marker} OLDNOTE`, mid: `${marker} MIDNOTE`, now: `${marker} TODAYNOTE`};
  const seed = (text, when) => sql.value(`INSERT INTO casemgmt_note (update_date,observation_date,demographic_no,provider_no,note,signed,
      signing_provider_no,encounter_type,program_no,reporter_caisi_role,history,uuid,locked,archived)
    VALUES (NOW(),${when},${patient},${q(provider)},${q(text)},1,${q(provider)},'',${q(program)},${q(role)},${q(text)},UUID(),'0',0);
    SELECT LAST_INSERT_ID()`);
  const ids = {
    old: seed(token.old, "'2021-06-15 10:00:00'"),
    mid: seed(token.mid, "'2022-09-15 10:00:00'"),
    now: seed(token.now, 'NOW()'),
  };
  const cppToken = `${marker} CPPHISTORY`;
  const cppNote = seed(cppToken, "'2021-06-15 09:00:00'");
  const cppIssue = sql.value("SELECT issue_id FROM issue WHERE code='MedHistory' LIMIT 1");
  h.assert(cppIssue, 'The Medical History CPP issue is missing');
  sql.execute(`INSERT INTO casemgmt_issue (demographic_no,issue_id,acute,certain,major,resolved,program_id,type,update_date)
    VALUES (${patient},${cppIssue},0,0,0,0,${program},'doctor',NOW());
    INSERT INTO casemgmt_issue_notes (id,note_id) VALUES (LAST_INSERT_ID(),${cppNote})`);
  sql.execute(`INSERT INTO allergies (demographic_no,entry_date,DESCRIPTION,TYPECODE,reaction,archived,position,lastUpdateDate,providerNo)
    VALUES (${patient},CURDATE(),${q(`${marker} ALLERGEN`)},0,'rash',0,0,NOW(),${q(provider)});
    INSERT INTO preventions (demographic_no,creation_date,prevention_date,provider_no,prevention_type,deleted,refused,never,lastUpdateDate)
    VALUES (${patient},NOW(),'2022-01-02 00:00:00',${q(provider)},'MMR','0','0','0',NOW())`);

  let chart = await s.chart();
  // The queue icons exist only on rendered notes: wait for the three seeded notes.
  for (const id of Object.values(ids)) await chart.locator(`#print${id}`).waitFor({state: 'attached', timeout: 20000});
  const posted = [];
  chart.on('request', r => { if (r.method() === 'POST' && /method=print/.test(r.postData() || '')) posted.push(r.url()); });

  await s.step('queued notes print and an unqueued note does not (Print selected)', async () => {
    await chart.locator(`#print${ids.old}`).click();
    await chart.locator(`#print${ids.now}`).click();
    h.assert(await chart.locator('#notes2print').inputValue() === `${ids.old},${ids.now}` ||
      await chart.locator('#notes2print').inputValue() === `${ids.now},${ids.old}`, 'The note printer icons did not queue the two notes');
    await print.openPrintDialog(chart);
    h.assert(await chart.locator('#printopSelected').isChecked(), 'With notes queued the dialog did not preselect "selected notes"');
    const {text} = await print.pressPrint(chart, scratch);
    h.assert(text.includes(token.old) && text.includes(token.now), 'The print is missing a queued note');
    h.assert(!text.includes(token.mid), 'The print carries a note that was never queued');
    h.assert(!text.includes(`${marker} ALLERGEN`), 'The print carries the allergy section although its icon was off');
  });

  // Application defects found on the way are collected and thrown after every other step has run,
  // so one cannot hide another.
  const defects = [];
  await s.step('Print with nothing queued raises the nothing-to-print alert and posts nothing (Clear unqueues the notes)', async () => {
    await print.openPrintDialog(chart);
    await chart.locator('#clearprintOp').click();
    const left = await chart.locator('#notes2print').inputValue();
    if (left !== '') {
      defects.push(`Clear left "${left}" queued: after the chart's older-notes fetch returns an empty batch, ChartNotesAjax.jsp:856 `
        + `sets maxNcId = 0, so clearAll() loops over no notes and the queued notes stay selected`);
      // Recover the way a clinician would: toggle the queued notes off one by one.
      for (const id of [ids.old, ids.now]) await chart.locator(`#print${id}`).click();
    }
    h.assert(await chart.locator('#notes2print').inputValue() === '', 'The note printer icons did not unqueue the notes');
    for (const id of [ids.old, ids.now]) {
      h.assert((await chart.locator(`#print${id}`).getAttribute('src')).endsWith('/printer.png'),
        'Clear left a note printer icon selected');
    }
    await print.openPrintDialog(chart);
    await chart.locator('#printopSelected').check();
    await print.setFlags(chart, []);
    const before = posted.length;
    let alerted = '';
    await h.withExpectedDialogs(chart, async () => {
      chart.once('dialog', d => { alerted = d.message(); });
      await chart.locator('#printOp').click();
      await chart.waitForTimeout(500);
    }, {accept: true});
    h.assert(alerted !== '', 'Print with nothing selected gave no alert');
    h.assert(posted.length === before, 'Print with nothing selected still posted a print request');
    await chart.locator('#cancelprintOp').click();
  });

  await s.step('Print all with the Allergies and Preventions icons on prints every note and both sections', async () => {
    await print.openPrintDialog(chart);
    await chart.locator('#printopAll').check();
    await print.setFlags(chart, ['printAllergies', 'printPreventions']);
    const {text} = await print.pressPrint(chart, scratch);
    for (const t of Object.values(token)) h.assert(text.includes(t), 'Print all is missing an owned note');
    h.assert(text.includes(`${marker} ALLERGEN`), 'The Allergies section is missing from the print');
    h.assert(/MMR/.test(text), 'The Preventions section is missing the owned MMR prevention');
  });

  await s.step('Print all with the section icons off prints notes only', async () => {
    await print.openPrintDialog(chart);
    await chart.locator('#printopAll').check();
    await print.setFlags(chart, []);
    const {text} = await print.pressPrint(chart, scratch);
    h.assert(Object.values(token).every(t => text.includes(t)), 'Print all is missing an owned note');
    h.assert(!text.includes(`${marker} ALLERGEN`) && !/MMR/.test(text), 'A section printed although its icon was off');
    h.assert(text.includes(cppToken), 'A CPP note was omitted even though its separate section was off');
  });

  await s.step('Print all with CPP enabled prints the CPP note exactly once', async () => {
    await print.openPrintDialog(chart);
    await chart.locator('#printopAll').check();
    await print.setFlags(chart, ['printCPP']);
    const {text} = await print.pressPrint(chart, scratch);
    const occurrences = text.replace(/\s+/g, ' ').split(cppToken).length - 1;
    if (occurrences !== 1) defects.push(`Print all with CPP rendered the CPP note ${occurrences} times, expected once`);
    h.assert(Object.values(token).every(t => text.includes(t)), 'CPP printing removed an ordinary note');
  });

  await s.step('Clear after a Print all empties the print queue', async () => {
    await print.openPrintDialog(chart);
    await chart.locator('#printopAll').check();
    await print.pressPrint(chart, scratch);
    await print.openPrintDialog(chart);
    await chart.locator('#clearprintOp').click();
    const queue = await chart.locator('#notes2print').inputValue();
    if (queue !== '') defects.push(`Clear after a Print all left "${queue}" in the print queue; the dialog then still counts as having a selection`);
    h.assert(Object.values(await print.flagState(chart)).every(value => value === 'false'),
      'Clear left a clinical section selected');
  });

  try {
    // A failed Clear (recorded above) leaves the queue and the dialog's scope radio as the earlier
    // steps set them; the Today check must not inherit that, so it runs on a freshly opened chart.
    await chart.close();
    chart = await s.chart();
    await chart.locator(`#print${ids.now}`).waitFor({state: 'attached', timeout: 20000});
    await s.step('the dialog\'s Today link prints the notes observed today (date-range print)', async () => {
      await print.openPrintDialog(chart);
      const {text} = await print.pressPrint(chart, scratch, () => chart.locator('#printOps a', {hasText: /today/i}).click());
      h.assert(text.includes(token.now), 'Print Today is missing the note observed today');
      h.assert(!text.includes(token.old) && !text.includes(token.mid), 'Print Today carries notes from earlier years');
    });
  } catch (error) {
    defects.push(error.message);
  }
  h.assert(defects.length === 0, defects.join(' | '));
}

if (require.main === module) {
  runWorkflow('gap-clinical-chart-print-scope', workflow, {openPatient: true, preflight: () => requirePoppler('pdftotext')});
}
module.exports = {workflow};
