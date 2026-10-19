#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// rx-delete-drug: the drug profile's "Del" link, which removes a drug written in error (as opposed to
// "Discon", which stops a drug for a clinical reason).
// User path: Schedule ▸ Search ▸ Master Record ▸ Prescriptions ▸ type a name ▸ Custom Drug ▸ Instructions ▸
// Save Only ▸ the drug's "Del" link in the profile (ListDrugs.jsp ▸ SearchDrug3.jsp Delete2 ▸ confirm ▸
// POST rx/deleteRx?parameterValue=Delete2, RxDeleteRx2Action.Delete2), then
//   1. dismissing the confirm sends nothing and keeps the drug current;
//   2. a GET of the same request is refused (405) and keeps the drug current;
//   3. accepting it posts the delete with the page's CSRF token, archives the drug with the reason "deleted"
//      and today's date, logs the delete against the patient, and the current profile no longer lists it;
//   4. "Show All" lists it again, struck through as deleted;
//   5. the E-Chart's Medications box does not list it.
// A second drug saved with it stays current throughout, so each "no longer listed" has a listed control.
// No other check uses "Del": rx-edit-discontinue and rx-prescription-lifecycle (open PR #4712) cover "Discon",
// and rx-stash-deletion covers clearing the staged drugs.
// Fixtures: the owned FAKE- patient and two marker-named custom drugs. Cleanup removes the patient's drugs,
// prescriptions, drugReason and DigitalSignature rows, and asserts none remain. The audit log rows stay.
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { stageCustomDrug } = require('./rx-stash-patient-isolation-playwright-checks');
const { masterRecordLink } = require('./lib/master-record-link');

const isPost = route => response => response.request().method() === 'POST' && h.pathOnly(response.url()).endsWith(route);
const isDelete = request => request.method() === 'POST' && h.pathOnly(request.url()).endsWith('/rx/deleteRx')
  && new URL(request.url()).searchParams.get('parameterValue') === 'Delete2';

async function profileLoaded(rx) {
  await rx.locator('#drugProfile[aria-busy="false"]').waitFor({ state: 'attached' });
}

async function workflow(s) {
  const { sql, patient, marker } = s;
  s.cleanup(() => {
    sql.execute(`DELETE FROM drugReason WHERE demographicNo=${patient};
      DELETE FROM drugs WHERE demographic_no=${patient}; DELETE FROM prescription WHERE demographic_no=${patient};
      DELETE FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='PRESCRIPTION'`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM DigitalSignature WHERE demographicId=${patient})`) === '0', 'Owned Rx rows were not removed');
  });
  // The E-Chart box may crop long names, so the word that tells the two drugs apart comes first.
  const goneName = `Gone ${marker} FAKE drug`;
  const keptName = `Kept ${marker} FAKE drug`;
  const rx = await s.popup(s.master, masterRecordLink(s.master, '/rx/choosePatient').first(), 'rx-module');
  h.assert(new URL(rx.url()).searchParams.get('demographicNo') === patient, 'The Rx module opened for another patient');
  await rx.locator('#searchString').waitFor({ state: 'visible' });
  await rx.waitForLoadState('networkidle');

  for (const name of [goneName, keptName]) {
    const card = await stageCustomDrug(rx, name);
    await rx.locator(`#instructions_${card}`).fill('1 tab PO OD x 30 days');
    const [parsed] = await Promise.all([
      rx.waitForResponse(isPost('/rx/UpdateScript')),
      rx.locator(`label[for="jsonDxSearch_${card}"]`).click(),
    ]);
    h.assert(parsed.status() === 200, `rx/UpdateScript answered HTTP ${parsed.status()}`);
  }
  const [saved] = await Promise.all([
    rx.waitForResponse(r => isPost('/rx/WriteScript')(r) && new URL(r.url()).searchParams.get('parameterValue') === 'updateSaveAllDrugs'),
    rx.locator('#saveOnlyButton').click(),
  ]);
  h.assert(saved.status() === 200, `Save Only answered HTTP ${saved.status()}`);
  await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND archived=0`, '2', 'Save Only did not store both drugs');
  const idOf = name => sql.value(`SELECT drugid FROM drugs WHERE demographic_no=${patient} AND customName=${h.sqlString(name)}`);
  const drug = idOf(goneName);
  const kept = idOf(keptName);
  h.assert(/^[1-9]\d*$/.test(drug) && /^[1-9]\d*$/.test(kept), 'The two saved drugs were not found by name');
  const state = () => sql.value(`SELECT CONCAT(archived,'|',IFNULL(archived_reason,''),'|',IFNULL(DATE(archived_date)=CURDATE(),''))
    FROM drugs WHERE drugid=${drug}`);
  const del = rx.locator(`#del_${drug}`);
  await del.waitFor({ state: 'visible' });

  await s.step('dismissing the Del confirm sends nothing and keeps the drug current', async () => {
    const sent = [];
    const watch = request => { if (h.pathOnly(request.url()).endsWith('/rx/deleteRx')) sent.push(request.method()); };
    rx.on('request', watch);
    try {
      const dialogs = await h.withExpectedDialogs(rx, () => del.click(), { accept: false });
      h.assert(dialogs.length === 1, `Del raised ${dialogs.length} dialogs instead of one confirm`);
    } finally {
      rx.off('request', watch);
    }
    h.assert(sent.length === 0, `A dismissed Del still sent ${JSON.stringify(sent)} to rx/deleteRx`);
    h.assert(state() === '0||', `A dismissed Del changed the drug to ${state()}`);
    h.assert(await del.isVisible(), 'A dismissed Del took the drug off the profile');
  });

  await s.step('a GET of the delete is refused and keeps the drug current', async () => {
    const response = await s.context.request.get(
      h.appUrl(s.config.baseUrl, `/rx/deleteRx?parameterValue=Delete2&deleteRxId=del_${drug}`), { maxRedirects: 0 });
    h.assert(response.status() === 405, `GET rx/deleteRx Delete2 answered HTTP ${response.status()} instead of 405`);
    h.assert(state() === '0||', `A refused GET changed the drug to ${state()}`);
  });

  await s.step('accepting Del posts the delete with a CSRF token, archives the drug as deleted and logs it', async () => {
    const [request] = await Promise.all([
      rx.waitForRequest(isDelete),
      rx.waitForEvent('load'),
      h.withExpectedDialogs(rx, () => del.click(), { accept: true }),
    ]);
    const response = await request.response();
    h.assert(response && response.status() === 200, `The Del POST answered HTTP ${response && response.status()}`);
    h.assert((request.headers()['csrf-token'] || '').trim(), 'The Del POST carried no CSRF-TOKEN header');
    const body = new URLSearchParams(request.postData() || '');
    h.assert(body.get('deleteRxId') === `del_${drug}`, `The Del POST named ${JSON.stringify(body.get('deleteRxId'))}`);
    h.assert(state() === '1|deleted|1', `The deleted drug is stored as ${state()}`);
    await expectValue(sql, `SELECT COUNT(*) FROM log WHERE action='delete' AND content='prescription'
      AND contentId=${h.sqlString(drug)} AND demographic_no=${patient}`, '1', 'The delete was not logged against the patient');
    await profileLoaded(rx);
    await rx.locator(`#del_${kept}`).waitFor({ state: 'visible' });
    h.assert(await rx.locator(`#del_${drug}, #rxDate_${drug}`).count() === 0, 'The current profile still lists the deleted drug');
    h.assert(sql.value(`SELECT archived FROM drugs WHERE drugid=${kept}`) === '0', 'Deleting one drug archived the other');
  });

  await s.step('Show All lists the deleted drug struck through', async () => {
    await rx.locator('a[onclick*="selectDrugProfile(\'all\'"]').click();
    await profileLoaded(rx);
    const start = rx.locator(`#rxDate_${drug}`);
    await start.waitFor({ state: 'visible' });
    const shown = await start.evaluate(el => ({ classes: el.className.split(/\s+/), line: getComputedStyle(el).textDecorationLine }));
    h.assert(shown.classes.includes('deleted') && shown.line.includes('line-through'),
      `Show All lists the deleted drug as ${JSON.stringify(shown)}`);
    const keptClasses = await rx.locator(`#rxDate_${kept}`).evaluate(el => el.className.split(/\s+/));
    h.assert(!keptClasses.includes('deleted'), `Show All marks the kept drug as ${JSON.stringify(keptClasses)}`);
  });

  await s.step('the E-Chart Medications box does not list the deleted drug', async () => {
    const chart = await s.chart();
    const box = chart.locator('#Rxlist');
    await box.waitFor({ state: 'attached' });
    const listed = await box.innerText();
    h.assert(listed.includes(`Kept ${marker}`), `The E-Chart Medications box does not list the kept drug: ${JSON.stringify(listed)}`);
    h.assert(!listed.includes('Gone '), 'The E-Chart Medications box still lists the deleted drug');
  });
}

if (require.main === module) runWorkflow('rx-delete-drug', workflow, { openPatient: true });
module.exports = { workflow };
