#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Concurrency check: two sessions discontinue the same medication.
 *
 * User path (both sessions of the shared test login): Schedule > Search > Master Record > Prescriptions
 * (Rx module) > the drug row's Discon link > reason + comment > Discontinue (rx/deleteRx
 * parameterValue=Discontinue). Both Rx pages are loaded while the drug is active. Session A
 * discontinues it for "doseChange"; session B, whose profile still shows it as active, discontinues it
 * for "allergy".
 *
 * Asserted: A's discontinue archives the drug with its reason and files one linked chart note
 * (control); after B's discontinue the first reason and date still stand (a refusal is fine) and the
 * chart holds exactly one discontinue note. RxDeleteRx2Action.Discontinue only checks that the drug
 * belongs to the patient, so a stale second discontinue overwrites archived_reason / archived_date and
 * files a second, contradictory chart note. The check fails at the reason / note step.
 *
 * Fixtures: one owned custom medication seeded by SQL for the owned FAKE- patient; cleanup removes the
 * drugs row, the discontinue chart notes with their links and asserts nothing remains.
 * Wave-7 sweep "concurrency".
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { openSecondSession, failureMark, consumeExpectedFailure } = require('./lib/concurrency-support');
const { clickOpensPopup } = require('./lib/playwright-ui');

const isDiscontinue = response => response.request().method() === 'POST' && h.pathOnly(response.url()).endsWith('/rx/deleteRx')
  && new URL(response.url()).searchParams.get('parameterValue') === 'Discontinue';

async function discontinue(rx, drug, reason, comment) {
  await rx.locator(`#discont_${drug}`).click();
  const panel = rx.locator('#discontinueUI');
  await panel.waitFor({ state: 'visible' });
  await rx.locator('#disReason').selectOption(reason);
  await rx.locator('#disComment').fill(comment);
  const [response] = await Promise.all([
    rx.waitForResponse(isDiscontinue, { timeout: 30000 }),
    panel.locator('input[onclick*="Discontinue2("]').click(),
  ]);
  return response;
}

async function workflow(s) {
  const { sql, patient, marker, provider } = s;
  s.cleanup(() => {
    const notes = sql.rows(`SELECT note_id FROM casemgmt_note WHERE demographic_no=${patient}`).map(row => row[0]);
    h.assert(notes.every(id => /^[1-9]\d*$/.test(id)), 'Owned note id is invalid');
    if (notes.length) {
      sql.execute(`DELETE FROM casemgmt_note_link WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_issue_notes WHERE note_id IN (${notes.join(',')});
        DELETE FROM casemgmt_note WHERE demographic_no=${patient} AND note_id IN (${notes.join(',')})`);
    }
    sql.execute(`DELETE FROM drugs WHERE demographic_no=${patient}`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})`) === '0', 'Owned Rx rows were not removed');
  });
  const drug = sql.value(`INSERT INTO drugs (provider_no,demographic_no,rx_date,end_date,written_date,customName,special,archived,archived_reason,
      create_date,position,lastUpdateDate,dispenseInternal,quantity,duration,durunit,freqcode)
    VALUES (${h.sqlString(provider)},${patient},CURDATE(),DATE_ADD(CURDATE(),INTERVAL 30 DAY),CURDATE(),${h.sqlString(marker)},${h.sqlString(`${marker} 1 tab PO daily`)},0,'',
      NOW(),0,NOW(),0,'30','30','D','OD'); SELECT LAST_INSERT_ID()`);
  h.assert(/^[1-9]\d*$/.test(drug), 'The drug fixture was not created');
  const archived = `SELECT CONCAT(archived,'|',archived_reason) FROM drugs WHERE drugid=${drug}`;
  const notesFor = () => sql.value(`SELECT COUNT(*) FROM casemgmt_note n JOIN casemgmt_note_link l ON l.note_id=n.note_id AND l.table_name=2
    AND l.table_id=${drug} WHERE n.demographic_no=${patient}`);

  const b = await openSecondSession(s, { label: 'second-session' });
  const openRx = async (context, master, label) => {
    const rx = await clickOpensPopup(master, master.locator('a[onclick*="/rx/choosePatient"]').first(), { context, recorder: s.recorder, label, timeout: 20000 });
    h.assert(new URL(rx.url()).searchParams.get('demographicNo') === patient, 'The Rx module opened for another patient');
    await rx.locator('#searchString').waitFor({ state: 'visible' });
    await rx.waitForLoadState('networkidle');
    await rx.locator(`#discont_${drug}`).waitFor({ state: 'visible', timeout: 20000 });
    return rx;
  };
  const aRx = await openRx(s.context, s.master, 'rx-a');
  const bRx = await openRx(b.context, b.master, 'rx-b');

  await s.step('session A discontinues the medication: archived with its reason and one linked chart note', async () => {
    const response = await discontinue(aRx, drug, 'doseChange', `${marker} A`);
    h.assert(response.status() === 200, `Discontinue answered HTTP ${response.status()}`);
    await expectValue(sql, archived, '1|doseChange', 'Session A\'s discontinue did not archive the drug with its reason');
    await expectValue(sql, `SELECT COUNT(*) FROM casemgmt_note n JOIN casemgmt_note_link l ON l.note_id=n.note_id AND l.table_name=2
      AND l.table_id=${drug} WHERE n.demographic_no=${patient}`, '1', 'Session A\'s discontinue did not file exactly one chart note');
  });
  const firstArchivedDate = sql.value(`SELECT archived_date FROM drugs WHERE drugid=${drug}`);
  let second;
  await s.step('session B discontinues the same medication from its stale profile', async () => {
    const mark = failureMark(s.recorder);
    const alerts = await h.withExpectedDialogs(bRx, async () => {
      second = await discontinue(bRx, drug, 'allergy', `${marker} B`);
      await bRx.waitForTimeout(500);
    });
    h.assert(alerts.length === 1 && alerts[0].type === 'alert' && /could not be completed/i.test(alerts[0].text),
      'The stale discontinue must tell the user that the request was refused');
    h.assert(second.status() === 409, `The stale discontinue answered HTTP ${second.status()} instead of 409`);
    // A 4xx is a valid refusal; consume exactly that one response so the strict page check after the step does not report it
    // (the next step judges the stored state).
    consumeExpectedFailure(s.recorder, mark, { status: 409, path: /\/rx\/deleteRx$/ });
  });
  await s.step('the first discontinue reason stands and the chart holds one discontinue note', async () => {
    const state = sql.value(archived);
    const notes = notesFor();
    const archivedDate = sql.value(`SELECT archived_date FROM drugs WHERE drugid=${drug}`);
    h.assert(state === '1|doseChange' && notes === '1' && archivedDate === firstArchivedDate,
      `After two sessions discontinued the same drug it is archived as '${state.split('|')[1]}' with ${notes} chart note(s) (expected the first reason, one note). `
      + 'RxDeleteRx2Action.Discontinue only checks the drug belongs to the patient, so the stale second discontinue overwrites archived_reason and archived_date '
      + 'and files a second, contradictory discontinue note.');
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('concurrency-rx-discontinue', workflow, { openPatient: true });
