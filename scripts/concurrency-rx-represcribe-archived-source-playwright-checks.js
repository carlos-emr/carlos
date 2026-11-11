#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/*
 * Concurrency check (#5987): a medication is re-prescribed from a window that still shows it as
 * active after another window has discontinued it.
 *
 * User path (two sessions of the shared test login): Schedule > Search > Master Record >
 * Prescriptions (Rx module). Session A ticks the drug's ReRx box and stages the copy (Stage
 * medication). Session B, whose profile also shows the drug as active, discontinues it for
 * "doseChange" (Discon link > reason + comment > Discontinue). Session A then presses Save Only.
 *
 * Asserted: A's save still writes exactly one new active copy (the clinician's prescription is
 * not lost), the source stays archived with B's reason ("doseChange") and B's archived date, and a
 * control source that nobody touched is still archived as "represcribed" by the same save.
 * Before the fix RxWriteScript2Action.archiveReRxSource called archiveDrug() on the
 * already-archived source, which overwrote archived_reason and archived_date.
 *
 * Fixtures: two marker-named current custom drugs for the owned FAKE- patient (the raced source and
 * an untouched control). Cleanup removes the patient's drugs, prescriptions, drugReason,
 * DigitalSignature and discontinue chart notes with their links, and asserts none remain.
 * Optional SCREENSHOT_DIR keeps before/after evidence of the profile.
 */
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { openSecondSession } = require('./lib/concurrency-support');
const { clickOpensPopup } = require('./lib/playwright-ui');
const { masterRecordLink } = require('./lib/master-record-link');

const q = h.sqlString;
const isPost = route => response => response.request().method() === 'POST' && h.pathOnly(response.url()).endsWith(route);
const isDiscontinue = response => isPost('/rx/deleteRx')(response)
  && new URL(response.url()).searchParams.get('parameterValue') === 'Discontinue';
const isSaveAll = response => isPost('/rx/WriteScript')(response)
  && new URL(response.url()).searchParams.get('parameterValue') === 'updateSaveAllDrugs';
const reRxAdd = drug => response => isPost('/rx/WriteScript')(response) && (() => {
  const form = new URLSearchParams(response.request().postData() || '');
  return form.get('parameterValue') === 'updateReRxDrug' && form.get('action') === 'addToReRxDrugIdList'
    && form.get('reRxDrugId') === String(drug);
})();
/** Waits for the response the predicate names; a timeout says which request never answered. */
const answered = (page, predicate, what) => page.waitForResponse(predicate, { timeout: 60000 })
  .catch(error => { throw new Error(`${what} never answered: ${error.message.split('\n')[0]}`); });

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
    sql.execute(`DELETE FROM drugReason WHERE demographicNo=${patient};
      DELETE FROM drugs WHERE demographic_no=${patient}; DELETE FROM prescription WHERE demographic_no=${patient};
      DELETE FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='PRESCRIPTION'`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM DigitalSignature WHERE demographicId=${patient})
      + (SELECT COUNT(*) FROM casemgmt_note WHERE demographic_no=${patient})`) === '0', 'Owned Rx rows were not removed');
  });

  /** Inserts one marker-named current custom drug for the owned patient; returns its id and name. */
  function insertDrug(suffix) {
    const name = `${marker}-${suffix}`;
    const id = sql.value(`INSERT INTO drugs(provider_no,demographic_no,rx_date,end_date,written_date,
      BN,GCN_SEQNO,customName,takemin,takemax,freqcode,duration,durunit,quantity,\`repeat\`,
      special,archived,archived_reason,position,dispenseInternal,create_date,lastUpdateDate,long_term)
      VALUES(${q(provider)},${patient},DATE_SUB(CURDATE(),INTERVAL 20 DAY),DATE_ADD(CURDATE(),INTERVAL 10 DAY),
      DATE_SUB(CURDATE(),INTERVAL 20 DAY),${q(name)},0,${q(name)},1,1,'OD','30','D','30',0,${q(`${name} one tablet daily`)},
      0,'',0,0,NOW(),NOW(),0); SELECT LAST_INSERT_ID()`);
    h.assert(/^[1-9]\d*$/.test(id), `The ${suffix} fixture drug was not inserted`);
    return { id, name };
  }
  const raced = insertDrug('RACED');
  const control = insertDrug('CONTROL');
  const state = drug => sql.value(`SELECT CONCAT(archived,'|',IFNULL(archived_reason,'')) FROM drugs WHERE drugid=${drug.id}`);
  const copies = drug => sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND customName=${q(drug.name)}
    AND drugid NOT IN (${raced.id},${control.id}) AND archived=0`);

  const b = await openSecondSession(s, { label: 'second-session' });
  const openRx = async (context, master, label) => {
    const rx = await clickOpensPopup(master, masterRecordLink(master, '/rx/choosePatient').first(),
      { context, recorder: s.recorder, label, timeout: 20000 });
    h.assert(new URL(rx.url()).searchParams.get('demographicNo') === patient, 'The Rx module opened for another patient');
    await rx.locator('#searchString').waitFor({ state: 'visible' });
    await rx.waitForLoadState('networkidle');
    await rx.locator(`#discont_${raced.id}`).waitFor({ state: 'visible', timeout: 20000 });
    return rx;
  };
  const aRx = await openRx(s.context, s.master, 'rx-a');
  const bRx = await openRx(b.context, b.master, 'rx-b');
  const shot = (page, name) => h.screenshot(page, s.config.screenshotDir, name);

  await s.step('session A ticks ReRx on both drugs and stages their copies', async () => {
    for (const drug of [raced, control]) {
      const [added] = await Promise.all([
        answered(aRx, reRxAdd(drug.id), `adding ${drug.name} to the ReRx list`),
        aRx.locator(`#reRxCheckBox_${drug.id}`).check(),
      ]);
      h.assert(added.status() === 200, `Adding ${drug.name} to the ReRx list answered HTTP ${added.status()}`);
    }
    await aRx.locator('#reRxConfirmBox input[name="stage"]').click();
    await aRx.waitForFunction(() => document.querySelectorAll("[id^='drugName_']").length >= 2, null, { timeout: 30000 });
    await aRx.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    await shot(aRx, 'before-save-A-staged-copies');
  });

  await s.step('session B discontinues the raced medication from its own window', async () => {
    await bRx.locator(`#discont_${raced.id}`).click();
    const panel = bRx.locator('#discontinueUI');
    await panel.waitFor({ state: 'visible' });
    await bRx.locator('#disReason').selectOption('doseChange');
    await bRx.locator('#disComment').fill(`${marker} B`);
    const [response] = await Promise.all([
      bRx.waitForResponse(isDiscontinue, { timeout: 30000 }),
      panel.locator('input[onclick*="Discontinue2("]').click(),
    ]);
    h.assert(response.status() === 200, `Discontinue answered HTTP ${response.status()}`);
    await expectValue(sql, `SELECT CONCAT(archived,'|',archived_reason) FROM drugs WHERE drugid=${raced.id}`, '1|doseChange',
      'Session B\'s discontinue did not archive the drug with its reason');
  });
  const discontinuedDate = sql.value(`SELECT archived_date FROM drugs WHERE drugid=${raced.id}`);
  h.assert(discontinuedDate && discontinuedDate !== 'NULL', 'The discontinue wrote no archived date');
  await shot(bRx, 'before-save-B-discontinued');

  await s.step('session A saves: both copies are written and the sources are settled', async () => {
    const [saved] = await Promise.all([
      answered(aRx, isSaveAll, 'Save Only (rx/WriteScript updateSaveAllDrugs)'),
      aRx.locator('#saveOnlyButton').click(),
    ]);
    h.assert(saved.status() === 200, `Save Only answered HTTP ${saved.status()}`);
    await expectValue(sql, `SELECT archived_reason FROM drugs WHERE drugid=${control.id}`, 'represcribed',
      'Save Only did not archive the untouched control source as represcribed');
    h.assert(copies(raced) === '1' && copies(control) === '1',
      `Save Only wrote ${copies(raced)} / ${copies(control)} active copies (expected 1 of each): the prescription must not be lost`);
  });

  await s.step('the discontinued source keeps its own reason and date', async () => {
    // Evidence first, so a failing run still keeps the profile it asserts on: the All view lists archived
    // drugs, and a discontinued one shows its archive reason in the Discontinue column.
    await aRx.reload({ waitUntil: 'domcontentloaded' });
    await aRx.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    await aRx.locator('a[onclick*="selectDrugProfile(\'all\'"]').click();
    await aRx.locator('#drugProfile').getByText(raced.name).first().waitFor({ state: 'visible', timeout: 20000 });
    await aRx.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    await shot(aRx, 'after-save-A-profile-all');
    const archivedDate = sql.value(`SELECT archived_date FROM drugs WHERE drugid=${raced.id}`);
    h.assert(state(raced) === '1|doseChange' && archivedDate === discontinuedDate,
      `After the stale re-prescribe the discontinued source is archived as '${state(raced).split('|')[1]}' on ${archivedDate} `
      + `(expected 'doseChange' on ${discontinuedDate}). RxWriteScript2Action.archiveReRxSource must skip an already archived source `
      + 'instead of letting archiveDrug() overwrite its archive reason and date.');
    h.assert(state(control) === '1|represcribed', `The control source is '${state(control)}', expected '1|represcribed'`);
  });
}

module.exports = { workflow };
if (require.main === module) runWorkflow('concurrency-rx-represcribe-archived-source', workflow, { openPatient: true });
