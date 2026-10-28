#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
// rx-static-script-pickup: the pick-up line of a saved prescription, on the static script (rx/ViewStaticScript2,
// StaticScript2.jsp) and on the drug record it links to (rx/ViewDisplayRxRecord, DisplayRxRecord.jsp). A pick-up date
// saved without a time is stored at midnight and should show as the date alone; a pick-up time shows as a time.
// rx-card-options covers saving and re-prescribing a pick-up date and time, not how they are shown.
// User path: Schedule ▸ Search ▸ Master Record ▸ Prescriptions ▸ type a name ▸ Custom Drug ▸ Instructions ▸ the card's
// "more" ▸ Pick up date (and time) ▸ Save Only ▸ the drug's name in the profile (the static script) ▸ the drug record.
//   1. a pick-up date with the time 14:30 is stored as 14:30 and shows its time on both pages;
//   2. LAST: a pick-up date with no time is stored at midnight and shows as the date alone on both pages (#4861).
// Fixtures: the owned FAKE- patient and two marker-named custom drugs. Cleanup removes the patient's drugs,
// prescriptions, drugReason and DigitalSignature rows, and asserts none remain.
const h = require('./lib/playwright-harness');
const { runWorkflow, expectValue } = require('./lib/workflow-session');
const { masterRecordLink } = require('./lib/master-record-link');
const { stageCustomDrug } = require('./rx-stash-patient-isolation-playwright-checks');

const PICKUP_DATE = '2026-12-01';
const isPost = route => response => response.request().method() === 'POST' && h.pathOnly(response.url()).endsWith(route);
/** Waits for the response the predicate names; a timeout says which request never answered. */
const answered = (page, predicate, what) => page.waitForResponse(predicate, { timeout: 60000 })
  .catch(error => { throw new Error(`${what} never answered: ${error.message.split('\n')[0]}`); });

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
  // The Master Record's Prescriptions link, by the URL it opens (an onclick on older pages, a .patient-nav href since #4468).
  const rx = await s.popup(s.master, masterRecordLink(s.master, '/rx/choosePatient').first(),
    'rx-module');
  h.assert(new URL(rx.url()).searchParams.get('demographicNo') === patient, 'The Rx module opened for another patient');
  await rx.locator('#searchString').waitFor({ state: 'visible' });
  await rx.waitForLoadState('networkidle');

  /** Writes one custom drug with the given pick-up date and time through the card, Save Only; returns its id. */
  async function saveWithPickup(name, time) {
    const card = await stageCustomDrug(rx, name);
    await rx.locator(`#instructions_${card}`).fill('1 tab PO OD x 30 days');
    // Leaving the instructions box (here for the indication label) posts rx/UpdateScript to parse them.
    const [parsed] = await Promise.all([
      answered(rx, isPost('/rx/UpdateScript'), 'rx/UpdateScript (parsing the instructions)'),
      rx.locator(`label[for="jsonDxSearch_${card}"]`).click(),
    ]);
    h.assert(parsed.status() === 200, `rx/UpdateScript answered HTTP ${parsed.status()}`);
    // The pick-up fields sit in the card's extra options, shown by its "more" link.
    await rx.locator(`#moreLessWord_${card}`).click();
    await rx.locator(`#rx_more_${card}`).waitFor({ state: 'visible' });
    await rx.locator(`#pickupDate_${card}`).fill(PICKUP_DATE);
    await rx.locator(`#pickupTime_${card}`).fill(time);
    const [saved] = await Promise.all([
      answered(rx, r => isPost('/rx/WriteScript')(r) && new URL(r.url()).searchParams.get('parameterValue') === 'updateSaveAllDrugs',
        'Save Only (rx/WriteScript updateSaveAllDrugs)'),
      rx.locator('#saveOnlyButton').click(),
    ]);
    h.assert(saved.status() === 200, `Save Only answered HTTP ${saved.status()}`);
    await expectValue(sql, `SELECT COUNT(*) FROM drugs WHERE demographic_no=${patient} AND customName=${h.sqlString(name)}`, '1',
      `Save Only did not store ${name}`);
    return sql.value(`SELECT drugid FROM drugs WHERE demographic_no=${patient} AND customName=${h.sqlString(name)}`);
  }

  /** The pick-up as the static script and the drug record show it. */
  async function shownPickup(drug) {
    await rx.reload({ waitUntil: 'domcontentloaded' });
    await rx.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    const link = rx.locator(`#prescrip_${drug}[href*="ViewStaticScript2"]`);
    await link.waitFor({ state: 'visible', timeout: 20000 });
    // Open the real profile link in another tab, keeping the prescribing
    // window available for the second medication's entry.
    const [page] = await Promise.all([
      s.context.waitForEvent('page'),
      link.click({ modifiers: ['ControlOrMeta'] }),
    ]);
    await page.waitForURL(/\/rx\/ViewStaticScript2(?:\?|$)/);
    await page.waitForLoadState('networkidle', { timeout: 20000 }).catch(() => {});
    const text = (await page.locator('body').innerText()).replace(/\s+/g, ' ');
    const script = (/Pickup Date (\d{4}-\d{2}-\d{2})(?: (\d{1,2}:\d{2} ?[ap]\.? ?m\.?))?/i.exec(text) || []).slice(1);
    // The static script's drug-record link (rx/ViewDisplayRxRecord) opens the drug's own record.
    const details = await s.popup(page,
      page.locator(`a[onclick*="/rx/ViewDisplayRxRecord?id=${drug}'"]`).first(), 'pickup-drug-record');
    const record = (await details.locator('td.label', { hasText: 'Pickup Date/Time' }).first()
      .locator('xpath=following-sibling::td[1]').innerText()).trim();
    await details.close();
    await page.close();
    return { scriptDate: script[0], scriptTime: script[1], record };
  }

  await s.step('a pick-up date with a time is stored with that time and shows it on the static script and the drug record', async () => {
    const drug = await saveWithPickup(`Timed ${marker} FAKE drug`, '14:30');
    await expectValue(sql, `SELECT DATE_FORMAT(pickup_datetime,'%Y-%m-%d %H:%i') FROM drugs WHERE drugid=${drug}`, `${PICKUP_DATE} 14:30`,
      'The pick-up date and time were not stored as entered');
    const shown = await shownPickup(drug);
    h.assert(shown.scriptDate === PICKUP_DATE && /^0?2:30 ?p/i.test(shown.scriptTime || ''),
      `The static script shows the pick-up as ${JSON.stringify(shown)}`);
    h.assert(shown.record === `${PICKUP_DATE} 14:30`, `The drug record shows the pick-up as ${JSON.stringify(shown.record)}`);
  });

  await s.step('a pick-up date with no time is stored at midnight and shows as the date alone on both pages', async () => {
    const drug = await saveWithPickup(`Dated ${marker} FAKE drug`, '');
    await expectValue(sql, `SELECT DATE_FORMAT(pickup_datetime,'%Y-%m-%d %H:%i') FROM drugs WHERE drugid=${drug}`, `${PICKUP_DATE} 00:00`,
      'A pick-up date without a time was not stored at midnight');
    const shown = await shownPickup(drug);
    h.assert(shown.scriptDate === PICKUP_DATE && !shown.scriptTime,
      `The static script shows a time for a pick-up date saved without one (${JSON.stringify(shown)})`);
    h.assert(shown.record === PICKUP_DATE, `The drug record shows the date-only pick-up as ${JSON.stringify(shown.record)}`);
  });
}

if (require.main === module) runWorkflow('rx-static-script-pickup', workflow, { openPatient: true });
module.exports = { workflow };
