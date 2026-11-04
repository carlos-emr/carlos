#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// rx-rerx-cancel: the ReRx confirmation box on the Rx page (SearchDrug3.jsp). Ticking a profile drug's ReRx box
// adds it to the session's ReRx list (rx/WriteScript updateReRxDrug addToReRxDrugIdList) and shows the box with
// Cancel and Stage medication. Cancel (cancelAndClearSelection) takes each ticked drug off the list again
// (removeFromReRxDrugIdList), unticks it and hides the box; Save does the same for ticks left unstaged, after a
// warning. rx-med-history and others press Stage, and rx-stash-patient-isolation checks one removal request's
// patient; no check pressed Cancel or left a tick unstaged at Save.
// User path: Schedule ▸ Search ▸ Master Record ▸ Prescriptions ▸ the profile's ReRx boxes ▸ Cancel / Stage
// medication ▸ Save Only.
//   1. ticking A and B adds each to the ReRx list and shows the box with 2 selected;
//   2. Cancel takes both off the list, unticks them and hides the box, staging nothing; reloaded, the profile
//      (which ticks the drugs still on the server's list) ticks neither;
//   3. ticking only C, Stage medication and Save Only re-prescribe C alone: A and B stay current;
//   4. LAST: with A staged and B ticked but not staged, Save Only warns that the unstaged drug will not be
//      re-prescribed; accepting saves A's copy, takes B off the list, and leaves B current.
// Fixtures: the owned FAKE- patient and three marker-named current custom drugs inserted for it (A, B, C). Cleanup
// removes the patient's drugs, prescriptions, drugReason and DigitalSignature rows, and asserts none remain. No
// clinic-wide state is changed.
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { masterRecordLink } = require('./lib/master-record-link');

const q = h.sqlString;
const isPost = route => response => response.request().method() === 'POST' && h.pathOnly(response.url()).endsWith(route);
/** Waits for the response the predicate names; a timeout says which request never answered. */
const answered = (page, predicate, what) => page.waitForResponse(predicate, { timeout: 60000 })
  .catch(error => { throw new Error(`${what} never answered: ${error.message.split('\n')[0]}`); });
/** An rx/WriteScript updateReRxDrug POST for the given action and drug. */
const reRxUpdate = (action, drug) => response => isPost('/rx/WriteScript')(response) && (() => {
  const form = new URLSearchParams(response.request().postData() || '');
  return form.get('parameterValue') === 'updateReRxDrug' && form.get('action') === action && form.get('reRxDrugId') === String(drug);
})();

async function workflow(s) {
  const { sql, patient, provider, marker } = s;
  s.cleanup(() => {
    sql.execute(`DELETE FROM drugReason WHERE demographicNo=${patient};
      DELETE FROM drugs WHERE demographic_no=${patient}; DELETE FROM prescription WHERE demographic_no=${patient};
      DELETE FROM DigitalSignature WHERE demographicId=${patient} AND ModuleType='PRESCRIPTION'`);
    h.assert(sql.value(`SELECT (SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM prescription WHERE demographic_no=${patient})
      + (SELECT COUNT(*) FROM DigitalSignature WHERE demographicId=${patient})`) === '0', 'Owned Rx rows were not removed');
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
  const [a, b, c] = ['A', 'B', 'C'].map(insertDrug);
  const archivedState = () => JSON.stringify(sql.rows(`SELECT customName, archived, IFNULL(archived_reason,'') FROM drugs
    WHERE drugid IN (${a.id},${b.id},${c.id}) ORDER BY drugid`));
  const copies = name => sql.value(`SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND customName=${q(name)}
    AND drugid NOT IN (${a.id},${b.id},${c.id})`);

  // The Master Record's Prescriptions link, by the URL it opens (an onclick on older pages, a .patient-nav href since #4468).
  const rx = await s.popup(s.master, masterRecordLink(s.master, '/rx/choosePatient').first(),
    'rx-module');
  h.assert(new URL(rx.url()).searchParams.get('demographicNo') === patient, 'The Rx module opened for another patient');
  await rx.locator('#searchString').waitFor({ state: 'visible' });
  await rx.waitForLoadState('networkidle');
  const box = rx.locator('#reRxConfirmBox');
  const tickBox = drug => rx.locator(`#reRxCheckBox_${drug.id}`);
  const staged = () => rx.locator("[id^='drugName_']").evaluateAll(els => els.map(el => el.value.trim()));
  const boxShown = async () => (await box.getAttribute('class') || '').split(/\s+/).includes('show');

  /** Ticks a drug's ReRx box and waits for the session's ReRx list to take it. */
  async function tick(drug) {
    const [added] = await Promise.all([
      answered(rx, reRxUpdate('addToReRxDrugIdList', drug.id), `adding ${drug.name} to the ReRx list`),
      tickBox(drug).check(),
    ]);
    h.assert(added.status() === 200, `Adding ${drug.name} to the ReRx list answered HTTP ${added.status()}`);
  }
  /** Stage medication, and the cards it adds for the ticked drugs. */
  async function stage(count) {
    const before = (await staged()).length;
    await box.locator('input[name="stage"]').click();
    await rx.waitForFunction(n => document.querySelectorAll("[id^='drugName_']").length >= n, before + count, { timeout: 30000 });
    await rx.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
  }
  /** Save Only: the save request, with whatever dialogs it raises answered as given. */
  async function saveOnly(dialogOptions) {
    let saved;
    const dialogs = await h.withExpectedDialogs(rx, async () => {
      [saved] = await Promise.all([
        answered(rx, response => isPost('/rx/WriteScript')(response)
          && new URL(response.url()).searchParams.get('parameterValue') === 'updateSaveAllDrugs', 'Save Only (rx/WriteScript updateSaveAllDrugs)'),
        rx.locator('#saveOnlyButton').click(),
      ]);
    }, dialogOptions);
    h.assert(saved.status() === 200, `Save Only answered HTTP ${saved.status()}`);
    return dialogs;
  }
  /** Reloads the Rx page until the profile lists the given (current) drug. */
  async function reload(listed) {
    await rx.reload({ waitUntil: 'domcontentloaded' });
    await rx.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    await tickBox(listed).waitFor({ state: 'visible', timeout: 20000 });
  }

  await s.step('ticking two drugs adds each to the ReRx list and shows the box with 2 selected', async () => {
    h.assert(!(await boxShown()), 'The ReRx box shows before anything is ticked');
    await tick(a);
    await tick(b);
    h.assert(await boxShown(), 'The ReRx box is hidden with two drugs ticked');
    h.assert((await rx.locator('#selectedCount').innerText()).trim() === '2', 'The ReRx box does not count 2 selected');
  });

  await s.step('Cancel takes both off the ReRx list, unticks them and hides the box', async () => {
    await Promise.all([
      answered(rx, reRxUpdate('removeFromReRxDrugIdList', a.id), `taking ${a.name} off the ReRx list`),
      answered(rx, reRxUpdate('removeFromReRxDrugIdList', b.id), `taking ${b.name} off the ReRx list`),
      box.locator('input[name="cancel"]').click(),
    ]);
    await rx.waitForFunction(() => !document.getElementById('reRxConfirmBox').classList.contains('show'), null, { timeout: 20000 });
    h.assert(!(await tickBox(a).isChecked()) && !(await tickBox(b).isChecked()), 'Cancel left a ReRx box ticked');
    h.assert((await staged()).length === 0, `Cancel staged ${JSON.stringify(await staged())}`);
    // The profile ticks the drugs still on the session's ReRx list, so a reload shows what the server kept.
    await reload(a);
    h.assert(!(await tickBox(a).isChecked()) && !(await tickBox(b).isChecked()), 'After Cancel the server still lists a drug for ReRx');
  });

  await s.step('ticking only the third drug, Stage medication and Save Only re-prescribe it alone', async () => {
    await tick(c);
    await stage(1);
    h.assert(JSON.stringify(await staged()) === JSON.stringify([c.name]), `Stage medication staged ${JSON.stringify(await staged())}`);
    const dialogs = await saveOnly();
    h.assert(dialogs.length === 0, `Save Only asked ${JSON.stringify(dialogs.map(entry => entry.text))}`);
    await expectValue(sql, `SELECT archived_reason FROM drugs WHERE drugid=${c.id}`, 'represcribed', 'Save Only did not archive the re-prescribed drug');
    h.assert(copies(c.name) === '1' && copies(a.name) === '0' && copies(b.name) === '0', 'Save Only re-prescribed more than the staged drug');
    h.assert(archivedState() === JSON.stringify([[a.name, '0', ''], [b.name, '0', ''], [c.name, '1', 'represcribed']]),
      `After Cancel and one re-prescription the drugs are ${archivedState()}`);
  });

  await s.step('Save Only warns about a ticked drug left unstaged and leaves it current', async () => {
    await reload(a);
    await tick(a);
    await stage(1);
    await tick(b);
    const dialogs = await saveOnly({ accept: true });
    // The parts of the warning that are translated (the sentence's first word is not; see the warning's code).
    h.assert(dialogs.length === 1 && dialogs[0].type === 'confirm'
      && /1 unstaged ReRx drug/.test(dialogs[0].text) && /will not be re-prescribed/.test(dialogs[0].text),
    `Save Only with an unstaged ReRx drug asked ${JSON.stringify(dialogs.map(entry => entry.text))}`);
    await expectValue(sql, `SELECT archived_reason FROM drugs WHERE drugid=${a.id}`, 'represcribed', 'Save Only did not re-prescribe the staged drug');
    h.assert(copies(a.name) === '1' && copies(b.name) === '0', 'Save Only re-prescribed the unstaged drug');
    h.assert(sql.value(`SELECT archived FROM drugs WHERE drugid=${b.id}`) === '0', 'Save Only archived the unstaged drug');
    await reload(b);
    h.assert(!(await tickBox(b).isChecked()), 'After Save the server still lists the unstaged drug for ReRx');
  });
}

if (require.main === module) runWorkflow('rx-rerx-cancel', workflow, { openPatient: true });
module.exports = { workflow };
